# Architecture Refactor and UI Refresh Plan

## Objective

Reduce the risk and cost of changing NoteShadow while giving the tablet UI a consistent, restrained, professional appearance. Every implementation batch must preserve current data, privacy, safe-page behavior, background recording/transcription, keyboard shortcuts, and downgrade/recovery paths.

This is an incremental plan, not a rewrite. `main` remains the verified `d80823a` baseline while work proceeds on `develop`.

## Current evidence

- `MainActivity` is about 2,613 lines and currently coordinates page navigation, five display modes, course persistence, reading progress, mixed transcript rendering, Markdown preview, note media, `/arc` and `/lm` commands, recording, live transcription, file transcription, and lifecycle safety.
- Recording, live transcription, storage repositories, parsers, command parsers, navigation policy, and most export logic already have useful boundaries and should not be rewritten.
- All five Activities build their views programmatically. The resource theme exposes almost no reusable color, typography, spacing, shape, or state tokens, so visual rules are repeated as hard-coded values.
- The most regression-sensitive behavior is state coordination rather than drawing: background/lock recovery, the boss key, asynchronous note/model writes, course snapshots, current-book switching, and service-owned work.

## Non-negotiable invariants

1. Cold launch, process recreation, screen lock, and ordinary background recovery open the transcript-only safe page. A note media picker may restore the note page only through its existing one-shot policy.
2. The original front page and persistent navigation remain neutral; no permanent entertainment wording or reading content is introduced.
3. Recording, live transcription, and file transcription survive Activity recreation according to their current service/application ownership.
4. Canonical transcripts, draft text, manual notes, mixed display text, reading text, and model context remain separate data sources.
5. Starting a new lesson snapshots the old audio/text/note state, clears only the intended active-course fields, and resets model and temporary novel-input state.
6. `/lm` uploads only explicit model conversation content; `/arc` remains local; reading text, recordings, and unrelated notes are never added to requests.
7. Each bookshelf entry retains independent progress. Switching books stops note novel substitution and cannot let old mixed ranges advance the new book.
8. Existing session, attachment, recycle-bin, export, legacy-book mirror, and signing compatibility remain intact.
9. Hardware boss-key and `Alt+Enter` flows remain fast and keyboard-accessible on the target Huawei tablet.

## Target boundaries

`MainActivity` should become a thin Android coordinator that owns lifecycle and routes user actions. Extraction should stop when these cohesive boundaries exist:

- `MainScreenState`: immutable or explicitly copied display state used to render title, status, button states, current page, and active-work indicators.
- `ReadingSessionController`: current book loading/switching, character emission, progress debounce/flush, chapter lookup, mixed-reading cursor reset, and legacy mirror synchronization.
- `CourseSessionController`: active fake/real/manual documents, cursor/scroll state, immediate save, snapshot, and new-lesson transition.
- `NoteCommandController`: `/arc`, `/lm`, block/continuous model modes, cancellation generations, and reply insertion through narrow callbacks.
- `NotePreviewView`: Markdown/table/image rendering and preview-generation cancellation, with no course mutation.
- `WorkStatusController`: recording/live-ASR/file-ASR start-stop intents and state reduction from callbacks. Services remain the owners of long-running work.
- `MainPageView`: creates and updates the editor, toolbar, preview container, and page-specific controls without reading repositories directly.

These are responsibility boundaries, not mandatory interfaces or packages. Do not add abstraction unless the current batch needs it and tests can exercise it.

## Visual direction

- Quiet, neutral, work-oriented presentation suitable for the original screen and classroom use.
- A small resource-backed token set for background/surface/text/muted/accent/danger colors, typography roles, 4/8/12/16/24 dp spacing, minimum 48 dp touch targets, corner radii, dividers, and selected/disabled states.
- Reusable lightweight view builders for page headers, section headings, primary/secondary/text buttons, cards, hints, empty states, and destructive confirmations.
- No new UI framework or third-party dependency in the first pass. Continue supporting API 23 and the existing Java toolchain.
- Preserve source text and interaction behavior; visual components must not become new persistence or business-logic owners.

## Delivery sequence

### Batch 0 — Characterization guardrails

