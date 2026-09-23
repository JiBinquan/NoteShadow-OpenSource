package com.noteshadow.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Non-exported provider for a single temporary camera capture URI. */
public final class NoteAttachmentProvider extends ContentProvider {
    private static final String PREFIX = "capture";
    public static Uri uriFor(android.content.Context context, String sessionId, File target) throws IOException {
        if (!NoteAttachmentRepository.isSafeSessionId(sessionId) || target == null) throw new IOException("invalid capture");
        File dir = new File(new File(new File(context.getFilesDir(), "sessions"), sessionId), "attachments");
        if (!target.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) throw new IOException("foreign capture");
        return new Uri.Builder().scheme("content").authority(context.getPackageName()+".noteattachments")
                .appendPath(PREFIX).appendPath(sessionId).appendPath(target.getName()).build();
    }
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        if (uri == null || uri.getPathSegments().size()!=3 || !PREFIX.equals(uri.getPathSegments().get(0))) throw new FileNotFoundException("invalid capture uri");
        String session = uri.getPathSegments().get(1), name = uri.getPathSegments().get(2);
        if (!NoteAttachmentRepository.isSafeSessionId(session) || !NoteAttachmentRepository.isSafeAttachmentPath("attachments/"+name)) throw new FileNotFoundException("invalid capture");
        File f = new File(new File(new File(getContext().getFilesDir(), "sessions"), session), "attachments/"+name);
        try { if (!f.getCanonicalFile().getParentFile().equals(new File(f.getParent()).getCanonicalFile())) throw new FileNotFoundException("invalid capture"); } catch (IOException e) { throw new FileNotFoundException("invalid capture"); }
        return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { File f=file(uri); int flags=mode.contains("w") ? ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE : ParcelFileDescriptor.MODE_READ_ONLY; return ParcelFileDescriptor.open(f, flags); }
    @Override public String getType(Uri uri) { try { file(uri); return "image/jpeg"; } catch (FileNotFoundException e) { return null; } }
    @Override public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) { try { File f=file(uri); MatrixCursor c=new MatrixCursor(new String[]{"_display_name","_size"}); c.addRow(new Object[]{f.getName(),f.exists()?f.length():0}); return c; } catch (FileNotFoundException e) { return null; } }
    @Override public int delete(Uri uri, String s, String[] a) { try { return file(uri).delete()?1:0; } catch (FileNotFoundException e) { return 0; } }
    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
