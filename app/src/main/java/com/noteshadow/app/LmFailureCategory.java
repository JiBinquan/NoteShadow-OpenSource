package com.noteshadow.app;

import org.json.JSONException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

/** Stable, non-sensitive error codes for the on-device model diagnostic log. */
final class LmFailureCategory {
    private static final Pattern STATUS = Pattern.compile("[（(](\\d{3})[）)]");

    private LmFailureCategory() { }

    static String of(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof UnknownHostException) return "DNS";
            if (current instanceof SocketTimeoutException) return "TIMEOUT";
            if (current instanceof SSLException) return "TLS";
            if (current instanceof ConnectException) return "CONNECT";
            if (current instanceof JSONException) return "RESPONSE_FORMAT";
            String message = current.getMessage();
            if (message != null) {
                Matcher status = STATUS.matcher(message);
                if (status.find()) return "HTTP_" + status.group(1);
                if (message.startsWith("密钥无效")) return "AUTH";
                if (message.startsWith("请求过于频繁")) return "RATE_LIMIT";
                if (message.startsWith("尚未配置模型密钥")) return "NO_KEY";
                if (message.startsWith("模型没有")) return "EMPTY_ANSWER";
                if (message.startsWith("模型回复过长")) return "RESPONSE_TOO_LONG";
            }
            if (current instanceof java.io.IOException) return "NETWORK";
            if (current instanceof IllegalArgumentException) return "INVALID_REQUEST";
        }
        return "UNKNOWN";
    }

    static String userHint(String code) {
        if ("HTTP_402".equals(code)) return "接口账户余额不足（402）";
        if ("HTTP_400".equals(code) || "HTTP_422".equals(code))
            return "模型 ID 或请求参数不兼容（" + code.substring(5) + "）";
        if ("AUTH".equals(code) || "HTTP_401".equals(code) || "HTTP_403".equals(code))
            return "当前接口密钥无效或无权限";
        if ("NO_KEY".equals(code)) return "当前接口没有密钥";
        if ("RATE_LIMIT".equals(code) || "HTTP_429".equals(code)) return "请求过于频繁";
        if ("DNS".equals(code)) return "域名解析失败";
        if ("TIMEOUT".equals(code)) return "连接或回复超时";
        if ("TLS".equals(code)) return "安全连接失败";
        if ("CONNECT".equals(code) || "NETWORK".equals(code)) return "网络连接异常";
        if ("EMPTY_ANSWER".equals(code)) return "接口未返回回答正文";
        if ("RESPONSE_FORMAT".equals(code)) return "接口回复格式不兼容";
        if (code != null && code.startsWith("HTTP_")) return "接口返回 " + code.substring(5);
        return "未知错误，请查看下方诊断记录";
    }
}
