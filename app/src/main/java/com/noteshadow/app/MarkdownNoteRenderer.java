package com.noteshadow.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, dependency-free Markdown renderer for note preview.
 *
 * <p>The rendered text contains the user-visible text; spans refer to offsets
 * in that text.  It intentionally supports only note-sized Markdown and keeps
 * unknown or incomplete constructs as literal text.</p>
 */
public final class MarkdownNoteRenderer {
    private MarkdownNoteRenderer() {
    }

    public enum Style { HEADING, UNORDERED_LIST, ORDERED_LIST, QUOTE,
        BOLD, ITALIC, STRIKETHROUGH, CODE, LINK, IMAGE, HORIZONTAL_RULE,
        TABLE, TABLE_HEADER }

    /** Alignment declared by a GFM table separator cell. */
    public enum Alignment { NONE, LEFT, CENTER, RIGHT }

    /** One data row in a rendered table. All collections exposed by this renderer are immutable. */
    public static final class TableRow {
        private final String timestamp;
        private final List<String> cells;
        private TableRow(String timestamp, String[] cells) {
            this.timestamp = timestamp;
            this.cells = immutableCells(cells);
        }
        public String getTimestamp() { return timestamp; }
        public List<String> getCells() { return cells; }
        public List<String> getCellTexts() { return cells; }
    }

    /** Structured information for a table represented in the rendered text. */
    public static final class TableBlock {
        private final int start;
        private final int end;
        private final String headerTimestamp;
        private final List<String> headerCells;
        private final String separatorTimestamp;
        private final List<TableRow> rows;
        private final List<Alignment> alignments;
        private TableBlock(int start, int end, String headerTimestamp, String[] headerCells,
                           String separatorTimestamp, List<TableRow> rows,
                           List<Alignment> alignments) {
            this.start = start;
            this.end = end;
            this.headerTimestamp = headerTimestamp;
            this.headerCells = immutableCells(headerCells);
            this.separatorTimestamp = separatorTimestamp;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.alignments = Collections.unmodifiableList(new ArrayList<>(alignments));
        }
        /** Start offset (inclusive) in {@link Result#getText()}. */
        public int getStart() { return start; }
        /** End offset (exclusive) in {@link Result#getText()}. */
        public int getEnd() { return end; }
        public String getHeaderTimestamp() { return headerTimestamp; }
        public List<String> getHeaderCells() { return headerCells; }
        public List<String> getHeaderCellTexts() { return headerCells; }
        public String getSeparatorTimestamp() { return separatorTimestamp; }
        public List<TableRow> getRows() { return rows; }
        public List<Alignment> getAlignments() { return alignments; }
    }

    public static final class Span {
        private final int start;
        private final int end;
        private final Style style;
        private final String data;
        private final String target;

        private Span(int start, int end, Style style) {
            this(start, end, style, null, null);
        }

        private Span(int start, int end, Style style, String data, String target) {
            this.start = start;
            this.end = end;
            this.style = style;
            this.data = data;
            this.target = target;
        }
        public int getStart() { return start; }
        public int getEnd() { return end; }
        public Style getStyle() { return style; }
        /** Optional source payload. For links and images this is the destination. */
        public String getData() { return data; }
        /** Link or image destination, suitable for resolving relative attachments. */
        public String getTarget() { return target; }
    }

    public static final class Result {
        private final String text;
        private final List<Span> spans;
        private final List<TableBlock> tables;
        private Result(String text, List<Span> spans, List<TableBlock> tables) {
            this.text = text;
            this.spans = Collections.unmodifiableList(new ArrayList<>(spans));
            this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
        }
        public String getText() { return text; }
        public List<Span> getSpans() { return spans; }
        public List<TableBlock> getTables() { return tables; }
    }

