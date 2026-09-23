# Agent Guide

## Priorities

1. Preserve product behavior unless the task explicitly changes it.
2. Keep changes minimal, scoped, and independently verifiable.
3. Never commit secrets, signing material, user data, logs, caches, or build output.
4. Do not overwrite unrelated working-tree changes.

## Start Here

- Project status and entry points: `README.md`
- Architecture and boundaries: `ARCHITECTURE.md`
- Product and engineering principles: `docs/PROJECT_PRINCIPLES.md`
- Current direction: `docs/ROADMAP.md`
- Coding rules: `docs/engineering/CODING_STANDARDS.md`
- Build and verification: `docs/engineering/TESTING.md`
- Release and branch workflow: `docs/engineering/RELEASE_PROCESS.md`
- Tracked technical debt: `docs/engineering/TECH_DEBT.md`
- Known issues and verified fixes: `docs/troubleshooting/KNOWN_ISSUES.md` and `docs/troubleshooting/SOLUTIONS.md`
- Durable decisions and scoped plans: `docs/decisions/` and `docs/plans/`

Read only the documents relevant to the task. Keep this file as rules and routing, not a project encyclopedia.

## Agent Routing

- `explorer` (Luna/low, read-only): file discovery, call paths, dependencies, quick scans.
- `planner` (Sol/medium, read-only): plans for non-trivial or multi-module changes.
- `implementer` (Luna/medium, workspace-write): bounded implementation and mechanical edits.
- `tester` (Luna/medium, workspace-write): build, test, lint, reproduction, regression checks.
- `reviewer` (Sol/medium, read-only): independent correctness and regression review.
- `docs_keeper` (Luna/low, workspace-write): durable, verified knowledge only.

Use fewer stages for simple, obviously safe changes. For non-trivial work, prefer explore → plan → implement → test → review → necessary documentation. Do not run multiple write agents against the same files concurrently; use separate branches or worktrees when parallel writes are truly necessary.

## Working Rules

- Search with `rg`/`rg --files` first.
- Before editing, inspect `git status` and relevant files.
- Commit and push project work to `develop` by default. Update or push `main` only when the user explicitly requests `main`.
- Follow existing style; avoid drive-by formatting or unrelated refactors.
- Add or update tests when behavior changes.
- After changes, run the narrowest relevant checks, inspect the diff, and report unverified areas.
- Use Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `build:`, `chore:`).
- Record an ADR only for a major, durable technical choice.
- Update long-term documentation only when verified knowledge materially changes.

## Current Commands

- Run JVM unit tests and create a Debug APK with `scripts/build.ps1`.
- Validate release compilation, R8, and resource shrinking with `scripts/build.ps1 -Tasks assembleRelease`.
