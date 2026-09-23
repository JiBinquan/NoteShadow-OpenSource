package com.noteshadow.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a portable, complete course archive.
 *
 * <p>The exporter deliberately reads the session directory rather than trusting
 * paths supplied by a UI or a caller.  Fake/novel text is never an export input.
 * Files are copied directly into the ZIP stream so recordings are not loaded into
 * memory.</p>
 */
public final class FullCourseBundleExporter {
    private static final String SESSION_DIRECTORY = "sessions";

    private FullCourseBundleExporter() { }

    /** Exports records and returns the number of records with real content. */
    public static int writeZip(File filesDir, List<SessionRecord> records,
                               OutputStream output) throws IOException {
        if (filesDir == null || output == null) {
            throw new IllegalArgumentException("filesDir/output must not be null");
        }
        File sessions = new File(filesDir, SESSION_DIRECTORY);
        Set<String> usedFolders = new HashSet<>();
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (records != null) {
                for (SessionRecord record : records) {
                    ExportPlan plan = plan(filesDir, sessions, record);
                    if (plan == null) continue;
                    String folder = uniqueFolder(plan.baseFolder, usedFolders);
                    String prefix = folder + "/";
                    put(zip, prefix + "manifest.json", manifest(record, plan));
                    if (!record.getMeetingNote().trim().isEmpty()) {
                        put(zip, prefix + "transcripts/formal.txt",
                                record.getMeetingNote().getBytes(StandardCharsets.UTF_8));
                    }
                    if (!record.getDraftNote().trim().isEmpty()) {
                        put(zip, prefix + "transcripts/draft.txt",
                                record.getDraftNote().getBytes(StandardCharsets.UTF_8));
                    }
                    if (!record.getTimestampNote().trim().isEmpty()) {
                        // Keep the note beside attachments/ so its existing relative
                        // Markdown image references remain portable after extraction.
                        put(zip, prefix + "timestamp_note.md",
                                record.getTimestampNote().getBytes(StandardCharsets.UTF_8));
                    }
                    Set<String> usedNames = new HashSet<>();
                    for (File recording : plan.recordings) {
                        String name = uniqueFileName(safeLeaf(recording.getName()), usedNames);
                        putFile(zip, prefix + "recordings/" + name, recording);
                    }
                    usedNames.clear();
                    for (File image : plan.attachments) {
                        String name = uniqueFileName(safeLeaf(image.getName()), usedNames);
                        putFile(zip, prefix + "attachments/" + name, image);
                    }
                    count++;
                }
            }
            zip.finish();
        }
        return count;
    }

    private static ExportPlan plan(File filesDir, File sessions, SessionRecord record) {
        if (record == null || !NoteAttachmentRepository.isSafeSessionId(record.getSessionId())) return null;
        if (!FilePathSafety.isDirectChild(sessions, filesDir) || !sessions.isDirectory()) return null;
        File session = new File(sessions, record.getSessionId());
        if (!FilePathSafety.isDirectChild(session, sessions) || !session.isDirectory()) return null;

        List<File> recordings = listRecordings(session);
        List<File> attachments = listAttachments(session);
        boolean formal = !record.getMeetingNote().trim().isEmpty();
        boolean draft = !record.getDraftNote().trim().isEmpty();
        boolean timestamp = !record.getTimestampNote().trim().isEmpty();
        // Fake/novel text is intentionally absent from this test: it must never
        // make an otherwise empty session look exportable.
        if (!formal && !draft && !timestamp && recordings.isEmpty() && attachments.isEmpty()) return null;
        String base = CourseNoteBundleExporter.safe(record.displayTitle()) + "-"
                + CourseNoteBundleExporter.safe(record.getSessionId());
        if (base.equals("-")) base = "记录-" + record.getSessionId();
        return new ExportPlan(base, recordings, attachments, formal, draft, timestamp);
    }

    private static List<File> listRecordings(File session) {
        List<File> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addRecordings(result, seen, session, session);
        File directory = new File(session, "recordings");
        if (FilePathSafety.isDirectChild(directory, session) && directory.isDirectory()) {
            addRecordings(result, seen, directory, session);
        }
        return result;
    }

    private static void addRecordings(List<File> result, Set<String> seen,
                                      File directory, File session) {
        if (!FilePathSafety.isWithin(session, directory) || FilePathSafety.isSymbolicLink(directory)) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || file.length() <= 0 || FilePathSafety.isSymbolicLink(file)
                    || !FilePathSafety.isDirectChild(file, directory)
                    || !file.getName().toLowerCase(Locale.US).endsWith(".m4a")) continue;
            if (!FilePathSafety.isWithin(session, file)) continue;
            try {
                String key = file.getCanonicalPath();
                if (seen.add(key)) result.add(file);
            } catch (IOException ignored) { }
        }
    }

    private static List<File> listAttachments(File session) {
        List<File> result = new ArrayList<>();
        File directory = new File(session, "attachments");
        if (!FilePathSafety.isDirectChild(directory, session) || !directory.isDirectory()) return result;
        File[] files = directory.listFiles();
        if (files == null) return result;
        for (File file : files) {
            if (!file.isFile() || FilePathSafety.isSymbolicLink(file)
                    || !FilePathSafety.isDirectChild(file, directory)
                    || !isSafeImage(file)) continue;
            result.add(file);
        }
        return result;
    }

    private static boolean isSafeImage(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return !name.startsWith(".") && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".webp"));
    }

    private static String uniqueFolder(String base, Set<String> used) {
        String value = base;
        int suffix = 2;
        while (!used.add(value.toLowerCase(Locale.ROOT))) value = base + "-" + suffix++;
        return value;
    }

    private static String uniqueFileName(String name, Set<String> used) {
        String base = name == null || name.isEmpty() ? "file" : name;
        String value = base;
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String extension = dot > 0 ? base.substring(dot) : "";
        int suffix = 2;
        while (!used.add(value.toLowerCase(Locale.ROOT))) value = stem + "-" + suffix++ + extension;
        return value;
    }

    private static String safeLeaf(String name) {
        String value = CourseNoteBundleExporter.safe(name);
        return value.isEmpty() || ".".equals(value) || "..".equals(value) ? "file" : value;
    }

    private static byte[] manifest(SessionRecord record, ExportPlan plan) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\n  \"formatVersion\": 1,")
                .append("\n  \"sessionId\": ").append(JsonCodec.quote(record.getSessionId())).append(',')
                .append("\n  \"title\": ").append(JsonCodec.quote(record.displayTitle())).append(',')
                .append("\n  \"projectId\": ").append(JsonCodec.quote(record.getProjectId())).append(',')
                .append("\n  \"createdAt\": ").append(record.getCreatedAt()).append(',')
                .append("\n  \"updatedAt\": ").append(record.getUpdatedAt()).append(',')
                .append("\n  \"archived\": ").append(record.isArchived()).append(',')
                .append("\n  \"contains\": [");
        boolean first = true;
        if (plan.formal) { json.append(contains(first, "formalTranscript")); first = false; }
        if (plan.draft) { json.append(contains(first, "draftTranscript")); first = false; }
        if (plan.timestamp) { json.append(contains(first, "timestampNote")); first = false; }
        if (!plan.recordings.isEmpty()) { json.append(contains(first, "recordings")); first = false; }
        if (!plan.attachments.isEmpty()) json.append(contains(first, "attachments"));
        return json.append("]\n}\n").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String contains(boolean first, String value) {
        return (first ? "" : ", ") + JsonCodec.quote(value);
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static void putFile(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
        } finally {
            zip.closeEntry();
        }
    }

    private static final class ExportPlan {
        final String baseFolder;
        final List<File> recordings;
        final List<File> attachments;
        final boolean formal;
        final boolean draft;
        final boolean timestamp;

        ExportPlan(String baseFolder, List<File> recordings, List<File> attachments,
                   boolean formal, boolean draft, boolean timestamp) {
            this.baseFolder = baseFolder;
            this.recordings = recordings;
            this.attachments = attachments;
            this.formal = formal;
            this.draft = draft;
            this.timestamp = timestamp;
        }
    }
}
