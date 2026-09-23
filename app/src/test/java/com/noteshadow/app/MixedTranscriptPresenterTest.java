package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MixedTranscriptPresenterTest {
    @Test public void insertsNovelOnlyAfterTwoToFiveCanonicalLines() {
        StringBuilder canonical = new StringBuilder();
        for (int i = 1; i <= 20; i++) canonical.append("[10:00:00] 真实句").append(i).append('\n');
        String novel = "小说甲段文字用于混合展示并且有足够的长度。小说乙段文字同样只用于屏幕展示。"
                + "小说丙段文字继续提供截取内容。小说丁段文字作为最后一段。";

        String mixed = MixedTranscriptPresenter.build(canonical.toString(), novel, 0);
        String[] lines = mixed.trim().split("\\n");
        int realRun = 0;
        int novelLines = 0;
        for (String line : lines) {
            if (line.contains("真实句")) {
                realRun++;
            } else {
                assertTrue("novel must follow 2-5 real lines", realRun >= 2 && realRun <= 5);
                assertTrue("novel keeps a derived timestamp", line.startsWith("[10:00:01] "));
                realRun = 0;
                novelLines++;
            }
        }
        assertTrue(novelLines > 0);
        assertTrue(mixed.contains("[10:00:00] 真实句1"));
    }

    @Test public void doesNotChangeCanonicalInputAndIsStableAcrossRebuilds() {
        String canonical = "[09:00:01] 第一条真实内容\n[09:00:06] 第二条真实内容\n[09:00:11] 第三条真实内容\n";
        String original = canonical;
        String novel = "这是一段只允许出现在前台混合展示中的小说文字，不能进入正式记录。";

        String first = MixedTranscriptPresenter.build(canonical, novel, 0);
        String second = MixedTranscriptPresenter.build(canonical, novel, 0);

        assertEquals(original, canonical);
        assertEquals(first, second);
        assertTrue(first.contains("第一条真实内容"));
    }

    @Test public void reportsNextNovelPositionWithoutShiftingStableRebuild() {
        StringBuilder canonical = new StringBuilder();
        for (int i = 1; i <= 12; i++) canonical.append("[10:00:00] 真实句").append(i).append('\n');
        String novel = "甲段小说文字足够长用于验证阅读位置会跟随混排输出向前移动，并且不会过早回到开头。"
                + "乙段小说文字继续提供足够内容，并保持重复构建完全稳定，不改变已经显示的片段。"
                + "丙段小说文字用于扩大测试正文长度，确保多次插入之后下一位置仍然大于起点。"
                + "丁段小说文字继续补足剩余内容，让这个测试只验证进度而不意外触发循环阅读。";

        MixedTranscriptPresenter.BuildResult first = MixedTranscriptPresenter.buildResult(
                canonical.toString(), novel, 3, "1-12");
        MixedTranscriptPresenter.BuildResult second = MixedTranscriptPresenter.buildResult(
                canonical.toString(), novel, 3, "1-12");

        assertTrue(first.nextNovelPosition > 3);
        assertEquals(first.nextNovelPosition, second.nextNovelPosition);
        assertEquals(first.text, second.text);
    }

    @Test public void reportedPositionUsesOriginalDocumentOffsetsAcrossWhitespace() {
        String canonical = "[10:00:00] 一\n[10:00:01] 二\n[10:00:02] 三\n[10:00:03] 四\n[10:00:04] 五\n[10:00:05] 六\n";
        StringBuilder novel = new StringBuilder();
        for (int i = 0; i < 80; i++) novel.append('\n');
        for (int i = 0; i < 20; i++) {
            novel.append("正文从大量换行之后开始，这个位置必须仍然对应原始文档中的字符偏移。后续正文继续补足切片长度。");
        }

        MixedTranscriptPresenter.BuildResult result = MixedTranscriptPresenter.buildResult(
                canonical, novel.toString(), 0, "1-6");

        assertTrue(result.text.contains("正文"));
        assertTrue(result.nextNovelPosition > 80);
    }

    @Test public void withoutImportedBookShowsOnlyRealText() {
        String canonical = "[09:00:01] 第一条\n[09:00:06] 第二条\n";
        assertEquals(canonical, MixedTranscriptPresenter.build(canonical, "", 0));
    }

    @Test public void novelTimestampWrapsAfterMidnight() {
        String canonical = "[23:59:59] 第一条\n[23:59:59] 第二条\n[23:59:59] 第三条\n"
                + "[23:59:59] 第四条\n[23:59:59] 第五条\n";
        String novel = "这是一段足够长的小说文字，用于验证时间戳跨日后能正确回到零点。";

        String mixed = MixedTranscriptPresenter.build(canonical, novel, 0);

        assertTrue(mixed.contains("[00:00:00] 这是一段"));
    }

    @Test public void ineligibleLinesNeverAdvanceNovelOutput() {
        StringBuilder canonical = new StringBuilder();
        for (int i = 1; i <= 20; i++) canonical.append("[10:00:00] 真实句").append(i).append('\n');

        String mixed = MixedTranscriptPresenter.build(canonical.toString(),
                "这是一段只能在假装页前台时插入的小说文字。", 0, "");

        assertEquals(canonical.toString(), mixed);
    }

    @Test public void eligibleRangesMergeAndClipForPersistentForegroundTracking() {
        String ranges = MixedTranscriptPresenter.appendEligibleRange("1-3", 4, 6);
        ranges = MixedTranscriptPresenter.appendEligibleRange(ranges, 10, 12);

        assertEquals("1-6,10-12", ranges);
        assertEquals("1-6,10", MixedTranscriptPresenter.clipEligibleRanges(ranges, 10));
    }

    @Test public void ineligibleMiddleRangeDoesNotInsertNovelWhilePageIsHidden() {
        StringBuilder canonical = new StringBuilder();
        for (int i = 1; i <= 15; i++) canonical.append("[10:00:00] 真实句").append(i).append('\n');
        String novel = "小说甲段文字足够长且只能在假装页显示。"
                + "小说乙段文字同样只能在假装页显示。";

        String[] lines = MixedTranscriptPresenter.build(
                canonical.toString(), novel, 0, "1-5,11-15").trim().split("\n");

        int previousReal = 0;
        int novelLines = 0;
        for (String line : lines) {
            if (line.contains("真实句")) {
                previousReal = Integer.parseInt(line.substring(line.indexOf("真实句") + 3));
            } else {
                assertTrue("hidden lines 6-10 must not insert novel",
                        previousReal <= 5 || previousReal >= 11);
                novelLines++;
            }
        }
        assertTrue(novelLines > 0);
    }
}
