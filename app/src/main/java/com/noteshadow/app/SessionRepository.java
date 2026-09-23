package com.noteshadow.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads saved sessions without changing their on-disk layout. */
public final class SessionRepository {
    private static final String SESSION_JSON = "session.json";
    private static final String[] NOTE_FILES = {"meeting_note.txt", "draft_note.txt", "fake_note.txt", "timestamp_note.txt"};
    private final File sessionsDirectory;
    private final SessionMetadataRepository metadataRepository;

    public SessionRepository(File filesDir) {
        this(filesDir, new File(filesDir, "sessions"));
    }

    SessionRepository(File filesDir, File storageDirectory) {
        if (filesDir == null) throw new IllegalArgumentException("filesDir must not be null");
        this.sessionsDirectory = storageDirectory;
        this.metadataRepository = new SessionMetadataRepository(filesDir, storageDirectory);
    }

    public List<SessionRecord> list() {
        return list(ProjectRecord.DEFAULT_ID, false, "");
    }

    /** Lists active or archived sessions in a project, optionally searching user-visible text. */
    public List<SessionRecord> list(String projectId, boolean archived, String query) {
        File[] children = sessionsDirectory.listFiles();
        List<SessionRecord> result = new ArrayList<>();
        if (children == null) return result;
        for (File child : children) {
            if (!child.isDirectory() || !isSafeSessionId(child.getName())
                    || !isChildDirectory(child)) continue;
            SessionRecord record = read(child);
            if (record != null && record.getProjectId().equals(projectId)
                    && record.isArchived() == archived && matches(record, query)) result.add(record);
        }
        result.sort(Comparator.comparingLong(SessionRecord::getUpdatedAt).reversed()
                .thenComparing(SessionRecord::getSessionId));
        return result;
    }

    public List<SessionRecord> listSessions() { return list(); }

    public List<SessionRecord> list(String projectId) { return list(projectId, false, ""); }

    public SessionStorageUsage usage(String sessionId) {
        if (!isSafeSessionId(sessionId)) return new SessionStorageUsage(0L, 0L);
        File directory = new File(sessionsDirectory, sessionId);
        if (!isChildDirectory(directory)) return new SessionStorageUsage(0L, 0L);
        return SessionStorageUsage.measure(directory);
    }

    public void updateTitle(String sessionId, String title) throws java.io.IOException {
        metadataRepository.updateTitle(sessionId, title == null ? "" : title);
    }

    public void setArchived(String sessionId, boolean archived) throws java.io.IOException {
        metadataRepository.setArchived(sessionId, archived);
    }

    public SessionRecord load(String sessionId) {
        if (!isSafeSessionId(sessionId)) return null;
        File directory = new File(sessionsDirectory, sessionId);
        if (!isChildDirectory(directory)) return null;
        return read(directory);
    }

