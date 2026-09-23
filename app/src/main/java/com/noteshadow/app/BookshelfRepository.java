package com.noteshadow.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** File-backed, Android-independent book catalogue and progress store. */
public final class BookshelfRepository {
    private static final int SCHEMA = 1;
    private final File root;
    private final File books;
    private final File staging;
    private final File index;

    public BookshelfRepository(File filesRoot) {
        if (filesRoot == null) throw new IllegalArgumentException("缺少书架目录");
        root = filesRoot;
        books = new File(root, "books");
        staging = new File(root, ".books-staging");
        index = new File(root, "bookshelf-index.json");
        if (!FilePathSafety.validContainer(books, root)) throw new IllegalArgumentException("书架目录无效");
        if (!FilePathSafety.validContainer(staging, root)) throw new IllegalArgumentException("临时目录无效");
    }

    public synchronized List<BookRecord> list() {
        List<BookRecord> result = new ArrayList<>();
        List<String> ids = indexValues().ids;
        for (String id : ids) { BookRecord b = readMetadata(id); if (b != null) result.add(b); }
        Collections.sort(result, new Comparator<BookRecord>() {
            @Override public int compare(BookRecord a, BookRecord b) {
                int c = Long.compare(b.getLastReadAt(), a.getLastReadAt());
                return c != 0 ? c : a.getId().compareTo(b.getId());
            }
        });
        return result;
    }

    public synchronized BookRecord current() {
        String id = indexValues().currentId;
        return id.isEmpty() ? null : readMetadata(id);
    }

    public synchronized String loadText(BookRecord book) throws IOException {
        if (book == null) throw new IllegalArgumentException("缺少书籍");
        File dir = bookDirectory(book.getId());
        File content = new File(dir, "content.txt");
        if (!FilePathSafety.isDirectChild(content, dir) || !content.isFile() || FilePathSafety.isSymbolicLink(content))
            throw new IOException("书籍正文不存在");
        return new String(readBytes(content), StandardCharsets.UTF_8);
    }

    public synchronized BookRecord importBook(InputStream source, String sourceName) throws IOException {
        if (source == null) throw new IllegalArgumentException("缺少书籍文件");
        String safeName = safeSourceName(sourceName);
        File temp = new File(staging, ".source-" + UUID.randomUUID().toString());
        copy(source, temp);
        try { return publish(temp, safeName); }
        finally { deleteQuietly(temp); }
    }

    public synchronized BookRecord importStagedSource(File source, String sourceName) throws IOException {
        if (source == null || !source.isFile() || FilePathSafety.isSymbolicLink(source))
            throw new IllegalArgumentException("暂存文件无效");
        String safeName = safeSourceName(sourceName == null ? source.getName() : sourceName);
        return publish(source, safeName);
    }

    public synchronized BookRecord selectCurrent(String id) throws IOException {
        BookRecord book = readMetadata(id);
        if (book == null) throw new IllegalArgumentException("书籍不存在");
        BookRecord selected = book.withProgress(book.getPosition(), book.getChapter(),
                System.currentTimeMillis());
        writeMetadata(selected);
        IndexValues values = indexValues(); values.currentId = id; writeIndex(values);
        return selected;
    }

    public synchronized BookRecord updateProgress(String id, int position, int chapter) throws IOException {
        BookRecord book = readMetadata(id);
        if (book == null) throw new IllegalArgumentException("书籍不存在");
        return updateProgress(book, position, chapter, loadText(book), System.currentTimeMillis());
    }

    /** Updates progress using text already loaded by the caller, avoiding a second full-book read. */
    public synchronized BookRecord updateProgress(String id, int position, int chapter,
                                                   String loadedText) throws IOException {
        BookRecord book = readMetadata(id);
        if (book == null) throw new IllegalArgumentException("书籍不存在");
        return updateProgress(book, position, chapter, loadedText == null ? "" : loadedText,
                System.currentTimeMillis());
    }

