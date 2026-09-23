package com.noteshadow.app;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class NoteImagePreviewSizingTest {
    @Test public void sharesTotalPixelBudgetAcrossFourLargePhotos() {
        long totalBudget = 8_000_000L;
        long perImage = totalBudget / 4;
        long total = 0L;
        for (int i = 0; i < 4; i++) {
            int[] size = NoteImagePreviewSizing.fit(8000, 6000, 2496, 1600, perImage);
            assertTrue(size[0] > 0 && size[1] > 0);
            assertTrue(size[0] <= 2496 && size[1] <= 1600);
            assertTrue((long)size[0] * size[1] <= perImage);
            total += (long)size[0] * size[1];
        }
        assertTrue(total <= totalBudget);
    }

    @Test public void preservesSmallImagesAndPortraitAspect() {
        int[] small = NoteImagePreviewSizing.fit(320, 200, 1200, 900, 1_000_000L);
        assertTrue(small[0] == 320 && small[1] == 200);

        int[] portrait = NoteImagePreviewSizing.fit(3000, 6000, 1200, 900, 500_000L);
        assertTrue(portrait[0] * 2 == portrait[1]);
        assertTrue((long)portrait[0] * portrait[1] <= 500_000L);
    }
}
