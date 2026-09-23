package com.noteshadow.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes a portable UTF-8 transcript bundle without including private audio or fake text. */
public final class TranscriptBundleExporter {
    private TranscriptBundleExporter() { }

    public static int writeZip(List<SessionRecord> records, OutputStream output) throws IOException {
        if (output == null) throw new IllegalArgumentException("output must not be null");
        Set<String> used = new HashSet<>();
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (records != null) for (SessionRecord record : records) {
                if (record == null || record.getMeetingNote().trim().isEmpty()) continue;
                String base = safeFileName(record.displayTitle()) + "-" + safeFileName(record.getSessionId());
                if (base.equals("-")) base = "记录";
                String name = base + ".txt";
                int suffix = 2;
                while (!used.add(name)) name = base + "-" + suffix++ + ".txt";
                byte[] text = record.getMeetingNote().getBytes(StandardCharsets.UTF_8);
                zip.putNextEntry(new ZipEntry(name));
                zip.write(text);
                zip.closeEntry();
                count++;
            }
            zip.finish();
        }
        return count;
    }

    static String safeFileName(String input) {
        if (input == null) return "";
        String value = input.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
