package com.noteshadow.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

public final class EpubParser {
    private EpubParser() {}

    public static final class Result {
        public final String text;
        public final int[] chapterStarts;

        Result(String text, List<Integer> starts) {
            this.text = text;
            this.chapterStarts = new int[starts.size()];
            for (int i = 0; i < starts.size(); i++) this.chapterStarts[i] = starts.get(i);
        }
    }

    public static String parse(InputStream input, File cacheDir) throws Exception {
        return parseDetailed(input, cacheDir).text;
    }

    public static Result parseDetailed(InputStream input, File cacheDir) throws Exception {
        File tmp = File.createTempFile("book_", ".epub", cacheDir);
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = input.read(buf)) >= 0) fos.write(buf, 0, n);
        }
        try {
            return parseFileDetailed(tmp);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    static String parseFile(File file) throws Exception {
        return parseFileDetailed(file).text;
    }

    static Result parseFileDetailed(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            String opfPath = findOpf(zip);
            ZipEntry opfEntry = zip.getEntry(opfPath);
            if (opfEntry == null) throw new IllegalArgumentException("EPUB 缺少 OPF");
            Document opf = parseXml(readAll(zip.getInputStream(opfEntry)));

            Map<String, String> idToHref = new HashMap<>();
            NodeList items = opf.getElementsByTagNameNS("*", "item");
            if (items.getLength() == 0) items = opf.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element e = (Element) items.item(i);
                String id = e.getAttribute("id");
                String href = e.getAttribute("href");
                if (!id.isEmpty() && !href.isEmpty()) idToHref.put(id, href);
            }

            String base = "";
            int slash = opfPath.lastIndexOf('/');
            if (slash >= 0) base = opfPath.substring(0, slash + 1);

            StringBuilder out = new StringBuilder();
            List<Integer> chapterStarts = new ArrayList<>();
            NodeList refs = opf.getElementsByTagNameNS("*", "itemref");
            if (refs.getLength() == 0) refs = opf.getElementsByTagName("itemref");
            for (int i = 0; i < refs.getLength(); i++) {
                Element ref = (Element) refs.item(i);
                String href = idToHref.get(ref.getAttribute("idref"));
                if (href == null) continue;
                int fragment = href.indexOf('#');
                if (fragment >= 0) href = href.substring(0, fragment);
                int query = href.indexOf('?');
                if (query >= 0) href = href.substring(0, query);
                String path = normalizePath(base + urlDecode(href));
                ZipEntry entry = zip.getEntry(path);
                if (entry == null) continue;
                String html = TextCodec.decode(readAll(zip.getInputStream(entry)));
                String text = htmlToVisibleText(html);
                if (!text.trim().isEmpty()) {
                    if (out.length() > 0 && !endsWithVisibleNewline(out)) out.append("\\n\\n");
                    chapterStarts.add(out.length());
                    out.append(text);
                }
            }
            String cleaned = cleanup(out.toString());
            if (chapterStarts.isEmpty() && !cleaned.isEmpty()) chapterStarts.add(0);
            return new Result(cleaned, chapterStarts);
        }
    }

    private static boolean endsWithVisibleNewline(StringBuilder b) {
        int n = b.length();
        return n >= 2 && b.charAt(n - 2) == '\\' && b.charAt(n - 1) == 'n';
    }

    private static String findOpf(ZipFile zip) throws Exception {
        ZipEntry container = zip.getEntry("META-INF/container.xml");
        if (container == null) throw new IllegalArgumentException("不是有效 EPUB：缺少 META-INF/container.xml");
        Document doc = parseXml(readAll(zip.getInputStream(container)));
        NodeList roots = doc.getElementsByTagNameNS("*", "rootfile");
        if (roots.getLength() == 0) roots = doc.getElementsByTagName("rootfile");
        if (roots.getLength() == 0) throw new IllegalArgumentException("EPUB container 中没有 rootfile");
        return ((Element) roots.item(0)).getAttribute("full-path");
    }

    private static Document parseXml(byte[] data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Throwable ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Throwable ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Throwable ignored) {}
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(data));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream src = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = src.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String htmlToVisibleText(String html) {
        if (html == null) return "";
        final String NL = "\uE000";
        final String TAB = "\uE001";
        String s = html;
        // Use sentinels so XHTML source-formatting whitespace does not become fake \n markers.
        s = s.replaceAll("(?is)<br\\s*/?>", NL);
        s = s.replaceAll("(?is)<p[^>]*(?:text-indent\\s*:\\s*[^;\"']+|class\\s*=\\s*[\"'][^\"']*(?:indent|text-indent)[^\"']*)[^>]*>", TAB);
        s = s.replaceAll("(?is)<blockquote[^>]*>", TAB);
        s = s.replaceAll("(?is)</(p|div|section|article|header|footer|aside|blockquote|pre|li|h[1-6])\\s*>", NL);
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        s = s.replaceAll("(?is)<[^>]+>", "");
        s = s.replaceAll("(?i)&(?:emsp|ensp|#8195|#x2003);", TAB);
        s = decodeEntities(s);
        // Whitespace used only to pretty-print XHTML is not semantic book layout.
        s = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        s = s.replaceAll("[ \f\u000B]+", " ");
        s = s.replace(NL, "\\n").replace(TAB, "\\t");
        return cleanup(s);
    }

    private static String cleanup(String s) {
        s = s.replace("\u00a0", " ");
        s = s.replaceAll("[ \\f\\u000B]+", " ");
        s = s.replaceAll("(?:\\\\n[ ]*){4,}", "\\\\n\\\\n\\\\n");
        return s.trim();
    }

    private static String decodeEntities(String s) {
        s = s
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&hellip;", "…")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–");
        s = s.replace("&lsquo;", "‘").replace("&rsquo;", "’")
                .replace("&ldquo;", "“").replace("&rdquo;", "”")
                .replace("&middot;", "·").replace("&copy;", "©")
                .replace("&reg;", "®");
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("&#(x?[0-9A-Fa-f]+);");
        java.util.regex.Matcher m = p.matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            try {
                String raw = m.group(1);
                int cp = raw.startsWith("x") || raw.startsWith("X")
                        ? Integer.parseInt(raw.substring(1), 16) : Integer.parseInt(raw, 10);
                m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(cp))));
            } catch (Throwable e) {
                m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String urlDecode(String path) {
        try { return URLDecoder.decode(path.replace("+", "%2B"), "UTF-8"); }
        catch (Exception e) { return path; }
    }

    private static String normalizePath(String path) {
        String[] parts = path.split("/");
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(p);
        }
        return String.join("/", stack);
    }
}
