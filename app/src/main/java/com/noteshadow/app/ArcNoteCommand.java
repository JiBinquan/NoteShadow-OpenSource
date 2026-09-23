package com.noteshadow.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser for the note-page {@code /arc} transcript excerpt command. */
public final class ArcNoteCommand {
    public static final int DEFAULT_COUNT = 5;
    public static final int MAX_COUNT = 200;

    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile(
            "^\\s*\\[\\d{2}:\\d{2}:\\d{2}]\\s*(.*)$");
    private static final Pattern COMMAND = Pattern.compile("^/arc(?:\\s+(.*))?$");

    private ArcNoteCommand() {
    }

    /** Parses one complete note line. A non-command line is not recognized. */
    public static Parsed parse(String timestampedLine) {
        if (timestampedLine == null) return Parsed.unrecognized();
        String line = timestampedLine.replace("\r", "");
        Matcher timestamp = TIMESTAMP_PREFIX.matcher(line);
        if (timestamp.matches()) line = timestamp.group(1);
        Matcher command = COMMAND.matcher(line.trim());
        if (!command.matches()) return Parsed.unrecognized();

        String argument = command.group(1);
        if (argument == null || argument.trim().isEmpty()) {
            return Parsed.valid(DEFAULT_COUNT);
        }
        String value = argument.trim();
        if (!value.matches("\\d+")) {
            return Parsed.invalid("/arc 后只能填写 1 到 " + MAX_COUNT + " 的行数");
        }
        try {
            int count = Integer.parseInt(value);
            if (count < 1 || count > MAX_COUNT) {
                return Parsed.invalid("行数范围为 1 到 " + MAX_COUNT);
            }
            return Parsed.valid(count);
        } catch (NumberFormatException ignored) {
            return Parsed.invalid("行数范围为 1 到 " + MAX_COUNT);
        }
    }

    public static final class Parsed {
        private final boolean recognized;
        private final boolean valid;
        private final int count;
        private final String error;

        private Parsed(boolean recognized, boolean valid, int count, String error) {
            this.recognized = recognized;
            this.valid = valid;
            this.count = count;
            this.error = error;
        }

        private static Parsed unrecognized() {
            return new Parsed(false, false, 0, null);
        }

        private static Parsed valid(int count) {
            return new Parsed(true, true, count, null);
        }

        private static Parsed invalid(String error) {
            return new Parsed(true, false, 0, error);
        }

        public boolean recognized() { return recognized; }
        public boolean valid() { return valid; }
        public int count() { return count; }
        public String error() { return error; }
    }
}
