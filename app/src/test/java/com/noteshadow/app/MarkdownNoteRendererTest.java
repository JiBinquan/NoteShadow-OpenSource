package com.noteshadow.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarkdownNoteRendererTest {
    @Test public void rendersInlineStylesAndLink() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("**bold** *it* `x` [site](url)");
        assertEquals("bold it x site", r.getText());
        assertEquals(4, r.getSpans().size());
        assertEquals(MarkdownNoteRenderer.Style.BOLD, r.getSpans().get(0).getStyle());
        assertEquals(MarkdownNoteRenderer.Style.LINK, r.getSpans().get(3).getStyle());
        assertEquals("url", r.getSpans().get(3).getData());
        assertEquals("url", r.getSpans().get(3).getTarget());
    }
    @Test public void rendersBlocksAndKeepsLineBreaks() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("# Title\n- one\n2. two\n> quote");
        assertEquals("Title\n• one\n2. two\nquote", r.getText());
        assertEquals(4, r.getSpans().size());
    }
    @Test public void fencedCodeIsNotParsed() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("```java\n**x** `y`\n```");
        assertEquals("java\n**x** `y`\n", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.CODE, r.getSpans().get(0).getStyle());
        assertEquals(0, r.getSpans().get(0).getStart());
    }
    @Test public void timestampsAndCommandsRemainLiteral() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "[09:00:00] **spoken**\n[09:00:01] /lm **keep**");
        assertEquals("[09:00:00] spoken\n[09:00:01] /lm **keep**", r.getText());
    }
    @Test public void timestampPrefixIsKeptBeforeBlockMarkdown() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "[09:00:00] # Title\n[09:00:01] - item\n[09:00:02] 2. numbered");
        assertEquals("[09:00:00] Title\n[09:00:01] • item\n[09:00:02] 2. numbered", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.HEADING, r.getSpans().get(0).getStyle());
        assertEquals(MarkdownNoteRenderer.Style.UNORDERED_LIST, r.getSpans().get(1).getStyle());
        assertEquals(MarkdownNoteRenderer.Style.ORDERED_LIST, r.getSpans().get(2).getStyle());
        assertEquals(11, r.getSpans().get(0).getStart());
    }

    @Test public void timestampedFencedCodeKeepsTimestampsAndHidesFences() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "[09:00:00] ```java\n[09:00:01] **x** `y`\n[09:00:02] ```");
        assertEquals("[09:00:00] java\n[09:00:01] **x** `y`\n[09:00:02] ", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.CODE, r.getSpans().get(0).getStyle());
        assertEquals(11, r.getSpans().get(0).getStart());
        assertEquals(15, r.getSpans().get(0).getEnd());
        assertEquals(27, r.getSpans().get(1).getStart());
        assertEquals(36, r.getSpans().get(1).getEnd());
    }
    @Test public void incompleteSyntaxDegradesSafely() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("**open [x](url) ```\nplain");
        assertEquals("**open x ```\nplain", r.getText());
        assertEquals(1, r.getSpans().size());
        assertEquals(MarkdownNoteRenderer.Style.LINK, r.getSpans().get(0).getStyle());
    }

    @Test public void rendersSafeAttachmentImageBeforeLinks() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "![板书](attachments/board-01.PNG) [site](https://example.com)");
        assertEquals("板书 site", r.getText());
        assertEquals(2, r.getSpans().size());
        assertEquals(MarkdownNoteRenderer.Style.IMAGE, r.getSpans().get(0).getStyle());
        assertEquals("attachments/board-01.PNG", r.getSpans().get(0).getData());
        assertEquals("attachments/board-01.PNG", r.getSpans().get(0).getTarget());
        assertEquals(MarkdownNoteRenderer.Style.LINK, r.getSpans().get(1).getStyle());
    }

    @Test public void emptyImageAltUsesFallbackLabel() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "[09:01:02] ![](attachments/photo.jpeg)");
        assertEquals("[09:01:02] 图片", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.IMAGE, r.getSpans().get(0).getStyle());
        assertEquals(11, r.getSpans().get(0).getStart());
    }

    @Test public void unsafeImagesRemainLiteralAndNotLinks() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "![bad](../secret.png) ![bad](attachments/a.gif) ![bad](attachments/a/b.png)");
        assertEquals("![bad](../secret.png) ![bad](attachments/a.gif) ![bad](attachments/a/b.png)", r.getText());
        assertTrue(r.getSpans().isEmpty());
    }

    @Test public void imagesAreLiteralInCommandsAndFencedCode() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "/lm ![cmd](attachments/cmd.png)\n```\n![code](attachments/code.webp)\n```");
        assertEquals("/lm ![cmd](attachments/cmd.png)\n![code](attachments/code.webp)\n", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.CODE, r.getSpans().get(0).getStyle());
    }
    @Test public void rendersStrikethroughAndEscapesMarkdownPunctuation() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("~~removed~~ \\*literal\\* \\#tag");
        assertEquals("removed *literal* #tag", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.STRIKETHROUGH, r.getSpans().get(0).getStyle());
    }

    @Test public void rendersTaskListsAndNestedListIndentation() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("- [ ] todo\n  - [x] done\n    1. nested");
        assertEquals("☐ todo\n  ☑ done\n    1. nested", r.getText());
        assertEquals(3, r.getSpans().size());
        assertEquals(MarkdownNoteRenderer.Style.UNORDERED_LIST, r.getSpans().get(0).getStyle());
    }

    @Test public void rendersHorizontalRule() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("before\n---\nafter");
        assertEquals("before\n────────────────\nafter", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.HORIZONTAL_RULE, r.getSpans().get(0).getStyle());
    }

    @Test public void rendersGfmTableAsReadableText() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "| Name | Score |\n| :--- | ---: |\n| A | 10 |\n| B | 20 |");
        assertEquals("│ Name │ Score │\n├───────┼───────┤\n"
                + "│ A    │ 10    │\n│ B    │ 20    │", r.getText());
        assertEquals(4, r.getSpans().size());
        assertEquals(MarkdownNoteRenderer.Style.TABLE_HEADER, r.getSpans().get(0).getStyle());
        assertEquals(MarkdownNoteRenderer.Style.TABLE, r.getSpans().get(1).getStyle());
    }

    @Test public void timestampedTableKeepsEveryTimestampAndFollowingLine() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "[09:00:00] | A | B |\n[09:00:01] | --- | --- |\n"
                        + "[09:00:02] | x | y |\n[09:00:03] after");
        assertEquals("[09:00:00] │ A │ B │\n"
                + "[09:00:01] ├────┼───┤\n"
                + "[09:00:02] │ x │ y │\n[09:00:03] after", r.getText());
    }

    @Test public void showsFenceLanguageButKeepsCodeLiteral() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("```java\n**x** `y`\n```");
        assertEquals("java\n**x** `y`\n", r.getText());
        assertEquals(MarkdownNoteRenderer.Style.CODE, r.getSpans().get(0).getStyle());
        assertEquals("java", r.getText().substring(r.getSpans().get(0).getStart(), r.getSpans().get(0).getEnd()));
    }

    @Test public void tableLikeAndCommandCodeRemainLiteralCode() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "```text\na | b\n--- | ---\n/lm keep | literal\n```");
        assertEquals("text\na | b\n--- | ---\n/lm keep | literal\n", r.getText());
        for (MarkdownNoteRenderer.Span span : r.getSpans()) {
            assertEquals(MarkdownNoteRenderer.Style.CODE, span.getStyle());
        }
    }

    @Test public void commandContainingPipeStopsTableRows() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "| A | B |\n| --- | --- |\n| x | y |\n/lm keep | literal");
        assertTrue(r.getText().endsWith("\n/lm keep | literal"));
        assertEquals("/lm keep | literal",
                r.getText().substring(r.getText().lastIndexOf('\n') + 1));
    }

    @Test public void exposesImmutableStructuredTableWithRenderedRangeAndTimestamps() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "[09:00:00] | Name | Score | Note |\n"
                        + "[09:00:01] | :--- | :---: | ---: |\n"
                        + "[09:00:02] | A | 10 | ok |\n"
                        + "after");
        assertEquals(1, r.getTables().size());
        MarkdownNoteRenderer.TableBlock table = r.getTables().get(0);
        assertEquals(0, table.getStart());
        assertEquals(r.getText().indexOf("after") - 1, table.getEnd());
        assertEquals("[09:00:00] ", table.getHeaderTimestamp());
        assertEquals("[09:00:01] ", table.getSeparatorTimestamp());
        assertEquals(3, table.getHeaderCells().size());
        assertEquals("Name", table.getHeaderCellTexts().get(0));
        assertEquals(1, table.getRows().size());
        assertEquals("[09:00:02] ", table.getRows().get(0).getTimestamp());
        assertEquals("10", table.getRows().get(0).getCellTexts().get(1));
        assertEquals(MarkdownNoteRenderer.Alignment.LEFT, table.getAlignments().get(0));
        assertEquals(MarkdownNoteRenderer.Alignment.CENTER, table.getAlignments().get(1));
        assertEquals(MarkdownNoteRenderer.Alignment.RIGHT, table.getAlignments().get(2));
        try {
            table.getRows().add(null);
            fail("table rows must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test public void structuredTableDoesNotConsumeFollowingText() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render(
                "| A | B |\n| --- | --- |\n| x | y |\nplain");
        assertEquals(1, r.getTables().size());
        assertEquals("plain", r.getText().substring(r.getText().lastIndexOf('\n') + 1));
        assertEquals(r.getText().indexOf("plain") - 1, r.getTables().get(0).getEnd());
    }

    @Test public void codeAndCommandPipesDoNotCreateStructuredTables() {
        MarkdownNoteRenderer.Result code = MarkdownNoteRenderer.render(
                "```\na | b\n--- | ---\n```");
        assertTrue(code.getTables().isEmpty());
        MarkdownNoteRenderer.Result command = MarkdownNoteRenderer.render(
                "/lm a | b\n| --- | --- |\n| x | y |");
        assertTrue(command.getTables().isEmpty());
    }

    @Test public void unknownAndUnclosedSyntaxStaysSafe() {
        MarkdownNoteRenderer.Result r = MarkdownNoteRenderer.render("~~open **bold\n\\q");
        assertEquals("~~open **bold\n\\q", r.getText());
        assertTrue(r.getSpans().isEmpty());
    }
}
