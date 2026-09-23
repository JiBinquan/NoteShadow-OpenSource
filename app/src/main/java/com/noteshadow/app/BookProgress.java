package com.noteshadow.app;

/** Pure helpers for converting a user-facing reading percentage to text offsets. */
public final class BookProgress {
    private BookProgress() {}

    public static int positionForPercentage(String text, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入进度百分比");
        }
        final double percent;
        try {
            percent = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("进度必须是数字");
        }
        if (Double.isNaN(percent) || Double.isInfinite(percent) || percent < 0 || percent > 100) {
            throw new IllegalArgumentException("进度必须在 0–100 之间");
        }
        if (text == null || text.isEmpty()) return 0;
        int codePoints = text.codePointCount(0, text.length());
        int target = (int) Math.round(codePoints * percent / 100.0);
        return text.offsetByCodePoints(0, Math.min(codePoints, Math.max(0, target)));
    }

    public static int clampToCodePointBoundary(String text, int position) {
        if (text == null || text.isEmpty()) return 0;
        int safe = Math.max(0, Math.min(position, text.length()));
        if (safe > 0 && safe < text.length()
                && Character.isLowSurrogate(text.charAt(safe))
                && Character.isHighSurrogate(text.charAt(safe - 1))) {
            safe--;
        }
        return safe;
    }
}
