package com.noteshadow.app;

/** Decides when foreground work should prevent the display timing out. */
final class ActiveWorkPolicy {
    static final String PREFERENCES = "app_settings";
    static final String KEEP_SCREEN_ON = "keep_screen_on_during_work";
    private ActiveWorkPolicy() { }

    static boolean shouldKeepScreenOn(boolean recording, boolean liveTranscription,
                                      boolean fileTranscription) {
        return shouldKeepScreenOn(true, recording, liveTranscription, fileTranscription);
    }

    static boolean shouldKeepScreenOn(boolean keepScreenOn, boolean recording,
                                      boolean liveTranscription, boolean fileTranscription) {
        return keepScreenOn && (recording || liveTranscription || fileTranscription);
    }

    static boolean shouldShowSafePage(boolean pausedSinceLastResume, boolean alreadySafe) {
        return pausedSinceLastResume && !alreadySafe;
    }
}
