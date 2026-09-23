# Roadmap

## Current

- Incrementally split the main-screen state coordinators and establish shared visual tokens/components, preserving every safety, privacy, background-work, and persistence invariant in `docs/plans/2026-09-18-architecture-ui-refresh.md`.
- Prepare the `0.5.0` release candidate on `develop`; retain the verified `0.4.1-v3` baseline on `main` until promotion is authorized.
- Validate project-based record management, title/search, reversible archiving, canonical-transcript batch export, and recoverable deletion on the target Huawei tablet.
- Compare Qwen-only live transcription against the current Zipformer-draft/Qwen-formal pipeline before removing the Zipformer fallback.
- Validate the new neutral settings page, persisted mode switching, optional active-work screen timeout, and local storage summary on the target tablet.
- Validate timestamped manual notes, multiline paste, rapid safe-page switching, and uninterrupted background transcription on the target tablet.
- Validate explicit three-page navigation and safe-key behavior across cold start, process recreation, settings transitions, and screen lock.
- Validate trash/restore, text and recording space accounting, conflict handling, and two-step permanent deletion with real tablet data.
- Expand automated regression coverage for transcription, persistence, and import behavior.
- Revalidate history compatibility and the existing critical flows on the target Huawei MatePad before promotion.

Future milestones should describe outcomes rather than speculative implementation details.
