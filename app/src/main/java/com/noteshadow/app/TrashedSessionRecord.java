package com.noteshadow.app;

import java.io.File;

/** A session that has been moved to the private application recycle bin. */
public final class TrashedSessionRecord {
    private final SessionRecord record;
    private final long deletedAt;
    private final boolean originalArchived;
    private final String originalProjectId;
    private final String originalTitle;

    TrashedSessionRecord(SessionRecord record, long deletedAt, boolean originalArchived,
                         String originalProjectId, String originalTitle) {
        this.record = record;
        this.deletedAt = Math.max(0L, deletedAt);
        this.originalArchived = originalArchived;
        this.originalProjectId = originalProjectId == null ? ProjectRecord.DEFAULT_ID : originalProjectId;
        this.originalTitle = originalTitle == null ? "" : originalTitle;
    }
    public SessionRecord getRecord() { return record; }
    public SessionRecord getSession() { return record; }
    public String getSessionId() { return record.getSessionId(); }
    public long getDeletedAt() { return deletedAt; }
    public boolean isOriginalArchived() { return originalArchived; }
    public String getOriginalProjectId() { return originalProjectId; }
    public String getOriginalTitle() { return originalTitle; }
    public long getUpdatedAt() { return record.getUpdatedAt(); }
    public java.util.List<File> getRecordings() { return record.getRecordings(); }
}
