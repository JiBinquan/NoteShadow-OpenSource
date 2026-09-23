package com.noteshadow.app;

/** Recognizes only a complete, single-line /ds question. */
final class DsNoteCommand {
    static final int MAX_QUESTION_LENGTH = 2000;

    private DsNoteCommand() { }

    static String question(String line) {
        if (line == null) return null;
        String command = line.replaceFirst("^\\[\\d{2}:\\d{2}:\\d{2}\\] ", "");
        if (!command.startsWith("/ds ")) return null;
        String question = command.substring(4).trim();
        return question.isEmpty() ? "" : question;
    }

    static boolean isValidLength(String question) {
        return question != null && !question.isEmpty() && question.length() <= MAX_QUESTION_LENGTH;
    }
}
