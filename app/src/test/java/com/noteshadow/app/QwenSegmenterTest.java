package com.noteshadow.app;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QwenSegmenterTest {
    @Test public void keepsAllAudioAndFlushesTail() {
        QwenSegmenter segmenter = new QwenSegmenter();
        List<float[]> output = new ArrayList<>();
        output.addAll(segmenter.accept(samples(2 * 16000), true));
        output.addAll(segmenter.accept(samples(16000), true));
        output.addAll(segmenter.flush());
        assertEquals(1, output.size());
        assertEquals(3 * 16000, output.get(0).length);
    }

    @Test public void silenceAfterThreeSecondsTriggersBoundary() {
        QwenSegmenter segmenter = new QwenSegmenter();
        assertTrue(segmenter.accept(samples(3 * 16000), true).isEmpty());
        List<float[]> output = segmenter.accept(samples(1600), false);
        assertEquals(1, output.size());
        assertEquals(3 * 16000 + 1600, output.get(0).length);
    }

    @Test public void continuousSpeechIsForcedAtSevenSeconds() {
        QwenSegmenter segmenter = new QwenSegmenter();
        List<float[]> output = segmenter.accept(samples(7 * 16000), true);
        assertEquals(1, output.size());
        assertEquals(7 * 16000, output.get(0).length);
        assertTrue(segmenter.flush().isEmpty());
    }

    @Test public void adjacentSegmentsHavePointEightSecondOverlap() {
        QwenSegmenter segmenter = new QwenSegmenter();
        List<float[]> first = segmenter.accept(constantSamples(7 * 16000, 1f), true);
        List<float[]> second = segmenter.accept(constantSamples(6 * 16000 + 3200, 2f), true);
        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(1f, second.get(0)[0], 0.0f);
    }

    @Test public void silenceBoundaryRetainsOverlapBeforeOrderedTail() {
        QwenSegmenter segmenter = new QwenSegmenter();
        segmenter.accept(constantSamples(3 * 16000, 1f), true);
        List<float[]> first = segmenter.accept(constantSamples(1600, 0f), false);
        segmenter.accept(constantSamples(2 * 16000, 2f), true);
        List<float[]> tail = segmenter.flush();

        assertEquals(1, first.size());
        assertEquals(1, tail.size());
        assertEquals(QwenSegmenter.OVERLAP_SAMPLES + 2 * 16000, tail.get(0).length);
        assertEquals(first.get(0)[first.get(0).length - QwenSegmenter.OVERLAP_SAMPLES],
                tail.get(0)[0], 0.0f);
        assertEquals(2f, tail.get(0)[tail.get(0).length - 1], 0.0f);
    }

    @Test public void doesNotEmitOverlapOnlyTail() {
        QwenSegmenter segmenter = new QwenSegmenter();
        segmenter.accept(samples(7 * 16000), true);
        assertTrue(segmenter.flush().isEmpty());
    }

    @Test public void flushesFirstRecordingEvenWhenShorterThanOverlap() {
        for (int length : new int[]{1, QwenSegmenter.OVERLAP_SAMPLES,
                QwenSegmenter.OVERLAP_SAMPLES + 1}) {
            QwenSegmenter segmenter = new QwenSegmenter();
            segmenter.accept(samples(length), true);
            List<float[]> output = segmenter.flush();
            assertEquals(1, output.size());
            assertEquals(length, output.get(0).length);
        }
    }

    @Test public void emptyRecordingDoesNotEmit() {
        assertTrue(new QwenSegmenter().flush().isEmpty());
    }

    @Test public void largeInputBatchDoesNotLoseSamplesAfterFirstWindow() {
        QwenSegmenter segmenter = new QwenSegmenter();
        List<float[]> output = new ArrayList<>();
        output.addAll(segmenter.accept(samples(10 * 16000), true));
        output.addAll(segmenter.flush());

        assertEquals(2, output.size());
        assertEquals(7 * 16000, output.get(0).length);
        assertEquals(3 * 16000 + QwenSegmenter.OVERLAP_SAMPLES, output.get(1).length);
        assertEquals(output.get(0)[7 * 16000 - QwenSegmenter.OVERLAP_SAMPLES],
                output.get(1)[0], 0.0f);
        assertEquals(10 * 16000 - 1, output.get(1)[output.get(1).length - 1], 0.0f);
    }

    private static float[] samples(int count) {
        float[] result = new float[count];
        for (int i = 0; i < count; i++) result[i] = i;
        return result;
    }

    private static float[] constantSamples(int count, float value) {
        float[] result = new float[count];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
