package com.noteshadow.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight compatibility fallback. It asks the system recognizer to prefer offline data.
 * On devices without an installed recognizer/offline language pack, isAvailable() is false or recognition fails.
 * The production local engine slot is intentionally isolated here so sherpa-onnx can replace this class without
 * touching the editor/recording architecture.
 */
public final class SystemOfflineAsr implements AsrEngine, RecognitionListener {
    private final Context context;
    private final AsrEngine.Listener listener;
    private SpeechRecognizer recognizer;
    private boolean running;
    private int consecutiveErrors;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    public SystemOfflineAsr(Context context, AsrEngine.Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    @Override public boolean isAvailable() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) return false;
            Intent query = new Intent(RecognitionService.SERVICE_INTERFACE);
            List<ResolveInfo> services = context.getPackageManager().queryIntentServices(query, 0);
            if (services == null) return false;
            for (ResolveInfo info : services) {
                if (info.serviceInfo == null) continue;
                String identity = (info.serviceInfo.packageName + "/" + info.serviceInfo.name).toLowerCase(Locale.ROOT);
                String permission = info.serviceInfo.permission;
                if (!identity.contains("fakerecognitionservice")
                        && "android.permission.BIND_SPEECH_RECOGNITION".equals(permission)) return true;
            }
            return false;
        }
        catch (Throwable ignored) { return false; }
    }

    @Override public String unavailableReason() {
        return "本机只有华为占位语音服务，无法转写；录音仍会保存";
    }

    @Override public void start() {
        if (running || !isAvailable()) return;
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(this);
            running = true;
            consecutiveErrors = 0;
            listen();
        } catch (Throwable e) {
            running = false;
            listener.onState("系统离线转写不可用");
        }
    }

    @Override public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Throwable ignored) {}
            try { recognizer.destroy(); } catch (Throwable ignored) {}
            recognizer = null;
        }
    }

    private void listen() {
        if (!running || recognizer == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { recognizer.startListening(i); listener.onState("转写中"); }
        catch (Throwable e) {
            running = false;
            listener.onState("系统离线转写不可用");
        }
    }

    private String first(Bundle b) {
        if (b == null) return "";
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list == null || list.isEmpty() ? "" : list.get(0);
    }

    @Override public void onResults(Bundle results) {
        consecutiveErrors = 0;
        String s = first(results);
        if (!s.isEmpty()) listener.onFinal(s);
        if (running && recognizer != null) recognizer.cancel();
        if (running) handler.postDelayed(this::listen, 180);
    }
    @Override public void onPartialResults(Bundle partialResults) { listener.onPartial(first(partialResults)); }
    @Override public void onError(int error) {
        if (!running) return;
        consecutiveErrors++;
        if (consecutiveErrors >= 3) {
            running = false;
            listener.onState("系统转写连续失败，已停止");
            return;
        }
        listener.onState("转写重试中");
        long delay = Math.min(5000L, 650L * consecutiveErrors);
        handler.postDelayed(this::listen, delay);
    }
    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int eventType, Bundle params) {}
}
