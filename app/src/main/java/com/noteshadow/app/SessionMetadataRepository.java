package com.noteshadow.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads and atomically writes session sidecar metadata without touching snapshots. */
public final class SessionMetadataRepository {
    private static final String FILE_NAME = "session_meta.json";
    private final File sessionsDirectory;
    private final ProjectRepository projects;
    private static final ConcurrentHashMap<String, Object> TRANSACTION_LOCKS = new ConcurrentHashMap<>();
    public SessionMetadataRepository(File filesDir) { this(filesDir, new File(filesDir, "sessions")); }
    SessionMetadataRepository(File filesDir, File sessionsDirectory) {
        if (filesDir == null || sessionsDirectory == null) throw new IllegalArgumentException("directory must not be null");
        this.sessionsDirectory = sessionsDirectory; projects = new ProjectRepository(filesDir);
    }

    public SessionMetadata load(String sessionId) {
        if (!SessionRepository.isSafeSessionId(sessionId)) return null;
        File dir = new File(sessionsDirectory, sessionId);
        try { if (FilePathSafety.isSymbolicLink(dir) || !dir.isDirectory() || !dir.getCanonicalFile().getParentFile().equals(sessionsDirectory.getCanonicalFile())) return null; } catch (IOException e) { return null; }
        File target = new File(dir, FILE_NAME);
        Object lock = transactionLock(target);
        synchronized (lock) {
        Map<String, String> fields = JsonCodec.parse(SafeFileWriter.read(target));
        String projectId = fields.get("projectId");
        if (projects.load(projectId) == null) projectId = ProjectRecord.DEFAULT_ID;
        return new SessionMetadata(sessionId, projectId, fields.get("title"), "true".equals(fields.get("archived")), number(fields.get("updatedAt"))); }
    }

    public void save(SessionMetadata metadata) throws IOException {
        if (metadata == null || !SessionRepository.isSafeSessionId(metadata.getSessionId()) || !ProjectRepository.isSafeId(metadata.getProjectId()) || projects.load(metadata.getProjectId()) == null) throw new IllegalArgumentException("invalid session metadata");
        if (metadata.getTitle().length() > 120 || metadata.getTitle().indexOf('\n') >= 0 || metadata.getTitle().indexOf('\r') >= 0) throw new IllegalArgumentException("invalid session title");
        File dir = new File(sessionsDirectory, metadata.getSessionId());
        try {
            if (FilePathSafety.isSymbolicLink(dir) || (dir.exists() && (!dir.isDirectory() || !dir.getCanonicalFile().getParentFile().equals(sessionsDirectory.getCanonicalFile())))) throw new IllegalArgumentException("invalid session directory");
        } catch (IOException e) { throw new IOException("cannot validate session directory", e); }
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create session directory");
        File target = new File(dir, FILE_NAME);
        String json = "{\"sessionId\":" + JsonCodec.quote(metadata.getSessionId()) + ",\"projectId\":" + JsonCodec.quote(metadata.getProjectId()) + ",\"title\":" + JsonCodec.quote(metadata.getTitle()) + ",\"archived\":" + metadata.isArchived() + ",\"updatedAt\":" + metadata.getUpdatedAt() + "}\n";
        synchronized (transactionLock(target)) { SafeFileWriter.write(target, json); }
    }
    public void save(String sessionId, String projectId, String title, boolean archived) throws IOException { save(new SessionMetadata(sessionId, projectId, title, archived, System.currentTimeMillis())); }

    public void updateTitle(String sessionId, String title) throws IOException { update(sessionId, title, null); }
    public void setArchived(String sessionId, boolean archived) throws IOException { update(sessionId, null, archived); }
    private void update(String sessionId, String title, Boolean archived) throws IOException {
        if (!SessionRepository.isSafeSessionId(sessionId)) throw new IllegalArgumentException("invalid session id");
        File target = new File(new File(sessionsDirectory, sessionId), FILE_NAME);
        File dir = target.getParentFile();
        try { if (FilePathSafety.isSymbolicLink(dir) || !dir.isDirectory() || !dir.getCanonicalFile().getParentFile().equals(sessionsDirectory.getCanonicalFile())) throw new IllegalArgumentException("unknown session"); }
        catch (IOException e) { throw new IOException("cannot validate session directory", e); }
        synchronized (transactionLock(target)) {
            SessionMetadata current = load(sessionId);
            if (current == null) current = new SessionMetadata(sessionId, ProjectRecord.DEFAULT_ID, "", false, 0L);
            save(new SessionMetadata(sessionId, current.getProjectId(), title == null ? current.getTitle() : title, archived == null ? current.isArchived() : archived, System.currentTimeMillis()));
        }
    }
    private static Object transactionLock(File target) { String key; try { key = target.getCanonicalPath(); } catch (IOException e) { key = target.getAbsolutePath(); } Object fresh = new Object(); Object old = TRANSACTION_LOCKS.putIfAbsent(key, fresh); return old == null ? fresh : old; }

    private static long number(String value) { try { return Long.parseLong(value); } catch (Exception e) { return 0L; } }
}
