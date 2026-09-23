package com.noteshadow.app;

import android.content.Context;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/** Stores note images below the owning session and never trusts a user path. */
public final class NoteAttachmentRepository {
    public static final long MAX_BYTES = 25L * 1024L * 1024L;
    private final File filesRoot;

    public NoteAttachmentRepository(Context context) { filesRoot = context.getApplicationContext().getFilesDir(); }
    public NoteAttachmentRepository(File filesRoot) { this.filesRoot = filesRoot; }

    public File createCameraTarget(String sessionId) throws IOException {
        return File.createTempFile(".capture-", ".jpg", attachmentsDirectory(sessionId));
    }
    public String importImage(String sessionId, InputStream input, String mimeType, String displayName) throws IOException {
        return importStream(sessionId, input, mimeType == null ? displayName : mimeType);
    }
    public String finishCameraTarget(String sessionId, File target) throws IOException {
        if (target == null || !isOwnedFile(sessionId, target)) throw new IOException("foreign capture target");
        return importFile(sessionId, target, ".jpg");
    }
    public boolean deleteIfOwned(String sessionId, File target) {
        return target != null && isOwnedFile(sessionId, target) && target.delete();
    }
    private boolean isOwnedFile(String sessionId, File target) {
        try { return isSafeSessionId(sessionId) && target.getCanonicalFile().getParentFile().equals(attachmentsDirectory(sessionId).getCanonicalFile()); }
        catch (IOException | RuntimeException e) { return false; }
    }

    public File attachmentsDirectory(String sessionId) {
        if (!isSafeSessionId(sessionId)) throw new IllegalArgumentException("unsafe session");
        File dir = new File(new File(new File(filesRoot, "sessions"), sessionId), "attachments");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cannot create attachments");
        return dir;
    }

    public String importStream(String sessionId, InputStream input, String mimeOrName) throws IOException {
        String extension = extensionFor(mimeOrName);
        File dir = attachmentsDirectory(sessionId);
        File temp = File.createTempFile(".upload-", ".tmp", dir);
        try {
            copyBounded(input, temp);
            verifyBitmap(temp);
            String name = UUID.randomUUID().toString().replace("-", "") + extension;
            File target = new File(dir, name);
            if (!temp.renameTo(target)) throw new IOException("cannot finalize attachment");
            return "attachments/" + name;
        } finally { if (temp.exists()) temp.delete(); }
    }

    public String importFile(String sessionId, File source, String mimeOrName) throws IOException {
        try (InputStream in = new FileInputStream(source)) { return importStream(sessionId, in, mimeOrName); }
    }

    public File resolve(String sessionId, String relativePath) {
        if (!isSafeSessionId(sessionId) || !isSafeAttachmentPath(relativePath)) return null;
        File dir = attachmentsDirectory(sessionId);
        File candidate = new File(dir, relativePath.substring("attachments/".length()));
        try {
            if (!candidate.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) return null;
            if (!candidate.exists() || candidate.isDirectory() || FilePathSafety.isSymbolicLink(candidate)) return null;
            return candidate;
        } catch (IOException e) { return null; }
    }

    public static boolean isSafeSessionId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,80}"); }
    public static boolean isSafeAttachmentPath(String path) {
        if (path == null || !path.startsWith("attachments/") || path.length() <= 11) return false;
        String name = path.substring("attachments/".length());
        return name.indexOf('/') < 0 && name.indexOf('\\') < 0 && !name.equals(".") && !name.equals("..")
                && !name.contains("..") && !name.contains(":") && !name.contains("?") && !name.contains("#")
                && !name.contains("%") && !name.contains("\0");
    }
    public static String extensionFor(String value) {
        String v = value == null ? "" : value.toLowerCase(Locale.US);
        if (v.contains("png") || v.endsWith(".png")) return ".png";
        if (v.contains("webp") || v.endsWith(".webp")) return ".webp";
        return ".jpg";
    }
    private static void copyBounded(InputStream in, File out) throws IOException {
        if (in == null) throw new IOException("missing input");
        byte[] buffer = new byte[8192]; long total = 0;
        try (FileOutputStream stream = new FileOutputStream(out)) {
            int n; while ((n = in.read(buffer)) != -1) { total += n; if (total > MAX_BYTES) throw new IOException("attachment too large"); stream.write(buffer, 0, n); }
        }
    }
    private static void verifyBitmap(File file) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0
                || options.outWidth > 20_000 || options.outHeight > 20_000)
            throw new IOException("not a supported image");
    }
}