    private BookRecord updateProgress(BookRecord book, int position, int chapter, String text,
                                      long readAt) throws IOException {
        int safe = BookProgress.clampToCodePointBoundary(text, position);
        int safeChapter = chapterForPosition(book, safe);
        if (chapter >= 0 && book.getChapterStarts().length == 0) safeChapter = Math.max(0, chapter);
        BookRecord updated = book.withProgress(safe, safeChapter, readAt);
        writeMetadata(updated); return updated;
    }

    public synchronized BookRecord setPercentage(String id, String percentage) throws IOException {
        BookRecord book = readMetadata(id);
        if (book == null) throw new IllegalArgumentException("书籍不存在");
        return updateProgress(id, BookProgress.positionForPercentage(loadText(book), percentage), -1);
    }

    public synchronized BookRecord selectChapter(String id, int chapter) throws IOException {
        BookRecord book = readMetadata(id);
        if (book == null) throw new IllegalArgumentException("书籍不存在");
        int[] starts = book.getChapterStarts();
        if (chapter < 0 || chapter >= starts.length) throw new IllegalArgumentException("章节不存在");
        return updateProgress(id, starts[chapter], chapter);
    }

    public int chapterForPosition(BookRecord book, int position) {
        if (book == null) return 0;
        int chapter = 0; int[] starts = book.getChapterStarts();
        for (int i = 0; i < starts.length; i++) { if (starts[i] <= position) chapter = i; else break; }
        return chapter;
    }

    /** Idempotently imports the old single-book state and leaves the legacy files untouched. */
    public synchronized BookRecord migrateLegacy(String name, String text, int position,
                                                  int chapter, long now) throws IOException {
        return migrateLegacy(name, text, position, chapter, new int[]{0}, now);
    }

    public synchronized BookRecord migrateLegacy(String name, String text, int position,
                                                  int chapter, int[] chapterStarts, long now) throws IOException {
        IndexValues values = indexValues();
        if (values.migrated) return current();
        BookRecord interrupted = interruptedLegacyImport(name, text);
        if (interrupted != null) {
            interrupted = interrupted.withChapterStarts(chapterStarts == null ? new int[]{0} : chapterStarts);
            writeMetadata(interrupted);
            interrupted = updateProgress(interrupted, position, chapter, text,
                    now > 0 ? now : System.currentTimeMillis());
            values = indexValues();
            if (!values.ids.contains(interrupted.getId())) values.ids.add(interrupted.getId());
            values.currentId = interrupted.getId();
            values.migrated = true;
            writeIndex(values);
            return interrupted;
        }
        IndexValues original = values;
        BookRecord result = null;
        if (text != null && !text.trim().isEmpty()) {
            IndexValues before = values;
            try {
                result = importBook(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                        legacyTextName(name));
                result = result.withChapterStarts(chapterStarts == null ? new int[]{0} : chapterStarts);
                writeMetadata(result);
                result = updateProgressAt(result.getId(), position, chapter, now > 0 ? now : System.currentTimeMillis());
                values = indexValues();
                values.currentId = result.getId();
            } catch (IOException | RuntimeException failure) {
                if (result != null) rollbackImported(result.getId(), before);
                throw failure;
            }
        }
        try {
            values.migrated = true; writeIndex(values);
            return result;
        } catch (IOException | RuntimeException failure) {
            if (result != null) rollbackImported(result.getId(), original);
            throw failure;
        }
    }

