package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class DsNoteCommandTest {
    @Test public void onlyQuestionOnOneCommandLineIsSelected() {
        assertEquals("烟草物流规定？", DsNoteCommand.question("[09:30:00] /ds 烟草物流规定？"));
        assertEquals("问题", DsNoteCommand.question("/ds 问题"));
        assertNull(DsNoteCommand.question("[09:30:00] 课堂文本 /ds 问题"));
        assertNull(DsNoteCommand.question("/dsst 问题"));
        assertNull(DsNoteCommand.question("/ds\n第二行"));
    }

    @Test public void emptyAndOverlongQuestionsAreRejected() {
        assertEquals("", DsNoteCommand.question("/ds    "));
        assertFalse(DsNoteCommand.isValidLength(""));
        assertTrue(DsNoteCommand.isValidLength("问题"));
        char[] longQuestion = new char[DsNoteCommand.MAX_QUESTION_LENGTH + 1];
        Arrays.fill(longQuestion, 'x');
        assertFalse(DsNoteCommand.isValidLength(new String(longQuestion)));
    }
}
