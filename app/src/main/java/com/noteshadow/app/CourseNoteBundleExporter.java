package com.noteshadow.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exports user notes and owned direct image attachments, never paths referenced by Markdown. */
public final class CourseNoteBundleExporter {
    private CourseNoteBundleExporter() { }

    public static int writeZip(File filesDir, List<SessionRecord> records, OutputStream output) throws IOException {
        if (filesDir == null || output == null) throw new IllegalArgumentException("filesDir/output must not be null");
        Set<String> used = new HashSet<>();
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (records != null) for (SessionRecord record : records) {
                if (record == null || !NoteAttachmentRepository.isSafeSessionId(record.getSessionId())) continue;
                File session = new File(new File(filesDir, "sessions"), record.getSessionId());
                if (!session.isDirectory() || !isDirectChild(session, new File(filesDir, "sessions"))) continue;
                String base = safe(record.displayTitle()) + "-" + safe(record.getSessionId());
                if (base.equals("-")) base = "记录-" + record.getSessionId();
                String folder = base; int suffix = 2;
                while (!used.add(folder)) folder = base + "-" + suffix++;
                String prefix = folder + "/";
                put(zip, prefix + "timestamp_note.md", record.getTimestampNote().getBytes(StandardCharsets.UTF_8));
                File attachments = new File(session, "attachments");
                if (isDirectChild(attachments, session) && attachments.isDirectory()) {
                    File[] images = attachments.listFiles();
                    if (images != null) for (File image : images) {
                        if (!isSafeImage(image, attachments)) continue;
                        putFile(zip, prefix + "attachments/" + image.getName(), image);
                    }
                }
                count++;
            }
            zip.finish();
        }
        return count;
    }

    private static boolean isDirectChild(File child, File parent) {
        try { return !FilePathSafety.isSymbolicLink(child) && child.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile()); }
        catch (IOException | RuntimeException e) { return false; }
    }
    private static boolean isSafeImage(File image, File parent) {
        if (image == null || !image.isFile() || FilePathSafety.isSymbolicLink(image) || !isDirectChild(image, parent)) return false;
        String name = image.getName().toLowerCase(Locale.US);
        return !name.startsWith(".") && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".webp"));
    }
    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(data); zip.closeEntry();
    }
    private static void putFile(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) >= 0) zip.write(buffer, 0, n);
        } finally { zip.closeEntry(); }
    }
    static String safe(String input) {
        if (input == null) return "";
        String value = input.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
