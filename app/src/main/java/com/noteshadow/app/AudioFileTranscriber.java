package com.noteshadow.app;

import android.content.ContentValues;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * File transcription uses the same fixed five-second Qwen windows as live
 * transcription. It never lets VAD suppress quiet or dialect audio.
 */
public final class AudioFileTranscriber {
    private static final String TAG = "NoteShadowFileAsr";
    private static final int SAMPLE_RATE = 16000;
    private static final int QWEN_WINDOW_SAMPLES = SAMPLE_RATE * 5;
    private static final Segment END = new Segment(-1L, -1L, new float[0]);

    public interface Listener {
        void onState(String text);
        void onProgress(long decodedUs, long durationUs, int completedSegments);
        void onDestinationCreated(String outputUri, String publicLocation);
        void onTranscriptLine(String line, long startUs, long endUs);
        void onPaused(String publicLocation, int completedSegments);
        void onCompleted(String publicLocation, int completedSegments);
        void onCancelled(String partialLocation, int completedSegments);
        void onFailed(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile boolean cancelledByUser;
    private volatile boolean pauseRequested;
    private volatile String failureMessage = "";
    private volatile Thread worker;
    private volatile Thread decoder;

    public AudioFileTranscriber(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized boolean start(Uri uri, String displayName) {
        return start(uri, displayName, 0L, "");
    }

    public synchronized boolean start(Uri uri, String displayName, long resumeAfterUs, String existingOutputUri) {
        return start(uri, displayName, resumeAfterUs, existingOutputUri, 0);
    }

    public synchronized boolean start(Uri uri, String displayName, long resumeAfterUs,
                                      String existingOutputUri, int completedSegments) {
        if (running) return false;
        running = true;
        cancelledByUser = false;
        pauseRequested = false;
        failureMessage = "";
        worker = new Thread(() -> run(uri, displayName, Math.max(0L, resumeAfterUs), existingOutputUri,
                Math.max(0, completedSegments)), "file-qwen-transcriber");
        worker.start();
        return true;
    }

    public synchronized void pause() {
        pauseRequested = true;
        running = false;
        Thread d = decoder;
        if (d != null) d.interrupt();
        Thread w = worker;
        if (w != null) w.interrupt();
    }

    public synchronized void cancel() {
        cancelledByUser = true;
        running = false;
        Thread d = decoder;
        if (d != null) d.interrupt();
        Thread w = worker;
        if (w != null) w.interrupt();
    }

    public boolean isRunning() { return running; }

    private void run(Uri uri, String displayName, long resumeAfterUs, String existingOutputUri,
                     int initialCompletedSegments) {
        OfflineRecognizer recognizer = null;
        BufferedWriter writer = null;
        TextDestination destination = null;
        BlockingQueue<Segment> queue = new ArrayBlockingQueue<>(4);
        AtomicInteger completed = new AtomicInteger(initialCompletedSegments);
        try {
            state("正在加载 Qwen 文件转写模型");
            recognizer = createQwen(requireQwenModel());
            destination = openDestination(displayName, existingOutputUri);
            writer = destination.writer;
            if (destination.isNew) {
                writer.write("# " + stripExtension(displayName) + "\n");
                writer.write("# 本地离线 Qwen 转写\n\n");
                writer.flush();
            }
            destinationCreated(destination.outputUri, destination.location);

            final OfflineRecognizer finalRecognizer = recognizer;
            final BufferedWriter finalWriter = writer;
            decoder = new Thread(() -> decodeSegments(queue, finalRecognizer, finalWriter, completed, resumeAfterUs),
                    "file-qwen-decoder");
            decoder.start();
            state(resumeAfterUs > 0 ? "正在从断点恢复转写" : "正在读取录音文件");
            decodeAudio(uri, queue, completed, resumeAfterUs);
            if (running) offer(queue, END);
            Thread d = decoder;
            if (d != null) d.join(120_000);
            writer.flush();
            writer.close();
            writer = null;
            if (!failureMessage.isEmpty()) return;
            if (!running) {
                if (pauseRequested) paused(destination.location, completed.get());
                else cancelled(destination.location, completed.get());
                return;
            }
            completed(destination.location, completed.get());
        } catch (Throwable e) {
            Log.e(TAG, "File transcription failed", e);
            if (!cancelledByUser && failureMessage.isEmpty()) {
                String saved = destination == null ? "" : "；已转写部分保留在 " + destination.location;
                reportFailure(readableError(e) + saved);
            }
        } finally {
            running = false;
            Thread d = decoder;
            if (d != null) d.interrupt();
            decoder = null;
            worker = null;
            if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
            if (recognizer != null) recognizer.release();
        }
    }

    private void decodeAudio(Uri uri, BlockingQueue<Segment> queue, AtomicInteger completed,
                             long resumeAfterUs) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            int track = findAudioTrack(extractor);
            if (track < 0) throw new IllegalArgumentException("所选文件没有可读取的音频轨");
            extractor.selectTrack(track);
            long timelineBaseUs = 0L;
            if (resumeAfterUs > 0L) {
                extractor.seekTo(Math.max(0L, resumeAfterUs - 2_000_000L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                long actual = extractor.getSampleTime();
                timelineBaseUs = actual >= 0L ? actual : Math.max(0L, resumeAfterUs - 2_000_000L);
            }
            MediaFormat inputFormat = extractor.getTrackFormat(track);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IllegalArgumentException("无法识别音频格式");
            long durationUs = inputFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? inputFormat.getLong(MediaFormat.KEY_DURATION) : -1L;
            int sourceRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : SAMPLE_RATE;
            int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int encoding = inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                    ? inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
            PcmResampler resampler = new PcmResampler(sourceRate);
            FixedWindowInput fixed = new FixedWindowInput(timelineBaseUs);

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            long latestUs = 0L;
            long lastProgressUs = -500_000L;
            while (running && !outputDone) {
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        ByteBuffer input = codec.getInputBuffer(index);
                        if (input == null) throw new IllegalStateException("音频解码输入缓冲不可用");
                        int count = extractor.readSampleData(input, 0);
                        if (count < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long timeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(index, 0, count, timeUs, 0);
                            extractor.advance();
                        }
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 10_000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output = codec.getOutputFormat();
                    sourceRate = output.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            ? output.getInteger(MediaFormat.KEY_SAMPLE_RATE) : sourceRate;
                    channels = output.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ? output.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : channels;
                    encoding = output.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? output.getInteger(MediaFormat.KEY_PCM_ENCODING) : encoding;
                    resampler = new PcmResampler(sourceRate);
                } else if (index >= 0) {
                    if (info.size > 0) {
                        ByteBuffer output = codec.getOutputBuffer(index);
                        if (output != null) {
                            ByteBuffer samples = output.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                            samples.position(info.offset);
                            samples.limit(info.offset + info.size);
                            feedPcm(samples.slice().order(ByteOrder.LITTLE_ENDIAN), channels, encoding,
                                    resampler, fixed, queue);
                        }
                    }
                    latestUs = Math.max(latestUs, info.presentationTimeUs);
                    codec.releaseOutputBuffer(index, false);
                    if (latestUs - lastProgressUs >= 500_000L) {
                        lastProgressUs = latestUs;
                        progress(latestUs, durationUs, completed.get());
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            if (running) {
                fixed.flush(queue);
                progress(durationUs > 0 ? durationUs : latestUs, durationUs, completed.get());
            }
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Throwable ignored) {}
                codec.release();
            }
            extractor.release();
        }
    }

    private void feedPcm(ByteBuffer pcm, int channels, int encoding, PcmResampler resampler,
                         FixedWindowInput fixed, BlockingQueue<Segment> queue) throws Exception {
        channels = Math.max(1, channels);
        int bytesPerSample = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
        int frames = pcm.remaining() / Math.max(1, bytesPerSample * channels);
        float[] mono = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float value = 0f;
            for (int c = 0; c < channels; c++) {
                value += encoding == AudioFormat.ENCODING_PCM_FLOAT ? pcm.getFloat() : pcm.getShort() / 32768f;
            }
            mono[frame] = value / channels;
        }
        float[] normalized = resampler.resample(mono);
        if (normalized.length == 0) return;
        fixed.accept(normalized, queue);
    }

    private void decodeSegments(BlockingQueue<Segment> queue, OfflineRecognizer recognizer,
                                BufferedWriter writer, AtomicInteger completed, long resumeAfterUs) {
        try {
            while (running || !queue.isEmpty()) {
                Segment segment = queue.poll(250, TimeUnit.MILLISECONDS);
                if (segment == null) continue;
                if (segment == END) return;
                OfflineStream stream = recognizer.createStream();
                try {
                    state("正在正式转写第 " + (completed.get() + 1) + " 段，等待 " + queue.size() + " 段");
                    stream.acceptWaveform(segment.samples, SAMPLE_RATE);
                    recognizer.decode(stream);
                    OfflineRecognizerResult result = recognizer.getResult(stream);
                String text = result == null ? "" : cleanQwenText(result.getText());
                    if (segment.endUs > resumeAfterUs) {
                        if (text == null || text.trim().isEmpty()) text = "【该 5 秒段未识别；原始录音已保留】";
                        String line = "[" + formatTime((int)(segment.startUs / 1_000_000L)) + "] " + text.trim();
                        writer.write(line + "\n");
                        writer.flush();
                        transcriptLine(line, segment.startUs, segment.endUs);
                    }
                } finally { stream.release(); }
                completed.incrementAndGet();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            Log.e(TAG, "Qwen file segment failed", e);
            if (!cancelledByUser) reportFailure("第 " + (completed.get() + 1) + " 段处理失败：" + readableError(e));
        }
    }

    private int findAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private File requireQwenModel() {
        File external = context.getExternalFilesDir(null);
        if (external == null) throw new IllegalStateException("无法访问应用模型目录");
        File dir = new File(external, "models/qwen3-int8");
        require(new File(dir, "conv_frontend.onnx"), 40_000_000L);
        require(new File(dir, "encoder.int8.onnx"), 170_000_000L);
        require(new File(dir, "decoder.int8.onnx"), 700_000_000L);
        require(new File(dir, "tokenizer/vocab.json"), 2_000_000L);
        require(new File(dir, "tokenizer/merges.txt"), 1_000_000L);
        require(new File(dir, "tokenizer/tokenizer_config.json"), 100L);
        return dir;
    }

    private static void require(File file, long minBytes) {
        if (!file.isFile() || file.length() < minBytes)
            throw new IllegalStateException("Qwen 模型未安装完整：" + file.getName());
    }

    private OfflineRecognizer createQwen(File dir) {
        OfflineQwen3AsrModelConfig qwen = OfflineQwen3AsrModelConfig.builder()
                .setConvFrontend(new File(dir, "conv_frontend.onnx").getAbsolutePath())
                .setEncoder(new File(dir, "encoder.int8.onnx").getAbsolutePath())
                .setDecoder(new File(dir, "decoder.int8.onnx").getAbsolutePath())
                .setTokenizer(new File(dir, "tokenizer").getAbsolutePath())
                .setMaxTotalLen(512).setMaxNewTokens(128).build();
        OfflineModelConfig model = OfflineModelConfig.builder().setQwen3Asr(qwen)
                .setTokens("").setNumThreads(2).setDebug(false).setProvider("cpu").build();
        return new OfflineRecognizer(OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(model).setDecodingMethod("greedy_search").build());
    }


    private TextDestination openDestination(String displayName, String existingOutputUri) throws Exception {
        if (Build.VERSION.SDK_INT < 29) throw new IllegalStateException("当前 Android 版本不支持自动保存到下载目录");
        if (existingOutputUri != null && !existingOutputUri.isEmpty()) {
            Uri uri = Uri.parse(existingOutputUri);
            OutputStream out = context.getContentResolver().openOutputStream(uri, "wa");
            if (out == null) throw new IllegalStateException("无法继续写入已有转写文件");
            return new TextDestination(existingOutputUri, "下载/NoteShadow/已有转写文件",
                    new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)), false);
        }
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        Uri uri = null;
        String name = safeName(stripExtension(displayName)) + "_转写_" + time + ".txt";
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NoteShadow");
            uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("无法创建下载文件");
            OutputStream out = context.getContentResolver().openOutputStream(uri, "w");
            if (out == null) throw new IllegalStateException("无法写入下载文件");
            return new TextDestination(uri.toString(), "下载/NoteShadow/" + name,
                    new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)), true);
        } catch (Throwable e) {
            if (uri != null) try { context.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) {}
            throw e;
        }
    }

    private void offer(BlockingQueue<Segment> queue, Segment segment) throws Exception {
        while (running && !queue.offer(segment, 250, TimeUnit.MILLISECONDS)) { /* bounded back pressure */ }
        if (!running) throw new InterruptedException("已取消");
    }

    private static String stripExtension(String name) {
        if (name == null || name.trim().isEmpty()) return "录音";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String safeName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static String formatTime(int seconds) {
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    private static String readableError(Throwable e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static String cleanQwenText(String value) {
        if (value == null) return "";
        int eos = value.indexOf("<|endoftext|>");
        if (eos >= 0) value = value.substring(0, eos);
        return value.replaceAll("<\\|[^|>]+\\|>", "")
                .replaceAll("(?i)language\\s+(Chinese|None)\\s*<asr_text>", "")
                .replace("<asr_text>", "")
                .trim();
    }

    private void state(String value) { main.post(() -> listener.onState(value)); }
    private void progress(long decodedUs, long durationUs, int completed) {
        main.post(() -> listener.onProgress(decodedUs, durationUs, completed));
    }
    private void completed(String location, int completed) { main.post(() -> listener.onCompleted(location, completed)); }
    private void destinationCreated(String uri, String location) { main.post(() -> listener.onDestinationCreated(uri, location)); }
    private void transcriptLine(String line, long startUs, long endUs) {
        main.post(() -> listener.onTranscriptLine(line, startUs, endUs));
    }
    private void paused(String location, int completed) { main.post(() -> listener.onPaused(location, completed)); }
    private void cancelled(String location, int completed) {
        main.post(() -> listener.onCancelled(location == null ? "" : location, completed));
    }
    private void failed(String message) { main.post(() -> listener.onFailed(message)); }
    private void reportFailure(String message) {
        if (!failureMessage.isEmpty()) return;
        failureMessage = message;
        running = false;
        failed(message);
    }

    private static final class Segment {
        final long startUs;
        final long endUs;
        final float[] samples;
        Segment(long startUs, long endUs, float[] samples) { this.startUs = startUs; this.endUs = endUs; this.samples = samples; }
    }

    private static final class TextDestination {
        final String outputUri;
        final String location;
        final BufferedWriter writer;
        final boolean isNew;
        TextDestination(String outputUri, String location, BufferedWriter writer, boolean isNew) {
            this.outputUri = outputUri;
            this.location = location;
            this.writer = writer;
            this.isNew = isNew;
        }
    }

    /** Emits an append-only, gap-free timeline of fixed Qwen windows. */
    private final class FixedWindowInput {
        private final float[] pending = new float[QWEN_WINDOW_SAMPLES];
        private final long baseUs;
        private int size;
        private long emittedSamples;

        FixedWindowInput(long baseUs) { this.baseUs = Math.max(0L, baseUs); }

        void accept(float[] audio, BlockingQueue<Segment> queue) throws Exception {
            int offset = 0;
            while (offset < audio.length) {
                int count = Math.min(pending.length - size, audio.length - offset);
                System.arraycopy(audio, offset, pending, size, count);
                size += count;
                offset += count;
                if (size == pending.length) emit(queue);
            }
        }
        void flush(BlockingQueue<Segment> queue) throws Exception { if (size > 0) emit(queue); }
        private void emit(BlockingQueue<Segment> queue) throws Exception {
            long startUs = baseUs + emittedSamples * 1_000_000L / SAMPLE_RATE;
            long endUs = startUs + size * 1_000_000L / SAMPLE_RATE;
            offer(queue, new Segment(startUs, endUs, Arrays.copyOf(pending, size)));
            emittedSamples += size;
            size = 0;
        }
    }

    /** Streaming mono linear resampler. It keeps the boundary sample so reads never lose audio. */
    private static final class PcmResampler {
        private final double step;
        private double next;
        private long sourceFrames;
        private boolean hasPrevious;
        private float previous;

        PcmResampler(int sourceRate) { step = Math.max(1, sourceRate) / (double) SAMPLE_RATE; }

        float[] resample(float[] input) {
            if (input.length == 0) return input;
            if (step == 1d) { sourceFrames += input.length; previous = input[input.length - 1]; hasPrevious = true; return input; }
            float[] output = new float[(int)Math.ceil(input.length / step) + 2];
            int written = 0;
            long first = sourceFrames;
            long last = first + input.length - 1L;
            while (true) {
                long left = (long)Math.floor(next);
                if (left + 1L > last) break;
                float a = sampleAt(left, first, input);
                float b = sampleAt(left + 1L, first, input);
                output[written++] = (float)(a + (b - a) * (next - left));
                next += step;
            }
            sourceFrames += input.length;
            previous = input[input.length - 1];
            hasPrevious = true;
            return written == output.length ? output : Arrays.copyOf(output, written);
        }

        private float sampleAt(long index, long first, float[] input) {
            if (index == first - 1L && hasPrevious) return previous;
            int local = (int)(index - first);
            return input[Math.max(0, Math.min(input.length - 1, local))];
        }
    }
}
