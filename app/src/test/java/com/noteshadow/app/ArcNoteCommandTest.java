package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArcNoteCommandTest {
    @Test public void defaultAndExplicitCountAreRecognized() {
        assertValid("/arc", 5);
        assertValid("[09:30:00] /arc 20", 20);
        assertValid("  /arc   1  ", 1);
        assertValid("/arc 200", 200);
    }

    @Test public void invalidArgumentsAreRecognizedButRejected() {
        assertInvalid("/arc 0");
        assertInvalid("/arc 201");
        assertInvalid("/arc many");
        assertInvalid("/arc 2 extra");
    }

    @Test public void substringCommandsAreNotRecognized() {
        assertFalse(ArcNoteCommand.parse("课堂 /arc 3").recognized());
        assertFalse(ArcNoteCommand.parse("/archive 3").recognized());
        assertFalse(ArcNoteCommand.parse(null).recognized());
    }

    private static void assertValid(String line, int count) {
        ArcNoteCommand.Parsed parsed = ArcNoteCommand.parse(line);
        assertTrue(parsed.recognized());
        assertTrue(parsed.valid());
        assertEquals(count, parsed.count());
    }

    private static void assertInvalid(String line) {
        ArcNoteCommand.Parsed parsed = ArcNoteCommand.parse(line);
        assertTrue(parsed.recognized());
        assertFalse(parsed.valid());
        assertTrue(parsed.error() != null && !parsed.error().isEmpty());
    }
}
