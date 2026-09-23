package com.noteshadow.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small dependency-free JSON object codec for the app's flat metadata files. */
final class JsonCodec {
    private JsonCodec() { }
    static Map<String, String> parse(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null) return out;
        int[] p = {0}; skip(text, p); if (!take(text, p, '{')) return out;
        while (true) {
            skip(text, p); if (take(text, p, '}')) return out;
            String key = string(text, p); if (key == null) return out;
            skip(text, p); if (!take(text, p, ':')) return out; skip(text, p);
            String value = peek(text, p) == '"' ? string(text, p) : token(text, p);
            if (value == null) return out; out.put(key, value);
            skip(text, p); if (take(text, p, '}')) return out; if (!take(text, p, ',')) return out;
        }
    }
    static String quote(String value) {
        String s = value == null ? "" : value; StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); switch (c) {
            case '"': out.append("\\\""); break; case '\\': out.append("\\\\"); break;
            case '\n': out.append("\\n"); break; case '\r': out.append("\\r"); break;
            case '\t': out.append("\\t"); break; case '\b': out.append("\\b"); break; case '\f': out.append("\\f"); break;
            default: if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c);
        }} return out.append('"').toString();
    }
    private static String string(String s, int[] p) { if (!take(s, p, '"')) return null; StringBuilder out = new StringBuilder();
        while (p[0] < s.length()) { char c = s.charAt(p[0]++); if (c == '"') return out.toString(); if (c != '\\') { out.append(c); continue; }
            if (p[0] >= s.length()) return null; char e = s.charAt(p[0]++); switch (e) { case '"': out.append('"'); break; case '\\': out.append('\\'); break; case '/': out.append('/'); break; case 'b': out.append('\b'); break; case 'f': out.append('\f'); break; case 'n': out.append('\n'); break; case 'r': out.append('\r'); break; case 't': out.append('\t'); break; case 'u': if (p[0] + 4 > s.length()) return null; try { out.append((char)Integer.parseInt(s.substring(p[0], p[0] + 4), 16)); p[0] += 4; } catch (NumberFormatException x) { return null; } break; default: return null; }
        } return null; }
    private static String token(String s, int[] p) { int start = p[0]; while (p[0] < s.length() && ",}".indexOf(s.charAt(p[0])) < 0 && !Character.isWhitespace(s.charAt(p[0]))) p[0]++; return start == p[0] ? null : s.substring(start, p[0]); }
    private static char peek(String s, int[] p) { return p[0] < s.length() ? s.charAt(p[0]) : 0; }
    private static boolean take(String s, int[] p, char expected) { if (peek(s, p) != expected) return false; p[0]++; return true; }
    private static void skip(String s, int[] p) { while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++; }
}
