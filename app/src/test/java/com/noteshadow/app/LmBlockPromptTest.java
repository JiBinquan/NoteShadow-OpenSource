package com.noteshadow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LmBlockPromptTest {
    @Test public void keepsCommandLookingLinesInsideBlock() {
        String note = "[10:00:00] /lmlbg\n[10:00:01] 第一行\n[10:00:02] /lmnew\n"
                + "[10:00:03] /lmlbg\n[10:00:04] 最后一行\n[10:00:05] /lmled";
        assertEquals("第一行\n/lmnew\n/lmlbg\n最后一行",
                LmBlockPrompt.extract(note, note.lastIndexOf("[10:00:05]")));
    }

    @Test public void skipsPreviousCompletedBlock() {
        String note = "/lmlbg\n旧\n/lmled\n/lmlbg\n新\n/lmled";
        assertEquals("新", LmBlockPrompt.extract(note, note.lastIndexOf("/lmled")));
    }
}
