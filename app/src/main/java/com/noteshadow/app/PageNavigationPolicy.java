package com.noteshadow.app;

/**
 * Keeps the safe-page and boss-key navigation state independent from Activity
 * rendering and from transient pages such as file previews.
 */
public final class PageNavigationPolicy {
    public enum Page {
        DUAL_TRANSCRIPT,
        TIMESTAMP_NOTE,
        TRANSCRIPT
    }

    private Page currentPage;
    private Page returnPage;

    public PageNavigationPolicy() {
        resetToSafePage();
    }

    public Page currentPage() {
        return currentPage;
    }

    /** The page that the next boss-key press may restore, or null. */
    public Page returnPage() {
        return returnPage;
    }

    public boolean hasReturnPage() {
        return returnPage != null;
    }

    /**
     * Selects a main page directly. Direct navigation always clears a pending
     * boss-key return target so it cannot unexpectedly reveal another page.
     */
    public void select(Page page) {
        if (page == null) {
            return;
        }
        currentPage = page;
        returnPage = null;
    }

    /**
     * Toggles between a work page and the safe transcript page. When already
     * on the safe page without a pending return target this is intentionally a
     * no-op.
     */
    public void toggleBoss() {
        if (currentPage == Page.DUAL_TRANSCRIPT || currentPage == Page.TIMESTAMP_NOTE) {
            returnPage = currentPage;
            currentPage = Page.TRANSCRIPT;
        } else if (currentPage == Page.TRANSCRIPT && returnPage != null) {
            currentPage = returnPage;
            returnPage = null;
        }
    }

    /** Clears all navigation history and returns to the safe page. */
    public void resetToSafePage() {
        currentPage = Page.TRANSCRIPT;
        returnPage = null;
    }
}
