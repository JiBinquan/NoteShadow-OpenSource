package com.noteshadow.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Read-only description of one of the app's current storage locations.
 *
 * <p>This is deliberately a JVM-only model.  It describes the existing
 * layout for the settings screen without changing where the app writes data.
 * A byte count of zero means that the caller did not measure that location,
 * not that it is necessarily empty.</p>
 */
public final class StorageLocationInfo {
    public static final String DEFAULT_PUBLIC_RECORDINGS_PATH = "音乐/NoteShadow";
    public static final String DEFAULT_TEXT_EXPORT_PATH = "下载/NoteShadow";

    public enum Kind {
        RECORDINGS,
        TRANSCRIPTS,
        NOTES,
        IMAGES
    }

    private final Kind kind;
    private final String label;
    private final String pathDescription;
    private final boolean systemOpenable;
    private final long bytes;

    public StorageLocationInfo(Kind kind, String label, String pathDescription,
                               boolean systemOpenable, long bytes) {
        if (kind == null) throw new IllegalArgumentException("kind required");
        this.kind = kind;
        this.label = label == null ? "" : label;
        this.pathDescription = pathDescription == null ? "" : pathDescription;
        this.systemOpenable = systemOpenable;
        this.bytes = Math.max(0L, bytes);
    }

    public Kind getKind() { return kind; }
    public String getLabel() { return label; }
    public String getPathDescription() { return pathDescription; }
    public boolean isSystemOpenable() { return systemOpenable; }
    public long getBytes() { return bytes; }

    public Kind kind() { return kind; }
    public String label() { return label; }
    public String pathDescription() { return pathDescription; }
    public boolean systemOpenable() { return systemOpenable; }
    public long bytes() { return bytes; }

    /**
     * Describes the default layout currently used by NoteShadow.
     *
     * @param usage usage of the private sessions directory, including trash
     * @param mediaStoreDefaults whether this Android version publishes to the fixed public folders
     */
    public static List<StorageLocationInfo> defaults(StorageCategoryUsage usage,
                                                      boolean mediaStoreDefaults) {
        StorageCategoryUsage measured = usage == null
                ? new StorageCategoryUsage(0L, 0L, 0L, 0L) : usage;
        String recordingLocation = mediaStoreDefaults
                ? "课程资料库；完成后另存公开副本到 " + DEFAULT_PUBLIC_RECORDINGS_PATH
                : "课程资料库；当前系统版本不创建固定位置的公开副本";
        String transcriptLocation = mediaStoreDefaults
                ? "课程资料库；手动导出到 " + DEFAULT_TEXT_EXPORT_PATH
                : "课程资料库；当前系统版本不支持固定下载目录导出";
        String noteLocation = mediaStoreDefaults
                ? "课程资料库；随课程包导出到 " + DEFAULT_TEXT_EXPORT_PATH
                : "课程资料库；当前系统版本不支持固定下载目录导出";
        return Collections.unmodifiableList(Arrays.asList(
                new StorageLocationInfo(Kind.RECORDINGS, "录音",
                        recordingLocation, mediaStoreDefaults, measured.recordingBytes()),
                new StorageLocationInfo(Kind.TRANSCRIPTS, "转写文本",
                        transcriptLocation, mediaStoreDefaults, measured.transcriptBytes()),
                new StorageLocationInfo(Kind.NOTES, "课堂笔记",
                        noteLocation, mediaStoreDefaults, measured.noteBytes()),
                new StorageLocationInfo(Kind.IMAGES, "笔记图片",
                        "课程资料库中的每课附件目录", false, measured.imageBytes())
        ));
    }
}
