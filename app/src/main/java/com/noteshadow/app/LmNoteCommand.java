package com.noteshadow.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the line-oriented language-model commands used in a note. */
public final class LmNoteCommand {
    public enum Kind {
        NONE,
        SINGLE_PROMPT,
        BLOCK_BEGIN,
        BLOCK_END,
        CONTINUOUS_BEGIN,
        CONTINUOUS_END,
        NEW_CONTEXT
    }

    public static final class Parsed {
        private final Kind kind;
        private final String payload;

        private Parsed(Kind kind, String payload) {
            this.kind = kind;
            this.payload = payload;
        }

        public Kind kind() {
            return kind;
        }

        public String payload() {
            return payload;
        }
    }

    private static final Pattern TIMESTAMP = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\]\\s+");
    private static final String EMPTY = "";

    private LmNoteCommand() {
    }

    /**
     * Parses one note line. A timestamp is permitted only as a prefix, so text
     * containing a command later in the line is ordinary note text.
     */
    public static Parsed parse(String line) {
        if (line == null || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            return result(Kind.NONE, EMPTY);
        }
        Matcher matcher = TIMESTAMP.matcher(line);
        String commandLine = matcher.find() ? line.substring(matcher.end()) : line;
        if (commandLine.equals("/lmlbg")) return result(Kind.BLOCK_BEGIN, EMPTY);
        if (commandLine.equals("/lmled")) return result(Kind.BLOCK_END, EMPTY);
        if (commandLine.equals("/lmcl")) return result(Kind.CONTINUOUS_BEGIN, EMPTY);
        if (commandLine.equals("///")) return result(Kind.CONTINUOUS_END, EMPTY);
        if (commandLine.equals("/lmnew")) return result(Kind.NEW_CONTEXT, EMPTY);

        String question = questionAfter(commandLine, "/lm");
        if (question == null) question = questionAfter(commandLine, "/ds");
        return question == null ? result(Kind.NONE, EMPTY) : result(Kind.SINGLE_PROMPT, question);
    }

    private static String questionAfter(String line, String command) {
        String prefix = command + " ";
        if (!line.startsWith(prefix)) return null;
        return line.substring(prefix.length()).trim();
    }

    private static Parsed result(Kind kind, String payload) {
        return new Parsed(kind, payload);
    }
}
