package com.noteshadow.app;

import java.util.Arrays;

/** Immutable book entry stored by {@link BookshelfRepository}. */
public final class BookRecord {
    private final String id;
    private final String title;
    private final String sourceName;
    private final String format;
    private final int position;
    private final int chapter;
    private final int[] chapterStarts;
    private final long createdAt;
    private final long lastReadAt;

    public BookRecord(String id, String title, String sourceName, String format,
                      int position, int chapter, int[] chapterStarts,
                      long createdAt, long lastReadAt) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "未命名" : title;
        this.sourceName = sourceName == null ? "" : sourceName;
        this.format = format == null ? "txt" : format;
        this.position = Math.max(0, position);
        this.chapter = Math.max(0, chapter);
        this.chapterStarts = chapterStarts == null ? new int[0] : chapterStarts.clone();
        this.createdAt = Math.max(0L, createdAt);
        this.lastReadAt = Math.max(0L, lastReadAt);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSourceName() { return sourceName; }
    public String getFormat() { return format; }
    public int getPosition() { return position; }
    public int getChapter() { return chapter; }
    public int[] getChapterStarts() { return chapterStarts.clone(); }
    public long getCreatedAt() { return createdAt; }
    public long getLastReadAt() { return lastReadAt; }

    public BookRecord withProgress(int nextPosition, int nextChapter, long readAt) {
        return new BookRecord(id, title, sourceName, format, nextPosition, nextChapter,
                chapterStarts, createdAt, readAt);
    }
    public BookRecord withChapterStarts(int[] starts) {
        return new BookRecord(id, title, sourceName, format, position, chapter,
                starts, createdAt, lastReadAt);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof BookRecord)) return false;
        BookRecord b = (BookRecord) other;
        return id.equals(b.id) && title.equals(b.title) && sourceName.equals(b.sourceName)
                && format.equals(b.format) && position == b.position && chapter == b.chapter
                && createdAt == b.createdAt && lastReadAt == b.lastReadAt
                && Arrays.equals(chapterStarts, b.chapterStarts);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
