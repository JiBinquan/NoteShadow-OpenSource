package com.noteshadow.app;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class StorageCategoryUsageTest {
    @Test public void separatesTranscriptNoteRecordingAndImageFiles() throws Exception {
        File root = java.nio.file.Files.createTempDirectory("storage-usage").toFile();
        File session = new File(root, "session");
        File recordings = new File(session, "recordings");
        File attachments = new File(session, "attachments");
        recordings.mkdirs(); attachments.mkdirs();
        write(new File(session, "meeting_note.txt"), "1234");
        write(new File(session, "draft_note.txt"), "12");
        write(new File(session, "timestamp_note.txt"), "123");
        write(new File(session, "fake_note.txt"), "1");
        write(new File(recordings, "meeting.m4a"), "12345");
        write(new File(attachments, "photo.jpg"), "123456");
        write(new File(session, "session.json"), "ignored");

        StorageCategoryUsage usage = StorageCategoryUsage.measure(root);
        assertEquals(6L, usage.transcriptBytes());
        assertEquals(4L, usage.noteBytes());
        assertEquals(5L, usage.recordingBytes());
        assertEquals(6L, usage.imageBytes());
    }

    @Test public void missingRootIsEmptyAndPlusSaturates() {
        StorageCategoryUsage empty = StorageCategoryUsage.measure(null);
        assertEquals(0L, empty.transcriptBytes());
        StorageCategoryUsage sum = new StorageCategoryUsage(Long.MAX_VALUE, 1, 2, 3)
                .plus(new StorageCategoryUsage(5, 6, 7, 8));
        assertEquals(Long.MAX_VALUE, sum.transcriptBytes());
        assertEquals(7L, sum.noteBytes());
        assertEquals(9L, sum.recordingBytes());
        assertEquals(11L, sum.imageBytes());
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
