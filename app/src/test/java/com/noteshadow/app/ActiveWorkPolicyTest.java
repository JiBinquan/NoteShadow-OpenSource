package com.noteshadow.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ActiveWorkPolicyTest {
    @Test public void idleAllowsScreenTimeout() {
        assertFalse(ActiveWorkPolicy.shouldKeepScreenOn(false, false, false));
    }

    @Test public void everyActiveAudioTaskKeepsScreenOn() {
        assertTrue(ActiveWorkPolicy.shouldKeepScreenOn(true, false, false));
        assertTrue(ActiveWorkPolicy.shouldKeepScreenOn(false, true, false));
        assertTrue(ActiveWorkPolicy.shouldKeepScreenOn(false, false, true));
    }

    @Test public void userCanAllowScreenTimeoutWithoutStoppingWork() {
        assertFalse(ActiveWorkPolicy.shouldKeepScreenOn(false, true, false, false));
        assertTrue(ActiveWorkPolicy.shouldKeepScreenOn(true, true, false, false));
    }

    @Test public void stoppingLastTaskAllowsScreenTimeoutImmediately() {
        boolean liveTranscription = true;
        assertTrue(ActiveWorkPolicy.shouldKeepScreenOn(false, liveTranscription, false));

        liveTranscription = false;
        assertFalse(ActiveWorkPolicy.shouldKeepScreenOn(false, liveTranscription, false));
    }

    @Test public void pausedPageReturnsSafeExactlyOnce() {
        assertTrue(ActiveWorkPolicy.shouldShowSafePage(true, false));
        assertFalse(ActiveWorkPolicy.shouldShowSafePage(true, true));
        assertFalse(ActiveWorkPolicy.shouldShowSafePage(false, false));
    }
}
