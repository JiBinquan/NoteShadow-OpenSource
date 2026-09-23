package com.noteshadow.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimestampNoteFormatterTest {
    @Test public void leavesTextWithoutLineBreakUntouched() {
        assertEquals("hello", TimestampNoteFormatter.formatInsertedText("hello", "09:05:07"));
    }

    @Test public void normalizesCrLfAndCr() {
        assertEquals("a\n[09:05:07] b\n[09:05:07] c",
                TimestampNoteFormatter.formatInsertedText("a\r\nb\rc", "09:05:07"));
    }

    @Test public void prefixesEveryLineAfterConsecutiveBreaksAndTrailingBreak() {
        assertEquals("a\n[12:00:00] \n[12:00:00] ",
                TimestampNoteFormatter.formatInsertedText("a\n\r\n", "12:00:00"));
    }

    @Test public void handlesEmptyAndNullInsertion() {
        assertEquals("", TimestampNoteFormatter.formatInsertedText("", "12:00:00"));
        assertEquals("", TimestampNoteFormatter.formatInsertedText(null, "12:00:00"));
    }

    @Test public void replacesSelectionAndPlacesCursorAfterInsertion() {
        TimestampNoteFormatter.Result result = TimestampNoteFormatter.replaceSelection(
                "before old after", 7, 10, "first\nsecond", "08:01:02");
        assertEquals("before first\n[08:01:02] second after", result.getText());
        assertEquals("before first\n[08:01:02] second".length(), result.getCursor());
    }

    @Test public void replacementWithEmptyTextDeletesSelectionAndKeepsCursorAtStart() {
        TimestampNoteFormatter.Result result = TimestampNoteFormatter.replaceSelection(
                "abc", 1, 3, "", "08:01:02");
        assertEquals("a", result.getText());
        assertEquals(1, result.getCursor());
    }

    @Test public void clampsOutOfRangeSelectionWithoutChangingExistingLineEndings() {
        TimestampNoteFormatter.Result result = TimestampNoteFormatter.replaceSelection(
                "a\r\nb", -5, 99, "x", "08:01:02");
        assertEquals("x", result.getText());
        assertEquals(1, result.getCursor());
    }

    @Test public void emptyTimestampStillUsesRequiredBrackets() {
        assertEquals("a\n[] b", TimestampNoteFormatter.formatInsertedText("a\nb", null));
        assertEquals("[09:05:07] ", TimestampNoteFormatter.bracketedLabel("09:05:07"));
    }
}