- Add focused tests for lifecycle-safe page reduction, new-lesson state transition, reading switch/reset behavior, and command-result application where logic can be made Android-independent.
- Record a manual Huawei smoke checklist covering recording, live transcription, boss key, note media return, `/arc`, `/lm`, `/novst`, new lesson, bookshelf switching, history/export, and process restart.
- No visible UI change.

Acceptance: the current APK behavior is reproducible before extraction; every later batch runs the same checks.

### Batch 1 — Visual foundation and low-risk screens

- Add resource-backed design tokens and small reusable view factories.
- Apply them first to Bookshelf, Settings, History, and Session Detail.
- Keep labels, actions, ordering, storage paths, and dialogs behaviorally identical.
- Validate tablet width, long Chinese text, large-font scaling, empty/loading/error states, and keyboard focus.

Acceptance: the four secondary screens look consistent; their repositories and workflows have no behavioral diff.

### Batch 2 — Extract Markdown preview rendering

- Move Markdown text spans, native tables, safe links, image sizing/loading, and render-generation cancellation from `MainActivity` into `NotePreviewView` or an equivalent cohesive component.
- Keep edit/preview source, image ownership validation, table limits, and delayed model-reply behavior unchanged.

Acceptance: existing Markdown/image tests pass and repeated edit/preview switching on the tablet shows no stale images or source mutation.

### Batch 3 — Extract reading-session coordination

- Move bookshelf startup, current-book reload, legacy mirror, progress debounce/flush, chapter resolution, character emission, and book-end transitions behind `ReadingSessionController`.
- Represent book switches as one atomic state transition before updating the page.
- Keep text in memory only where the current behavior already requires it.

Acceptance: two TXT/EPUB books retain independent progress across switching/restart; chapter selection, meeting mixing, `/novst`, snapshots, and downgrade mirror remain correct.

### Batch 4 — Extract course state and note commands

- Introduce one course-state owner for document text, selection, scroll, snapshot revision, and new-lesson reset.
- Route `/arc`, `/lm`, `/lmlbg`…`/lmled`, `/lmcl`, `/lmnew`, `/novst`, and `///` through an explicit dispatcher/result model.
- Keep Android `Editable` manipulation at the view boundary and model/network work outside it.

Acceptance: asynchronous old-course replies cannot mutate a new course; current privacy and diagnostic behavior remains unchanged.

### Batch 5 — Extract work status and simplify the main shell

- Reduce recording/transcription broadcasts and file-transcription callbacks into a testable status model.
- Keep services as long-running owners; the Activity sends intents, observes events, updates keep-screen-on state, and renders status.
- Replace broad `refreshStatus()` branching with rendering from `MainScreenState`.

Acceptance: Activity recreation never stops active work, stopping the last task restores normal screen timeout, and status/buttons remain accurate.

### Batch 6 — Main-screen visual refresh

- Apply the same tokens and components to the transcript-only, meeting, and manual-note pages.
- Preserve one-frame boss-key rendering and safe-page defaults; avoid animations or content previews that could reveal protected content during a switch.
- Optimize the tablet toolbar and page selector for touch and hardware keyboard use.

Acceptance: all critical flows pass on the target tablet and no frame exposes meeting/reading content on a safety transition.

## Verification and rollback

For every batch:

1. Start from `develop`; make one focused Conventional Commit.
2. Run the narrow unit tests, then all JVM tests and Debug APK build.
3. Run Release/R8 validation before device installation.
4. Inspect the complete diff and obtain independent review for state/lifecycle changes.
5. Sign with the existing v3 certificate, verify its digest, back up the installed APK when the batch changes runtime behavior, install with `adb install -r`, and run the batch smoke checks.
6. Publish a GitHub pre-release for each tablet-test candidate. Promote to `main` only after explicit user approval.

If a batch regresses a critical flow, install its predecessor pre-release and revert only that focused commit; do not reset or reconstruct unrelated work.

## First implementation batch

Begin with Batch 0 and Batch 1 together only where changes are mechanical: add the guardrail checklist/tests, introduce the visual token/component layer, and restyle Bookshelf first. Validate it before applying the same components to Settings, History, and Session Detail. Do not touch the main recording/transcription screen in that first implementation batch.
