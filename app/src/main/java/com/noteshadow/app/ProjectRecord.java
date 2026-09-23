package com.noteshadow.app;

/** Persistent project entry used to group recorded sessions. */
public final class ProjectRecord {
    public static final String DEFAULT_ID = "default";
    private final String projectId;
    private final String name;
    private final long createdAt;
    private final long updatedAt;

    public ProjectRecord(String projectId, String name, long createdAt, long updatedAt) {
        this.projectId = projectId == null ? "" : projectId;
        this.name = name == null ? "" : name;
        this.createdAt = Math.max(0L, createdAt);
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public String getProjectId() { return projectId; }
    public String getName() { return name; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public String projectId() { return projectId; }
    public String name() { return name; }
}
