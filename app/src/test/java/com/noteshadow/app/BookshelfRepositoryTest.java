package com.noteshadow.app;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

public class BookshelfRepositoryTest {
    @Test public void importsMultipleBooksAndKeepsIndependentProgressAfterRestart() throws Exception {
        File root = Files.createTempDirectory("shelf").toFile();
        BookshelfRepository shelf = new BookshelfRepository(root);
        BookRecord a = shelf.importBook(bytes("甲😀乙"), "同名.txt");
        BookRecord b = shelf.importBook(bytes("第二本正文"), "同名.txt");
        shelf.setPercentage(a.getId(), "50");
        assertEquals(2, shelf.list().size());
        assertEquals(b.getId(), shelf.current().getId());
        BookshelfRepository restarted = new BookshelfRepository(root);
        assertEquals(2, restarted.list().size());
        assertEquals(b.getId(), restarted.current().getId());
        assertEquals(0, restarted.current().getPosition());
        BookRecord restoredA = null;
        for (BookRecord candidate : restarted.list())
            if (candidate.getId().equals(a.getId())) restoredA = candidate;
        assertNotNull(restoredA);
        assertEquals(3, restoredA.getPosition());
        assertEquals("第二本正文", restarted.loadText(b));
    }

    @Test public void unicodeAndChapterMetadataRoundTrip() throws Exception {
        File root = Files.createTempDirectory("shelf").toFile();
        BookshelfRepository shelf = new BookshelfRepository(root);
        BookRecord b = shelf.importBook(bytes("😀中文"), "阅读😀.txt");
        assertArrayEquals(new int[]{0}, b.getChapterStarts());
        assertEquals(0, shelf.chapterForPosition(b, 1));
        assertEquals("😀中文", shelf.loadText(b));
    }

    @Test public void rejectsBadImportWithoutAddingEntry() throws Exception {
        File root = Files.createTempDirectory("shelf").toFile();
        BookshelfRepository shelf = new BookshelfRepository(root);
        try { shelf.importBook(bytes(""), "empty.txt"); fail(); } catch (IllegalArgumentException expected) { }
        try { shelf.importBook(bytes("x"), "../escape.txt"); fail(); } catch (IllegalArgumentException expected) { }
        try { shelf.importBook(bytes("x"), "book.pdf"); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(shelf.list().isEmpty());
        assertFalse(new File(root, "bookshelf-index.json").exists());
    }

    @Test public void legacyMigrationIsIdempotentAndLeavesLegacyData() throws Exception {
        File root = Files.createTempDirectory("shelf").toFile();
        BookshelfRepository shelf = new BookshelfRepository(root);
        BookRecord first = shelf.migrateLegacy("旧书.txt", "旧内容😀", 2, 0, new int[]{0, 2}, 1);
        assertNotNull(first);
        assertEquals(1, shelf.list().size());
        assertArrayEquals(new int[]{0, 2}, first.getChapterStarts());
        assertEquals(1, first.getLastReadAt());
        BookRecord second = shelf.migrateLegacy("旧书.txt", "别的内容", 0, 0, 2);
        assertEquals(first.getId(), second.getId());
        assertEquals("旧内容😀", shelf.loadText(second));
        assertTrue(new File(new File(new File(root, "books"), first.getId()), "source.txt").isFile());
        BookRecord legacyEpub = new BookshelfRepository(Files.createTempDirectory("legacy-epub").toFile())
                .migrateLegacy("旧书.epub", "已提取正文", 0, 0, new int[]{0}, 3);
        assertEquals("旧书", legacyEpub.getTitle());
    }

    @Test public void resumesInterruptedLegacyMigrationWithoutDuplicate() throws Exception {
        File root = Files.createTempDirectory("shelf-interrupted-migration").toFile();
        BookshelfRepository shelf = new BookshelfRepository(root);
        BookRecord halfMigrated = shelf.importBook(bytes("旧内容😀"), "旧书.txt");
        assertTrue(new File(root, "bookshelf-index.json").delete());

        BookRecord recovered = new BookshelfRepository(root).migrateLegacy(
                "旧书.txt", "旧内容😀", 2, 1, new int[]{0, 2}, 7);

        assertEquals(halfMigrated.getId(), recovered.getId());
        assertEquals(1, shelf.list().size());
        assertEquals(2, recovered.getPosition());
        assertEquals(1, recovered.getChapter());
        assertEquals(7, recovered.getLastReadAt());
        assertArrayEquals(new int[]{0, 2}, recovered.getChapterStarts());
        assertEquals(recovered.getId(), new BookshelfRepository(root).current().getId());
    }

    @Test public void fileMigrationAlsoResumesWithoutDuplicate() throws Exception {
        File root = Files.createTempDirectory("shelf-file-migration").toFile();
        File legacy = new File(root, "legacy.txt");
        Files.write(legacy.toPath(), "文件旧内容".getBytes(StandardCharsets.UTF_8));
        BookshelfRepository shelf = new BookshelfRepository(root);
        BookRecord halfMigrated = shelf.importStagedSource(legacy, "legacy.txt");

        BookRecord recovered = new BookshelfRepository(root).migrateLegacy(
                legacy, "legacy.txt", 2, 0, 9);

        assertEquals(halfMigrated.getId(), recovered.getId());
        assertEquals(1, shelf.list().size());
        assertEquals(2, recovered.getPosition());
        assertEquals(9, recovered.getLastReadAt());
    }

    @Test public void percentageAndProgressClampToCodePoints() throws Exception {
        File root = Files.createTempDirectory("shelf").toFile();
        BookshelfRepository shelf = new BookshelfRepository(root);
        BookRecord b = shelf.importBook(bytes("甲😀乙"), "book.txt");
        BookRecord updated = shelf.setPercentage(b.getId(), "50");
        assertEquals(3, updated.getPosition());
        assertEquals(1, shelf.updateProgress(b.getId(), 2, 99).getPosition());
    }

    @Test public void selectsStoredChapterStart() throws Exception {
        File root = Files.createTempDirectory("shelf-chapters").toFile();
        BookshelfRepository shelf = new BookshelfRepository(root);
        BookRecord book = shelf.migrateLegacy("章节书.txt", "第一章正文\n第二章正文", 0, 0,
                new int[]{0, 6}, 1);

        BookRecord selected = shelf.selectChapter(book.getId(), 1);

        assertEquals(6, selected.getPosition());
        assertEquals(1, selected.getChapter());
        try { shelf.selectChapter(book.getId(), 2); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
