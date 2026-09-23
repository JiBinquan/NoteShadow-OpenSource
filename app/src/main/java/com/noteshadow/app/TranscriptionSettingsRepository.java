package com.noteshadow.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Small durable store shared by the Activity and the restartable ASR service. */
public final class TranscriptionSettingsRepository {
    private static final String PREFS = "transcription_settings";
    private static final String MODE = "live_mode";
    private final SharedPreferences preferences;

    public TranscriptionSettingsRepository(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public LiveTranscriptionMode mode() {
        return LiveTranscriptionMode.fromStored(preferences.getString(MODE, null));
    }

    public void setMode(LiveTranscriptionMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode == null");
        preferences.edit().putString(MODE, mode.value()).apply();
    }

    /** Imports the old preference only when the new setting has not been written. */
    public LiveTranscriptionMode migrateLegacyIfNeeded(boolean legacyQualityAsr) {
        if (preferences.contains(MODE)) return mode();
        LiveTranscriptionMode migrated = LiveTranscriptionMode.fromLegacyQuality(legacyQualityAsr);
        setMode(migrated);
        return migrated;
    }
}
