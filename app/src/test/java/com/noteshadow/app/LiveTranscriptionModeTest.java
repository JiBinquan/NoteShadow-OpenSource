package com.noteshadow.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveTranscriptionModeTest {
    @Test public void storedValuesAreStableAndUnknownDefaultsToHybrid() {
        assertEquals("qwen_only", LiveTranscriptionMode.QWEN_ONLY.value());
        assertEquals("hybrid", LiveTranscriptionMode.HYBRID.value());
        assertEquals("sensevoice_fallback", LiveTranscriptionMode.SENSEVOICE_FALLBACK.value());
        assertEquals(LiveTranscriptionMode.QWEN_ONLY,
                LiveTranscriptionMode.fromStored("QWEN_ONLY"));
        assertEquals(LiveTranscriptionMode.HYBRID,
                LiveTranscriptionMode.fromStored("not-a-mode"));
        assertEquals(LiveTranscriptionMode.HYBRID,
                LiveTranscriptionMode.fromStored(null));
    }

    @Test public void legacyQualityFlagMapsWithoutChangingOldBehavior() {
        assertEquals(LiveTranscriptionMode.SENSEVOICE_FALLBACK,
                LiveTranscriptionMode.fromLegacyQuality(true));
        assertEquals(LiveTranscriptionMode.HYBRID,
                LiveTranscriptionMode.fromLegacyQuality(false));
    }
}