    public static Result render(String markdown) {
        String source = markdown == null ? "" : markdown;
        StringBuilder out = new StringBuilder(source.length());
        List<Span> spans = new ArrayList<>();
        List<TableBlock> tables = new ArrayList<>();
        String[] lines = source.split("(?<=\\n)", -1);
        boolean inCode = false;
        boolean hasCodeEnd = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String body = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
            boolean newline = line.endsWith("\n");
            boolean showNewline = newline;
            int timestampLength = timestampPrefixLength(body);
            String timestamp = body.substring(0, timestampLength);
            String markdownBody = body.substring(timestampLength);
            append(out, spans, timestamp, null);
            String trimmed = markdownBody.trim();
            if (inCode) {
                if (isFence(markdownBody)) {
                    showNewline = timestampLength > 0 && newline;
                    inCode = false;
                } else {
                    append(out, spans, markdownBody, Style.CODE);
                }
            } else if (trimmed.startsWith("/")) { // commands and their arguments stay byte-for-byte visible
                append(out, spans, markdownBody, null);
            } else if (isTableHeader(markdownBody, i + 1 < lines.length ? lines[i + 1] : "")) {
                i = renderTable(lines, i, out, spans, tables, markdownBody);
                showNewline = false;
            } else if (isFence(markdownBody)) {
                showNewline = timestampLength > 0 && newline;
                hasCodeEnd = findClosingFence(lines, i + 1);
                if (hasCodeEnd) {
                    inCode = true;
                    String language = fenceLanguage(markdownBody);
                    if (!language.isEmpty()) {
                        append(out, spans, language, Style.CODE);
                        if (newline) out.append('\n');
                        showNewline = false;
                    }
                }
                else append(out, spans, markdownBody, null);
            } else {
                int start = blockPrefix(markdownBody);
                Style blockStyle = blockStyle(markdownBody, start);
                int visibleStart = out.length();
                String marker = blockMarker(markdownBody, start);
                if (marker != null) {
                    out.append(marker);
                }
                if (isHorizontalRule(markdownBody)) {
                    visibleStart = out.length();
                    append(out, spans, "────────────────", Style.HORIZONTAL_RULE);
                } else {
                    appendInline(markdownBody.substring(start), out, spans);
                }
                if (blockStyle != null && out.length() > visibleStart) {
                    spans.add(new Span(visibleStart, out.length(), blockStyle));
                }
            }
            if (showNewline) out.append('\n');
        }
        return new Result(out.toString(), spans, tables);
    }

    private static int blockPrefix(String line) {
        int p = 0;
        while (p < line.length() && line.charAt(p) == ' ') p++;
        if (p + 1 < line.length() && (line.charAt(p) == '-' || line.charAt(p) == '*' || line.charAt(p) == '+')
                && line.charAt(p + 1) == ' ') {
            int content = p + 2;
            if (content + 3 < line.length() && line.charAt(content) == '['
                    && (line.charAt(content + 1) == ' ' || line.charAt(content + 1) == 'x'
                    || line.charAt(content + 1) == 'X') && line.charAt(content + 2) == ']'
                    && line.charAt(content + 3) == ' ') return content + 4;
            return content;
        }
        int q = p;
        while (q < line.length() && Character.isDigit(line.charAt(q))) q++;
        if (q > p && q + 1 < line.length() && (line.charAt(q) == '.' || line.charAt(q) == ')')
                && line.charAt(q + 1) == ' ') return q + 2;
        if (p < line.length() && line.charAt(p) == '>') return p + (p + 1 < line.length() && line.charAt(p + 1) == ' ' ? 2 : 1);
        q = p;
        while (q < line.length() && line.charAt(q) == '#') q++;
        if (q > p && q - p <= 6 && q < line.length() && line.charAt(q) == ' ') return q + 1;
        return 0;
    }

    private static Style blockStyle(String line, int prefix) {
        int p = 0;
        while (p < line.length() && line.charAt(p) == ' ') p++;
        if (prefix == 0) return null;
        if (p < line.length() && line.charAt(p) == '>') return Style.QUOTE;
        if (p < line.length() && (line.charAt(p) == '-' || line.charAt(p) == '*' || line.charAt(p) == '+')) return Style.UNORDERED_LIST;
        if (p < line.length() && Character.isDigit(line.charAt(p))) return Style.ORDERED_LIST;
        if (p < line.length() && line.charAt(p) == '#') return Style.HEADING;
        return null;
    }

    private static String blockMarker(String line, int prefix) {
        if (prefix == 0) {
            return null;
        }
        int p = 0;
        int indent = 0;
        while (p < line.length() && line.charAt(p) == ' ') {
            p++;
            indent++;
        }
        String indentation = repeat("  ", indent / 2);
        if (p + 2 < line.length() && line.charAt(p) == '['
                && (line.charAt(p + 1) == ' ' || line.charAt(p + 1) == 'x' || line.charAt(p + 1) == 'X')
                && line.charAt(p + 2) == ']' && p + 3 < line.length() && line.charAt(p + 3) == ' ') {
            return indentation + (line.charAt(p + 1) == ' ' ? "☐ " : "☑ ");
        }
        if (p < line.length()
                && (line.charAt(p) == '-' || line.charAt(p) == '*' || line.charAt(p) == '+')) {
            int content = p + 2;
            if (content + 3 < line.length() && line.charAt(content) == '['
                    && (line.charAt(content + 1) == ' ' || line.charAt(content + 1) == 'x'
                    || line.charAt(content + 1) == 'X') && line.charAt(content + 2) == ']'
                    && line.charAt(content + 3) == ' ') {
                return indentation + (line.charAt(content + 1) == ' ' ? "☐ " : "☑ ");
            }
            return indentation + "• ";
        }
        int q = p;
        while (q < line.length() && Character.isDigit(line.charAt(q))) {
            q++;
        }
        if (q > p && q + 1 < line.length()
                && (line.charAt(q) == '.' || line.charAt(q) == ')')
                && line.charAt(q + 1) == ' ') {
            return indentation + line.substring(p, q + 2);
        }
        return null;
    }

    private static void appendInline(String s, StringBuilder out, List<Span> spans) {
        for (int i = 0; i < s.length();) {
            if (s.charAt(i) == '\\' && i + 1 < s.length() && isEscapable(s.charAt(i + 1))) {
                out.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (s.charAt(i) == '`') {
                int e = s.indexOf('`', i + 1);
                if (e > i + 1) {
                    int start = out.length();
                    out.append(s, i + 1, e);
                    spans.add(new Span(start, out.length(), Style.CODE));
                    i = e + 1;
                    continue;
                }
            }
            // Images must be recognized before ordinary links. Restrict image
            // targets to one safe filename under attachments/.
            if (s.charAt(i) == '!' && i + 1 < s.length() && s.charAt(i + 1) == '[') {
                int close = s.indexOf(']', i + 2);
                if (close >= 0 && close + 1 < s.length() && s.charAt(close + 1) == '(') {
                    int end = s.indexOf(')', close + 2);
                    if (end >= 0) {
                        String target = s.substring(close + 2, end);
                        if (isSafeImageTarget(target)) {
                            String alt = s.substring(i + 2, close);
                            int start = out.length();
                            out.append(alt.isEmpty() ? "图片" : alt);
                            spans.add(new Span(start, out.length(), Style.IMAGE, target, target));
                        } else {
                            out.append(s, i, end + 1);
                        }
                        i = end + 1;
                        continue;
                    }
                }
                // Keep incomplete image syntax literal; do not let '[' become a link.
                out.append(s, i, s.length());
                break;
            }
            if (s.charAt(i) == '[') {
                int close = s.indexOf(']', i + 1);
                if (close > i + 1 && close + 1 < s.length() && s.charAt(close + 1) == '(') {
                    int end = s.indexOf(')', close + 2);
                    if (end > close + 2) {
                        String target = s.substring(close + 2, end);
                        int start = out.length();
                        out.append(s, i + 1, close);
                        spans.add(new Span(start, out.length(), Style.LINK, target, target));
                        i = end + 1;
                        continue;
                    }
                }
            }
            String marker = i + 1 < s.length() && s.startsWith("**", i)
                    ? "**" : i + 1 < s.length() && s.startsWith("__", i) ? "__" : null;
            if (marker != null) {
                int e = s.indexOf(marker, i + 2);
                if (e > i + 2) {
                    int start = out.length();
                    out.append(s, i + 2, e);
                    spans.add(new Span(start, out.length(), Style.BOLD));
                    i = e + 2;
                    continue;
                }
            }
            if (i + 1 < s.length() && s.startsWith("~~", i)) {
                int e = s.indexOf("~~", i + 2);
                if (e > i + 2) {
                    int start = out.length();
                    out.append(s, i + 2, e);
                    spans.add(new Span(start, out.length(), Style.STRIKETHROUGH));
                    i = e + 2;
                    continue;
                }
            }
            if (s.charAt(i) == '*' || s.charAt(i) == '_') {
                char markerChar = s.charAt(i);
                int e = s.indexOf(markerChar, i + 1);
                if (e > i + 1) {
                    int start = out.length();
                    out.append(s, i + 1, e);
                    spans.add(new Span(start, out.length(), Style.ITALIC));
                    i = e + 1;
                    continue;
                }
            }
            out.append(s.charAt(i++));
        }
    }

    private static boolean isSafeImageTarget(String target) {
        if (!target.startsWith("attachments/") || target.length() <= "attachments/".length()) {
            return false;
        }
        String name = target.substring("attachments/".length());
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return false;
        }
        String extension = name.substring(dot + 1);
        if (!(extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg")
                || extension.equalsIgnoreCase("png") || extension.equalsIgnoreCase("webp"))) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static void append(StringBuilder out, List<Span> spans, String text, Style style) {
        int start = out.length();
        out.append(text);
        if (style != null && out.length() > start) {
            spans.add(new Span(start, out.length(), style));
        }
    }
    private static boolean isFence(String line) {
        return line.trim().startsWith("```");
    }

    private static String fenceLanguage(String line) {
        String value = line.trim().substring(3).trim();
        int space = value.indexOf(' ');
        return space >= 0 ? value.substring(0, space) : value;
    }

    private static boolean isHorizontalRule(String line) {
        String value = line.trim();
        if (value.length() < 3) return false;
        char marker = value.charAt(0);
        if (marker != '-' && marker != '*' && marker != '_') return false;
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == marker) count++;
            else if (value.charAt(i) != ' ') return false;
        }
        return count >= 3;
    }

    private static boolean isTableHeader(String line, String next) {
        return line.indexOf('|') >= 0 && isTableSeparator(markdownBody(next));
    }

    private static boolean isTableSeparator(String line) {
        String value = line.trim();
        if (value.indexOf('|') < 0) return false;
        String[] cells = value.split("\\|", -1);
        boolean any = false;
        for (String cell : cells) {
            String c = cell.trim();
            if (c.isEmpty()) continue;
            if (!c.matches(":?-{3,}:?")) return false;
            any = true;
        }
        return any;
    }

    private static int renderTable(String[] lines, int headerIndex, StringBuilder out,
                                   List<Span> spans, List<TableBlock> tables, String header) {
        String headerLine = withoutNewline(lines[headerIndex]);
        String headerTimestamp = headerLine.substring(0, timestampPrefixLength(headerLine));
        int tableStart = out.length() - headerTimestamp.length();
        String[] headerCells = tableCells(header);
        List<String[]> rows = new ArrayList<>();
        List<String> timestamps = new ArrayList<>();
        int i = headerIndex + 2;
        while (i < lines.length) {
            String line = withoutNewline(lines[i]);
            String body = markdownBody(line);
            String trimmed = body.trim();
            if (trimmed.startsWith("/") || isFence(body) || trimmed.indexOf('|') < 0) break;
            rows.add(tableCells(body));
            timestamps.add(line.substring(0, timestampPrefixLength(line)));
            i++;
        }
        int columns = headerCells.length;
        for (String[] row : rows) columns = Math.max(columns, row.length);
        int[] widths = new int[columns];
        for (int c = 0; c < headerCells.length; c++) widths[c] = headerCells[c].length();
        for (String[] row : rows) for (int c = 0; c < row.length; c++) widths[c] = Math.max(widths[c], row[c].length());
        appendTableRow(out, spans, headerCells, widths, Style.TABLE_HEADER);
        String separator = withoutNewline(lines[headerIndex + 1]);
        String separatorTimestamp = separator.substring(0, timestampPrefixLength(separator));
        out.append('\n');
        out.append(separatorTimestamp);
        append(out, spans, tableRule(widths), Style.TABLE);
        for (int r = 0; r < rows.size(); r++) {
            out.append('\n');
            out.append(timestamps.get(r));
            appendTableRow(out, spans, rows.get(r), widths, Style.TABLE);
        }
        List<TableRow> tableRows = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            tableRows.add(new TableRow(timestamps.get(r), rows.get(r)));
        }
        String separatorBody = markdownBody(separator);
        List<Alignment> alignments = tableAlignments(separatorBody, columns);
        tables.add(new TableBlock(tableStart, out.length(), headerTimestamp, headerCells,
                separatorTimestamp, tableRows, alignments));
        int lastConsumed = rows.isEmpty() ? headerIndex + 1 : i - 1;
        if (lines[lastConsumed].endsWith("\n")) out.append('\n');
        return lastConsumed;
    }

    private static void appendTableRow(StringBuilder out, List<Span> spans, String[] row,
                                       int[] widths, Style style) {
        int start = out.length();
        out.append("│ ");
        for (int c = 0; c < widths.length; c++) {
            String cell = c < row.length ? row[c] : "";
            out.append(cell);
            for (int p = cell.length(); p < widths[c]; p++) out.append(' ');
            out.append(c + 1 == widths.length ? " │" : " │ ");
        }
        spans.add(new Span(start, out.length(), style));
    }

    private static String tableRule(int[] widths) {
        StringBuilder rule = new StringBuilder();
        rule.append("├─");
        for (int c = 0; c < widths.length; c++) {
            for (int n = 0; n < widths[c] + 2; n++) rule.append('─');
            rule.append(c + 1 == widths.length ? "┤" : "┼");
        }
        return rule.toString();
    }

    private static String withoutNewline(String line) {
        return line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    }

    private static String markdownBody(String line) {
        String body = withoutNewline(line);
        return body.substring(timestampPrefixLength(body));
    }

    private static String[] tableCells(String line) {
        String value = line.trim();
        if (value.startsWith("|")) value = value.substring(1);
        if (value.endsWith("|")) value = value.substring(0, value.length() - 1);
        String[] cells = value.split("\\|", -1);
        for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
        return cells;
    }

    private static List<Alignment> tableAlignments(String line, int columns) {
        String[] cells = tableCells(line);
        List<Alignment> result = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            String cell = i < cells.length ? cells[i] : "";
            boolean left = cell.startsWith(":");
            boolean right = cell.endsWith(":");
            result.add(left && right ? Alignment.CENTER : right ? Alignment.RIGHT
                    : left ? Alignment.LEFT : Alignment.NONE);
        }
        return result;
    }

    private static List<String> immutableCells(String[] cells) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, cells);
        return Collections.unmodifiableList(result);
    }

    private static boolean isEscapable(char c) {
        return "\\`*_{}[]()#+-.!|>~".indexOf(c) >= 0;
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static int timestampPrefixLength(String line) {
        if (line.length() < 11 || line.charAt(0) != '[' || line.charAt(3) != ':'
                || line.charAt(6) != ':' || line.charAt(9) != ']' || line.charAt(10) != ' ') {
            return 0;
        }
        for (int index : new int[]{1, 2, 4, 5, 7, 8}) {
            if (!Character.isDigit(line.charAt(index))) {
                return 0;
            }
        }
        return 11;
    }

    private static boolean findClosingFence(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            String line = lines[i].endsWith("\n")
                    ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
            if (isFence(line.substring(timestampPrefixLength(line)))) {
                return true;
            }
        }
        return false;
    }
}
