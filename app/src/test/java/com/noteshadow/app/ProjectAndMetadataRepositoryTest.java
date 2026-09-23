package com.noteshadow.app;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

public class ProjectAndMetadataRepositoryTest {
    @Test public void projectRegistryCreatesRenamesAndRejectsDuplicateNames() throws Exception {
        Path root = Files.createTempDirectory("projects");
        ProjectRepository repo = new ProjectRepository(root.toFile());
        assertEquals("默认项目", repo.load("default").getName());
        ProjectRecord p = repo.create("物理课");
        assertEquals("物理课", p.getName());
        repo.rename(p.getProjectId(), "物理课-一班");
        try { repo.create("物理课-一班"); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void metadataFiltersProjectArchiveAndSearchExcludesFakeText() throws Exception {
        Path root = Files.createTempDirectory("metadata");
        ProjectRecord science = new ProjectRepository(root.toFile()).create("Science");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s1"));
        Files.write(session.resolve("session.json"), "{\"updatedAt\":10}".getBytes(StandardCharsets.UTF_8));
        Files.write(session.resolve("meeting_note.txt"), "正式内容".getBytes(StandardCharsets.UTF_8));
        Files.write(session.resolve("fake_note.txt"), "伪装词".getBytes(StandardCharsets.UTF_8));
        SessionMetadataRepository metadata = new SessionMetadataRepository(root.toFile());
        metadata.save("s1", science.getProjectId(), "标题", false);
        SessionRepository sessions = new SessionRepository(root.toFile());
        assertEquals(1, sessions.list(science.getProjectId(), false, "标题").size());
        assertTrue(sessions.list(science.getProjectId(), false, "伪装词").isEmpty());
        metadata.save("s1", science.getProjectId(), "标题", true);
        assertTrue(sessions.list(science.getProjectId(), false, "").isEmpty());
        assertEquals(1, sessions.list(science.getProjectId(), true, "").size());
    }

    @Test public void specialCharactersRoundTripAndUpdatesPreserveFields() throws Exception {
        Path root = Files.createTempDirectory("special");
        ProjectRecord project = new ProjectRepository(root.toFile()).create("A, \"B\\C\" {中文}");
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s2"));
        Files.write(session.resolve("meeting_note.txt"), "正文".getBytes(StandardCharsets.UTF_8));
        SessionRepository repo = new SessionRepository(root.toFile());
        repo.updateTitle("s2", "标题, \"引号\\反斜杠\" {中文}");
        repo.setArchived("s2", true);
        SessionRecord record = repo.load("s2");
        assertEquals(ProjectRecord.DEFAULT_ID, record.getProjectId());
        assertEquals("标题, \"引号\\反斜杠\" {中文}", record.getTitle());
        assertTrue(record.isArchived());
    }

    @Test public void caseInsensitiveNamesAndBackupRecoveryWorkAcrossRestart() throws Exception {
        Path root = Files.createTempDirectory("recovery");
        ProjectRepository first = new ProjectRepository(root.toFile());
        first.create("Chemistry, \"Lab\\1\" {中文}");
        try { first.create("cHeMiStRy, \"lAb\\1\" {中文}"); fail(); } catch (IllegalArgumentException expected) { }
        ProjectRepository restarted = new ProjectRepository(root.toFile());
        assertEquals(2, restarted.list().size());
        Path target = root.resolve("projects.json");
        Files.move(target, root.resolve("projects.json.bak"));
        assertEquals(2, new ProjectRepository(root.toFile()).list().size());
    }

    @Test public void concurrentRepositoriesDoNotLoseProjectsOrMetadataFields() throws Exception {
        Path root = Files.createTempDirectory("concurrent");
        ProjectRepository a = new ProjectRepository(root.toFile()), b = new ProjectRepository(root.toFile());
        CountDownLatch start = new CountDownLatch(1); Thread one = new Thread(() -> { try { start.await(); a.create("One"); } catch (Exception ignored) { } }); Thread two = new Thread(() -> { try { start.await(); b.create("Two"); } catch (Exception ignored) { } }); one.start(); two.start(); start.countDown(); one.join(); two.join();
        assertEquals(3, new ProjectRepository(root.toFile()).list().size());
        Path session = Files.createDirectories(root.resolve("sessions").resolve("s")); Files.write(session.resolve("meeting_note.txt"), "内容".getBytes(StandardCharsets.UTF_8));
        SessionRepository x = new SessionRepository(root.toFile()), y = new SessionRepository(root.toFile());
        Thread title = new Thread(() -> { try { x.updateTitle("s", "并发标题"); } catch (Exception ignored) { } }); Thread archive = new Thread(() -> { try { y.setArchived("s", true); } catch (Exception ignored) { } }); title.start(); archive.start(); title.join(); archive.join();
        SessionRecord record = x.load("s"); assertEquals("并发标题", record.getTitle()); assertTrue(record.isArchived());
    }

    @Test public void metadataUpdatesRequireExistingSessionAndRejectExternalSymlink() throws Exception {
        Path root = Files.createTempDirectory("metadata-safety");
        SessionMetadataRepository metadata = new SessionMetadataRepository(root.toFile());
        try { metadata.updateTitle("missing", "x"); fail(); } catch (Exception expected) { }
        try {
            Path sessions = Files.createDirectories(root.resolve("sessions"));
            Path outside = Files.createTempDirectory("outside-session");
            Files.createSymbolicLink(sessions.resolve("linked"), outside);
            try { metadata.save("linked", ProjectRecord.DEFAULT_ID, "x", false); fail(); } catch (IllegalArgumentException expected) { }
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException unavailable) { }
    }

    @Test public void jsonCodecRoundTripsControlCharactersAndPunctuation() {
        String value = "逗号, 引号\" 反斜杠\\ 花括号{}\t换行\n";
        String json = "{\"value\":" + JsonCodec.quote(value) + "}";
        assertEquals(value, JsonCodec.parse(json).get("value"));
    }
}
