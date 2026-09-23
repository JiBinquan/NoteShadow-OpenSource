package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NovelInputPolicyTest {
    @Test public void recognizesOnlyExactCommands() {
        assertEquals(NovelInputPolicy.Command.START,
                NovelInputPolicy.parseCommand("/novst"));
        assertEquals(NovelInputPolicy.Command.START,
                NovelInputPolicy.parseCommand("[09:30:00] /novst"));
        assertEquals(NovelInputPolicy.Command.END,
                NovelInputPolicy.parseCommand("///"));
        assertEquals(NovelInputPolicy.Command.END,
                NovelInputPolicy.parseCommand("[09:30:00] ///"));
    }

    @Test public void rejectsEmbeddedMalformedAndMultilineCommands() {
        String[] ordinary = {
                " /novst", "/novst ", "课堂 /novst", "/novst now", "////",
                "[9:30:00] /novst", "[09:30:00]/novst", "[09:30:00] /novst\n",
                "/novst\r///", null
        };
        for (String line : ordinary) {
            assertEquals(NovelInputPolicy.Command.NONE,
                    NovelInputPolicy.parseCommand(line));
        }
    }

    @Test public void redirectsAsciiLettersOnly() {
        assertTrue(NovelInputPolicy.shouldRedirect('A'));
        assertTrue(NovelInputPolicy.shouldRedirect('z'));
        assertFalse(NovelInputPolicy.shouldRedirect(' '));
        assertFalse(NovelInputPolicy.shouldRedirect('7'));
        assertFalse(NovelInputPolicy.shouldRedirect('.'));
        assertFalse(NovelInputPolicy.shouldRedirect('中'));
        assertFalse(NovelInputPolicy.shouldRedirect(0x1F642));
    }
}
