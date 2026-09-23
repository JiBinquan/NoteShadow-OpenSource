package com.noteshadow.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppStorage {
    private static final String PREFS = "state";
    private static final SessionSnapshotCoordinator SESSION_COORDINATOR =
            new SessionSnapshotCoordinator();
    private final Context context;
    private final SharedPreferences prefs;

    public AppStorage(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveBook(String name, String text) {
        write("book.txt", text);
        prefs.edit().putString("book_name", name == null ? "未命名" : name)
                .putInt("book_pos", 0).putInt("book_chapter", 0).apply();
    }

    public String loadBook() { return read("book.txt"); }
    public String bookName() { return prefs.getString("book_name", "未导入书籍"); }
    public int bookPos() { return Math.max(0, prefs.getInt("book_pos", 0)); }
    public void setBookPos(int pos) { prefs.edit().putInt("book_pos", Math.max(0, pos)).apply(); }
    public int bookChapter() { return Math.max(0, prefs.getInt("book_chapter", 0)); }
    public void setBookChapter(int chapter) { prefs.edit().putInt("book_chapter", Math.max(0, chapter)).apply(); }

    /** Keeps the legacy single-book mirror current for services, snapshots, and safe rollback. */
    public void syncActiveBook(String name, String text, int position, int chapter, int[] starts) {
        write("book.txt", text == null ? "" : text);
        StringBuilder encoded = new StringBuilder();
        if (starts != null) for (int value : starts) {
            if (encoded.length() > 0) encoded.append(',');
            encoded.append(Math.max(0, value));
        }
        prefs.edit().putString("book_name", name == null ? "未命名" : name)
                .putInt("book_pos", Math.max(0, position))
                .putInt("book_chapter", Math.max(0, chapter))
                .putString("chapter_starts", encoded.length() == 0 ? "0" : encoded.toString())
                .commit();
    }

    public int[] chapterStarts() {
        String raw = prefs.getString("chapter_starts", "0");
        if (raw == null || raw.trim().isEmpty()) return new int[] {0};
        String[] values = raw.split(",");
        int[] parsed = new int[values.length];
        int count = 0;
        for (String value : values) {
            try { parsed[count++] = Math.max(0, Integer.parseInt(value)); }
            catch (NumberFormatException ignored) { }
        }
        if (count == 0) return new int[] {0};
        if (count == parsed.length) return parsed;
        int[] compact = new int[count];
        System.arraycopy(parsed, 0, compact, 0, count);
        return compact;
    }

    public void setChapterStarts(int[] starts) {
        StringBuilder s = new StringBuilder();
        if (starts != null) for (int v : starts) {
            if (s.length() > 0) s.append(',');
            s.append(Math.max(0, v));
        }
        prefs.edit().putString("chapter_starts", s.toString()).apply();
    }

    public int chapterForPosition(int pos) {
        String raw = prefs.getString("chapter_starts", "0");
        int chapter = 0;
        if (raw != null) {
            String[] values = raw.split(",");
            for (int i = 0; i < values.length; i++) {
                try { if (Integer.parseInt(values[i]) <= pos) chapter = i; else break; }
                catch (NumberFormatException ignored) {}
            }
        }
        return chapter;
    }

    public String fakeNote() { return read("fake_note.txt"); }
    public void setFakeNote(String text) { write("fake_note.txt", text); }
    public String realNote() { return read("real_note.txt"); }
    public void setRealNote(String text) { write("real_note.txt", text); }
    public String saveEditedRealNoteIfCurrent(String expectedSessionId, String base, String edited) {
        final String[] saved = {null};
        SESSION_COORDINATOR.runIfCurrent(expectedSessionId, this::currentSessionId, () -> {
            String current = realNote();
            if (!current.startsWith(base)) return;
            String appended = current.substring(base.length());
            saved[0] = edited + (appended.isEmpty() || edited.isEmpty() || edited.endsWith("\n") ? "" : "\n")
                    + appended;
            setRealNote(saved[0]);
        });
        return saved[0];
    }
    public String draftNote() { return read("draft_note.txt"); }
    public void setDraftNote(String text) { write("draft_note.txt", text); }
    public String refinedNote() { return read("refined_note.txt"); }
    public void setRefinedNote(String text) { write("refined_note.txt", text); }
    /** User-authored notes for the currently active course session. */
    public String timestampNote() { return read("timestamp_note.txt"); }
    public void setTimestampNote(String text) { write("timestamp_note.txt", text); }

    public int fakeSelection() { return prefs.getInt("fake_sel", 0); }
    public int realSelection() { return prefs.getInt("real_sel", 0); }
    public void setFakeSelection(int v) { prefs.edit().putInt("fake_sel", Math.max(0, v)).apply(); }
    public void setRealSelection(int v) { prefs.edit().putInt("real_sel", Math.max(0, v)).apply(); }
    public int fakeScroll() { return Math.max(0, prefs.getInt("fake_scroll", 0)); }
    public int realScroll() { return Math.max(0, prefs.getInt("real_scroll", 0)); }
    public void setFakeScroll(int v) { prefs.edit().putInt("fake_scroll", Math.max(0, v)).apply(); }
    public void setRealScroll(int v) { prefs.edit().putInt("real_scroll", Math.max(0, v)).apply(); }
    public int timestampNoteSelection() { return Math.max(0, prefs.getInt("timestamp_note_sel", 0)); }
    public void setTimestampNoteSelection(int v) {
        prefs.edit().putInt("timestamp_note_sel", Math.max(0, v)).apply();
    }
    public int timestampNoteScroll() { return Math.max(0, prefs.getInt("timestamp_note_scroll", 0)); }
    public void setTimestampNoteScroll(int v) {
        prefs.edit().putInt("timestamp_note_scroll", Math.max(0, v)).apply();
    }
    public boolean mixedPolicyInitialized() { return prefs.getBoolean("mixed_policy_initialized", false); }
    public int mixedProcessedLines() { return Math.max(0, prefs.getInt("mixed_processed_lines", 0)); }
    public String mixedEligibleRanges() { return prefs.getString("mixed_eligible_ranges", ""); }
    public boolean mixedNovelStartInitialized() { return prefs.contains("mixed_novel_start"); }
    public int mixedNovelStart() { return Math.max(0, prefs.getInt("mixed_novel_start", 0)); }
    public void setMixedNovelStart(int position) {
        prefs.edit().putInt("mixed_novel_start", Math.max(0, position)).apply();
    }
    public boolean mixedNovelEndInitialized() { return prefs.contains("mixed_novel_end"); }
    public int mixedNovelEnd() { return Math.max(0, prefs.getInt("mixed_novel_end", 0)); }
    public void setMixedNovelEnd(int position) {
        prefs.edit().putInt("mixed_novel_end", Math.max(0, position)).apply();
    }
    public void setMixedPolicy(int processedLines, String eligibleRanges) {
        prefs.edit().putBoolean("mixed_policy_initialized", true)
                .putInt("mixed_processed_lines", Math.max(0, processedLines))
                .putString("mixed_eligible_ranges", eligibleRanges == null ? "" : eligibleRanges).apply();
    }
    public void clearMixedPolicy() {
        prefs.edit().remove("mixed_policy_initialized").remove("mixed_processed_lines")
                .remove("mixed_eligible_ranges").remove("mixed_novel_start")
                .remove("mixed_novel_end").apply();
    }
    public boolean realMode() { return prefs.getBoolean("real_mode", false); }
    public void setRealMode(boolean value) { prefs.edit().putBoolean("real_mode", value).apply(); }

    public boolean isRecording() { return prefs.getBoolean("recording", false); }
    public void setRecording(boolean value) { prefs.edit().putBoolean("recording", value).apply(); }
    public String lastRecording() { return prefs.getString("recording_file", ""); }
    public void setLastRecording(String path) { prefs.edit().putString("recording_file", path).apply(); }
    public String publicRecordingUri() { return prefs.getString("recording_public_uri", ""); }
    public void setPublicRecordingUri(String uri) { prefs.edit().putString("recording_public_uri", uri == null ? "" : uri).apply(); }

    public String currentSessionId() {
        return SESSION_COORDINATOR.callExclusive(() -> {
            String id = prefs.getString("session_id", "");
            if (id == null || id.isEmpty()) {
                id = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                prefs.edit().putString("session_id", id).putLong("session_created", System.currentTimeMillis()).commit();
            }
            return id;
        });
    }

    /** The project receiving newly-created sessions; old installs use the default project. */
    public String currentProjectId() {
        String id = prefs.getString("project_id", ProjectRecord.DEFAULT_ID);
        if (id == null || id.trim().isEmpty() || !ProjectRepository.isSafeId(id)
                || new ProjectRepository(context.getFilesDir()).load(id) == null) {
            prefs.edit().putString("project_id", ProjectRecord.DEFAULT_ID).apply();
            return ProjectRecord.DEFAULT_ID;
        }
        return id;
    }

    public void setCurrentProjectId(String projectId) {
        if (!ProjectRepository.isSafeId(projectId) || new ProjectRepository(context.getFilesDir()).load(projectId) == null) throw new IllegalArgumentException("invalid project id");
        prefs.edit().putString("project_id", projectId).apply();
    }

    /** Starts a meeting session without clearing either document or reading progress. */
    public String beginNewSession() {
        return SESSION_COORDINATOR.callExclusive(() -> {
            String id = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            prefs.edit().putString("session_id", id)
                    .putLong("session_created", System.currentTimeMillis())
                    .putString("recording_file", "").commit();
            try {
                new SessionMetadataRepository(context.getFilesDir()).save(id, currentProjectId(), "", false);
            } catch (Exception ignored) { }
            return id;
        });
    }

    public File sessionDirectory() {
        File dir = new File(new File(context.getFilesDir(), "sessions"), currentSessionId());
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File sessionRecordingsDirectory() {
        File dir = new File(sessionDirectory(), "recordings");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void snapshotSession(String fake, String real, int position, int chapter) {
        SESSION_COORDINATOR.callExclusive(() -> {
            snapshotSession(fake, real, timestampNote(), position, chapter);
            return null;
        });
    }

    /**
     * Saves the complete current course record.  The explicit timestamp note
     * argument lets callers snapshot a stable UI value before starting the
     * next lesson, while the four-argument overload keeps older call sites
     * source-compatible.
     */
    public void snapshotSession(String fake, String real, String timestampNote,
                                int position, int chapter) {
        SESSION_COORDINATOR.callExclusive(() -> {
            File dir = sessionDirectory();
            writeFile(new File(dir, "fake_note.txt"), fake);
            writeFile(new File(dir, "meeting_note.txt"), real);
            writeFile(new File(dir, "timestamp_note.txt"), timestampNote);
            writeFile(new File(dir, "draft_note.txt"), draftNote());
            String json = "{\n"
                    + "  \"sessionId\": " + JsonCodec.quote(currentSessionId()) + ",\n"
                    + "  \"createdAt\": " + prefs.getLong("session_created", System.currentTimeMillis()) + ",\n"
                    + "  \"updatedAt\": " + System.currentTimeMillis() + ",\n"
                    + "  \"bookName\": " + JsonCodec.quote(bookName()) + ",\n"
                    + "  \"bookPosition\": " + Math.max(0, position) + ",\n"
                    + "  \"bookChapter\": " + Math.max(0, chapter) + ",\n"
                    + "  \"lastRecording\": " + JsonCodec.quote(lastRecording()) + "\n"
                    + "}\n";
            writeFile(new File(dir, "session.json"), json);
            return null;
        });
    }

    /** Drops a delayed UI snapshot after the active course has already changed. */
    public boolean snapshotSessionIfCurrent(String expectedSessionId, String fake, String real,
                                            String timestampNote, int position, int chapter) {
        return SESSION_COORDINATOR.runIfCurrent(expectedSessionId, this::currentSessionId,
                () -> snapshotSession(fake, real, timestampNote, position, chapter));
    }

    /** Commits a delayed model answer only if its original course is still active. */
    public boolean saveTimestampNoteIfCurrent(String expectedSessionId, String fake, String real,
                                              String timestampNote, int position, int chapter) {
        return SESSION_COORDINATOR.runIfCurrent(expectedSessionId, this::currentSessionId, () -> {
            setTimestampNote(timestampNote);
            snapshotSession(fake, real, timestampNote, position, chapter);
        });
    }

    /** Allows a completed model request to be retained after its Activity is recreated. */
    public String appendTimestampNoteIfCurrent(String expectedSessionId, String answer, String stamp) {
        final String[] saved = {null};
        SESSION_COORDINATOR.runIfCurrent(expectedSessionId, this::currentSessionId, () -> {
            String current = timestampNote();
            String appended = (current.isEmpty() || current.endsWith("\n") ? "" : "\n")
                    + TimestampNoteFormatter.bracketedLabel(stamp) + "模型回复："
                    + TimestampNoteFormatter.formatInsertedText("\n" + answer.trim(), stamp) + "\n";
            saved[0] = current + appended;
            setTimestampNote(saved[0]);
            snapshotSession(fakeNote(), realNote(), saved[0], bookPos(), bookChapter());
        });
        return saved[0];
    }

    /** Replaces one request marker against the latest persisted note, not stale Activity fields. */
    public String replaceTimestampNoteMarkerIfCurrent(String expectedSessionId, String marker,
                                                      String replacement) {
        final String[] saved = {null};
        SESSION_COORDINATOR.runIfCurrent(expectedSessionId, this::currentSessionId, () -> {
            String current = timestampNote();
            int at = current.indexOf(marker);
            if (at < 0) return;
            saved[0] = current.substring(0, at) + replacement + current.substring(at + marker.length());
            setTimestampNote(saved[0]);
            snapshotSession(fakeNote(), realNote(), saved[0], bookPos(), bookChapter());
        });
        return saved[0];
    }

    private void write(String name, String text) {
        writeFile(new File(context.getFilesDir(), name), text);
    }

    private void writeFile(File file, String text) {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            atomic.finishWrite(out);
        } catch (Exception ignored) {
            if (out != null) atomic.failWrite(out);
        }
    }

    private static String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private String read(String name) {
        File f = new File(context.getFilesDir(), name);
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int)Math.min(f.length(), Integer.MAX_VALUE)];
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            return new String(data, 0, off, StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
}
