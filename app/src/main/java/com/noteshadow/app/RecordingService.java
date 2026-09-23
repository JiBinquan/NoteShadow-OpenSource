package com.noteshadow.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.ContentValues;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Environment;
import android.provider.MediaStore;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {
    public static final String ACTION_START = "com.noteshadow.app.START_RECORDING";
    public static final String ACTION_STOP = "com.noteshadow.app.STOP_RECORDING";
    public static final String ACTION_STATE = "com.noteshadow.app.RECORDING_STATE";
    public static final String EXTRA_RECORDING = "recording";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_PUBLIC_URI = "public_uri";
    public static final String EXTRA_PUBLIC_LOCATION = "public_location";
    public static final String EXTRA_ERROR = "error";
    private static final String TAG = "NoteShadowRecording";
    private static final String CHANNEL = "recording";
    private static final int NOTIFICATION_ID = 41;
    private static volatile boolean active;

    private MediaRecorder recorder;
    private AppStorage storage;
    private File output;

    public static boolean isActive() { return active; }

    @Override public void onCreate() {
        super.onCreate();
        storage = new AppStorage(this);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (recorder == null) {
            // A deliberate new recording is a new meeting Session. A sticky
            // service restart keeps the existing Session id.
            if (intent != null && ACTION_START.equals(intent.getAction())) {
                String newSession = storage.beginNewSession();
                new LmContextStore(this, newSession);
                LmDiagnosticLog.record(this, LmDiagnosticLog.Event.CONTEXT_RESET, "COURSE");
            }
            startRecording();
        }
        return START_STICKY;
    }

    private void startRecording() {
        try {
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this);
            Notification notification = nb
                    .setSmallIcon(com.noteshadow.app.R.drawable.ic_note)
                    .setContentTitle("会议记录")
                    .setContentText("录音进行中")
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            File dir = storage.sessionRecordingsDirectory();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            output = new File(dir, "meeting_" + stamp + ".m4a");

            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(24000);
            recorder.setOutputFile(output.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            active = true;
            storage.setRecording(true);
            storage.setLastRecording(output.getAbsolutePath());
            storage.snapshotSession(storage.fakeNote(), storage.realNote(), storage.bookPos(), storage.bookChapter());
            Log.i(TAG, "Recording started");
            broadcast(true);
        } catch (Throwable e) {
            active = false;
            Log.e(TAG, "Unable to start recording", e);
            safeRelease();
            storage.setRecording(false);
            broadcast(false, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            stopForeground(true);
            stopSelf();
        }
    }

    private void stopRecording() {
        if (recorder == null) {
            active = false;
            storage.setRecording(false);
            stopForeground(true);
            return;
        }
        try { recorder.stop(); } catch (Throwable e) { Log.e(TAG, "Recorder stop failed", e); }
        safeRelease();
        active = false;
        storage.setRecording(false);
        storage.snapshotSession(storage.fakeNote(), storage.realNote(), storage.bookPos(), storage.bookChapter());
        if (output != null) Log.i(TAG, "Recording stopped"
                + " bytes=" + output.length());
        validateOutput(output);
        String publicUri = publishRecording(output);
        broadcast(false, null, publicUri);
        stopForeground(true);
    }

    private String publishRecording(File file) {
        if (file == null || !file.isFile() || file.length() == 0 || Build.VERSION.SDK_INT < 29) return null;
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/NoteShadow");
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("MediaStore insert returned null");
            try (FileInputStream in = new FileInputStream(file);
                 OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("MediaStore output is null");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            }
            values.clear();
            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            storage.setPublicRecordingUri(uri.toString());
            Log.i(TAG, "Recording published to Music/NoteShadow");
            return uri.toString();
        } catch (Throwable e) {
            Log.e(TAG, "Unable to publish recording", e);
            if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Throwable ignored) {}
            return null;
        }
    }

    private void validateOutput(File file) {
        if (file == null || !file.isFile() || file.length() == 0) return;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                            ? format.getLong(MediaFormat.KEY_DURATION) : -1L;
                    int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : -1;
                    int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : -1;
                    Log.i(TAG, "Recording validated mime=" + mime + " durationUs=" + durationUs
                            + " sampleRate=" + sampleRate + " channels=" + channels);
                    return;
                }
            }
            Log.e(TAG, "Recording has no readable audio track");
        } catch (Throwable e) {
            Log.e(TAG, "Recording validation failed: " + e.getClass().getSimpleName());
        } finally {
            extractor.release();
        }
    }

    private void safeRelease() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Throwable ignored) {}
            try { recorder.release(); } catch (Throwable ignored) {}
            recorder = null;
        }
    }

    private void broadcast(boolean active) {
        broadcast(active, null, null);
    }

    private void broadcast(boolean active, String error) {
        broadcast(active, error, null);
    }

    private void broadcast(boolean active, String error, String publicUri) {
        Intent i = new Intent(ACTION_STATE);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_RECORDING, active);
        if (output != null) i.putExtra(EXTRA_PATH, output.getAbsolutePath());
        if (publicUri != null) {
            i.putExtra(EXTRA_PUBLIC_URI, publicUri);
            i.putExtra(EXTRA_PUBLIC_LOCATION, "音乐/NoteShadow/" + output.getName());
        }
        if (error != null) i.putExtra(EXTRA_ERROR, error);
        sendBroadcast(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "会议录音", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("后台会议录音状态");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(c);
        }
    }

    @Override public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
