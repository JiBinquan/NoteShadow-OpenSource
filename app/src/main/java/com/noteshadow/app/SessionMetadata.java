package com.noteshadow.app;

/** Mutable-in-concept sidecar metadata; transcript snapshots never overwrite it. */
public final class SessionMetadata {
    public static final String DEFAULT_PROJECT_ID = ProjectRecord.DEFAULT_ID;
    private final String sessionId;
    private final String projectId;
    private final String title;
    private final boolean archived;
    private final long updatedAt;

    public SessionMetadata(String sessionId, String projectId, String title,
                           boolean archived, long updatedAt) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.projectId = projectId == null || projectId.isEmpty() ? DEFAULT_PROJECT_ID : projectId;
        this.title = title == null ? "" : title;
        this.archived = archived;
        this.updatedAt = Math.max(0L, updatedAt);
    }
    public String getSessionId() { return sessionId; }
    public String getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public boolean isArchived() { return archived; }
    public long getUpdatedAt() { return updatedAt; }
}
