package com.noteshadow.app;

/** Pure sizing math for memory-bounded note image thumbnails. */
final class NoteImagePreviewSizing {
    private NoteImagePreviewSizing() { }

    static int[] fit(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight,
                     long maxPixels) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0
                || maxPixels <= 0) return new int[] {0, 0};
        double scale = Math.min(1d, Math.min(maxWidth / (double)sourceWidth,
                maxHeight / (double)sourceHeight));
        double pixels = (double)sourceWidth * sourceHeight;
        if (pixels * scale * scale > maxPixels) {
            scale = Math.min(scale, Math.sqrt(maxPixels / pixels));
        }
        int width = Math.max(1, (int)Math.floor(sourceWidth * scale));
        int height = Math.max(1, (int)Math.floor(sourceHeight * scale));
        return new int[] {width, height};
    }
}
