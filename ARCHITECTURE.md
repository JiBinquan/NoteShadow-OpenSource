# Architecture

NoteShadow is a single-module native Android application written in Java.

- `MainActivity` owns the meeting, transcript-only safe, course timestamp-note, and file-preview views, plus document import, mode switching, and export actions.
- `AppStorage` persists active course notes, reading state, session snapshots, and transcription checkpoints. Each session snapshot includes its timestamp-note document.
- `BookshelfRepository` stores each imported TXT/EPUB source, parsed text, metadata, chapter offsets, and independent reading position under an app-private UUID directory. `BookshelfActivity` exposes the neutral “进度” workflow for import, selection, and numeric progress changes. The first launch migrates the old single-document state once; the selected document is also mirrored to the legacy `AppStorage` fields so foreground services, course snapshots, and downgrade builds remain compatible.
- `ProjectRepository` and `SessionMetadataRepository` keep project names, record titles, and archive state in recoverable metadata files without moving existing session data.
- `SessionRepository` provides a path-safe view of saved session snapshots and recordings, including project/archive filtering and canonical-transcript search.
- `TrashRepository` moves complete session directories into app-private recoverable storage, restores them without overwriting conflicts, accounts for transcript/recording space, and permanently removes only validated private entries.
- `NoteAttachmentRepository` stores validated images under the owning session's `attachments` directory. `NoteAttachmentProvider` grants the external camera temporary access to one private capture target; Markdown preview resolves only those owned relative paths.
- `FilePathSafety` provides API-23-compatible canonical path and link checks for all session and trash filesystem operations.
- `HistoryActivity` and `SessionDetailActivity` manage projects and records, play private session recordings, export canonical transcripts, create note-package ZIP files containing timestamp notes and their private images, and create complete course-package ZIP files with transcripts, notes, images, recordings, and a non-sensitive manifest. Exporters revalidate private paths and never include disguise/reading text.
- `RecordingService` records foreground AAC/M4A audio and publishes completed recordings.
- `TranscriptionSettingsRepository` persists a stable live-transcription mode shared by the UI and restartable foreground service.
- `RealtimeTranscriptionService`, `LocalSherpaAsr`, and `AudioFileTranscriber` provide offline live and file transcription; live mode can use Qwen alone, Zipformer draft plus Qwen, or the SenseVoice compatibility path.
- `SettingsActivity` exposes neutral transcription, display-timeout, local-storage, and version settings without changing the safe default note page.
- `TextCodec` and `EpubParser` handle TXT/EPUB import.
- `MixedTranscriptPresenter` creates display-only mixed text; canonical transcripts remain the export source.
- `TimestampNoteFormatter` decorates only newly inserted line breaks; programmatic rendering and transcription updates never rewrite the active course note. Starting a new lesson snapshots that note before clearing the active document.
- `/arc [n]` reads only the active course's canonical `realNote` and inserts the latest confirmed transcript lines into the manual note at the command position. It never reads draft or mixed display text and makes no network request.
- `PageNavigationPolicy` separates the note and single/dual transcript views from the in-memory safe-page return target; lifecycle safety resets always discard that target.
- `com.k2fsa.sherpa.onnx` and the ARM64 JNI library form the bundled sherpa-onnx integration.

The offline recording and transcription pipeline has no account, cloud SDK, analytics SDK, or database. Explicit `/lm` commands (with `/ds` as an alias) and the bounded `/lmlbg`…`/lmled` or `/lmcl` modes are the sole model network path. Each request sends its explicit question plus prior successful user/assistant model turns from the same course. `/lmnew` and new lessons invalidate old requests and clear that context. The active context lives in non-backed-up app storage; API keys are encrypted with Android Keystore in non-backed-up app storage. Custom OpenAI-compatible endpoints use configurable model/request parameters but do not execute model tool/function calls. Recordings, transcripts, unrelated note lines, and reading documents are not uploaded.

`LmDiagnosticLog` retains only bounded, non-sensitive model status/category entries in no-backup app storage and mirrors those codes to Android logcat. The settings connection test sends a fixed short prompt with the active interface configuration, without reading the current course note or LM context.
