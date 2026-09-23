package com.noteshadow.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Successful user/assistant turns that form the current note's LM context. */
public final class LmConversation {
    public static final class Message {
        private final String role;
        private final String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String role() {
            return role;
        }

        public String content() {
            return content;
        }
    }

    private final List<Message> messages = new ArrayList<>();

    /** Returns an immutable snapshot of the current context. */
    public List<Message> messages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /** Adds both sides only after the caller has received a successful answer. */
    public void addSuccessfulTurn(String user, String assistant) {
        if (user == null || assistant == null) return;
        messages.add(new Message("user", user));
        messages.add(new Message("assistant", assistant));
    }

    public void reset() {
        messages.clear();
    }
}
