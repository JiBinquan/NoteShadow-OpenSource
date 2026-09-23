package com.noteshadow.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the temporary "real speech + novel" foreground presentation.
 *
 * This class is deliberately pure and has no access to AppStorage.  Its output
 * can therefore never be mistaken for, or written into, the canonical ASR
 * transcript.  Rebuilding with the same inputs produces the same prefix, which
 * lets MainActivity append only the newly displayed portion without flashing.
 */
final class MixedTranscriptPresenter {
    private static final int MIN_REAL_LINES = 2;
    private static final int REAL_LINE_VARIANTS = 4; // 2, 3, 4 or 5
    private static final int MIN_NOVEL_CHARS = 16;
    private static final int TARGET_NOVEL_CHARS = 24;
    private static final int MAX_NOVEL_CHARS = 34;
    private static final long GROUP_SEED = 0x4e6f746553686164L;
    private static final Pattern TIMESTAMP = Pattern.compile(
            "^\\s*\\[(\\d{2}):(\\d{2}):(\\d{2})]\\s*");

    private MixedTranscriptPresenter() {}

    static String build(String canonicalTranscript, String novel, int novelStart) {
        return build(canonicalTranscript, novel, novelStart, null);
    }

    /** Null eligibility means all lines; an empty string means no line may advance novel output. */
    static String build(String canonicalTranscript, String novel, int novelStart, String eligibleRanges) {
        return buildResult(canonicalTranscript, novel, novelStart, eligibleRanges).text;
    }

    static BuildResult buildResult(String canonicalTranscript, String novel, int novelStart,
                                   String eligibleRanges) {
        List<String> realLines = canonicalLines(canonicalTranscript);
        String normalizedNovel = normalizeNovel(novel);
        int novelCursor = normalizedNovel.isEmpty() ? 0
                : Math.max(0, Math.min(novelStart, normalizedNovel.length() - 1));
        if (realLines.isEmpty()) return new BuildResult("", novelCursor);
        List<LineRange> eligibility = eligibleRanges == null ? null : parseRanges(eligibleRanges);

        Random random = new Random(GROUP_SEED);
        int untilNovel = MIN_REAL_LINES + random.nextInt(REAL_LINE_VARIANTS);
        int realSinceNovel = 0;
        String previousTimestamp = "";
        StringBuilder out = new StringBuilder(canonicalTranscript == null ? 64 : canonicalTranscript.length());

        for (int i = 0; i < realLines.size(); i++) {
            String realLine = realLines.get(i);
            appendLine(out, realLine);
            previousTimestamp = timestampAfterOneSecond(realLine);
            if (!isEligible(i + 1, eligibility)) continue;
            realSinceNovel++;
            if (!normalizedNovel.isEmpty() && realSinceNovel >= untilNovel) {
                NovelSlice slice = nextNovelSlice(normalizedNovel, novelCursor);
                if (!slice.text.isEmpty()) {
                    appendLine(out, previousTimestamp.isEmpty()
                            ? slice.text
                            : previousTimestamp + " " + slice.text);
                }
                novelCursor = slice.next;
                realSinceNovel = 0;
                untilNovel = MIN_REAL_LINES + random.nextInt(REAL_LINE_VARIANTS);
            }
        }
        return new BuildResult(out.toString(), novelCursor);
    }

    static int lineCount(String source) {
        return canonicalLines(source).size();
    }

    static String appendEligibleRange(String encoded, int start, int end) {
        if (start <= 0 || end < start) return encoded == null ? "" : encoded;
        List<LineRange> ranges = parseRanges(encoded);
        if (!ranges.isEmpty()) {
            LineRange last = ranges.get(ranges.size() - 1);
            if (start <= last.end + 1) {
                last.end = Math.max(last.end, end);
                return encodeRanges(ranges);
            }
        }
        ranges.add(new LineRange(start, end));
        return encodeRanges(ranges);
    }

    static String clipEligibleRanges(String encoded, int maximumLine) {
        if (maximumLine <= 0) return "";
        List<LineRange> clipped = new ArrayList<>();
        for (LineRange range : parseRanges(encoded)) {
            if (range.start > maximumLine) break;
            clipped.add(new LineRange(range.start, Math.min(range.end, maximumLine)));
        }
        return encodeRanges(clipped);
    }

