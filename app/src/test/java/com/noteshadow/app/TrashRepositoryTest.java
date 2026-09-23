package com.noteshadow.app;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class TrashRepositoryTest {
    @Test public void combinesAttachmentUsageWithoutChangingLegacyCallers() {
        SessionStorageUsage active = new SessionStorageUsage(2, 3, 5);
        SessionStorageUsage total = active.plus(7, 11, 13);
        assertEquals(9, total.getTextBytes());
        assertEquals(14, total.getRecordingBytes());
        assertEquals(18, total.getAttachmentBytes());
        assertEquals(41, total.getTotalBytes());
        assertEquals(5, active.plus(0, 0).getAttachmentBytes());
    }

    @Test public void trashListsRestoresMetadataAndCountsUsage() throws Exception {
        Path root = Files.createTempDirectory("trash");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Files.write(session.resolve("meeting_note.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(session.resolve("recordings"));
        Files.write(session.resolve("recordings").resolve("a.m4a"), new byte[7]);
        Files.createDirectories(session.resolve("attachments"));
        Files.write(session.resolve("attachments").resolve("board.jpg"), new byte[9]);
        new SessionMetadataRepository(root.toFile()).save("s1", ProjectRecord.DEFAULT_ID, "课堂一", true);
        TrashRepository repo = new TrashRepository(root.toFile());
        assertTrue(repo.trash("s1"));
        assertNull(new SessionRepository(root.toFile()).load("s1"));
        TrashedSessionRecord item = repo.load("s1");
        assertNotNull(item); assertEquals("课堂一", item.getOriginalTitle()); assertTrue(item.isOriginalArchived());
        assertEquals(5, repo.usage().getTextBytes()); assertEquals(7, repo.usage().getRecordingBytes());
        assertEquals(9, repo.usage().getAttachmentBytes());
        assertTrue(repo.restore("s1"));
        assertEquals("课堂一", new SessionMetadataRepository(root.toFile()).load("s1").getTitle());
        assertTrue(new SessionMetadataRepository(root.toFile()).load("s1").isArchived());
    }

    @Test public void refusesConflictAndFakeMarkersAndPermanentlyDeletesValidEntry() throws Exception {
        Path root = Files.createTempDirectory("trash");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Files.write(session.resolve("meeting_note.txt"), "x".getBytes(StandardCharsets.UTF_8));
        TrashRepository repo = new TrashRepository(root.toFile());
        assertTrue(repo.trash("s1"));
        Files.createDirectories(root.resolve("sessions").resolve("s1"));
        assertFalse(repo.restore("s1"));
        assertTrue(repo.permanentlyDelete("s1"));
        assertFalse(repo.permanentlyDelete("s1"));
        Path fake = Files.createDirectories(root.resolve("trash").resolve("fake"));
        Files.write(fake.resolve("trash_meta.json"), "{\"sessionId\":\"other\",\"deletedAt\":1}".getBytes(StandardCharsets.UTF_8));
        assertNull(repo.load("fake"));
    }

    @Test public void rejectsTraversalAndSymlinkedTrashTree() throws Exception {
        Path root = Files.createTempDirectory("trash");
        TrashRepository repo = new TrashRepository(root.toFile());
        assertFalse(repo.trash("../x"));
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Files.write(session.resolve("meeting_note.txt"), "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(repo.trash("s1"));
        try {
            Path external = Files.createTempDirectory("external");
            Files.createSymbolicLink(root.resolve("trash").resolve("s1").resolve("escape"), external);
            assertFalse(repo.permanentlyDelete("s1"));
            assertTrue(Files.isRegularFile(root.resolve("trash").resolve("s1").resolve("trash_meta.json")));
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException ignored) { }
    }

    @Test public void countsLegacyRootRecordingAndRejectsLinkedContainer() throws Exception {
        Path root = Files.createTempDirectory("trash");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("legacy"));
        Files.write(session.resolve("meeting_note.txt"), "abc".getBytes(StandardCharsets.UTF_8));
        Files.write(session.resolve("legacy.m4a"), new byte[11]);
        SessionStorageUsage usage = new SessionRepository(root.toFile()).usage("legacy");
        assertEquals(3, usage.getTextBytes());
        assertEquals(11, usage.getRecordingBytes());
        try {
            Path linkedRoot = Files.createTempDirectory("linked-container");
            Path outside = Files.createTempDirectory("linked-sessions");
            Path link = linkedRoot.resolve("sessions");
            Files.createSymbolicLink(link, outside);
            assertTrue(Files.isSymbolicLink(link));
            assertTrue(new TrashRepository(linkedRoot.toFile()).list().isEmpty());
            assertFalse(new TrashRepository(linkedRoot.toFile()).trash("legacy"));
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException ignored) { }
    }

    @Test public void ignoresSymlinkedSessionDirectoriesEverywhere() throws Exception {
        Path root = Files.createTempDirectory("trash");
        Path outside = Files.createTempDirectory("outside");
        Files.write(outside.resolve("meeting_note.txt"), "outside".getBytes(StandardCharsets.UTF_8));
        try {
            Path linked = root.resolve("sessions").resolve("linked");
            Files.createDirectories(root.resolve("sessions"));
            Files.createSymbolicLink(linked, outside);
            SessionRepository sessions = new SessionRepository(root.toFile());
            assertTrue(sessions.list(ProjectRecord.DEFAULT_ID, false, "").isEmpty());
            assertNull(sessions.load("linked"));
            assertEquals(0L, sessions.usage("linked").getTotalBytes());
            SessionMetadataRepository metadata = new SessionMetadataRepository(root.toFile());
            assertNull(metadata.load("linked"));
            try {
                metadata.save("linked", ProjectRecord.DEFAULT_ID, "blocked", false);
                fail("metadata save must reject a symlinked session directory");
            } catch (IllegalArgumentException expected) { }
            try {
                metadata.setArchived("linked", true);
                fail("metadata update must reject a symlinked session directory");
            } catch (IllegalArgumentException expected) { }
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException ignored) { }
    }
}
