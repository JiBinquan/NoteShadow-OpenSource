package com.noteshadow.app;

import android.content.Context;
import android.content.SharedPreferences;

/** One durable checkpoint for the temporary file-transcription workflow. */
public final class FileTranscriptionCheckpoint {
    private static final String PREFS = "file_transcription_checkpoint";
    private final SharedPreferences prefs;

    public FileTranscriptionCheckpoint(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void begin(String sourceUri, String displayName) {
        archiveCurrent();
        prefs.edit().putBoolean("active", true).putString("state", "starting")
                .putString("source", sourceUri).putString("name", displayName)
                .putString("output", "").putString("location", "")
                .putString("pipeline", "fixed5").putLong("end_us", 0L).putInt("completed", 0).commit();
    }

    public void outputCreated(String outputUri, String location) {
        prefs.edit().putString("output", outputUri).putString("location", location)
                .putString("state", "running").commit();
    }

    public void checkpoint(long endUs, int completed) {
        prefs.edit().putLong("end_us", Math.max(0L, endUs))
                .putInt("completed", Math.max(0, completed)).putString("state", "running").commit();
    }

    public void pause() { prefs.edit().putString("state", "paused").commit(); }
    public void fail() { prefs.edit().putString("state", "paused").commit(); }
    /** Finishing the current task restores the most recently paused task, if any. */
    public void complete() {
        int count = prefs.getInt("paused_count", 0);
        if (count <= 0) { prefs.edit().clear().commit(); return; }
        int slot = count - 1;
        android.content.SharedPreferences.Editor e = prefs.edit()
                .putBoolean("active", prefs.getBoolean(key(slot, "active"), false))
                .putString("state", prefs.getString(key(slot, "state"), "paused"))
                .putString("source", prefs.getString(key(slot, "source"), ""))
                .putString("name", prefs.getString(key(slot, "name"), "录音"))
                .putString("output", prefs.getString(key(slot, "output"), ""))
                .putString("location", prefs.getString(key(slot, "location"), "下载/NoteShadow"))
                .putString("pipeline", prefs.getString(key(slot, "pipeline"), ""))
                .putLong("end_us", prefs.getLong(key(slot, "end_us"), 0L))
                .putInt("completed", prefs.getInt(key(slot, "completed"), 0))
                .putInt("paused_count", slot);
        clearSlot(e, slot);
        e.commit();
    }

    /** True when another paused file will become available after the current one. */
    public boolean hasSuspendedTasks() { return prefs.getInt("paused_count", 0) > 0; }

    public boolean canResume() {
        return prefs.getBoolean("active", false) && !prefs.getString("source", "").isEmpty()
                && !prefs.getString("output", "").isEmpty()
                && "fixed5".equals(prefs.getString("pipeline", ""));
    }

    public String sourceUri() { return prefs.getString("source", ""); }
    public String name() { return prefs.getString("name", "录音"); }
    public String outputUri() { return prefs.getString("output", ""); }
    public String location() { return prefs.getString("location", "下载/NoteShadow"); }
    public long endUs() { return Math.max(0L, prefs.getLong("end_us", 0L)); }
    public int completed() { return Math.max(0, prefs.getInt("completed", 0)); }

    /** Preserve the current durable task before replacing it with a newly chosen file. */
    private void archiveCurrent() {
        if (!prefs.getBoolean("active", false) || prefs.getString("source", "").isEmpty()) return;
        int slot = prefs.getInt("paused_count", 0);
        android.content.SharedPreferences.Editor e = prefs.edit()
                .putBoolean(key(slot, "active"), true)
                .putString(key(slot, "state"), "paused")
                .putString(key(slot, "source"), prefs.getString("source", ""))
                .putString(key(slot, "name"), prefs.getString("name", "录音"))
                .putString(key(slot, "output"), prefs.getString("output", ""))
                .putString(key(slot, "location"), prefs.getString("location", "下载/NoteShadow"))
                .putString(key(slot, "pipeline"), prefs.getString("pipeline", ""))
                .putLong(key(slot, "end_us"), prefs.getLong("end_us", 0L))
                .putInt(key(slot, "completed"), prefs.getInt("completed", 0))
                .putInt("paused_count", slot + 1);
        e.commit();
    }

    private static String key(int slot, String name) { return "paused_" + slot + "_" + name; }
    private static void clearSlot(android.content.SharedPreferences.Editor e, int slot) {
        e.remove(key(slot, "active")).remove(key(slot, "state")).remove(key(slot, "source"))
                .remove(key(slot, "name")).remove(key(slot, "output")).remove(key(slot, "location")).remove(key(slot, "pipeline"))
                .remove(key(slot, "end_us")).remove(key(slot, "completed"));
    }
}