    private static List<String> canonicalLines(String source) {
        List<String> result = new ArrayList<>();
        if (source == null || source.isEmpty()) return result;
        for (String raw : source.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    private static List<LineRange> parseRanges(String encoded) {
        List<LineRange> result = new ArrayList<>();
        if (encoded == null || encoded.trim().isEmpty()) return result;
        for (String token : encoded.split(",")) {
            String[] bounds = token.trim().split("-");
            try {
                int start = Integer.parseInt(bounds[0]);
                int end = bounds.length > 1 ? Integer.parseInt(bounds[1]) : start;
                if (start > 0 && end >= start) result.add(new LineRange(start, end));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private static boolean isEligible(int line, List<LineRange> ranges) {
        if (ranges == null) return true;
        for (LineRange range : ranges) {
            if (line < range.start) return false;
            if (line <= range.end) return true;
        }
        return false;
    }

    private static String encodeRanges(List<LineRange> ranges) {
        StringBuilder out = new StringBuilder();
        for (LineRange range : ranges) {
            if (out.length() > 0) out.append(',');
            out.append(range.start);
            if (range.end != range.start) out.append('-').append(range.end);
        }
        return out.toString();
    }

    private static String timestampAfterOneSecond(String line) {
        Matcher matcher = TIMESTAMP.matcher(line == null ? "" : line);
        if (!matcher.find()) return "";
        int hours = Integer.parseInt(matcher.group(1));
        int minutes = Integer.parseInt(matcher.group(2));
        int seconds = Integer.parseInt(matcher.group(3));
        if (hours > 23 || minutes > 59 || seconds > 59) return "";
        int next = (hours * 3600 + minutes * 60 + seconds + 1) % (24 * 3600);
        return String.format(Locale.ROOT, "[%02d:%02d:%02d]",
                next / 3600, (next / 60) % 60, next % 60);
    }

    private static String normalizeNovel(String source) {
        if (source == null) return "";
        // Preserve one UTF-16 position per source character so the returned cursor can be
        // persisted as the real document offset. Whitespace is compacted only in the slice
        // shown on screen, never in the cursor-bearing source.
        return source.replace('\r', ' ').replace('\n', ' ')
                .replace('\t', ' ').replace('\u3000', ' ');
    }

    private static NovelSlice nextNovelSlice(String novel, int start) {
        if (novel.isEmpty()) return new NovelSlice("", 0);
        int cursor = Math.max(0, Math.min(start, novel.length() - 1));
        while (cursor < novel.length() && isSkippable(novel.charAt(cursor))) cursor++;
        if (cursor >= novel.length()) cursor = 0;

        int minimumEnd = Math.min(novel.length(), cursor + MIN_NOVEL_CHARS);
        int targetEnd = Math.min(novel.length(), cursor + TARGET_NOVEL_CHARS);
        int maximumEnd = Math.min(novel.length(), cursor + MAX_NOVEL_CHARS);
        int end = targetEnd;
        for (int i = minimumEnd; i < maximumEnd; i++) {
            if (isSentenceEnd(novel.charAt(i))) {
                end = i + 1;
                break;
            }
        }
        if (end <= cursor) end = Math.min(novel.length(), cursor + 1);
        String text = novel.substring(cursor, end).replaceAll(" +", " ").trim();
        int next = end >= novel.length() ? 0 : end;
        return new NovelSlice(text, next);
    }

    private static boolean isSkippable(char c) {
        return Character.isWhitespace(c) || "，。！？；：、,.!?;:\"'“”‘’）】》".indexOf(c) >= 0;
    }

    private static boolean isSentenceEnd(char c) {
        return "。！？；.!?;".indexOf(c) >= 0;
    }

    private static void appendLine(StringBuilder out, String line) {
        if (line == null || line.trim().isEmpty()) return;
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
        out.append(line.trim()).append('\n');
    }

    private static final class NovelSlice {
        final String text;
        final int next;
        NovelSlice(String text, int next) { this.text = text; this.next = next; }
    }

    static final class BuildResult {
        final String text;
        final int nextNovelPosition;
        BuildResult(String text, int nextNovelPosition) {
            this.text = text;
            this.nextNovelPosition = Math.max(0, nextNovelPosition);
        }
    }

    private static final class LineRange {
        final int start;
        int end;
        LineRange(int start, int end) { this.start = start; this.end = end; }
    }
}
