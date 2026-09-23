package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ArcTranscriptExcerptTest {
    @Test public void returnsNewestFormalLinesInOriginalOrder() {
        String note = "[09:00:00] 第一行\n"
                + "第一行的转写续行\n"
                + "\n"
                + "[09:00:02] 第二行\r\n"
                + "[09:00:03] 第三行\n";
        assertEquals(Arrays.asList("[09:00:02] 第二行", "[09:00:03] 第三行"),
                ArcTranscriptExcerpt.latest(note, 2));
    }

    @Test public void keepsEmbeddedMultilineAsrContinuationWithInheritedTimestamp() {
        String note = "[09:00:00] 第一段\n第二句\n\n[09:00:02] 下一段";
        assertEquals(Arrays.asList("[09:00:00] 第一段", "[09:00:00] 第二句",
                        "[09:00:02] 下一段"),
                ArcTranscriptExcerpt.latest(note, 5));
        assertEquals(Arrays.asList("[09:00:00] 第二句", "[09:00:02] 下一段"),
                ArcTranscriptExcerpt.latest(note, 2));
    }

    @Test public void ignoresBlankTimestampLinesAndDoesNotExposeMutableList() {
        List<String> result = ArcTranscriptExcerpt.latest(
                "[09:00:00] 有内容\n[09:00:01]   \n非转写续行", 5);
        assertEquals(Arrays.asList("[09:00:00] 有内容"), result);
        try {
            result.add("unexpected");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
            return;
        }
        throw new AssertionError("result must be immutable");
    }

    @Test public void handlesEmptyAndOversizedRequests() {
        assertTrue(ArcTranscriptExcerpt.latest(null, 5).isEmpty());
        assertTrue(ArcTranscriptExcerpt.latest("[09:00:00] text", 0).isEmpty());
        assertEquals(1, ArcTranscriptExcerpt.latest("[09:00:00] text", 50).size());
    }
}
