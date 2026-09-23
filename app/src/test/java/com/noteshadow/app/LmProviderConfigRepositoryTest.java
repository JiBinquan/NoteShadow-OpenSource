package com.noteshadow.app;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class LmProviderConfigRepositoryTest {
    @Test public void rejectsInsecureOrLocalEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> LmProviderConfigRepository.validateUrl("http://example.com/v1/chat/completions"));
        assertThrows(IllegalArgumentException.class,
                () -> LmProviderConfigRepository.validateUrl("https://localhost/v1/chat/completions"));
        assertThrows(IllegalArgumentException.class,
                () -> LmProviderConfigRepository.validateUrl("https://user:pass@example.com/v1/chat/completions"));
    }
}
