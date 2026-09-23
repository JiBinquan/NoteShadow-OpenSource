package com.noteshadow.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PageNavigationPolicyTest {
    @Test
    public void startsOnSafePageWithoutReturnTarget() {
        PageNavigationPolicy policy = new PageNavigationPolicy();

        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertNull(policy.returnPage());
        assertFalse(policy.hasReturnPage());
    }

    @Test
    public void selectingAnyMainPageClearsReturnTarget() {
        PageNavigationPolicy policy = new PageNavigationPolicy();

        policy.select(PageNavigationPolicy.Page.DUAL_TRANSCRIPT);
        policy.toggleBoss();
        assertTrue(policy.hasReturnPage());

        policy.select(PageNavigationPolicy.Page.TIMESTAMP_NOTE);
        assertEquals(PageNavigationPolicy.Page.TIMESTAMP_NOTE, policy.currentPage());
        assertNull(policy.returnPage());

        policy.select(PageNavigationPolicy.Page.TRANSCRIPT);
        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertFalse(policy.hasReturnPage());
    }

    @Test
    public void selectingNullIsSafeNoOp() {
        PageNavigationPolicy policy = new PageNavigationPolicy();
        policy.select(PageNavigationPolicy.Page.DUAL_TRANSCRIPT);
        policy.toggleBoss();

        policy.select(null);

        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertEquals(PageNavigationPolicy.Page.DUAL_TRANSCRIPT, policy.returnPage());
    }

    @Test
    public void bossKeyFromDualOutputGoesToSafeAndBack() {
        PageNavigationPolicy policy = new PageNavigationPolicy();
        policy.select(PageNavigationPolicy.Page.DUAL_TRANSCRIPT);

        policy.toggleBoss();
        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertEquals(PageNavigationPolicy.Page.DUAL_TRANSCRIPT, policy.returnPage());

        policy.toggleBoss();
        assertEquals(PageNavigationPolicy.Page.DUAL_TRANSCRIPT, policy.currentPage());
        assertNull(policy.returnPage());
    }

    @Test
    public void bossKeyFromTimestampNoteGoesToSafeAndBack() {
        PageNavigationPolicy policy = new PageNavigationPolicy();
        policy.select(PageNavigationPolicy.Page.TIMESTAMP_NOTE);

        policy.toggleBoss();
        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertEquals(PageNavigationPolicy.Page.TIMESTAMP_NOTE, policy.returnPage());

        policy.toggleBoss();
        assertEquals(PageNavigationPolicy.Page.TIMESTAMP_NOTE, policy.currentPage());
        assertNull(policy.returnPage());
    }

    @Test
    public void bossKeyOnSafePageWithoutReturnIsIdempotent() {
        PageNavigationPolicy policy = new PageNavigationPolicy();

        policy.toggleBoss();
        policy.toggleBoss();

        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertNull(policy.returnPage());
    }

    @Test
    public void repeatedBossKeyFromWorkPageDoesNotLoseOriginalReturn() {
        PageNavigationPolicy policy = new PageNavigationPolicy();
        policy.select(PageNavigationPolicy.Page.DUAL_TRANSCRIPT);

        policy.toggleBoss();
        policy.toggleBoss();
        policy.toggleBoss();

        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertEquals(PageNavigationPolicy.Page.DUAL_TRANSCRIPT, policy.returnPage());
    }

    @Test
    public void resetAlwaysClearsReturnAndUsesSafePage() {
        PageNavigationPolicy policy = new PageNavigationPolicy();
        policy.select(PageNavigationPolicy.Page.DUAL_TRANSCRIPT);
        policy.toggleBoss();

        policy.resetToSafePage();
        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertNull(policy.returnPage());

        policy.select(PageNavigationPolicy.Page.TIMESTAMP_NOTE);
        policy.toggleBoss();
        policy.resetToSafePage();
        assertEquals(PageNavigationPolicy.Page.TRANSCRIPT, policy.currentPage());
        assertFalse(policy.hasReturnPage());
    }
}
