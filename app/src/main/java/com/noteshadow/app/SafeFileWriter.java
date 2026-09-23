package com.noteshadow.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** Recoverable replacement for small JSON files, with a process-wide per-path lock. */
final class SafeFileWriter {
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private SafeFileWriter() { }
    static String read(File target) {
        synchronized (lock(target)) {
            File backup = new File(target.getPath() + ".bak");
            if (!target.isFile() && backup.isFile() && !backup.renameTo(target)) return readPlain(backup);
            else if (target.isFile() && backup.exists()) backup.delete();
            return readPlain(target);
        }
    }
    static void write(File target, String text) throws IOException {
        synchronized (lock(target)) {
            File temp = new File(target.getPath() + ".tmp");
            File backup = new File(target.getPath() + ".bak");
            if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) throw new IOException("cannot create parent");
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(text.getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
            if (backup.exists()) backup.delete();
            if (target.exists() && !target.renameTo(backup)) throw new IOException("cannot backup target");
            if (!temp.renameTo(target)) { if (backup.exists()) backup.renameTo(target); throw new IOException("cannot install target"); }
            if (backup.exists()) backup.delete();
        }
    }
    private static Object lock(File target) { String key; try { key = target.getCanonicalPath(); } catch (IOException e) { key = target.getAbsolutePath(); } Object fresh = new Object(); Object old = LOCKS.putIfAbsent(key, fresh); return old == null ? fresh : old; }
    private static String readPlain(File file) { if (!file.isFile()) return ""; try (FileInputStream in = new FileInputStream(file)) { byte[] bytes = new byte[(int)Math.min(Integer.MAX_VALUE, file.length())]; int off = 0, n; while (off < bytes.length && (n = in.read(bytes, off, bytes.length - off)) > 0) off += n; return new String(bytes, 0, off, StandardCharsets.UTF_8); } catch (IOException e) { return ""; } }
}
