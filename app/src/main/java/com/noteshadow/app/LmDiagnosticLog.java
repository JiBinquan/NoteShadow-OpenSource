package com.noteshadow.app;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small no-backup diagnostic ring: status codes only, never prompts, answers, URLs or keys. */
final class LmDiagnosticLog {
    enum Event {
        NOTE_COMMAND, NOTE_REJECTED, NOTE_WAITING, NOTE_BLOCK_LITERAL,
        REQUEST_QUEUED, REQUEST_STARTED, REQUEST_SUCCESS, REQUEST_FAILURE,
        REPLY_WRITTEN, REPLY_SKIPPED, REQUEST_CANCELLED, CONTEXT_RESET,
        SETTINGS_TEST_STARTED, SETTINGS_TEST_SUCCESS, SETTINGS_TEST_FAILURE,
        CONFIG_SAVED, CONFIG_REJECTED
    }

    private static final Object LOCK = new Object();
    private static final int MAX_LINES = 100;
    private static final int MAX_CHARS = 16_000;
    private static final String TAG = "NoteShadowLM";

    private LmDiagnosticLog() { }

    static void record(Context context, Event event, String detailCode) {
        if (context == null || event == null) return;
        String detail = detailCode != null && detailCode.matches("[A-Z0-9_]{1,40}")
                ? detailCode : "NONE";
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + "  " + event.name() + "  " + detail;
        Log.i(TAG, event.name() + " " + detail);
        synchronized (LOCK) {
            try {
                AtomicFile file = file(context);
                String previous;
                try { previous = new String(file.readFully(), StandardCharsets.UTF_8); }
                catch (Exception missing) { previous = ""; }
                String[] lines = (previous + line + "\n").split("\n");
                StringBuilder kept = new StringBuilder();
                for (int i = Math.max(0, lines.length - MAX_LINES); i < lines.length; i++) {
                    if (lines[i].isEmpty()) continue;
                    if (kept.length() + lines[i].length() + 1 > MAX_CHARS) continue;
                    kept.append(lines[i]).append('\n');
                }
                FileOutputStream out = null;
                try {
                    out = file.startWrite();
                    out.write(kept.toString().getBytes(StandardCharsets.UTF_8));
                    file.finishWrite(out);
                } catch (Exception failure) { if (out != null) file.failWrite(out); }
            } catch (Exception ignored) { }
        }
    }

    static String recent(Context context) {
        synchronized (LOCK) {
            try {
                String all = new String(file(context).readFully(), StandardCharsets.UTF_8).trim();
                if (all.isEmpty()) return "暂无模型诊断记录";
                String[] lines = all.split("\n");
                StringBuilder display = new StringBuilder();
                for (int i = lines.length - 1; i >= Math.max(0, lines.length - 30); i--) {
                    String[] fields = lines[i].split("  ", 3);
                    if (fields.length != 3) continue;
                    display.append(fields[0]).append(" · ").append(eventLabel(fields[1], fields[2]))
                            .append(" · ").append(detailLabel(fields[2])).append('\n');
                }
                return display.toString().trim();
            } catch (Exception ignored) { return "暂无模型诊断记录"; }
        }
    }

    static boolean clear(Context context) {
        synchronized (LOCK) {
            File target = new File(context.getNoBackupFilesDir(), "lm_diagnostic_log.txt");
            return !target.exists() || target.delete();
        }
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "lm_diagnostic_log.txt"));
    }

    private static String eventLabel(String code, String detail) {
        switch (code) {
            case "NOTE_COMMAND": return "笔记指令已识别";
            case "NOTE_REJECTED": return "笔记指令未执行";
            case "NOTE_WAITING": return "笔记指令模式";
            case "NOTE_BLOCK_LITERAL": return "多行问题中的指令按原文保留";
            case "REQUEST_QUEUED": return "请求已排队";
            case "REQUEST_STARTED": return "开始连接模型";
            case "REQUEST_SUCCESS": return "模型已回复";
            case "REQUEST_FAILURE": return "模型请求失败";
            case "REPLY_WRITTEN": return "回复已写入笔记";
            case "REPLY_SKIPPED": return "回复未写入笔记";
            case "REQUEST_CANCELLED": return "LMNEW".equals(detail)
                    ? "手动重置模型对话" : "旧请求已取消";
            case "CONTEXT_RESET": return "COURSE".equals(detail)
                    ? "新课程模型对话已重置" : "手动重置模型对话";
            case "SETTINGS_TEST_STARTED": return "开始连接测试";
            case "SETTINGS_TEST_SUCCESS": return "连接测试成功";
            case "SETTINGS_TEST_FAILURE": return "连接测试失败";
            case "CONFIG_SAVED": return "接口设置已保存";
            case "CONFIG_REJECTED": return "接口设置未保存";
            default: return code;
        }
    }

    private static String detailLabel(String code) {
        if ("SINGLE".equals(code)) return "单行提问";
        if ("BLOCK".equals(code)) return "多行提问";
        if ("CONTINUOUS".equals(code)) return "连续对话";
        if ("NORMAL".equals(code)) return "普通模式";
        if ("NO_KEY".equals(code)) return "当前接口没有密钥";
        if ("EMPTY".equals(code)) return "问题为空";
        if ("TOO_LONG".equals(code)) return "问题过长";
        if ("NO_BLOCK".equals(code)) return "未开始多行提问";
        if ("NO_TIMESTAMP".equals(code) || "NO_NEWLINE".equals(code)) return "换行位置不符合指令格式";
        if ("MARKER_OR_COURSE".equals(code)) return "课程已切换或占位行被编辑";
        if ("GENERATION".equals(code)) return "课程或对话状态已更换";
        if ("LMNEW".equals(code)) return "执行 /lmnew";
        if ("COURSE".equals(code)) return "已切换课程";
        if ("FIXED_PROMPT".equals(code)) return "仅发送固定测试句";
        if (code.startsWith("S") && code.substring(1).matches("\\d{1,3}"))
            return code.substring(1) + " 秒";
        if (code.startsWith("HTTP_") || "AUTH".equals(code) || "NO_KEY".equals(code)
                || "DNS".equals(code) || "TIMEOUT".equals(code) || "TLS".equals(code)
                || "NETWORK".equals(code) || "CONNECT".equals(code)
                || "RESPONSE_FORMAT".equals(code) || "EMPTY_ANSWER".equals(code))
            return LmFailureCategory.userHint(code) + " [" + code + "]";
        return code;
    }
}
