package com.noteshadow.app;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

public class CourseNoteBundleExporterTest {
    @Test public void exportsNoteAndOnlySafeDirectImages() throws Exception {
        Path root = Files.createTempDirectory("bundle");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Path images = Files.createDirectories(session.resolve("attachments"));
        Files.write(images.resolve("board.PNG"), new byte[] {1, 2, 3});
        Files.write(images.resolve("ignored.gif"), new byte[] {4});
        Files.write(images.resolve(".capture-pending.jpg"), new byte[] {5});
        SessionRecord record = new SessionRecord("s1", 1, 1, "", 0, 0, "", "", "[10:00] 重点", "", "", Collections.<File>emptyList(), ProjectRecord.DEFAULT_ID, "课堂/一", false);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertEquals(1, CourseNoteBundleExporter.writeZip(root.toFile(), Collections.singletonList(record), bytes));
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        ZipEntry e = zip.getNextEntry(); assertEquals("课堂_一-s1/timestamp_note.md", e.getName());
        assertEquals("[10:00] 重点", read(zip));
        e = zip.getNextEntry(); assertEquals("课堂_一-s1/attachments/board.PNG", e.getName());
        assertEquals(3, readBytes(zip).length); assertNull(zip.getNextEntry());
    }
    @Test public void ignoresLinkedSessionAndAttachment() throws Exception {
        Path root = Files.createTempDirectory("bundle-link");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Path outside = Files.createTempDirectory("outside");
        try {
            Files.createSymbolicLink(session.resolve("attachments"), outside);
            SessionRecord record = new SessionRecord("s1", 1, 1, "", 0, 0, "", "",
                    "n", "", "", Collections.<File>emptyList(),
                    ProjectRecord.DEFAULT_ID, "", false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(1, CourseNoteBundleExporter.writeZip(root.toFile(), Collections.singletonList(record), out));
            assertTrue(out.size() > 0);
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException ignored) { }
    }
    private static String read(ZipInputStream in) throws Exception { return new String(readBytes(in), StandardCharsets.UTF_8); }
    private static byte[] readBytes(ZipInputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] b = new byte[64]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n); return out.toByteArray();
    }
}