    public synchronized BookRecord migrateLegacy(File legacySource, String name, int position,
                                                  int chapter, long now) throws IOException {
        if (legacySource == null || !legacySource.isFile()) return migrateLegacy(name, "", position, chapter, now);
        String sourceName = safeSourceName(name == null ? legacySource.getName() : name);
        IndexValues values = indexValues();
        if (values.migrated) return current();
        BookRecord interrupted = interruptedLegacyFileImport(legacySource, sourceName);
        if (interrupted != null) {
            interrupted = updateProgressAt(interrupted.getId(), position, chapter,
                    now > 0 ? now : System.currentTimeMillis());
            values = indexValues();
            if (!values.ids.contains(interrupted.getId())) values.ids.add(interrupted.getId());
            values.currentId = interrupted.getId();
            values.migrated = true;
            writeIndex(values);
            return interrupted;
        }
        FileInputStream in = new FileInputStream(legacySource);
        try {
            IndexValues original = values;
            BookRecord result = null;
            try {
                result = importBook(in, sourceName);
                result = updateProgressAt(result.getId(), position, chapter, now > 0 ? now : System.currentTimeMillis());
                values = indexValues(); values.currentId = result.getId(); values.migrated = true; writeIndex(values); return result;
            } catch (IOException | RuntimeException failure) {
                if (result != null) rollbackImported(result.getId(), original);
                throw failure;
            }
        } finally { in.close(); }
    }

    private BookRecord publish(File source, String sourceName) throws IOException {
        String format = sourceName.toLowerCase(java.util.Locale.US).endsWith(".epub") ? "epub" : "txt";
        byte[] raw = readBytes(source);
        if (raw.length == 0) throw new IllegalArgumentException("书籍文件为空");
        String text; int[] starts;
        if (format.equals("epub")) {
            try {
                EpubParser.Result parsed = EpubParser.parseDetailed(new ByteArrayInputStream(raw), staging);
                text = parsed.text; starts = parsed.chapterStarts;
            } catch (Exception e) {
                throw new IllegalArgumentException("EPUB 文件无效", e);
            }
        } else { text = TextCodec.visibleControls(TextCodec.decode(raw)); starts = new int[]{0}; }
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("书籍正文为空");
        String id = UUID.randomUUID().toString();
        File tempDir = new File(staging, ".book-" + id);
        File finalDir = bookDirectory(id);
        if (!tempDir.mkdirs()) throw new IOException("无法创建书籍暂存目录");
        try {
            writeBytes(new File(tempDir, "source." + format), raw);
            writeBytes(new File(tempDir, "content.txt"), text.getBytes(StandardCharsets.UTF_8));
            long now = System.currentTimeMillis();
            BookRecord book = new BookRecord(id, titleFrom(sourceName), sourceName, format,
                    0, 0, starts, now, now);
            writeMetadataInto(tempDir, book);
            if (!tempDir.renameTo(finalDir)) throw new IOException("无法发布书籍");
            try {
                IndexValues values = indexValues(); values.ids.add(id); values.currentId = id; writeIndex(values);
                return book;
            } catch (IOException | RuntimeException failure) {
                deleteTree(finalDir);
                throw failure;
            }
        } finally { if (tempDir.exists()) deleteTree(tempDir); }
    }

    private BookRecord readMetadata(String id) {
        if (id == null || id.length() != 36 || id.indexOf("..") >= 0) return null;
        File dir = bookDirectory(id), file = new File(dir, "metadata.json");
        if (!FilePathSafety.isDirectChild(dir, books) || !dir.isDirectory() || FilePathSafety.isSymbolicLink(dir)
                || !file.isFile() || FilePathSafety.isSymbolicLink(file)) return null;
        Map<String,String> m;
        try { m = JsonCodec.parse(new String(readBytes(file), StandardCharsets.UTF_8)); } catch (IOException e) { return null; }
        if (!id.equals(m.get("id"))) return null;
        return new BookRecord(id, m.get("title"), m.get("sourceName"), m.get("format"),
                number(m.get("position")), number(m.get("chapter")), starts(m.get("chapterStarts")),
                longNumber(m.get("createdAt")), longNumber(m.get("lastReadAt")));
    }

    private BookRecord updateProgressAt(String id, int position, int chapter, long readAt) throws IOException {
        BookRecord book = readMetadata(id);
        if (book == null) throw new IllegalArgumentException("书籍不存在");
        return updateProgress(book, position, chapter, loadText(book), readAt);
    }

