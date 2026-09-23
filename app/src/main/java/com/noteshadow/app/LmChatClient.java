package com.noteshadow.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

/** Explicit-message OpenAI-compatible client. It has no access to note or recording storage. */
public final class LmChatClient {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    public String answer(android.content.Context context, List<LmConversation.Message> messages) throws Exception {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("对话不能为空");
        LmProviderConfig config = new LmProviderConfigRepository(context).current();
        if (config.apiKey() == null || config.apiKey().isEmpty()) throw new IllegalStateException("尚未配置模型密钥");
        LmProviderConfigRepository.validateUrl(config.url());
        JSONArray prompt = new JSONArray();
        for (LmConversation.Message message : messages) {
            if (message == null || message.role() == null || message.content() == null || message.content().isEmpty()
                    || message.role().indexOf('\r') >= 0 || message.role().indexOf('\n') >= 0)
                throw new IllegalArgumentException("对话消息无效");
            prompt.put(new JSONObject().put("role", message.role()).put("content", message.content()));
        }
        JSONObject request = new JSONObject().put("model", config.modelId()).put("stream", false)
                .put("max_tokens", config.maxOutputTokens()).put("messages", prompt);
        if (config.thinking() != null && !config.thinking().isEmpty()) {
            if (LmProviderConfig.DEFAULT_URL.equals(config.url()))
                request.put("thinking", new JSONObject().put("type", config.thinking()));
            else if (!"disabled".equals(config.thinking()))
                request.put("thinking", config.thinking().startsWith("{")
                        ? new JSONObject(config.thinking()) : config.thinking());
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(config.url()).openConnection();
        connection.setConnectTimeout(config.timeoutMs()); connection.setReadTimeout(config.timeoutMs());
        connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + config.apiKey());
        try {
            byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new IllegalStateException("密钥无效或无权限");
            if (status == 402) throw new IllegalStateException("账户余额不足（402）");
            if (status == 429) throw new IllegalStateException("请求过于频繁，请稍后重试");
            if (status == 400 || status == 422)
                throw new IllegalStateException("接口参数不兼容（" + status + "）");
            if (status < 200 || status >= 300) throw new IllegalStateException("服务暂不可用（" + status + "）");
            try (InputStream input = connection.getInputStream()) {
                JSONObject response = new JSONObject(readBounded(input));
                JSONArray choices = response.optJSONArray("choices");
                if (choices == null || choices.length() == 0) throw new IllegalStateException("模型没有返回正文");
                JSONObject choice = choices.getJSONObject(0);
                String text = choice.optJSONObject("message") == null ? ""
                        : choice.getJSONObject("message").optString("content", "").trim();
                if (text.isEmpty()) throw new IllegalStateException("模型没有返回正文");
                if ("length".equalsIgnoreCase(choice.optString("finish_reason")))
                    text += "\n\n[回答因输出长度限制被截断]";
                return text;
            }
        } finally { connection.disconnect(); }
    }

    /** Best-effort OpenAI-compatible model discovery; callers may fall back to manual model ID. */
    public List<String> listModels(android.content.Context context) throws Exception {
        LmProviderConfig config = new LmProviderConfigRepository(context).current();
        return listModels(config.url(), config.apiKey(), config.timeoutMs());
    }

    /** Model discovery for a proposed URL+key before saving it. */
    public List<String> listModels(String completionsUrl, String apiKey, int timeoutMs) throws Exception {
        String endpoint = modelsEndpoint(completionsUrl);
        LmProviderConfigRepository.validateUrl(endpoint);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(timeoutMs); connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("GET"); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("模型列表不可用（" + status + "）");
            JSONObject response;
            try (InputStream input = connection.getInputStream()) { response = new JSONObject(readBounded(input)); }
            JSONArray data = response.optJSONArray("data");
            List<String> result = new ArrayList<>();
            if (data != null) for (int i = 0; i < data.length(); i++) {
                String id = data.getJSONObject(i).optString("id", "").trim();
                if (!id.isEmpty()) result.add(id);
            }
            return result;
        } finally { connection.disconnect(); }
    }

    static String modelsEndpoint(String completionsUrl) {
        String clean = completionsUrl.replaceFirst("/chat/completions(?:\\?.*)?$", "");
        if (!clean.equals(completionsUrl)) return clean + "/models";
        int slash = completionsUrl.lastIndexOf('/');
        return completionsUrl.substring(0, slash) + "/models";
    }

    private static String readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) >= 0) {
            if (output.size() + count > MAX_RESPONSE_BYTES) throw new IllegalStateException("模型回复过长");
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
