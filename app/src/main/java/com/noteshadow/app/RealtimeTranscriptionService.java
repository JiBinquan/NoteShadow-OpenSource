package com.noteshadow.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Owns microphone ASR so it survives Activity backgrounding and screen-off. */
public final class RealtimeTranscriptionService extends Service implements AsrEngine.Listener {
    public static final String ACTION_START = "com.noteshadow.app.START_TRANSCRIPTION";
    public static final String ACTION_STOP = "com.noteshadow.app.STOP_TRANSCRIPTION";
    public static final String ACTION_EVENT = "com.noteshadow.app.TRANSCRIPTION_EVENT";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_QUALITY = "quality";
    public static final String KIND_STATE = "state";
    public static final String KIND_PARTIAL = "partial";
    public static final String KIND_FINAL = "final";
    private static final String CHANNEL = "transcription";
    private static final int NOTIFICATION_ID = 42;
    private static volatile boolean active;

    private AsrEngine engine;
    private int engineStartId;
    private AppStorage storage;
    private TranscriptionSettingsRepository transcriptionSettings;
    private PowerManager.WakeLock wakeLock;

    public static boolean isActive() { return active; }

    @Override public void onCreate() {
        super.onCreate();
        storage = new AppStorage(this);
        transcriptionSettings = new TranscriptionSettingsRepository(this);
        createChannel();
    }

    @Override public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecognition();
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (engine == null) {
            LiveTranscriptionMode mode;
            if (intent != null && intent.hasExtra(EXTRA_QUALITY)) {
                // Keep old callers working while allowing the new setting to win
                // once it has been explicitly persisted.
                mode = transcriptionSettings.migrateLegacyIfNeeded(
                        intent.getBooleanExtra(EXTRA_QUALITY, false));
            } else {
                mode = transcriptionSettings.mode();
            }
            AsrEngine candidate = new LocalSherpaAsr(this, this, mode);
            if (!candidate.isAvailable()) {
                broadcast(KIND_STATE, candidate.unavailableReason());
                active = false;
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }
            startForegroundNotification();
            acquireWakeLock();
            active = true;
            engine = candidate;
            engineStartId = startId;
            try {
                engine.start();
            } catch (Throwable failure) {
                broadcast(KIND_STATE, "实时转写启动失败：" + failure.getClass().getSimpleName());
                stopRecognition();
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }
        } else {
            // A repeated START belongs to the same live engine. Remember the
            // newest id so an older completion cannot stop newer service work.
            engineStartId = startId;
        }
        // Keep the microphone foreground service alive across ordinary process pressure.
        // The captured recording remains the recovery authority if Android must still kill us.
        return START_STICKY;
    }

    private void startForegroundNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(R.drawable.ic_note)
                .setContentTitle("会议记录")
                .setContentText("本地实时转写进行中")
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else startForeground(NOTIFICATION_ID, notification);
    }

    private void acquireWakeLock() {
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        if (power == null) return;
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoteShadow:RealtimeAsr");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private synchronized void stopRecognition() {
        active = false;
        if (engine != null) engine.stop();
        engine = null;
        engineStartId = 0;
        releaseForegroundResources();
    }

    private void releaseForegroundResources() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        stopForeground(true);
    }

    @Override public void onPartial(String text) { broadcast(KIND_PARTIAL, text); }

    @Override public void onState(String text) { broadcast(KIND_STATE, text); }

    @Override public void onFinal(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String current = storage.realNote();
        if (!current.isEmpty() && !current.endsWith("\n")) current += "\n";
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        current += "[" + stamp + "] " + text.trim() + "\n";
        storage.setRealNote(current);
        storage.snapshotSession(storage.fakeNote(), current, storage.bookPos(), storage.bookChapter());
        broadcast(KIND_FINAL, text.trim());
    }

    @Override public void onDraftFinal(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String current = storage.draftNote();
        if (!current.isEmpty() && !current.endsWith("\n")) current += "\n";
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        storage.setDraftNote(current + "[" + stamp + "] " + text.trim() + "\n");
    }

    @Override public void onRefined(String text) {
        if (text == null || text.trim().isEmpty()) return;
        // Qwen is the formal note. Zipformer is retained separately as a draft;
        // it can never shorten or overwrite this append-only record.
        String current = storage.realNote();
        if (!current.isEmpty() && !current.endsWith("\n")) current += "\n";
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        current += "[" + stamp + "] " + text.trim() + "\n";
        storage.setRealNote(current);
        storage.setRefinedNote(current);
        storage.snapshotSession(storage.fakeNote(), current, storage.bookPos(), storage.bookChapter());
        broadcast(KIND_FINAL, text.trim());
    }

    @Override public synchronized void onStopped(AsrEngine stoppedEngine) {
        // Ignore a late callback from an explicitly stopped, already replaced engine.
        if (engine != stoppedEngine) return;
        int completedStartId = engineStartId;
        engine = null;
        engineStartId = 0;
        active = false;
        releaseForegroundResources();
        stopSelfResult(completedStartId);
    }

    private void broadcast(String kind, String text) {
        Intent event = new Intent(ACTION_EVENT).setPackage(getPackageName());
        event.putExtra(EXTRA_KIND, kind);
        event.putExtra(EXTRA_TEXT, text == null ? "" : text);
        sendBroadcast(event);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "实时转写", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("息屏后保持本地实时转写");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        stopRecognition();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
