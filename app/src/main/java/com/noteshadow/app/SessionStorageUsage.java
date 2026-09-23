package com.noteshadow.app;

import java.io.File;
import java.util.Locale;

/** Saturating storage totals for session text and recordings. */
public final class SessionStorageUsage {
    private final long textBytes;
    private final long recordingBytes;
    private final long attachmentBytes;

    public SessionStorageUsage(long textBytes, long recordingBytes) {
        this(textBytes, recordingBytes, 0L);
    }
    public SessionStorageUsage(long textBytes, long recordingBytes, long attachmentBytes) {
        this.textBytes = Math.max(0L, textBytes);
        this.recordingBytes = Math.max(0L, recordingBytes);
        this.attachmentBytes = Math.max(0L, attachmentBytes);
    }
    public long getTextBytes() { return textBytes; }
    public long getRecordingBytes() { return recordingBytes; }
    public long getAttachmentBytes() { return attachmentBytes; }
    public long getTotalBytes() { return saturatingAdd(saturatingAdd(textBytes, recordingBytes), attachmentBytes); }
    public long textBytes() { return textBytes; }
    public long recordingBytes() { return recordingBytes; }
    public long attachmentBytes() { return attachmentBytes; }
    public long totalBytes() { return getTotalBytes(); }
    static long saturatingAdd(long a, long b) {
        if (a >= Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }
    SessionStorageUsage plus(long text, long recording) {
        return plus(text, recording, 0L);
    }
    SessionStorageUsage plus(long text, long recording, long attachments) {
        return new SessionStorageUsage(saturatingAdd(textBytes, Math.max(0L, text)),
                saturatingAdd(recordingBytes, Math.max(0L, recording)),
                saturatingAdd(attachmentBytes, Math.max(0L, attachments)));
    }

    static SessionStorageUsage measure(File root) {
        if (root == null || FilePathSafety.isSymbolicLink(root)) return new SessionStorageUsage(0L, 0L);
        try { return measureInside(root, root.getCanonicalFile()); }
        catch (java.io.IOException e) { return new SessionStorageUsage(0L, 0L); }
    }

    private static SessionStorageUsage measureInside(File file, File root) throws java.io.IOException {
        if (FilePathSafety.isSymbolicLink(file) || !within(root, file.getCanonicalFile())) return new SessionStorageUsage(0L, 0L);
        if (file.isFile()) {
            long size = Math.max(0L, file.length());
            String name = file.getName().toLowerCase(Locale.US);
            boolean image = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp");
            return new SessionStorageUsage(name.endsWith(".txt") ? size : 0L,
                    name.endsWith(".m4a") ? size : 0L, image ? size : 0L);
        }
        File[] children = file.listFiles();
        if (children == null) return new SessionStorageUsage(0L, 0L);
        SessionStorageUsage total = new SessionStorageUsage(0L, 0L);
        for (File child : children) {
            SessionStorageUsage next = measureInside(child, root);
            total = new SessionStorageUsage(saturatingAdd(total.textBytes, next.textBytes),
                    saturatingAdd(total.recordingBytes, next.recordingBytes),
                    saturatingAdd(total.attachmentBytes, next.attachmentBytes));
        }
        return total;
    }

    private static boolean within(File root, File candidate) {
        String r = root.getPath(); String c = candidate.getPath();
        return FilePathSafety.isWithinCanonical(root, candidate);
    }
}
