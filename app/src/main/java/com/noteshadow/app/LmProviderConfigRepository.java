package com.noteshadow.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.InetAddress;
import java.net.URI;

/** Stores non-secret provider settings; API keys stay in no-backup encrypted storage. */
public final class LmProviderConfigRepository {
    private static final String PREFS = "lm_provider";
    private static final String KEY_URL = "url", KEY_MODEL = "model", KEY_MAX = "max",
            KEY_THINKING = "thinking", KEY_TIMEOUT = "timeout";
    private final Context context;
    private final SharedPreferences prefs;
    private final DeepSeekCredentialStore credentials;
    private final DeepSeekCredentialStore legacyCredentials;

    public LmProviderConfigRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        credentials = new DeepSeekCredentialStore(this.context, "lm_api_key.bin", "noteshadow_lm_api");
        legacyCredentials = new DeepSeekCredentialStore(this.context);
    }

    public LmProviderConfig current() {
        String url = prefs.getString(KEY_URL, LmProviderConfig.DEFAULT_URL);
        String model = prefs.getString(KEY_MODEL, LmProviderConfig.DEFAULT_MODEL);
        String thinking = prefs.getString(KEY_THINKING, "disabled");
        int max = bounded(prefs.getInt(KEY_MAX, LmProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS), 1, 32768);
        int timeout = bounded(prefs.getInt(KEY_TIMEOUT, LmProviderConfig.DEFAULT_TIMEOUT_MS), 1000, 300000);
        String key = "";
        if (LmProviderConfig.DEFAULT_URL.equals(url)) {
            try { key = legacyCredentials.load(); } catch (Exception ignored) { }
        } else {
            try { key = credentials.load(); } catch (Exception ignored) { }
        }
        return new LmProviderConfig(url, model, key, max, thinking, timeout);
    }

    public void save(String url, String apiKey, String modelId, int maxOutputTokens,
                     String thinking, int timeoutMs) throws Exception {
        String cleanUrl = validateUrl(url);
        String model = modelId == null ? "" : modelId.trim();
        if (model.isEmpty() || model.length() > 200 || model.indexOf('\n') >= 0 || model.indexOf('\r') >= 0)
            throw new IllegalArgumentException("模型 ID 无效");
        String mode = thinking == null ? "disabled" : thinking.trim();
        if (mode.length() > 40 || mode.indexOf('\n') >= 0 || mode.indexOf('\r') >= 0)
            throw new IllegalArgumentException("thinking 参数无效");
        if (maxOutputTokens < 1 || maxOutputTokens > 32768 || timeoutMs < 1000 || timeoutMs > 300000)
            throw new IllegalArgumentException("参数超出范围");
        if (LmProviderConfig.DEFAULT_URL.equals(cleanUrl)) legacyCredentials.save(apiKey);
        else credentials.save(apiKey);
        prefs.edit().putString(KEY_URL, cleanUrl).putString(KEY_MODEL, model)
                .putInt(KEY_MAX, maxOutputTokens).putString(KEY_THINKING, mode)
                .putInt(KEY_TIMEOUT, timeoutMs).apply();
    }

    public void saveUrlModel(String url, String modelId) throws Exception {
        LmProviderConfig c = current(); save(url, c.apiKey(), modelId, c.maxOutputTokens(), c.thinking(), c.timeoutMs());
    }

    public void clear() { credentials.clear(); prefs.edit().clear().apply(); }

    public boolean clearAllCredentials() {
        boolean customCleared = credentials.clear();
        boolean defaultCleared = legacyCredentials.clear();
        return customCleared && defaultCleared;
    }

    public boolean hasAnyCredential() {
        try { if (!credentials.load().isEmpty()) return true; }
        catch (Exception ignored) { }
        try { return !legacyCredentials.load().isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    static String validateUrl(String value) {
        if (value == null || value.length() > 2048 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("接口地址无效");
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getHost() == null || uri.getPath() == null
                    || uri.getPath().isEmpty()) throw new IllegalArgumentException("仅支持安全 HTTPS 接口地址");
            String host = uri.getHost().toLowerCase(java.util.Locale.US);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                    || host.equals("0.0.0.0") || host.equals("::1")) throw new IllegalArgumentException("禁止本机地址");
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress())
                    throw new IllegalArgumentException("禁止私有网络地址");
            }
            return uri.toString();
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("接口地址无效", e); }
    }

    private static int bounded(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
