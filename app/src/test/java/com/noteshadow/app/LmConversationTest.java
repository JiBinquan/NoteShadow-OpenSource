package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LmConversationTest {
    @Test public void storesOnlySuccessfulTurnsInHttpOrder() {
        LmConversation conversation = new LmConversation();
        conversation.addSuccessfulTurn("问题", "回答");
        assertEquals(2, conversation.messages().size());
        assertEquals("user", conversation.messages().get(0).role());
        assertEquals("问题", conversation.messages().get(0).content());
        assertEquals("assistant", conversation.messages().get(1).role());
    }

    @Test public void listIsReadOnlyAndResetClearsContext() {
        LmConversation conversation = new LmConversation();
        conversation.addSuccessfulTurn("问题", "回答");
        try {
            conversation.messages().clear();
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        assertEquals(2, conversation.messages().size());
        conversation.reset();
        assertTrue(conversation.messages().isEmpty());
    }
}
