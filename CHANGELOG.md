# Changelog

All notable changes to this project will be documented here. New releases use three-part Semantic Versioning; historical v3 tags retain their original names.

## Unreleased

## 0.5.0 - 2026-09-23 (preview)

- Published signed `full` and `lite` preview APKs with the existing v3 certificate. The full package bundles Zipformer, SenseVoice, and Silero VAD with license notices; the lite package omits model weights. Device regression testing is pending.
- Standardized the Android version name to three parts and increased its version code.

- Replaced the single reading-document slot with a local TXT/EPUB bookshelf, per-document progress and chapter state, current-document selection, safe one-time migration, and a neutral “进度” entry that keeps the original front page free of persistent entertainment labels.
- Added direct EPUB chapter selection with a short chapter-opening preview while retaining percentage and character-offset controls.
- Renamed the existing Markdown-and-image ZIP to “note package” and added complete course-package export for single or selected records, including formal and draft transcripts, timestamped notes, original images, recordings, and a non-sensitive manifest while excluding disguise/reading and model data.
- Added an edit/preview Markdown mode to course notes with headings, lists, emphasis, quotes, safe web links, inline code, and fenced code blocks while preserving the timestamped source text.
- Added quick camera capture and image import to course notes, private per-course image storage, local Markdown image preview, image-aware storage reporting, and ZIP course-package export containing the note and its images.
- Fixed camera and image-picker returns so they resume the course-note page within the same Activity instance while ordinary background, lock-screen, and process-recreation recovery still opens the transcript-only safe page.
- Fixed later note photos remaining as italic placeholders after earlier full-size previews exhausted the memory budget; the preview now shares a fixed pixel budget across all displayed thumbnails without altering originals.
- Added `/novst` and context-sensitive `///` commands for temporary novel substitution inside timestamped course notes, with separate hardware/IME handling that redirects ASCII letters only and preserves existing meeting-page fake typing.
- Expanded course-note Markdown preview with strikethrough, task and nested lists, horizontal rules, escaped punctuation, fenced-code language labels, and timestamp-aware readable tables.
- Replaced text-art Markdown tables in course-note preview with native bordered tables featuring styled headers, cell alignment, wrapped long text, horizontal scrolling, timestamp columns, and bounded large-table rendering.
- Added a read-only storage locations section to Settings showing the current default locations for recordings, transcripts, notes, and images, with category usage plus shortcuts to browse public files and course records; no files or save paths are changed.

## 0.4.1-v3 - 2026-09-16

- Separated explicit page navigation from the safe-page shortcut so meeting, manual-note, and transcript pages remain directly accessible while restart/background recovery cannot reveal a previous work page.
- Added a third, independent note page that automatically prefixes every user-created new line with a timestamp while live transcription continues in the background.
- Renamed the transcript-only safe page to "实时转写" and kept it as the cold-launch and lock-screen recovery page.
- Added a neutral settings page for choosing Qwen-only, Zipformer-draft plus Qwen, or SenseVoice compatibility transcription.
- Added an optional keep-screen-on preference for active recording/transcription, plus local text/recording space and version information.
- Made the restartable transcription service persist its selected mode and added a Qwen-only path that keeps the existing 3–7 second, 0.8-second-overlap segmentation without loading Zipformer.
- Added course-scoped `/lm`, multi-line and continuous model conversations with configurable OpenAI-compatible endpoints and privacy-safe diagnostics.
- Added `/arc n` for inserting the latest formal transcript lines into the timestamp note without uploading transcript or audio.
- Preserved delayed model replies across page edits, cleared notes reliably for a new lesson, and synchronized model context when recording starts a course.

## 0.4.0-v3 - 2026-09-10

- Changed live Qwen transcription from fixed five-second windows to dynamic three-to-seven-second windows with 0.8 seconds of overlap; VAD selects boundaries without filtering captured audio.
- Added a lightweight reading-progress menu for selecting TXT/EPUB files, showing the current percentage and character offset, and setting a new percentage.
- Added a read-only record history under a neutral default project, with canonical transcript previews, session details, private recording playback, and single-record export.
- Kept the display awake while recording or transcription is active, and made the transcript-only note page the safe default after launch, recreation, or background recovery.
- Added local project creation, project renaming and switching, record titles, canonical-transcript search, reversible archiving, and selected-record ZIP export.
- Added an app-private recycle bin for records, restoration to the original project/title/archive state, text and recording space accounting, and two-step confirmed permanent deletion.
- Fixed reading progress so novel text emitted by the mixed meeting page advances and persists the displayed position without shifting previously generated slices.

## 0.3.20-v3 - 2026-09-06

- Imported the v3 `0.3.19-v3` development source on top of the formal v2 baseline.
- Kept v3 signing credentials and signing material outside version control.
- Restored timestamps in the mixed transcript view and timestamped novel lines one second after the preceding transcription.
- Kept Alt+Enter mode switching and added explicit transcript editing and new-lesson controls.
- Preserved canonical timestamps on the temporary note display so both primary pages keep the same line layout.
- Advanced novel insertion only for transcription lines received while the mixed disguise page is visibly in the foreground.

## 0.3.19-v3 - 2026-09-05

- Added an independently signed `com.noteshadow.app3` build that can coexist with v2.
- Made boss-key switching show a precomputed clean transcript immediately and persist input asynchronously.
- Added display-only novel mixing while keeping boss-mode and exported transcript content clean.

## 0.3.17 - 2026-09-05

- Preserved the stable v2 application as the formal `main` baseline.
- Kept the meeting transcript free of automatic novel-content insertion.
- Retained the original v2 application ID for compatible upgrades.
