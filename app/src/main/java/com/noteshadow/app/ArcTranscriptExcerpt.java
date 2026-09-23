package com.noteshadow.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects the newest formal timestamped lines from the canonical transcript. */
public final class ArcTranscriptExcerpt {
    private static final Pattern FORMAL_LINE = Pattern.compile(
            "^\\s*\\[(\\d{2}:\\d{2}:\\d{2})](\\s*)(.*)$");

    private ArcTranscriptExcerpt() {
    }

    /**
     * Returns at most {@code count} newest transcript lines in chronological order.
     * A Qwen segment can contain embedded newlines: nonblank continuation lines
     * inherit the preceding ASR timestamp so they are not silently lost.
     * Input is canonical transcript text, so draft/novel content is never considered.
     */
    public static List<String> latest(String canonicalRealNote, int count) {
        if (canonicalRealNote == null || canonicalRealNote.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        String[] lines = canonicalRealNote.replace("\r", "").split("\\n", -1);
        List<String> matching = new ArrayList<>();
        String currentTimestamp = null;
        for (String line : lines) {
            Matcher formal = FORMAL_LINE.matcher(line);
            if (formal.matches()) {
                String payload = formal.group(3);
                if (payload.trim().isEmpty()) {
                    currentTimestamp = null;
                    continue;
                }
                currentTimestamp = formal.group(1);
                // Keep the canonical line byte-for-byte, including its timestamp spacing.
                matching.add(line);
            } else if (currentTimestamp != null && !line.trim().isEmpty()) {
                // Preserve the continuation text while making the inherited timestamp explicit.
                matching.add("[" + currentTimestamp + "] " + line);
            }
        }
        int from = Math.max(0, matching.size() - count);
        return Collections.unmodifiableList(new ArrayList<>(matching.subList(from, matching.size())));
    }
}
