package com.noteshadow.app;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Read-only, symlink-safe size totals for the four user-visible storage categories. */
public final class StorageCategoryUsage {
    private final long transcriptBytes;
    private final long noteBytes;
    private final long recordingBytes;
    private final long imageBytes;

    public StorageCategoryUsage(long transcriptBytes, long noteBytes,
                                long recordingBytes, long imageBytes) {
        this.transcriptBytes = Math.max(0L, transcriptBytes);
        this.noteBytes = Math.max(0L, noteBytes);
        this.recordingBytes = Math.max(0L, recordingBytes);
        this.imageBytes = Math.max(0L, imageBytes);
    }

    public long transcriptBytes() { return transcriptBytes; }
    public long noteBytes() { return noteBytes; }
    public long recordingBytes() { return recordingBytes; }
    public long imageBytes() { return imageBytes; }

    public StorageCategoryUsage plus(StorageCategoryUsage other) {
        if (other == null) return this;
        return new StorageCategoryUsage(
                SessionStorageUsage.saturatingAdd(transcriptBytes, other.transcriptBytes),
                SessionStorageUsage.saturatingAdd(noteBytes, other.noteBytes),
                SessionStorageUsage.saturatingAdd(recordingBytes, other.recordingBytes),
                SessionStorageUsage.saturatingAdd(imageBytes, other.imageBytes));
    }

    public static StorageCategoryUsage measure(File root) {
        if (root == null || FilePathSafety.isSymbolicLink(root)) return empty();
        try { return measureInside(root, root.getCanonicalFile()); }
        catch (IOException ignored) { return empty(); }
    }

    private static StorageCategoryUsage measureInside(File file, File root) throws IOException {
        File canonical = file.getCanonicalFile();
        if (FilePathSafety.isSymbolicLink(file)
                || !FilePathSafety.isWithinCanonical(root, canonical)) return empty();
        if (file.isFile()) {
            long size = Math.max(0L, file.length());
            String name = file.getName().toLowerCase(Locale.US);
            boolean transcript = name.equals("meeting_note.txt") || name.equals("draft_note.txt")
                    || name.equals("real_note.txt") || name.equals("refined_note.txt");
            boolean note = name.equals("timestamp_note.txt") || name.equals("fake_note.txt");
            boolean recording = name.endsWith(".m4a");
            boolean image = name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png") || name.endsWith(".webp");
            return new StorageCategoryUsage(transcript ? size : 0L, note ? size : 0L,
                    recording ? size : 0L, image ? size : 0L);
        }
        File[] children = file.listFiles();
        if (children == null) return empty();
        StorageCategoryUsage total = empty();
        for (File child : children) total = total.plus(measureInside(child, root));
        return total;
    }

    private static StorageCategoryUsage empty() {
        return new StorageCategoryUsage(0L, 0L, 0L, 0L);
    }
}
