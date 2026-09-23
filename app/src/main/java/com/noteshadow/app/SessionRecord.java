package com.noteshadow.app;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A read-only view of one saved recording session. */
public final class SessionRecord {
    private final String sessionId;
    private final long createdAt;
    private final long updatedAt;
    private final String bookName;
    private final int bookPosition;
    private final int bookChapter;
    private final String lastRecording;
    private final String meetingNote;
    private final String timestampNote;
    private final String draftNote;
    private final String fakeNote;
    private final List<File> recordings;
    private final String projectId;
    private final String title;
    private final boolean archived;

    public SessionRecord(String sessionId, long createdAt, long updatedAt,
                         String bookName, int bookPosition, int bookChapter,
                         String lastRecording, String meetingNote, String draftNote,
                         String fakeNote, List<File> recordings) {
        this(sessionId, createdAt, updatedAt, bookName, bookPosition, bookChapter,
                lastRecording, meetingNote, "", draftNote, fakeNote, recordings,
                ProjectRecord.DEFAULT_ID, "", false);
    }

    public SessionRecord(String sessionId, long createdAt, long updatedAt,
                         String bookName, int bookPosition, int bookChapter,
                         String lastRecording, String meetingNote, String timestampNote, String draftNote,
                         String fakeNote, List<File> recordings, String projectId,
                         String title, boolean archived) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.createdAt = Math.max(0L, createdAt);
        this.updatedAt = Math.max(0L, updatedAt);
        this.bookName = safe(bookName);
        this.bookPosition = Math.max(0, bookPosition);
        this.bookChapter = Math.max(0, bookChapter);
        this.lastRecording = safe(lastRecording);
        this.meetingNote = safe(meetingNote);
        this.timestampNote = safe(timestampNote);
        this.draftNote = safe(draftNote);
        this.fakeNote = safe(fakeNote);
        this.recordings = Collections.unmodifiableList(new ArrayList<>(
                recordings == null ? Collections.<File>emptyList() : recordings));
        this.projectId = projectId == null || projectId.isEmpty() ? ProjectRecord.DEFAULT_ID : projectId;
        this.title = safe(title);
        this.archived = archived;
    }

    /** Backward-compatible constructor for callers that do not have manual notes. */
    public SessionRecord(String sessionId, long createdAt, long updatedAt,
                         String bookName, int bookPosition, int bookChapter,
                         String lastRecording, String meetingNote, String draftNote,
                         String fakeNote, List<File> recordings, String projectId,
                         String title, boolean archived) {
        this(sessionId, createdAt, updatedAt, bookName, bookPosition, bookChapter,
                lastRecording, meetingNote, "", draftNote, fakeNote, recordings,
                projectId, title, archived);
    }

    public String getSessionId() { return sessionId; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public String getBookName() { return bookName; }
    public int getBookPosition() { return bookPosition; }
    public int getBookChapter() { return bookChapter; }
    public String getLastRecording() { return lastRecording; }
    public String getMeetingNote() { return meetingNote; }
    public String getTimestampNote() { return timestampNote; }
    public String getDraftNote() { return draftNote; }
    public String getFakeNote() { return fakeNote; }
    public List<File> getRecordings() { return recordings; }
    public String getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public boolean isArchived() { return archived; }

    // Short aliases keep the model convenient for simple list UIs.
    public String sessionId() { return sessionId; }
    public long updatedAt() { return updatedAt; }
    public List<File> recordings() { return recordings; }

    /** Neutral title suitable for a history list; it never exposes a file path. */
    public String displayTitle() {
        return title.trim().isEmpty() ? "记录 " + sessionId : title;
    }

    public String getDisplayTitle() { return displayTitle(); }
    public String getPreview() { return preview(); }

    /** Returns the first useful transcript, collapsed and bounded for previews. */
    public String preview() { return previewOf(preferredNote(), 120); }

    public String preferredNote() {
        if (!meetingNote.trim().isEmpty()) return meetingNote;
        if (!draftNote.trim().isEmpty()) return draftNote;
        if (!timestampNote.trim().isEmpty()) return timestampNote;
        return "";
    }

    public static String previewOf(String text, int maxCharacters) {
        if (text == null || maxCharacters <= 0) return "";
        String compact = text.trim().replaceAll("\\s+", " ");
        if (compact.length() <= maxCharacters) return compact;
        if (maxCharacters <= 1) return compact.substring(0, maxCharacters);
        return compact.substring(0, maxCharacters - 1).trim() + "…";
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
