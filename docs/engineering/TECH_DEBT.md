# Technical Debt

## Main screen responsibility concentration

- Impact: state changes in notes, reading, recording, transcription, navigation, and lifecycle recovery can interact through one Activity, increasing regression risk and making isolated tests difficult.
- Evidence: `MainActivity` is about 2,613 lines and coordinates five display modes, persistence/snapshots, book progress, Markdown preview, media, commands, and three work pipelines.
- Retirement condition: the Activity is a lifecycle/action coordinator around cohesive reading, course, command, preview, and work-status components, with characterization tests for their state transitions.

## Repeated programmatic visual styling

- Impact: colors, spacing, typography, buttons, cards, and states drift between the five Activities; broad visual improvements require risky repeated edits.
- Evidence: the app theme currently exposes only basic system colors/accent while Activities repeatedly create and style views in Java.
- Retirement condition: shared resource tokens and lightweight view components cover all repeated visual roles, and screen code supplies content/actions rather than raw styling constants.

The scoped retirement sequence is recorded in `docs/plans/2026-09-18-architecture-ui-refresh.md`.
