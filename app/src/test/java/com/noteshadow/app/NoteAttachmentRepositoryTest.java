package com.noteshadow.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class NoteAttachmentRepositoryTest {
    @Test public void acceptsOnlySingleRelativeAttachmentName() {
        assertTrue(NoteAttachmentRepository.isSafeAttachmentPath("attachments/photo.jpg"));
        assertFalse(NoteAttachmentRepository.isSafeAttachmentPath("/attachments/photo.jpg"));
        assertFalse(NoteAttachmentRepository.isSafeAttachmentPath("attachments/../secret.jpg"));
        assertFalse(NoteAttachmentRepository.isSafeAttachmentPath("attachments/a/b.jpg"));
        assertFalse(NoteAttachmentRepository.isSafeAttachmentPath("attachments/photo.jpg?x=1"));
        assertFalse(NoteAttachmentRepository.isSafeAttachmentPath("attachments/C:\\x.jpg"));
    }
    @Test public void acceptsSafeSessionIdsAndChoosesImageExtension() {
        assertTrue(NoteAttachmentRepository.isSafeSessionId("20260916_120000_001"));
        assertFalse(NoteAttachmentRepository.isSafeSessionId("../outside"));
        assertEquals(".png", NoteAttachmentRepository.extensionFor("image/png"));
        assertEquals(".webp", NoteAttachmentRepository.extensionFor("photo.webp"));
        assertEquals(".jpg", NoteAttachmentRepository.extensionFor("image/jpeg"));
    }
}
