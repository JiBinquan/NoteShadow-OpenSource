package com.noteshadow.app;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

public class FullCourseBundleExporterTest {
    @Test public void exportsCompleteContentAndExcludesFakeText() throws Exception {
        Path root = Files.createTempDirectory("full-bundle");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Path recordings = Files.createDirectories(session.resolve("recordings"));
        Path attachments = Files.createDirectories(session.resolve("attachments"));
        Files.write(recordings.resolve("class.m4a"), bytes("audio"));
        Files.write(attachments.resolve("board.png"), bytes("image"));
        Files.write(session.resolve("fake_note.txt"), bytes("novel must not leave app"));
        String note = "timestamp\n![board](attachments/board.png)";
        SessionRecord record = record("s1", "课堂记录", "formal", "draft", note, "novel");

        Map<String, byte[]> zip = readZip(root, Collections.singletonList(record));
        assertTrue(zip.containsKey("课堂记录-s1/manifest.json"));
        assertEquals("formal", text(zip, "课堂记录-s1/transcripts/formal.txt"));
        assertEquals("draft", text(zip, "课堂记录-s1/transcripts/draft.txt"));
        assertEquals(note, text(zip, "课堂记录-s1/timestamp_note.md"));
        assertEquals("audio", text(zip, "课堂记录-s1/recordings/class.m4a"));
        assertEquals("image", text(zip, "课堂记录-s1/attachments/board.png"));
        assertTrue(text(zip, "课堂记录-s1/timestamp_note.md").contains("attachments/board.png"));
        assertFalse(zip.keySet().toString(), zip.keySet().stream().anyMatch(s -> s.contains("fake")));
        String manifest = text(zip, "课堂记录-s1/manifest.json");
        assertTrue(manifest.contains("\"formatVersion\": 1"));
        assertTrue(manifest.contains("\"contains\""));
        assertFalse(manifest.contains("novel"));
        assertFalse(manifest.contains("bookName"));
        assertFalse(manifest.contains("bookPosition"));
    }

    @Test public void exportsDraftOrRecordingButSkipsFakeOnly() throws Exception {
        Path root = Files.createTempDirectory("full-bundle-min");
        Path sessions = Files.createDirectories(root.resolve("sessions"));
        Path draftOnly = Files.createDirectories(sessions.resolve("draft"));
        Files.write(draftOnly.resolve("draft_note.txt"), bytes("draft only"));
        Path audioOnly = Files.createDirectories(sessions.resolve("audio"));
        Files.write(audioOnly.resolve("old.m4a"), bytes("audio only"));
        Path fakeOnly = Files.createDirectories(sessions.resolve("fake"));
        Files.write(fakeOnly.resolve("fake_note.txt"), bytes("fake only"));

        List<SessionRecord> records = Arrays.asList(
                record("draft", "Draft", "", "draft only", "", "fake"),
                record("audio", "Audio", "", "", "", "fake"),
                record("fake", "Fake", "", "", "", "fake only"));
        Map<String, byte[]> zip = readZip(root, records);
        assertTrue(zip.containsKey("Draft-draft/transcripts/draft.txt"));
        assertTrue(zip.containsKey("Audio-audio/recordings/old.m4a"));
        assertFalse(zip.keySet().stream().anyMatch(s -> s.startsWith("Fake-fake/")));
    }

    @Test public void duplicateRecordsGetUniqueSafeTopLevelNames() throws Exception {
        Path root = Files.createTempDirectory("full-bundle-unique");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("same"));
        Files.write(session.resolve("meeting_note.txt"), bytes("x"));
        Map<String, byte[]> zip = readZip(root, Arrays.asList(
                record("same", "A:/bad", "x", "", "", ""),
                record("same", "A:/bad", "x", "", "", "")));
        assertTrue(zip.containsKey("A__bad-same/manifest.json"));
        assertTrue(zip.containsKey("A__bad-same-2/manifest.json"));
    }

    @Test public void filtersSymlinkAndOutOfSessionRecordingsAndAttachments() throws Exception {
        Path root = Files.createTempDirectory("full-bundle-links");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Path recordings = Files.createDirectories(session.resolve("recordings"));
        Path attachments = Files.createDirectories(session.resolve("attachments"));
        Path outside = Files.createTempDirectory("full-bundle-outside");
        Files.write(outside.resolve("outside.m4a"), bytes("outside"));
        Files.write(outside.resolve("outside.png"), bytes("outside image"));
        Files.write(recordings.resolve("inside.m4a"), bytes("inside"));
        Files.write(attachments.resolve("inside.png"), bytes("inside image"));
        try {
            Files.createSymbolicLink(recordings.resolve("link.m4a"), outside.resolve("outside.m4a"));
            Files.createSymbolicLink(attachments.resolve("link.png"), outside.resolve("outside.png"));
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException ignored) {
            // The remaining assertions still cover direct ownership on platforms
            // where the test runner cannot create links.
        }
        SessionRecord record = record("s1", "Safe", "", "", "", "");
        Map<String, byte[]> zip = readZip(root, Collections.singletonList(record));
        assertTrue(zip.containsKey("Safe-s1/recordings/inside.m4a"));
        assertTrue(zip.containsKey("Safe-s1/attachments/inside.png"));
        assertFalse(zip.keySet().stream().anyMatch(s -> s.contains("link") || s.contains("outside")));
    }

    @Test public void acceptsLegacyRootRecordingLayout() throws Exception {
        Path root = Files.createTempDirectory("full-bundle-legacy");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("legacy"));
        Files.write(session.resolve("old.M4A"), bytes("legacy audio"));
        Map<String, byte[]> zip = readZip(root, Collections.singletonList(
                record("legacy", "Legacy", "", "", "", "")));
        assertEquals("legacy audio", text(zip, "Legacy-legacy/recordings/old.M4A"));
    }

    private static SessionRecord record(String id, String title, String formal, String draft,
                                        String timestamp, String fake) {
        return new SessionRecord(id, 10, 20, "Book", 99, 5, "", formal, timestamp,
                draft, fake, Collections.<File>emptyList(), "project", title, false);
    }

    private static Map<String, byte[]> readZip(Path root, List<SessionRecord> records) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(records.size() == 3 ? 2 : records.size(),
                FullCourseBundleExporter.writeZip(root.toFile(), records, output));
        Map<String, byte[]> entries = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            entries.put(entry.getName(), read(zip));
        }
        return entries;
    }

    private static byte[] read(ZipInputStream zip) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[128];
        int n;
        while ((n = zip.read(buffer)) != -1) result.write(buffer, 0, n);
        return result.toByteArray();
    }

    private static String text(Map<String, byte[]> entries, String name) {
        assertTrue("missing " + name, entries.containsKey(name));
        return new String(entries.get(name), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
