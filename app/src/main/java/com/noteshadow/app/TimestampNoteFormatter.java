package com.noteshadow.app;

/**
 * Pure text policy for timestamped notes.
 *
 * <p>The caller supplies the timestamp label so this class never reads the
 * clock. Existing document text is deliberately left untouched; only the
 * newly inserted text is normalized and decorated.</p>
 */
public final class TimestampNoteFormatter {
    private TimestampNoteFormatter() {
    }

    /** Result of replacing a selection with timestamped inserted text. */
    public static final class Result {
        private final String text;
        private final int cursor;

        private Result(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }

        public String getText() {
            return text;
        }

        /** Cursor position immediately after the inserted/decorated text. */
        public int getCursor() {
            return cursor;
        }
    }

    /**
     * Formats inserted text using a timestamp label such as {@code 09:05:07}.
     * Every normalized LF receives {@code [label] } immediately after it.
     */
    public static String formatInsertedText(String insertedText, String timestampLabel) {
        if (insertedText == null || insertedText.isEmpty()) return "";
        String label = timestampLabel == null ? "" : timestampLabel;
        StringBuilder result = new StringBuilder(insertedText.length() + 16);
        for (int i = 0; i < insertedText.length(); i++) {
            char c = insertedText.charAt(i);
            if (c == '\r') {
                if (i + 1 < insertedText.length() && insertedText.charAt(i + 1) == '\n') {
                    i++;
                }
                result.append('\n').append('[').append(label).append("] ");
            } else if (c == '\n') {
                result.append('\n').append('[').append(label).append("] ");
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Replaces {@code [selectionStart, selectionEnd)} in {@code existingText}.
     * The returned cursor is placed after the decorated insertion.
     */
    public static Result replaceSelection(String existingText,
                                           int selectionStart,
                                           int selectionEnd,
                                           String insertedText,
                                           String timestampLabel) {
        String existing = existingText == null ? "" : existingText;
        int start = clamp(selectionStart, 0, existing.length());
        int end = clamp(selectionEnd, start, existing.length());
        String formatted = formatInsertedText(insertedText, timestampLabel);
        String result = existing.substring(0, start) + formatted + existing.substring(end);
        return new Result(result, start + formatted.length());
    }

    /** Returns a stable bracketed label for callers that already have HH:mm:ss. */
    public static String bracketedLabel(String timestampLabel) {
        return "[" + (timestampLabel == null ? "" : timestampLabel) + "] ";
    }

    private static int clamp(int value, int lower, int upper) {
        return Math.max(lower, Math.min(value, upper));
    }
}
