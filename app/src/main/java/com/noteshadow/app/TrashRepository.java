package com.noteshadow.app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Private, recoverable recycle bin for session directories. */
public final class TrashRepository {
    private static final String MARKER = "trash_meta.json";
    private final File filesDir;
    private final File trashDirectory;
    private final File sessionsDirectory;
    private final SessionRepository trashSessions;
    private final SessionMetadataRepository sessionMetadata;

    public TrashRepository(File filesDir) {
        if (filesDir == null) throw new IllegalArgumentException("filesDir must not be null");
        this.filesDir = filesDir;
        this.trashDirectory = new File(filesDir, "trash");
        this.sessionsDirectory = new File(filesDir, "sessions");
        this.trashSessions = new SessionRepository(filesDir, trashDirectory);
        this.sessionMetadata = new SessionMetadataRepository(filesDir);
    }

    public synchronized List<TrashedSessionRecord> list() {
        List<TrashedSessionRecord> result = new ArrayList<>();
        if (!validContainer(trashDirectory, filesDir)) return result;
        File[] children = trashDirectory.listFiles();
        if (children == null) return result;
        for (File child : children) {
            TrashedSessionRecord item = readValid(child);
            if (item != null) result.add(item);
        }
        result.sort(Comparator.comparingLong(TrashedSessionRecord::getDeletedAt).reversed()
                .thenComparing(TrashedSessionRecord::getSessionId));
        return result;
    }

    public synchronized TrashedSessionRecord load(String sessionId) {
        if (!SessionRepository.isSafeSessionId(sessionId)) return null;
        if (!validContainer(trashDirectory, filesDir)) return null;
        return readValid(new File(trashDirectory, sessionId));
    }

    /** Moves a session atomically at directory level, leaving source intact on marker failure. */
    public synchronized boolean trash(String sessionId) {
        if (!SessionRepository.isSafeSessionId(sessionId)) return false;
        File source = new File(sessionsDirectory, sessionId);
        File target = new File(trashDirectory, sessionId);
        if (!validContainer(sessionsDirectory, filesDir) || !validDirectChild(source, sessionsDirectory) || !source.isDirectory()
                || target.exists() || !validContainer(trashDirectory, filesDir)) return false;
        SessionMetadata metadata = sessionMetadata.load(sessionId);
        if (metadata == null) metadata = new SessionMetadata(sessionId, ProjectRecord.DEFAULT_ID, "", false, 0L);
        if (!trashDirectory.exists() && !trashDirectory.mkdirs()) return false;
        File marker = new File(source, MARKER);
        try {
            SafeFileWriter.write(marker, markerJson(sessionId, System.currentTimeMillis(), metadata));
            if (source.renameTo(target)) return true;
            cleanupMarkerArtifacts(marker);
            return false;
        } catch (IOException failure) {
            cleanupMarkerArtifacts(marker);
            return false;
        }
    }

