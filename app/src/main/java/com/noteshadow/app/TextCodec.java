package com.noteshadow.app;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class TextCodec {
    private TextCodec() {}

    public static String decode(byte[] data) {
        if (data == null || data.length == 0) return "";
        if (data.length >= 3 && (data[0] & 0xff) == 0xef && (data[1] & 0xff) == 0xbb && (data[2] & 0xff) == 0xbf) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        if (data.length >= 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xfe) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        if (data.length >= 2 && (data[0] & 0xff) == 0xfe && (data[1] & 0xff) == 0xff) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        // UTF-8 first; GB18030 fallback for common Chinese TXT files.
        String utf8 = new String(data, StandardCharsets.UTF_8);
        long replacementCount = utf8.chars().filter(c -> c == 0xfffd).count();
        if (replacementCount == 0) return utf8;
        try {
            return Charset.forName("GB18030").decode(ByteBuffer.wrap(data)).toString();
        } catch (Throwable ignored) {
            return utf8;
        }
    }

    public static String visibleControls(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() + 32);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                out.append("\\n");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\t') {
                out.append("\\t");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
