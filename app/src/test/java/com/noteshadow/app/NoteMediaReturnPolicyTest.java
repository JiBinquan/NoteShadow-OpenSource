package com.noteshadow.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NoteMediaReturnPolicyTest {
    @Test public void restoresExactlyOnceAfterOwnedLaunchReturns() {
        NoteMediaReturnPolicy policy = new NoteMediaReturnPolicy();
        policy.onLaunch();

        assertTrue(policy.onResult());
        assertTrue(policy.consumeRestore());
        assertFalse(policy.consumeRestore());
    }

    @Test public void unownedResultAndFailedLaunchStaySafe() {
        NoteMediaReturnPolicy freshActivity = new NoteMediaReturnPolicy();
        assertFalse(freshActivity.onResult());
        assertFalse(freshActivity.consumeRestore());

        freshActivity.onLaunch();
        freshActivity.onLaunchFailed();
        assertFalse(freshActivity.onResult());
        assertFalse(freshActivity.consumeRestore());
    }
}
