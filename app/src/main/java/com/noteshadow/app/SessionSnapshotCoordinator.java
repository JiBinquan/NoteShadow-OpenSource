package com.noteshadow.app;

/** Serializes course rotation with delayed snapshots and rejects stale work. */
final class SessionSnapshotCoordinator {
    interface Action<T> { T run(); }
    interface CurrentSession { String get(); }
    interface Snapshot { void run(); }

    synchronized <T> T callExclusive(Action<T> action) {
        return action.run();
    }

    synchronized boolean runIfCurrent(String expectedSessionId,
                                      CurrentSession currentSessionId,
                                      Snapshot snapshot) {
        if (expectedSessionId == null || !expectedSessionId.equals(currentSessionId.get())) {
            return false;
        }
        snapshot.run();
        return true;
    }
}