    public static boolean isSafeSessionId(String id) {
        if (id == null || id.isEmpty() || ".".equals(id) || "..".equals(id)) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.')) return false;
        }
        return true;
    }

    private SessionRecord read(File directory) {
        File jsonFile = new File(directory, SESSION_JSON);
        Map<String, String> values = parseJson(jsonFile);
        String meeting = readText(new File(directory, NOTE_FILES[0]));
        String draft = readText(new File(directory, NOTE_FILES[1]));
        String fake = readText(new File(directory, NOTE_FILES[2]));
        String timestamp = readText(new File(directory, NOTE_FILES[3]));
        List<File> recordings = recordings(directory);
        // The fake page may contain disguise/reading text repeated across many
        // sessions. History is for actual transcripts and recordings only.
        if (meeting.trim().isEmpty() && draft.trim().isEmpty() && timestamp.trim().isEmpty()
                && recordings.isEmpty()) return null;

        long fallbackUpdated = latestModified(directory);
        long updated = positiveLong(values.get("updatedAt"), fallbackUpdated);
        long created = positiveLong(values.get("createdAt"), Math.min(updated, directory.lastModified()));
        SessionMetadata metadata = metadataRepository.load(directory.getName());
        // The directory name is the lookup key. Metadata must never redirect a
        // list row to a different directory, even when session.json is corrupt.
        String id = directory.getName();
        return new SessionRecord(id, created, updated, values.get("bookName"),
                nonNegativeInt(values.get("bookPosition")), nonNegativeInt(values.get("bookChapter")),
                values.get("lastRecording"), meeting, timestamp, draft, fake, recordings,
                metadata == null ? ProjectRecord.DEFAULT_ID : metadata.getProjectId(),
                metadata == null ? "" : metadata.getTitle(),
                metadata != null && metadata.isArchived());
    }

    private static boolean matches(SessionRecord record, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return record.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                || record.getMeetingNote().toLowerCase(Locale.ROOT).contains(needle)
                || record.getDraftNote().toLowerCase(Locale.ROOT).contains(needle)
                || record.getTimestampNote().toLowerCase(Locale.ROOT).contains(needle);
    }

    private List<File> recordings(File directory) {
        List<File> result = new ArrayList<>();
        File sessionCanonical;
        try { sessionCanonical = directory.getCanonicalFile(); }
        catch (IOException e) { return result; }
        File recordingDirectory = new File(directory, "recordings");
        addRecordings(result, recordingDirectory, sessionCanonical);
        // Accept legacy layouts that kept the recording beside session.json.
        addRecordings(result, directory, sessionCanonical);
        result.sort(Comparator.comparingLong(File::lastModified).reversed()
                .thenComparing(File::getName));
        return result;
    }

    private static void addRecordings(List<File> output, File directory, File sessionCanonical) {
        try {
            // Do not follow an external recordings symlink. Canonicalizing both
            // directories also prevents a symlinked .m4a from escaping the session.
            File canonicalDirectory = directory.getCanonicalFile();
            if (!isWithin(sessionCanonical, canonicalDirectory)) return;
        } catch (IOException e) { return; }
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || file.length() <= 0
                    || !file.getName().toLowerCase(Locale.US).endsWith(".m4a")) continue;
            try {
                File canonicalFile = file.getCanonicalFile();
                if (isWithin(sessionCanonical, canonicalFile)
                        && !canonicalFile.equals(sessionCanonical)) output.add(canonicalFile);
            } catch (IOException ignored) { }
        }
    }

    private static boolean isWithin(File root, File candidate) {
        String rootPath = root.getPath();
        String candidatePath = candidate.getPath();
        return FilePathSafety.isWithinCanonical(root, candidate);
    }

    private static boolean isChildDirectory(File directory) {
        try {
            return !FilePathSafety.isSymbolicLink(directory)
                    && directory.isDirectory() && directory.getCanonicalFile().getParentFile()
                    .equals(directory.getParentFile().getCanonicalFile());
        } catch (IOException e) { return false; }
    }

    private static long latestModified(File directory) {
        long latest = directory.lastModified();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) latest = Math.max(latest, file.lastModified());
        File recordings = new File(directory, "recordings");
        File[] audio = recordings.listFiles();
        if (audio != null) for (File file : audio) latest = Math.max(latest, file.lastModified());
        return Math.max(0L, latest);
    }

    private static String readText(File file) {
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
        catch (IOException e) { return ""; }
    }

    private static long positiveLong(String value, long fallback) {
        try { return Math.max(0L, Long.parseLong(value)); }
        catch (Exception e) { return Math.max(0L, fallback); }
    }

    private static int nonNegativeInt(String value) {
        try { return Math.max(0, Integer.parseInt(value)); }
        catch (Exception e) { return 0; }
    }

    private static Map<String, String> parseJson(File file) {
        Map<String, String> result = new HashMap<>();
        String json = readText(file);
        int i = 0;
        while (i < json.length()) {
            int keyStart = json.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = quotedEnd(json, keyStart);
            if (keyEnd < 0) break;
            int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) break;
            String key = unescape(json.substring(keyStart + 1, keyEnd));
            int valueStart = colon + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) valueStart++;
            String value;
            if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                int valueEnd = quotedEnd(json, valueStart);
                if (valueEnd < 0) break;
                value = unescape(json.substring(valueStart + 1, valueEnd));
                i = valueEnd + 1;
            } else {
                int valueEnd = valueStart;
                while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') valueEnd++;
                value = json.substring(valueStart, valueEnd).trim();
                i = valueEnd + 1;
            }
            result.put(key, value);
        }
        return result;
    }

    private static int quotedEnd(String text, int start) {
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '"') continue;
            int slashes = 0;
            for (int j = i - 1; j > start && text.charAt(j) == '\\'; j--) slashes++;
            if ((slashes & 1) == 0) return i;
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                result.append(c);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"': result.append('"'); break;
                case '\\': result.append('\\'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'u':
                    if (i + 4 < value.length()) {
                        try {
                            result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                            break;
                        } catch (NumberFormatException ignored) { }
                    }
                    result.append('u');
                    break;
                default: result.append(escaped); break;
            }
        }
        return result.toString();
    }
}
