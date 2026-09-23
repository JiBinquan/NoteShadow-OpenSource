# Open source readiness: 0.5.0 source-only snapshot

Date: 2026-09-23. Working branch: `develop`. This is a release audit record, not an APK announcement.

## Decisions

- Keep the existing development repository and its Git history private. Its history contains bundled speech-model weights and vocabularies. Their exact license chain and notice obligations require verification before that history could be public.
- Prepare a fresh-history public source repository without Zipformer, SenseVoice, Silero VAD weights, vocabularies, or self-test audio. Do not publish an APK containing those assets.
- Project-owned code and documentation use Apache-2.0. The README banner, demonstration screenshots, and launcher icon use CC BY 4.0 with attribution; see `ASSETS_LICENSE.md`.
- Use three-part SemVer for new tags, starting with `0.5.0`; preserve historical tag names. Keep the Android application ID and signing identity stable. The release `versionCode` is 5.
- Use GitHub private vulnerability reporting once the public repository exists and its setting is enabled.

## Public-content and privacy audit

- Targeted scans found no tracked signing key, API key, user database, log, or APK in the current tree. The old private history is unsuitable for publication because it contains model assets.
- `docs/design/prototypes/` and a local classroom-note file are untracked and are not part of the public export. `BUILD_STATUS.md` is an internal historical test record and is also excluded.
- The source snapshot copies only tracked working-tree files, then excludes every tracked speech-model asset and self-test audio. This preserves the checked-out sherpa-onnx JNI libraries while discarding old Git history.
- App cloud backup is disabled and Android 12+ extraction rules exclude app data. Public `Music/NoteShadow` recording copies and manual exports persist independently of in-app deletion; the UI and `PRIVACY.md` now explain this.
- Logs no longer print local recording paths or self-test transcript content in the audited paths.

## Verification and remaining release gates

- After the model-loading change, `scripts/build.ps1 -Tasks testDebugUnitTest,assembleRelease` passed on 2026-09-23 (unit tests, Java compilation, lint, R8, and resource shrinking). The source-only snapshot still needs the same check.
- The public repository `JiBinquan/NoteShadow-OpenSource` was created with one new root commit and no weights, vocabularies, recordings, signing materials, or build output. Targeted source and APK scans found no bundled models or test audio. The snapshot passed unit tests, Debug build, and Release build on 2026-09-23.
- GitHub private vulnerability reporting is enabled for the public repository; `SECURITY.md` describes the active channel.
- Device testing of recording, model installation, transcription, storage behavior, and an update signed with the existing certificate remain separate from source publication. No signed APK is published in this source-only release.
