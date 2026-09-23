package com.noteshadow.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dynamic live-Qwen segmenter. VAD is only a boundary hint: every sample is
 * appended and the tail of an emitted segment is retained for overlap.
 */
final class QwenSegmenter {
    static final int SAMPLE_RATE = 16000;
    static final int MIN_SEGMENT_SAMPLES = SAMPLE_RATE * 3;
    static final int MAX_SEGMENT_SAMPLES = SAMPLE_RATE * 7;
    static final int OVERLAP_SAMPLES = (int) (SAMPLE_RATE * 0.8f);

    private float[] pending = new float[MAX_SEGMENT_SAMPLES + OVERLAP_SAMPLES];
    private int size;
    private boolean sawSpeech;
    private boolean emitted;

    List<float[]> accept(float[] audio, boolean speechDetected) {
        if (audio == null || audio.length == 0) return new ArrayList<>();
        append(audio);
        if (speechDetected) sawSpeech = true;
        List<float[]> result = new ArrayList<>();
        while (size >= MAX_SEGMENT_SAMPLES) {
            result.add(emit(MAX_SEGMENT_SAMPLES));
        }
        if (speechDetected && size > OVERLAP_SAMPLES) sawSpeech = true;
        // Silence ends a sufficiently long utterance. No audio is discarded;
        // the overlap remains in pending and is included in the next segment.
        if (!speechDetected && sawSpeech && size >= MIN_SEGMENT_SAMPLES) {
            result.add(emit(size));
        }
        return result;
    }

    /** Flushes the non-overlap tail at recording stop. */
    List<float[]> flush() {
        List<float[]> result = new ArrayList<>();
        if (size > 0 && (!emitted || size > OVERLAP_SAMPLES)) result.add(emit(size));
        else size = 0; // after an emitted window, an overlap-only tail is already covered
        return result;
    }

    private void append(float[] audio) {
        ensureCapacity(size + audio.length);
        System.arraycopy(audio, 0, pending, size, audio.length);
        size += audio.length;
    }

    private float[] emit(int length) {
        float[] output = Arrays.copyOf(pending, length);
        int retained = Math.min(OVERLAP_SAMPLES, length);
        int remaining = size - length;
        System.arraycopy(pending, length - retained, pending, 0, retained + remaining);
        size = retained + remaining;
        sawSpeech = false;
        emitted = true;
        return output;
    }

    private void ensureCapacity(int needed) {
        if (needed <= pending.length) return;
        pending = Arrays.copyOf(pending, Math.max(needed, pending.length * 2));
    }
}
