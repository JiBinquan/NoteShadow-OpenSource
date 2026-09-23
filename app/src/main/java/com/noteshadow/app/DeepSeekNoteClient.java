package com.noteshadow.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Sends one explicit /ds question; never reads note, transcript, or audio files. */
final class DeepSeekNoteClient {
    private static final String URL = "https://api.deepseek.com/chat/completions";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    String answer(String credential, String question) throws Exception {
        if (!DsNoteCommand.isValidLength(question) || credential == null || credential.isEmpty()) {
            throw new IllegalArgumentException("问题或密钥无效");
        }
        JSONObject request = new JSONObject();
        request.put("model", "deepseek-v4-flash");
        request.put("stream", false);
        request.put("thinking", new JSONObject().put("type", "disabled"));
        request.put("max_tokens", 1024);
        request.put("messages", new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", question)));

        HttpURLConnection connection = (HttpURLConnection) new URL(URL).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + credential);
        try {
            byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new IllegalStateException("密钥无效或无权限");
            if (status == 429) throw new IllegalStateException("请求过于频繁，请稍后重试");
            if (status < 200 || status >= 300) throw new IllegalStateException("服务暂不可用（" + status + "）");
            try (InputStream input = connection.getInputStream()) {
                JSONObject response = new JSONObject(readBounded(input));
                String text = response.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content", "").trim();
                if (text.isEmpty()) throw new IllegalStateException("模型没有返回正文");
                return text;
            }
        } finally { connection.disconnect(); }
    }

    private static String readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (output.size() + count > MAX_RESPONSE_BYTES) throw new IllegalStateException("模型回复过长");
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
