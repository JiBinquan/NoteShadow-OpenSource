# Release Process

1. Branch `feature/*` or `fix/*` from `develop`.
2. Implement the scoped change and run relevant checks.
3. Obtain independent review; resolve any `CHANGES_REQUIRED` result.
4. Merge into `develop` and run integration verification.
5. Promote the verified `develop` state to `main`.
6. For each release, use a three-part `versionName` such as `0.5.0`, increase Android `versionCode` above every prior release, and keep the existing application ID and signing certificate for in-place updates. Then create the matching tag, such as `v0.5.0`. Historical `-v3` tags are unchanged.

Commits should be small, focused, and use Conventional Commits. Do not include unrelated changes, secrets, user data, logs, caches, or build artifacts. Configure GitHub branch protection after the remote repository exists.
