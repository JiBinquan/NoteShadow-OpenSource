package com.noteshadow.app;

/**
 * Grants one in-memory return to the note page after this Activity launches a
 * camera or image picker. A fresh Activity intentionally starts untrusted.
 */
final class NoteMediaReturnPolicy {
    private boolean launchPending;
    private boolean restorePending;

    void onLaunch() {
        launchPending = true;
        restorePending = false;
    }

    void onLaunchFailed() {
        launchPending = false;
        restorePending = false;
    }

    boolean onResult() {
        restorePending = launchPending;
        launchPending = false;
        return restorePending;
    }

    boolean consumeRestore() {
        boolean restore = restorePending;
        restorePending = false;
        return restore;
    }
}