    /** Recovers the narrow window where import was indexed but the migration marker was not. */
    private BookRecord interruptedLegacyImport(String name, String text) throws IOException {
        if (text == null || text.trim().isEmpty()) return null;
        String sourceName = legacyTextName(name);
        for (BookRecord candidate : allPublishedBooks())
            if (sourceName.equals(candidate.getSourceName()) && text.equals(loadText(candidate)))
                return candidate;
        return null;
    }

    private BookRecord interruptedLegacyFileImport(File source, String sourceName) throws IOException {
        String extension = sourceName.toLowerCase(java.util.Locale.US).endsWith(".epub") ? "epub" : "txt";
        for (BookRecord candidate : allPublishedBooks()) {
            if (!sourceName.equals(candidate.getSourceName())) continue;
            File saved = new File(bookDirectory(candidate.getId()), "source." + extension);
            if (sameContents(source, saved)) return candidate;
        }
        return null;
    }

    /** Includes complete directories published just before an interrupted index write. */
    private List<BookRecord> allPublishedBooks() {
        List<BookRecord> result = new ArrayList<>();
        File[] children = books.listFiles();
        if (children == null) return result;
        for (File child : children) {
            if (!child.isDirectory() || !child.getName().matches("[0-9a-fA-F-]{36}")) continue;
            BookRecord record = readMetadata(child.getName());
            if (record != null) result.add(record);
        }
        return result;
    }

    private static boolean sameContents(File left, File right) throws IOException {
        if (left == null || right == null || !left.isFile() || !right.isFile()
                || left.length() != right.length()) return false;
        FileInputStream a = new FileInputStream(left), b = new FileInputStream(right);
        try {
            byte[] x = new byte[65536], y = new byte[65536];
            int nx;
            while ((nx = a.read(x)) >= 0) {
                int offset = 0;
                while (offset < nx) {
                    int ny = b.read(y, offset, nx - offset);
                    if (ny < 0) return false;
                    offset += ny;
                }
                for (int i = 0; i < nx; i++) if (x[i] != y[i]) return false;
            }
            return b.read() < 0;
        } finally { a.close(); b.close(); }
    }

    private File bookDirectory(String id) { return new File(books, id); }
    private void writeMetadata(BookRecord b) throws IOException { writeMetadataInto(bookDirectory(b.getId()), b); }
    private void writeMetadataInto(File dir, BookRecord b) throws IOException {
        StringBuilder s = new StringBuilder("{\"id\":").append(JsonCodec.quote(b.getId()))
                .append(",\"title\":").append(JsonCodec.quote(b.getTitle()))
                .append(",\"sourceName\":").append(JsonCodec.quote(b.getSourceName()))
                .append(",\"format\":").append(JsonCodec.quote(b.getFormat()))
                .append(",\"position\":").append(b.getPosition()).append(",\"chapter\":").append(b.getChapter())
                .append(",\"chapterStarts\":").append(JsonCodec.quote(chapterStartsValue(b.getChapterStarts())))
                .append(",\"createdAt\":").append(b.getCreatedAt()).append(",\"lastReadAt\":").append(b.getLastReadAt()).append('}');
        atomicWrite(new File(dir, "metadata.json"), s.toString().getBytes(StandardCharsets.UTF_8));
    }

