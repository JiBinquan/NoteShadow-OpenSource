package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class SessionSnapshotCoordinatorTest {
    @Test public void currentSessionWritesButStaleSessionIsRejected() {
        SessionSnapshotCoordinator coordinator = new SessionSnapshotCoordinator();
        AtomicReference<String> current = new AtomicReference<>("course-old");
        AtomicInteger writes = new AtomicInteger();

        assertTrue(coordinator.runIfCurrent("course-old", current::get, writes::incrementAndGet));
        coordinator.callExclusive(() -> {
            current.set("course-new");
            return null;
        });
        assertFalse(coordinator.runIfCurrent("course-old", current::get, writes::incrementAndGet));
        assertEquals(1, writes.get());
    }
}
