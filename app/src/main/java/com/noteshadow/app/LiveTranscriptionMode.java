package com.noteshadow.app;

/**
 * The persisted strategy used by the foreground transcription service.
 * Values are deliberately stable because they are stored in preferences.
 */
public enum LiveTranscriptionMode {
    QWEN_ONLY("qwen_only"),
    HYBRID("hybrid"),
    SENSEVOICE_FALLBACK("sensevoice_fallback");

    private final String value;

    LiveTranscriptionMode(String value) { this.value = value; }

    public String value() { return value; }

    public static LiveTranscriptionMode fromStored(String value) {
        if (value != null) {
            for (LiveTranscriptionMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value.trim())) return mode;
            }
        }
        return HYBRID;
    }

    /** Compatibility mapping for the old MainActivity quality_asr boolean. */
    public static LiveTranscriptionMode fromLegacyQuality(boolean qualityAsr) {
        return qualityAsr ? SENSEVOICE_FALLBACK : HYBRID;
    }
}
