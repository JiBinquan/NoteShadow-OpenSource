package com.noteshadow.app;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** No-backup, current-course-only model context. Never derives context from the note. */
final class LmContextStore {
    enum Mode { NORMAL, BLOCK, CONTINUOUS }

    private static final Object LOCK = new Object();
    private static final int MAX_CONTEXT_CHARS = 20_000;
    private static AtomicFile file;
    private static String session = "";
    private static long generation;
    private static Mode mode = Mode.NORMAL;
    private static final LmConversation conversation = new LmConversation();

    LmContextStore(Context context, String currentSession) {
        synchronized (LOCK) {
            if (file == null) {
                file = new AtomicFile(new File(context.getNoBackupFilesDir(), "lm_course_context.json"));
                try {
                    JSONObject json = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
                    if (currentSession.equals(json.optString("session"))) {
                        session = currentSession;
                        generation = json.optLong("generation", 0);
                        try { mode = Mode.valueOf(json.optString("mode", "NORMAL")); }
                        catch (Exception ignored) { mode = Mode.NORMAL; }
                        JSONArray turns = json.optJSONArray("turns");
                        if (turns != null) for (int i = 0; i < turns.length(); i++) {
                            JSONArray turn = turns.optJSONArray(i);
                            if (turn != null && turn.length() == 2)
                                conversation.addSuccessfulTurn(turn.optString(0), turn.optString(1));
                        }
                    }
                } catch (Exception ignored) { }
            }
            ensureSession(currentSession);
        }
    }

    Mode mode() { synchronized (LOCK) { return mode; } }
    long generation() { synchronized (LOCK) { return generation; } }

    /** Align model history when another component starts a new course. */
    boolean ensureSession(String currentSession) {
        synchronized (LOCK) {
            if (currentSession.equals(session)) return false;
            session = currentSession;
            generation++;
            conversation.reset();
            mode = Mode.NORMAL;
            persist();
            return true;
        }
    }

    void setMode(Mode next) {
        synchronized (LOCK) { mode = next; persist(); }
    }

    void reset(String currentSession) {
        reset(currentSession, Mode.NORMAL);
    }

    void reset(String currentSession, Mode nextMode) {
        synchronized (LOCK) {
            session = currentSession;
            generation++;
            mode = nextMode;
            conversation.reset();
            persist();
        }
    }

    boolean isCurrent(String currentSession, long expectedGeneration) {
        synchronized (LOCK) { return session.equals(currentSession) && generation == expectedGeneration; }
    }

    List<LmConversation.Message> requestMessages(String currentSession, long expectedGeneration,
                                                 String prompt) {
        synchronized (LOCK) {
            if (!isCurrent(currentSession, expectedGeneration)) return null;
            List<LmConversation.Message> history = conversation.messages();
            int start = 0;
            int chars = prompt.length();
            for (int i = history.size() - 2; i >= 0; i -= 2) {
                int turnChars = history.get(i).content().length() + history.get(i + 1).content().length();
                if (chars + turnChars > MAX_CONTEXT_CHARS) { start = i + 2; break; }
                chars += turnChars;
            }
            List<LmConversation.Message> result = new ArrayList<>(history.subList(start, history.size()));
            result.add(new LmConversation.Message("user", prompt));
            return Collections.unmodifiableList(result);
        }
    }

    boolean addSuccess(String currentSession, long expectedGeneration, String prompt, String answer) {
        synchronized (LOCK) {
            if (!isCurrent(currentSession, expectedGeneration)) return false;
            conversation.addSuccessfulTurn(prompt, answer);
            persist();
            return true;
        }
    }

    private void persist() {
        FileOutputStream out = null;
        try {
            JSONObject json = new JSONObject();
            json.put("session", session);
            json.put("generation", generation);
            json.put("mode", mode.name());
            JSONArray turns = new JSONArray();
            List<LmConversation.Message> messages = conversation.messages();
            for (int i = 0; i + 1 < messages.size(); i += 2) {
                JSONArray turn = new JSONArray();
                turn.put(messages.get(i).content());
                turn.put(messages.get(i + 1).content());
                turns.put(turn);
            }
            json.put("turns", turns);
            out = file.startWrite();
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception failure) {
            if (out != null) file.failWrite(out);
        }
    }
}
