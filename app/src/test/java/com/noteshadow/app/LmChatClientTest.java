package com.noteshadow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LmChatClientTest {
    @Test public void deepSeekModelsEndpointUsesApiRoot() {
        assertEquals("https://api.deepseek.com/models",
                LmChatClient.modelsEndpoint("https://api.deepseek.com/chat/completions"));
    }

    @Test public void versionedCompatibleModelsEndpointKeepsVersion() {
        assertEquals("https://example.com/v1/models",
                LmChatClient.modelsEndpoint("https://example.com/v1/chat/completions"));
    }
}
