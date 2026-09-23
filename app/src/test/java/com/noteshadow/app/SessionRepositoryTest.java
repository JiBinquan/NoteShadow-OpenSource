package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class SessionRepositoryTest {
    @Test public void listsValidSessionsNewestFirstAndLoadsAllNotes() throws Exception {
        Path root = Files.createTempDirectory("note-shadow");
        Path old = session(root, "old_1");
        write(old.resolve("session.json"), "{\"sessionId\":\"old_1\",\"updatedAt\":10,\"bookName\":\"课本\"}");
        write(old.resolve("meeting_note.txt"), "旧记录");
        Path fresh = session(root, "fresh_2");
        write(fresh.resolve("session.json"), "{\"sessionId\":\"fresh_2\",\"updatedAt\":20,\"createdAt\":5,\"bookPosition\":12}");
        write(fresh.resolve("meeting_note.txt"), "正式文本");
        write(fresh.resolve("timestamp_note.txt"), "[10:00:00] 课堂重点");
        write(fresh.resolve("draft_note.txt"), "草稿");
        write(fresh.resolve("fake_note.txt"), "模拟文本");
        Files.createDirectories(fresh.resolve("recordings"));
        write(fresh.resolve("recordings/a.m4a"), "a");
        write(fresh.resolve("recordings/b.M4A"), "b");
        fresh.resolve("recordings/a.m4a").toFile().setLastModified(1_000L);
        fresh.resolve("recordings/b.M4A").toFile().setLastModified(2_000L);

        List<SessionRecord> records = new SessionRepository(root.toFile()).list();
        assertEquals(2, records.size());
        assertEquals("fresh_2", records.get(0).getSessionId());
        assertEquals("正式文本", records.get(0).getMeetingNote());
        assertEquals("[10:00:00] 课堂重点", records.get(0).getTimestampNote());
        assertEquals(2, records.get(0).getRecordings().size());
        assertEquals("b.M4A", records.get(0).getRecordings().get(0).getName());
        assertEquals("正式文本", records.get(0).preview());
        assertEquals("记录 old_1", records.get(1).displayTitle());
    }

    @Test public void includesMeaningfulLegacyDataAndUsesSafeFallbackTime() throws Exception {
        Path root = Files.createTempDirectory("note-shadow");
        Path legacy = session(root, "legacy");
        write(legacy.resolve("draft_note.txt"), "只有草稿");
        Path empty = session(root, "empty");
        write(empty.resolve("unrelated.txt"), "not a session");
        List<SessionRecord> records = new SessionRepository(root.toFile()).list();
        assertEquals(1, records.size());
        assertEquals("legacy", records.get(0).getSessionId());
        assertTrue(records.get(0).getUpdatedAt() >= 0);
        assertEquals("", records.get(0).getTimestampNote());
    }

    @Test public void timestampNoteOnlySessionIsVisibleAndSearchable() throws Exception {
        Path root = Files.createTempDirectory("note-shadow");
        Path session = session(root, "notes_only");
        write(session.resolve("session.json"), "{\"sessionId\":\"notes_only\",\"updatedAt\":30}");
        write(session.resolve("timestamp_note.txt"), "[09:15:00] 重要定义");

        SessionRepository repository = new SessionRepository(root.toFile());
        List<SessionRecord> records = repository.list();
        assertEquals(1, records.size());
        assertEquals("[09:15:00] 重要定义", records.get(0).getTimestampNote());
        assertEquals(1, repository.list(ProjectRecord.DEFAULT_ID, false, "重要定义").size());
        assertTrue(repository.list(ProjectRecord.DEFAULT_ID, false, "不存在").isEmpty());
    }

    @Test public void rejectsTraversalAndMalformedMetadataWithoutCrashing() throws Exception {
        Path root = Files.createTempDirectory("note-shadow");
        Path safe = session(root, "safe");
        write(safe.resolve("session.json"), "{broken metadata");
        write(safe.resolve("meeting_note.txt"), "text");
        SessionRepository repository = new SessionRepository(root.toFile());
        assertNull(repository.load("../safe"));
        assertNull(repository.load("safe/child"));
        assertNull(repository.load(""));
        assertNotNull(repository.load("safe"));
        assertFalse(SessionRepository.isSafeSessionId("C:\\temp"));
    }

    @Test public void hidesSessionsThatOnlyContainDisguiseTextOrEmptyMetadata() throws Exception {
        Path root = Files.createTempDirectory("note-shadow");
        Path fakeOnly = session(root, "fake_only");
        write(fakeOnly.resolve("session.json"), "{\"sessionId\":\"fake_only\"}");
        write(fakeOnly.resolve("fake_note.txt"), "reading disguise");
        Path empty = session(root, "empty_json");
        write(empty.resolve("session.json"), "{\"sessionId\":\"empty_json\"}");

        assertTrue(new SessionRepository(root.toFile()).list().isEmpty());
    }

    @Test public void decodesJsonEscapesWithoutCollapsingLiteralBackslashes() throws Exception {
        Path root = Files.createTempDirectory("note-shadow");
        Path session = session(root, "escaped");
        write(session.resolve("session.json"), "{\"sessionId\":\"escaped\",\"bookName\":\"line\\nnext\\\\n\"}");
        write(session.resolve("meeting_note.txt"), "text");
        SessionRecord record = new SessionRepository(root.toFile()).load("escaped");
        assertNotNull(record);
        assertEquals("line\nnext\\n", record.getBookName());
    }

    @Test public void displayPreviewIsNeutralAndBounded() {
        assertEquals("一 二…", SessionRecord.previewOf("一\n二三四", 4));
        assertEquals("", SessionRecord.previewOf(null, 10));
        SessionRecord record = new SessionRecord("abc", 0, 0, "", 0, 0,
                "", "", "", "", null);
        assertEquals("记录 abc", record.displayTitle());
    }

    @Test public void ignoresRecordingsThatResolveOutsideTheSession() throws Exception {
        Path root = Files.createTempDirectory("note-shadow");
        Path session = session(root, "links");
        Path recordings = Files.createDirectories(session.resolve("recordings"));
        Path external = Files.createTempFile("outside", ".m4a");
        Files.write(external, "secret".getBytes(StandardCharsets.UTF_8));
        try {
            Files.createSymbolicLink(recordings.resolve("outside.m4a"), external);
            Files.createSymbolicLink(session.resolve("external-recordings"), external.getParent());
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException e) {
            return; // Symlink creation is unavailable on some Windows test hosts.
        }
        write(session.resolve("meeting_note.txt"), "text");
        SessionRecord record = new SessionRepository(root.toFile()).load("links");
        assertNotNull(record);
        assertTrue(record.getRecordings().isEmpty());
    }

    private static Path session(Path root, String id) throws Exception {
        return Files.createDirectories(root.resolve("sessions").resolve(id));
    }

    private static void write(Path path, String value) throws Exception {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }
}
