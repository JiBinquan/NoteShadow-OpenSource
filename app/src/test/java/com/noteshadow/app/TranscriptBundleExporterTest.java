package com.noteshadow.app;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TranscriptBundleExporterTest {
    @Test public void exportsOnlyMeetingNotesAsUtf8WithUniqueSafeNames() throws Exception {
        SessionRecord first = record("one", "同名/记录", "第一条\n中文");
        SessionRecord second = record("two", "同名/记录", "第二条");
        SessionRecord empty = record("empty", "空", "");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertEquals(2, TranscriptBundleExporter.writeZip(Arrays.asList(first, second, empty), bytes));
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        ZipEntry entry = zip.getNextEntry(); assertTrue(entry.getName().endsWith(".txt")); assertTrue(entry.getName().indexOf('/') < 0);
        byte[] data = read(zip); assertTrue(new String(data, "UTF-8").contains("中文"));
        String firstName = entry.getName(); assertTrue(firstName.contains("同名_记录-one.txt"));
        entry = zip.getNextEntry(); assertTrue(entry.getName().contains("同名_记录-two.txt")); assertTrue(!firstName.equals(entry.getName()));
        assertEquals("第二条", new String(read(zip), "UTF-8"));
        assertEquals(null, zip.getNextEntry());
        zip.close();
    }

    private static SessionRecord record(String id, String title, String note) {
        return new SessionRecord(id, 1, 1, "", 0, 0, "", note, "", "", Collections.<File>emptyList(), ProjectRecord.DEFAULT_ID, title, false);
    }
    private static byte[] read(ZipInputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[128]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n); return out.toByteArray();
    }
}
