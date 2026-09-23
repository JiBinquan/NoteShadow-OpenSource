package com.noteshadow.app;

import java.util.regex.Pattern;

/** Pure parsing and character policy for the note-page novel input mode. */
public final class NovelInputPolicy {
    public enum Command {
        NONE,
        START,
        END
    }

    private static final Pattern TIMESTAMPED_PREFIX = Pattern.compile(
            "^\\[\\d{2}:\\d{2}:\\d{2}] ");

    private NovelInputPolicy() {
    }

    /**
     * Parses exactly one complete note line. A timestamp may prefix the
     * command, but commands embedded in ordinary text are not recognized.
     */
    public static Command parseCommand(String line) {
        if (line == null || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            return Command.NONE;
        }
        String commandLine = line;
        if (TIMESTAMPED_PREFIX.matcher(commandLine).find()) {
            commandLine = commandLine.substring(11);
        }
        if ("/novst".equals(commandLine)) return Command.START;
        if ("///".equals(commandLine)) return Command.END;
        return Command.NONE;
    }

    /** Returns whether a character should be redirected to novel output. */
    public static boolean shouldRedirect(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z');
    }
}