    /** Restores a valid entry without overwriting an existing session. */
    public synchronized boolean restore(String sessionId) {
        TrashedSessionRecord item = load(sessionId);
        if (item == null) return false;
        File source = new File(trashDirectory, sessionId);
        File target = new File(sessionsDirectory, sessionId);
        if (target.exists() || !validContainer(sessionsDirectory, filesDir)) return false;
        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) return false;
        if (!source.renameTo(target)) return false;
        // The original session_meta.json moved together with the directory.
        // Do not rewrite it here: a project may have been removed while this
        // entry was in the bin, and a failed metadata write must not make the
        // successfully restored directory invisible. The marker is only
        // recovery metadata and can be cleaned up after the move.
        new File(target, MARKER).delete();
        return true;
    }

    /** Permanently removes only a valid, private trash entry. */
    public synchronized boolean permanentlyDelete(String sessionId) {
        TrashedSessionRecord item = load(sessionId);
        if (item == null) return false;
        File target = new File(trashDirectory, sessionId);
        if (!validContainer(trashDirectory, filesDir) || !safeTree(target, trashDirectory)) return false;
        // The entry itself is the deletion root. It is also the containment
        // root, so its marker is explicitly retained until all payload is gone.
        return deleteTree(target, target);
    }

    public synchronized SessionStorageUsage usage() {
        SessionStorageUsage result = new SessionStorageUsage(0L, 0L);
        for (TrashedSessionRecord item : list()) result = merge(result, usage(item.getSessionId()));
        return result;
    }

    public synchronized SessionStorageUsage usage(String sessionId) {
        if (!validContainer(trashDirectory, filesDir) || load(sessionId) == null) return new SessionStorageUsage(0L, 0L);
        return SessionStorageUsage.measure(new File(trashDirectory, sessionId));
    }

    private static SessionStorageUsage merge(SessionStorageUsage a, SessionStorageUsage b) {
        return new SessionStorageUsage(SessionStorageUsage.saturatingAdd(a.getTextBytes(), b.getTextBytes()),
                SessionStorageUsage.saturatingAdd(a.getRecordingBytes(), b.getRecordingBytes()),
                SessionStorageUsage.saturatingAdd(a.getAttachmentBytes(), b.getAttachmentBytes()));
    }

    private TrashedSessionRecord readValid(File directory) {
        if (!validContainer(trashDirectory, filesDir) || !validDirectChild(directory, trashDirectory) || !directory.isDirectory()) return null;
        Map<String, String> v = JsonCodec.parse(SafeFileWriter.read(new File(directory, MARKER)));
        String id = v.get("sessionId");
        if (!directory.getName().equals(id) || !SessionRepository.isSafeSessionId(id)) return null;
        long deletedAt;
        try { deletedAt = Long.parseLong(v.get("deletedAt")); } catch (Exception e) { return null; }
        SessionRecord record = trashSessions.load(id);
        if (record == null) return null;
        String project = v.get("originalProjectId");
        if (!ProjectRepository.isSafeId(project)) project = ProjectRecord.DEFAULT_ID;
        return new TrashedSessionRecord(record, deletedAt, "true".equals(v.get("originalArchived")), project, v.get("originalTitle"));
    }

    private static String markerJson(String id, long deletedAt, SessionMetadata m) {
        return "{\"sessionId\":" + JsonCodec.quote(id) + ",\"deletedAt\":" + deletedAt
                + ",\"originalArchived\":" + m.isArchived() + ",\"originalProjectId\":"
                + JsonCodec.quote(m.getProjectId()) + ",\"originalTitle\":" + JsonCodec.quote(m.getTitle()) + "}\n";
    }

    private static boolean validContainer(File directory, File expectedParent) {
        return FilePathSafety.validContainer(directory, expectedParent);
    }
    private static boolean validDirectChild(File child, File parent) {
        return FilePathSafety.isDirectChild(child, parent);
    }
    private static boolean safeTree(File root, File parent) {
        return FilePathSafety.safeTree(root, parent);
    }
    private static boolean safeTreeInside(File file, File root) {
        if (FilePathSafety.isSymbolicLink(file) || !FilePathSafety.isWithin(root, file)) return false;
        if (!file.isDirectory()) return file.isFile();
        File[] children = file.listFiles();
        if (children == null) return false;
        for (File child : children) if (!safeTreeInside(child, root)) return false;
        return true;
    }
    private static boolean within(File root, File candidate) {
        return FilePathSafety.isWithinCanonical(root, candidate);
    }
    private static boolean deleteTree(File file, File root) {
        if (FilePathSafety.isSymbolicLink(file) || !FilePathSafety.isWithin(root, file)) return false;
        File[] children = file.listFiles();
        String markerText = null;
        boolean isRoot = file.equals(root);
        if (isRoot) markerText = SafeFileWriter.read(new File(file, MARKER));
        if (children != null) for (File child : children) {
            if (isRoot && (MARKER.equals(child.getName()) || (MARKER + ".tmp").equals(child.getName())
                    || (MARKER + ".bak").equals(child.getName()))) continue;
            if (!deleteTree(child, root)) return false;
        }
        if (file.isDirectory() && children == null) return false;
        if (isRoot) {
            new File(file, MARKER + ".tmp").delete();
            new File(file, MARKER + ".bak").delete();
            File marker = new File(file, MARKER);
            if (!marker.delete()) return false;
            if (file.delete()) return true;
            if (markerText != null && !markerText.isEmpty()) {
                try { SafeFileWriter.write(marker, markerText); } catch (IOException ignored) { }
            }
            return false;
        }
        return file.delete();
    }

    private static void cleanupMarkerArtifacts(File marker) {
        marker.delete();
        new File(marker.getPath() + ".tmp").delete();
        new File(marker.getPath() + ".bak").delete();
    }
}
