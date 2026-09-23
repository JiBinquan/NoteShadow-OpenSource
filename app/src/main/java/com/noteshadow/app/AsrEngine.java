package com.noteshadow.app;

/** Small engine boundary for replacing the system recognizer with sherpa-onnx. */
public interface AsrEngine {
    interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onState(String text);
        /** Completed Zipformer draft. It is retained separately and never becomes the formal note. */
        default void onDraftFinal(String text) {}
        /** Optional non-authoritative refinement. It must never replace a final draft. */
        default void onRefined(String text) {}
        /** The engine worker has fully stopped and no longer owns native resources. */
        default void onStopped(AsrEngine engine) {}
    }

    boolean isAvailable();
    String unavailableReason();
    void start();
    void stop();
}
