package com.noteshadow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BookProgressTest {
    @Test public void percentageUsesCodePointBoundaries() {
        String text = "甲😀乙😀丙";
        assertEquals(0, BookProgress.positionForPercentage(text, "0"));
        assertEquals(4, BookProgress.positionForPercentage(text, "50"));
        assertEquals(text.length(), BookProgress.positionForPercentage(text, "100"));
    }

    @Test public void rejectsBlankNonNumericAndOutOfRangeValues() {
        assertFails("", "请输入进度百分比");
        assertFails("abc", "进度必须是数字");
        assertFails("101", "进度必须在 0–100 之间");
        assertFails("-1", "进度必须在 0–100 之间");
    }

    @Test public void clampsSplitSurrogateToPreviousCodePoint() {
        assertEquals(1, BookProgress.clampToCodePointBoundary("甲😀乙", 2));
        assertEquals(4, BookProgress.clampToCodePointBoundary("甲😀乙", 99));
    }

    private static void assertFails(String input, String message) {
        try {
            BookProgress.positionForPercentage("正文", input);
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
