package com.noteshadow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LmNoteCommandTest {
    @Test public void parsesQuestionAndLegacyAliasOnlyAtLineStart() {
        assertEquals(LmNoteCommand.Kind.SINGLE_PROMPT, LmNoteCommand.parse("[09:30:00] /lm 你好").kind());
        assertEquals("你好", LmNoteCommand.parse("/lm 你好").payload());
        assertEquals("旧问题", LmNoteCommand.parse("/ds 旧问题").payload());
        assertEquals(LmNoteCommand.Kind.NONE, LmNoteCommand.parse("课堂文本 /lm 你好").kind());
        assertEquals(LmNoteCommand.Kind.NONE, LmNoteCommand.parse("/lmost 你好").kind());
    }

    @Test public void parsesModesAndReset() {
        assertEquals(LmNoteCommand.Kind.BLOCK_BEGIN, LmNoteCommand.parse("[00:00:01] /lmlbg").kind());
        assertEquals(LmNoteCommand.Kind.BLOCK_END, LmNoteCommand.parse("/lmled").kind());
        assertEquals(LmNoteCommand.Kind.CONTINUOUS_BEGIN, LmNoteCommand.parse("/lmcl").kind());
        assertEquals(LmNoteCommand.Kind.CONTINUOUS_END, LmNoteCommand.parse("///").kind());
        assertEquals(LmNoteCommand.Kind.NEW_CONTEXT, LmNoteCommand.parse("/lmnew").kind());
    }

    @Test public void doesNotTreatMultilineInputAsOneCommand() {
        assertEquals(LmNoteCommand.Kind.NONE, LmNoteCommand.parse("/lm 问题\n下一行").kind());
        assertEquals("", LmNoteCommand.parse("/lm   ").payload());
    }
}
