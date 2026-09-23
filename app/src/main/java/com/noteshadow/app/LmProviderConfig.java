package com.noteshadow.app;

/** Immutable OpenAI-compatible chat-completions configuration. */
public final class LmProviderConfig {
    public static final String DEFAULT_URL = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;
    public static final int DEFAULT_TIMEOUT_MS = 60000;

    private final String url, modelId, apiKey, thinking;
    private final int maxOutputTokens, timeoutMs;

    public LmProviderConfig(String url, String modelId, String apiKey,
                            int maxOutputTokens, String thinking, int timeoutMs) {
        this.url = url; this.modelId = modelId; this.apiKey = apiKey;
        this.maxOutputTokens = maxOutputTokens; this.thinking = thinking;
        this.timeoutMs = timeoutMs;
    }
    public String url() { return url; }
    public String modelId() { return modelId; }
    public String apiKey() { return apiKey; }
    public int maxOutputTokens() { return maxOutputTokens; }
    public String thinking() { return thinking; }
    public int timeoutMs() { return timeoutMs; }
}
