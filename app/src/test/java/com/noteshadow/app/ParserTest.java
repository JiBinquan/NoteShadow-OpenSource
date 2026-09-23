package com.noteshadow.app;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParserTest {
    @Test public void txtControlsAreVisibleAndGb18030Decodes() {
        assertEquals("甲\\n乙\\t丙", TextCodec.visibleControls("甲\r\n乙\t丙"));
        byte[] gb = "中文测试".getBytes(Charset.forName("GB18030"));
        assertEquals("中文测试", TextCodec.decode(gb));
    }

    @Test public void epubUsesSpineOrderAndVisibleLayout() throws Exception {
        File epub = File.createTempFile("noteshadow_", ".epub");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(epub))) {
            put(out, "META-INF/container.xml",
                    "<?xml version=\"1.0\"?><container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>");
            put(out, "OPS/book.opf",
                    "<?xml version=\"1.0\"?><package xmlns=\"http://www.idpf.org/2007/opf\"><manifest>"
                            + "<item id=\"two\" href=\"chap2.xhtml\"/><item id=\"one\" href=\"chap+1.xhtml#top\"/>"
                            + "</manifest><spine><itemref idref=\"one\"/><itemref idref=\"two\"/></spine></package>");
            put(out, "OPS/chap+1.xhtml",
                    "<html><body><h1>第一章</h1><p>甲&amp;乙<br/>下一行</p></body></html>");
            put(out, "OPS/chap2.xhtml",
                    "<html><body><p style=\"text-indent:2em\">第二章&emsp;乙</p></body></html>");
        }
        try {
            EpubParser.Result result = EpubParser.parseFileDetailed(epub);
            assertTrue(result.text, result.text.indexOf("第一章") < result.text.indexOf("第二章"));
            assertTrue(result.text, result.text.contains("甲&乙\\n下一行"));
            assertTrue(result.text, result.text.contains("\\t第二章\\t乙"));
            assertEquals(2, result.chapterStarts.length);
            assertEquals(0, result.chapterStarts[0]);
            assertTrue(result.chapterStarts[1] > result.chapterStarts[0]);
        } finally {
            epub.delete();
        }
    }

    private static void put(ZipOutputStream out, String name, String text) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