    private IndexValues indexValues() {
        IndexValues v = new IndexValues();
        if (!index.isFile()) return v;
        try { Map<String,String> m = JsonCodec.parse(new String(readBytes(index), StandardCharsets.UTF_8));
            v.currentId = safe(m.get("currentBookId")); v.migrated = "1".equals(m.get("migration"));
            String raw = m.get("bookIds"); if (raw != null) for (String id : raw.split(",")) if (id.matches("[0-9a-fA-F-]{36}")) v.ids.add(id);
        } catch (IOException ignored) { }
        return v;
    }
    private void writeIndex(IndexValues v) throws IOException {
        StringBuilder ids = new StringBuilder(); for (String id : v.ids) { if(ids.length()>0)ids.append(','); ids.append(id); }
        String s = "{\"schema\":1,\"currentBookId\":" + JsonCodec.quote(v.currentId)
                + ",\"migration\":" + (v.migrated ? "1" : "0") + ",\"bookIds\":" + JsonCodec.quote(ids.toString()) + "}";
        atomicWrite(index, s.getBytes(StandardCharsets.UTF_8));
    }
    private static final class IndexValues { String currentId=""; boolean migrated; List<String> ids=new ArrayList<>(); }
    private static int number(String s) { try{return Math.max(0,Integer.parseInt(s));}catch(Exception e){return 0;} }
    private static long longNumber(String s) { try{return Math.max(0,Long.parseLong(s));}catch(Exception e){return 0;} }
    private static int[] starts(String s) { if(s==null||s.trim().isEmpty())return new int[0]; String x=s.replaceAll("[\\[\\]]",""); String[] a=x.split(","); int[] out=new int[a.length]; for(int i=0;i<a.length;i++)out[i]=number(a[i].trim()); return out; }
    private static String chapterStartsValue(int[] starts) { StringBuilder s=new StringBuilder(); if(starts!=null) for(int i=0;i<starts.length;i++){if(i>0)s.append(',');s.append(Math.max(0,starts[i]));} return s.toString(); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String safeSourceName(String n) { if(n==null||n.trim().isEmpty())throw new IllegalArgumentException("缺少文件名"); String x=n.trim(); if(!(x.toLowerCase().endsWith(".txt")||x.toLowerCase().endsWith(".epub"))||x.indexOf('/')>=0||x.indexOf('\\')>=0||x.indexOf("..")>=0)throw new IllegalArgumentException("仅支持 TXT 或 EPUB 文件"); return x; }
    private static String titleFrom(String n) { int p=n.lastIndexOf('.'); return p>0?n.substring(0,p):n; }
    private static String legacyTextName(String n) {
        if (n == null || n.trim().isEmpty()) return "book.txt";
        String x = n.trim();
        String lower = x.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".txt") ? x
                : (lower.endsWith(".epub") ? x.substring(0, x.length() - 5) + ".txt" : x + ".txt");
    }
    private static byte[] readBytes(File f)throws IOException { ByteArrayOutputStream b=new ByteArrayOutputStream(); FileInputStream in=new FileInputStream(f); try{byte[] x=new byte[65536];int n;while((n=in.read(x))>=0)b.write(x,0,n);}finally{in.close();}return b.toByteArray(); }
    private static void copy(InputStream in, File f)throws IOException { FileOutputStream out=new FileOutputStream(f); try{byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)out.write(b,0,n);}finally{out.close();} }
    private static void writeBytes(File f, byte[] b)throws IOException { FileOutputStream o=new FileOutputStream(f); try{o.write(b);}finally{o.close();} }
    private static void atomicWrite(File f, byte[] b)throws IOException {
        File parent = f.getParentFile(); if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("无法创建保存目录");
        File t=new File(parent,"."+f.getName()+"."+UUID.randomUUID()+".tmp");
        File backup=new File(parent,"."+f.getName()+"."+UUID.randomUUID()+".bak");
        writeBytes(t,b); boolean moved=false;
        try {
            if (f.exists() && !f.renameTo(backup)) throw new IOException("无法备份原文件");
            if (!t.renameTo(f)) throw new IOException("无法保存文件");
            moved=true; deleteQuietly(backup);
        } catch (IOException failure) {
            deleteQuietly(t); if (moved) deleteQuietly(f); else if (backup.exists()) backup.renameTo(f);
            throw failure;
        }
    }
    private void rollbackImported(String id, IndexValues before) {
        deleteTree(bookDirectory(id));
        try { writeIndex(before); } catch (IOException ignored) { }
    }
    private static void deleteQuietly(File f){if(f!=null&&f.exists())f.delete();}
    private static void deleteTree(File f){if(f.isDirectory()){File[] c=f.listFiles();if(c!=null)for(File x:c)deleteTree(x);}deleteQuietly(f);}
}
