package com.noteshadow.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k2fsa.sherpa.onnx.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local ASR: Zipformer provides an immediate visible draft. The formal note
 * is Qwen only, fed every captured sample through dynamic 3-7 second windows.
 */
public final class LocalSherpaAsr implements AsrEngine {
    private static final String TAG = "NoteShadowSherpa";
    private static final int SAMPLE_RATE = 16000;
    private static final int VAD_WINDOW = 512;
    private static final float[] END_OF_AUDIO = new float[0];
    private final Context context;
    private final Listener listener;
    private final LiveTranscriptionMode mode;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile AudioRecord audioRecord;
    private Thread worker;

    /**
     * AudioRecord is free to return a buffer that is larger or smaller than a
     * VAD window. Keep every sample and present VAD with exact, contiguous
     * windows; truncating a read here corrupts the utterance sent to ASR.
     */
    private static final class VadInput {
        private final float[] pending = new float[VAD_WINDOW];
        private int size;

        void accept(Vad vad, float[] audio) {
            int offset = 0;
            while (offset < audio.length) {
                int count = Math.min(VAD_WINDOW - size, audio.length - offset);
                System.arraycopy(audio, offset, pending, size, count);
                size += count;
                offset += count;
                if (size == VAD_WINDOW) {
                    vad.acceptWaveform(pending);
                    size = 0;
                }
            }
        }

        void flush(Vad vad) {
            if (size > 0) {
                Arrays.fill(pending, size, VAD_WINDOW, 0f);
                vad.acceptWaveform(pending);
                size = 0;
            }
            vad.flush();
        }
    }

    private static final class QwenWindow {
        final int index;
        final float[] samples;
        QwenWindow(int index, float[] samples) { this.index = index; this.samples = samples; }
    }
    private static final QwenWindow END_OF_QWEN = new QwenWindow(-1, END_OF_AUDIO);

    public LocalSherpaAsr(Context context, Listener listener, boolean qualityMode) {
        this(context, listener, LiveTranscriptionMode.fromLegacyQuality(qualityMode));
    }

