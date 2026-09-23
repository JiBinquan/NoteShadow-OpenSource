package com.noteshadow.app;

/** Extracts only the text enclosed by the most recent explicit multiline pair. */
final class LmBlockPrompt {
    private LmBlockPrompt() { }

    static String extract(String note, int endLineStart) {
        String before = note.substring(0, Math.min(note.length(), endLineStart));
        int start = -1;
        int cursor = 0;
        while (cursor < before.length()) {
            int end = before.indexOf('\n', cursor);
            if (end < 0) break;
            LmNoteCommand.Kind kind = LmNoteCommand.parse(before.substring(cursor, end)).kind();
            if (kind == LmNoteCommand.Kind.BLOCK_END) start = -1;
            else if (kind == LmNoteCommand.Kind.BLOCK_BEGIN && start < 0) start = end + 1;
            cursor = end + 1;
        }
        if (start < 0) return "";
        StringBuilder prompt = new StringBuilder();
        for (String line : before.substring(start).split("\\n", -1)) {
            String content = line.replaceFirst("^\\[\\d{2}:\\d{2}:\\d{2}\\] ", "");
            if (prompt.length() > 0) prompt.append('\n');
            prompt.append(content);
            if (prompt.length() > 8_000) break;
        }
        return prompt.toString().trim();
    }
}