    public LocalSherpaAsr(Context context, Listener listener, LiveTranscriptionMode mode) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.mode = mode == null ? LiveTranscriptionMode.HYBRID : mode;
    }

    @Override public boolean isAvailable() {
        return Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
    }

    @Override public String unavailableReason() { return "本地中文转写仅支持 ARM64 设备"; }

    @Override public synchronized void start() {
        if (running || !isAvailable()) return;
        running = true;
        boolean quality = mode == LiveTranscriptionMode.SENSEVOICE_FALLBACK;
        Runnable recognition = quality ? this::runQualityRecognition
                : (mode == LiveTranscriptionMode.QWEN_ONLY
                ? this::runQwenOnlyRecognition : this::runHybridRecognition);
        worker = new Thread(() -> {
            try {
                recognition.run();
            } finally {
                listener.onStopped(this);
            }
        },
                quality ? "sensevoice-quality-asr"
                        : (mode == LiveTranscriptionMode.QWEN_ONLY
                        ? "qwen-only-asr" : "zipformer-qwen-asr"));
        worker.start();
    }

    @Override public synchronized void stop() {
        running = false;
        AudioRecord current = audioRecord;
        if (current != null) try { current.stop(); } catch (Throwable ignored) {}
        worker = null;
    }

    @SuppressLint("MissingPermission")
    private void runQualityRecognition() {
        OfflineRecognizer recognizer = null;
        Vad vad = null;
        AudioRecord recorder = null;
        BlockingQueue<float[]> segments = null;
        Thread decoder = null;
        VadInput vadInput = new VadInput();
        try {
            state("正在加载 SenseVoice 质量模型");
            File dir = prepareQualityModel();
            recognizer = createSenseVoice(dir);
            vad = createVad(dir);
            File selfTest = new File(dir, "self-test.wav");
            if (selfTest.isFile()) runQualitySelfTest(recognizer, selfTest);
            if (!running) return;
            segments = new LinkedBlockingQueue<>();
            OfflineRecognizer decodeRecognizer = recognizer;
            BlockingQueue<float[]> decodeQueue = segments;
            decoder = new Thread(() -> decodeLoop(decodeQueue, decodeRecognizer), "sensevoice-decoder");
            decoder.start();
            recorder = createAudioRecord(VAD_WINDOW * 8);
            audioRecord = recorder;
            recorder.startRecording();
            ensureRecording(recorder);
            state("质量转写中 · 自动分句");
            short[] pcm = new short[VAD_WINDOW];
            while (running) {
                int count = recorder.read(pcm, 0, pcm.length);
                if (count <= 0) continue;
                float[] samples = toFloat(pcm, count);
                vadInput.accept(vad, samples);
                partial(vad.isSpeechDetected() ? "已检测到语音，等待句尾" : "");
                queueSegments(vad, segments);
            }
            vadInput.flush(vad);
            queueSegments(vad, segments);
            partial("");
        } catch (Throwable e) {
            Log.e(TAG, "SenseVoice ASR failed", e);
            state("质量转写失败：" + e.getClass().getSimpleName());
        } finally {
            running = false;
            audioRecord = null;
            releaseRecorder(recorder);
            if (segments != null) segments.offer(END_OF_AUDIO);
            if (decoder != null) {
                awaitDecoder(decoder);
            }
            if (vad != null) vad.release();
            if (recognizer != null) recognizer.release();
        }
    }

    private void queueSegments(Vad vad, BlockingQueue<float[]> segments) {
        while (!vad.empty()) {
            SpeechSegment segment = vad.front();
            vad.pop();
            float[] samples = segment.getSamples();
            if (samples == null || samples.length < SAMPLE_RATE / 3) continue;
            segments.offer(samples);
        }
    }

    private void decodeLoop(BlockingQueue<float[]> segments, OfflineRecognizer recognizer) {
        while (true) {
            float[] samples;
            try { samples = segments.take(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            if (samples == END_OF_AUDIO) return;
            state("正在识别整句");
            OfflineStream stream = recognizer.createStream();
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE);
                recognizer.decode(stream);
                OfflineRecognizerResult result = recognizer.getResult(stream);
                String text = result == null ? "" : cleanSenseVoiceText(result.getText());
                if (!text.isEmpty()) finalText(text);
            } catch (Throwable e) {
                Log.e(TAG, "SenseVoice segment failed", e);
                state("本句识别失败，继续监听");
            } finally { stream.release(); }
            state("质量转写中 · 自动分句");
        }
    }

    /** Qwen-only path: retain the same VAD and 3-7 second overlapping windows,
     * but do not load or run the Zipformer draft model. */
    @SuppressLint("MissingPermission")
    private void runQwenOnlyRecognition() {
        OfflineRecognizer qwenRecognizer = null;
        Vad vad = null;
        AudioRecord recorder = null;
        BlockingQueue<QwenWindow> windows = null;
        Thread decoder = null;
        QwenSegmenter segmenter = new QwenSegmenter();
        VadInput vadInput = new VadInput();
        int nextWindowIndex = 1;
        try {
            state("正在加载 Qwen 正式模型");
            File qwenDir = prepareQwenModel();
            vad = createVad(prepareVadModel());
            qwenRecognizer = createQwen(qwenDir);
            if (!running) return;

            windows = new LinkedBlockingQueue<>();
            AtomicInteger completed = new AtomicInteger();
            OfflineRecognizer decoderRecognizer = qwenRecognizer;
            BlockingQueue<QwenWindow> decodeQueue = windows;
            decoder = new Thread(() -> decodeQwenLoop(decodeQueue, decoderRecognizer, completed),
                    "qwen-window-decoder");
            decoder.start();

            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = createAudioRecord(Math.max(min, SAMPLE_RATE / 2) * 2);
            audioRecord = recorder;
            recorder.startRecording();
            ensureRecording(recorder);
            state("Qwen 动态 3–7 秒正式转写 0 段");
            short[] pcm = new short[Math.max(1600, min / 2)];
            while (running) {
                int count = recorder.read(pcm, 0, pcm.length);
                if (count <= 0) continue;
                float[] audio = toFloat(pcm, count);
                boolean speechDetected = vad.isSpeechDetected();
                int added = 0;
                for (float[] samples : segmenter.accept(audio, speechDetected)) {
                    windows.offer(new QwenWindow(nextWindowIndex++, samples));
                    added++;
                }
                // Feed VAD every sample batch. It is a boundary hint only;
                // QwenSegmenter remains the authority for retained audio.
                vadInput.accept(vad, audio);
                while (!vad.empty()) vad.pop();
                if (added > 0) state("Qwen 正式转写 · 等待 " + windows.size() + " 段");
            }
        } catch (Throwable e) {
            Log.e(TAG, "Qwen-only ASR failed", e);
            state("Qwen 转写失败：" + e.getClass().getSimpleName());
        } finally {
            running = false;
            audioRecord = null;
            releaseRecorder(recorder);
            if (windows != null) {
                try {
                    for (float[] samples : segmenter.flush()) {
                        windows.offer(new QwenWindow(nextWindowIndex++, samples));
                    }
                } catch (Throwable e) { Log.e(TAG, "Qwen tail flush failed", e); }
                windows.offer(END_OF_QWEN);
            }
            if (decoder != null) {
                awaitDecoder(decoder);
            }
            if (qwenRecognizer != null) qwenRecognizer.release();
            if (vad != null) vad.release();
        }
    }

    /** One microphone stream feeds the live draft, VAD and Qwen queue. */
    @SuppressLint("MissingPermission")
    private void runHybridRecognition() {
        OnlineRecognizer draftRecognizer = null;
        OnlineStream draftStream = null;
        OfflineRecognizer qwenRecognizer = null;
        Vad vad = null;
        AudioRecord recorder = null;
        BlockingQueue<QwenWindow> windows = null;
        Thread decoder = null;
        VadInput vadInput = new VadInput();
        QwenSegmenter segmenter = new QwenSegmenter();
        int nextWindowIndex = 1;
        try {
            state("正在加载 Zipformer 草稿与 Qwen 正式模型");
            File realtimeDir = prepareRealtimeModel();
            File qwenDir = prepareQwenModel();
            vad = createVad(prepareVadModel());
            draftRecognizer = createRealtimeRecognizer(realtimeDir);
            qwenRecognizer = createQwen(qwenDir);
            if (!running) return;

            windows = new LinkedBlockingQueue<>();
            AtomicInteger completed = new AtomicInteger();
            OfflineRecognizer decoderRecognizer = qwenRecognizer;
            BlockingQueue<QwenWindow> decodeQueue = windows;
            decoder = new Thread(() -> decodeQwenLoop(decodeQueue, decoderRecognizer, completed), "qwen-window-decoder");
            decoder.start();

            draftStream = draftRecognizer.createStream();
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = createAudioRecord(Math.max(min, SAMPLE_RATE / 2) * 2);
            audioRecord = recorder;
            recorder.startRecording();
            ensureRecording(recorder);
            state("Zipformer 草稿实时显示 · Qwen 动态 3–7 秒正式转写 0 段");
            short[] pcm = new short[Math.max(1600, min / 2)];
            String last = "";
            while (running) {
                int count = recorder.read(pcm, 0, pcm.length);
                if (count <= 0) continue;
                float[] audio = toFloat(pcm, count);
                boolean speechDetected = vad.isSpeechDetected();
                int added = 0;
                for (float[] samples : segmenter.accept(audio, speechDetected)) {
                    windows.offer(new QwenWindow(nextWindowIndex++, samples));
                    added++;
                }
                // Update the boundary hint for the next microphone read only
                // after Qwen has retained this batch, so a VAD/draft failure
                // cannot strand already-captured audio outside the tail flush.
                vadInput.accept(vad, audio);
                while (!vad.empty()) vad.pop();
                draftStream.acceptWaveform(audio, SAMPLE_RATE);
                while (draftRecognizer.isReady(draftStream)) draftRecognizer.decode(draftStream);
                OnlineRecognizerResult result = draftRecognizer.getResult(draftStream);
                String text = result == null || result.getText() == null ? "" : result.getText().trim();
                if (!text.equals(last)) {
                    last = text;
                    partial(text.isEmpty() ? "" : "草稿：" + text);
                }

                if (added > 0) {
                    state("草稿实时显示 · Qwen 等待 " + windows.size() + " 段");
                }
                if (draftRecognizer.isEndpoint(draftStream)) {
                    if (!text.isEmpty()) listener.onDraftFinal(text);
                    draftRecognizer.reset(draftStream);
                    last = "";
                }
            }
            draftStream.inputFinished();
            while (draftRecognizer.isReady(draftStream)) draftRecognizer.decode(draftStream);
            OnlineRecognizerResult tailResult = draftRecognizer.getResult(draftStream);
            String tail = tailResult == null || tailResult.getText() == null ? "" : tailResult.getText().trim();
            if (!tail.isEmpty()) listener.onDraftFinal(tail);
            vadInput.flush(vad);
            partial("");
        } catch (Throwable e) {
            Log.e(TAG, "Zipformer + Qwen ASR failed", e);
            state("草稿+精修转写失败：" + e.getClass().getSimpleName());
        } finally {
            running = false;
            audioRecord = null;
            releaseRecorder(recorder);
            if (windows != null) {
                try {
                    for (float[] samples : segmenter.flush()) {
                        windows.offer(new QwenWindow(nextWindowIndex++, samples));
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Qwen tail flush failed", e);
                }
                windows.offer(END_OF_QWEN);
            }
            if (decoder != null) {
                awaitDecoder(decoder);
            }
            if (draftStream != null) draftStream.release();
            if (draftRecognizer != null) draftRecognizer.release();
            if (qwenRecognizer != null) qwenRecognizer.release();
            if (vad != null) vad.release();
        }
    }

    private void decodeQwenLoop(BlockingQueue<QwenWindow> windows, OfflineRecognizer recognizer,
                                AtomicInteger completed) {
        while (true) {
            QwenWindow window;
            try { window = windows.take(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            if (window == END_OF_QWEN) return;
            int index = window.index;
            state("Qwen 正在正式转写第 " + index + " 段 · 等待 " + windows.size() + " 段");
            OfflineStream stream = recognizer.createStream();
            try {
                stream.acceptWaveform(window.samples, SAMPLE_RATE);
                recognizer.decode(stream);
                OfflineRecognizerResult result = recognizer.getResult(stream);
                String text = result == null ? "" : cleanQwenText(result.getText());
                if (text != null && !text.trim().isEmpty()) {
                    // Preserve every decoded result. Even an exact textual
                    // overlap may be a speaker's intentional repetition.
                    text = text.trim();
                    Log.i(TAG, "Qwen result chars=" + text.trim().length());
                    if (!text.isEmpty()) listener.onRefined(text);
                } else {
                    listener.onRefined("【第 " + index + " 段未识别；对应录音已保留】");
                }
            } catch (Throwable e) {
                Log.e(TAG, "Qwen segment failed", e);
                listener.onRefined("【第 " + index + " 段识别失败；对应录音已保留】");
                state("第 " + index + " 段识别失败，继续监听");
            } finally { stream.release(); }
            completed.incrementAndGet();
            String prefix = mode == LiveTranscriptionMode.QWEN_ONLY
                    ? "Qwen 正式转写" : "Zipformer 草稿实时显示 · Qwen";
            state(prefix + " 已完成 " + completed.get() + " 段 · 等待 " + windows.size() + " 段");
        }
    }

    /** Native recognizers must outlive their decoder threads, including a long queued tail. */
    private void awaitDecoder(Thread decoder) {
        boolean interrupted = false;
        while (decoder.isAlive()) {
            try {
                decoder.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private String cleanSenseVoiceText(String value) {
        if (value == null) return "";
        return value.replaceAll("<\\|[^|>]+\\|>", "").trim();
    }

    /** Qwen occasionally echoes control tokens or continues after EOS. */
    private String cleanQwenText(String value) {
        if (value == null) return "";
        int eos = value.indexOf("<|endoftext|>");
        if (eos >= 0) value = value.substring(0, eos);
        return value.replaceAll("<\\|[^|>]+\\|>", "")
                .replaceAll("(?i)language\\s+(Chinese|None)\\s*<asr_text>", "")
                .replace("<asr_text>", "")
                .trim();
    }

    @SuppressLint("MissingPermission")
    private void runRealtimeRecognition() {
        OnlineRecognizer recognizer = null;
        OnlineStream stream = null;
        AudioRecord recorder = null;
        try {
            state("正在加载实时中文模型");
            File dir = prepareRealtimeModel();
            recognizer = createRealtimeRecognizer(dir);
            File selfTest = new File(dir, "self-test.wav");
            if (selfTest.isFile()) runRealtimeSelfTest(recognizer, selfTest);
            if (!running) return;
            stream = recognizer.createStream();
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = createAudioRecord(Math.max(min, SAMPLE_RATE / 2) * 2);
            audioRecord = recorder;
            recorder.startRecording();
            ensureRecording(recorder);
            state("实时转写中");
            short[] pcm = new short[Math.max(1600, min / 2)];
            String last = "";
            while (running) {
                int count = recorder.read(pcm, 0, pcm.length);
                if (count <= 0) continue;
                stream.acceptWaveform(toFloat(pcm, count), SAMPLE_RATE);
                while (recognizer.isReady(stream)) recognizer.decode(stream);
                OnlineRecognizerResult result = recognizer.getResult(stream);
                String text = result == null || result.getText() == null ? "" : result.getText().trim();
                if (!text.equals(last)) { last = text; partial(text); }
                if (recognizer.isEndpoint(stream)) {
                    if (!text.isEmpty()) finalText(text);
                    recognizer.reset(stream);
                    last = "";
                    partial("");
                }
            }
            stream.inputFinished();
            while (recognizer.isReady(stream)) recognizer.decode(stream);
            String tail = recognizer.getResult(stream).getText().trim();
            if (!tail.isEmpty()) finalText(tail);
        } catch (Throwable e) {
            Log.e(TAG, "Realtime ASR failed", e);
            state("实时转写失败：" + e.getClass().getSimpleName());
        } finally {
            running = false;
            audioRecord = null;
            releaseRecorder(recorder);
            if (stream != null) stream.release();
            if (recognizer != null) recognizer.release();
        }
    }

    @SuppressLint("MissingPermission")
    private AudioRecord createAudioRecord(int bufferSize) {
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            throw new IllegalStateException("麦克风初始化失败");
        }
        return recorder;
    }

    private void ensureRecording(AudioRecord recorder) {
        if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new IllegalStateException("麦克风未开始采集");
    }

    private float[] toFloat(short[] pcm, int count) {
        float[] samples = new float[count];
        for (int i = 0; i < count; i++) samples[i] = pcm[i] / 32768.0f;
        return samples;
    }

    private void releaseRecorder(AudioRecord recorder) {
        if (recorder == null) return;
        try { recorder.stop(); } catch (Throwable ignored) {}
        recorder.release();
    }

    private OfflineRecognizer createSenseVoice(File dir) {
        FeatureConfig feat = FeatureConfig.builder().setSampleRate(SAMPLE_RATE)
                .setFeatureDim(80).setDither(0).build();
        OfflineSenseVoiceModelConfig senseVoice = OfflineSenseVoiceModelConfig.builder()
                .setModel(new File(dir, "sensevoice.int8.onnx").getAbsolutePath())
                .setLanguage("zh").setInverseTextNormalization(true).build();
        OfflineModelConfig model = OfflineModelConfig.builder().setSenseVoice(senseVoice)
                .setTokens(new File(dir, "tokens.txt").getAbsolutePath())
                .setNumThreads(2).setDebug(false).setProvider("cpu").build();
        OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
                .setFeatureConfig(feat).setOfflineModelConfig(model)
                .setDecodingMethod("greedy_search").build();
        return new OfflineRecognizer(config);
    }

    private OfflineRecognizer createQwen(File dir) {
        OfflineQwen3AsrModelConfig qwen = OfflineQwen3AsrModelConfig.builder()
                .setConvFrontend(new File(dir, "conv_frontend.onnx").getAbsolutePath())
                .setEncoder(new File(dir, "encoder.int8.onnx").getAbsolutePath())
                .setDecoder(new File(dir, "decoder.int8.onnx").getAbsolutePath())
                .setTokenizer(new File(dir, "tokenizer").getAbsolutePath())
                // 128 truncates a normal fast classroom sentence. Keep a
                // generous generation budget; VAD limits each input segment.
                .setMaxTotalLen(512).setMaxNewTokens(128).build();
        OfflineModelConfig model = OfflineModelConfig.builder().setQwen3Asr(qwen)
                .setTokens("").setNumThreads(2).setDebug(false).setProvider("cpu").build();
        return new OfflineRecognizer(OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(model).setDecodingMethod("greedy_search").build());
    }

    private Vad createVad(File dir) {
        SileroVadModelConfig silero = SileroVadModelConfig.builder()
                .setModel(new File(dir, "silero_vad.onnx").getAbsolutePath())
                .setThreshold(0.45f).setMinSilenceDuration(0.65f)
                // VAD only supplies a speech/silence boundary hint for the
                // dynamic Qwen segmenter; it never filters captured samples.
                .setMinSpeechDuration(0.35f).setMaxSpeechDuration(7.0f)
                .setWindowSize(VAD_WINDOW).build();
        VadModelConfig config = VadModelConfig.builder().setSileroVadModelConfig(silero)
                .setSampleRate(SAMPLE_RATE).setNumThreads(1).setDebug(false).setProvider("cpu").build();
        return new Vad(config);
    }

    private OnlineRecognizer createRealtimeRecognizer(File dir) {
        FeatureConfig feat = FeatureConfig.builder().setSampleRate(SAMPLE_RATE)
                .setFeatureDim(80).setDither(0).build();
        OnlineZipformer2CtcModelConfig ctc = OnlineZipformer2CtcModelConfig.builder()
                .setModel(new File(dir, "model.int8.onnx").getAbsolutePath()).build();
        OnlineModelConfig model = OnlineModelConfig.builder().setZipformer2Ctc(ctc)
                .setTokens(new File(dir, "tokens.txt").getAbsolutePath())
                .setNumThreads(2).setDebug(false).setProvider("cpu").build();
        OnlineRecognizerConfig config = OnlineRecognizerConfig.builder()
                .setFeatureConfig(feat).setOnlineModelConfig(model)
                .setEnableEndpoint(true).setDecodingMethod("greedy_search").build();
        return new OnlineRecognizer(config);
    }

    private void runQualitySelfTest(OfflineRecognizer recognizer, File wave) {
        WaveReader reader = new WaveReader(wave.getAbsolutePath());
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(reader.getSamples(), reader.getSampleRate());
            recognizer.decode(stream);
            String text = cleanSenseVoiceText(recognizer.getResult(stream).getText());
            if (text.isEmpty()) throw new IllegalStateException("SenseVoice 模型自检无结果");
            Log.i(TAG, "SenseVoice self-test passed");
        } finally { stream.release(); }
    }

    private void runRealtimeSelfTest(OnlineRecognizer recognizer, File wave) {
        OnlineStream test = recognizer.createStream();
        try {
            WaveReader reader = new WaveReader(wave.getAbsolutePath());
            test.acceptWaveform(reader.getSamples(), reader.getSampleRate());
            test.inputFinished();
            while (recognizer.isReady(test)) recognizer.decode(test);
            String text = recognizer.getResult(test).getText();
            if (text == null || text.trim().isEmpty()) throw new IllegalStateException("实时模型自检无结果");
            Log.i(TAG, "Realtime ASR self-test passed");
        } finally { test.release(); }
    }

    private File prepareQualityModel() throws Exception {
        File dir = new File(context.getFilesDir(), "asr-sensevoice-v1");
        ensureDir(dir);
        File externalModels = externalModelsDir();
        File externalSenseVoice = new File(externalModels, "sensevoice-int8");
        copyModelFile(new File(externalSenseVoice, "sensevoice.int8.onnx"),
                "asr-quality/sensevoice.int8.onnx", new File(dir, "sensevoice.int8.onnx"),
                200_000_000L, "SenseVoice 模型");
        copyModelFile(new File(externalSenseVoice, "tokens.txt"), "asr-quality/tokens.txt",
                new File(dir, "tokens.txt"), 100_000L, "SenseVoice 词表");
        copyVadModel(dir, externalModels);
        copyAssetIfPresent("asr/self-test.wav", new File(dir, "self-test.wav"), 100_000L);
        return dir;
    }

    private File prepareVadModel() throws Exception {
        File dir = new File(context.getFilesDir(), "asr-vad-v1");
        ensureDir(dir);
        copyVadModel(dir, externalModelsDir());
        return dir;
    }

    private File externalModelsDir() {
        File external = context.getExternalFilesDir(null);
        if (external == null) return new File(context.getFilesDir(), "external-models-unavailable");
        return new File(external, "models");
    }

    private void copyVadModel(File targetDir, File externalModels) throws Exception {
        copyModelFile(new File(new File(externalModels, "silero-vad"), "silero_vad.onnx"),
                "asr-quality/silero_vad.onnx", new File(targetDir, "silero_vad.onnx"),
                500_000L, "Silero VAD 模型");
    }

    /** Prefer an already installed copy, then app-specific external files, then bundled assets. */
    private void copyModelFile(File external, String asset, File target, long minimumLength,
                               String displayName) throws Exception {
        if (target.isFile() && target.length() >= minimumLength) return;
        if (external.isFile() && external.length() >= minimumLength) {
            copyFile(external, target);
            return;
        }
        if (copyAssetIfPresent(asset, target, minimumLength)) return;
        throw new IllegalStateException(displayName + "未找到。请将有效文件放到："
                + external.getAbsolutePath() + "（或使用包含该模型的完整安装包）"
                + "。完整路径已包含文件名。");
    }

    private File prepareQwenModel() throws Exception { return QwenModelFiles.require(context); }

    private File prepareRealtimeModel() throws Exception {
        File dir = new File(context.getFilesDir(), "asr-realtime-v1");
        ensureDir(dir);
        File externalRealtime = new File(externalModelsDir(), "zipformer-int8");
        copyModelFile(new File(externalRealtime, "model.int8.onnx"), "asr/model.int8.onnx",
                new File(dir, "model.int8.onnx"), 20_000_000L, "Zipformer 模型");
        copyModelFile(new File(externalRealtime, "tokens.txt"), "asr/tokens.txt",
                new File(dir, "tokens.txt"), 1_000L, "Zipformer 词表");
        copyAssetIfPresent("asr/self-test.wav", new File(dir, "self-test.wav"), 100_000L);
        return dir;
    }

    private void ensureDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建模型目录");
    }

    private void copyAsset(String asset, File target, long minimumLength) throws Exception {
        if (target.isFile() && target.length() >= minimumLength) return;
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (InputStream in = context.getAssets().open(asset); FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            out.getFD().sync();
        }
        if (!temp.renameTo(target)) throw new IllegalStateException("模型文件写入失败");
    }

    private boolean copyAssetIfPresent(String asset, File target, long minimumLength) throws Exception {
        try {
            copyAsset(asset, target, minimumLength);
            return target.isFile() && target.length() >= minimumLength;
        } catch (java.io.IOException e) {
            // Source-only distributions may omit model assets altogether.
            return false;
        }
    }

    private void copyFile(File source, File target) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (InputStream in = new java.io.FileInputStream(source);
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            out.getFD().sync();
        }
        if (!temp.renameTo(target)) throw new IllegalStateException("模型文件写入失败：" + target.getName());
    }

    private void state(String value) { main.post(() -> listener.onState(value)); }
    private void partial(String value) { main.post(() -> listener.onPartial(value)); }
    private void finalText(String value) { main.post(() -> listener.onFinal(value)); }
}
