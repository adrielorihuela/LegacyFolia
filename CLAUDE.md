# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

LegacyFolia is a fork of PaperMC/Folia (Minecraft 26.3, Java 25, Gradle 9.8.1,
paperweight-patcher 2.0.0-beta.24) that adds 1.8 PvP, a working locator bar, TOML configuration in
`config/`, migration from other servers, a plugin auto-updater and an interactive EULA on top of
Folia's regionised threading. Upstream branch followed: `ver/26.3.x`; `paperRef` is in `gradle.properties`.

## How to work in this repo

You are the orchestrator. Your job is to understand, plan, delegate and verify; not to do what a subagent does cheaper.

1. Before touching code, delegate exploration to `explorer` (Haiku). Ask for paths and a summary, not full file contents.
2. Write a spec of 5 to 15 lines: goal, files, acceptance criteria, verification command. Delegate execution to `implementer` (Sonnet).
3. Hand the diff to `reviewer` (Opus High). If REJECTED, send the list back to the implementer. At most two rounds.
4. If it still fails after two rounds, ask before raising effort to xhigh or switching to Fable; explain why in one line.
5. Do yourself only: architecture decisions, changes that cross modules, and the final synthesis.
6. Never turn on ultracode or workflows unless asked; when they are asked for, subagents run on Haiku.
7. Suggest `/clear` when the next task is unrelated to the current one.
8. At the start of the first session each week, run the check in `.claude/lean-check.md` (one skill load; it updates only if the synced preset moved).
9. `/init` in this project is the LeanFlow version (`.claude/skills/init/`); use it to refresh this file.

Verification commands for this repo:

- Compile: `./gradlew :folia-server:compileJava`
- Tests: `./gradlew :folia-server:test` (our suite is `LegacyFoliaTestSuite`)
- Runtime: `./gradlew :folia-server:runDevServer` until `Done (`
- After NMS or Paper edits, a fresh `applyAllPatches` must apply cleanly

### Talking to the user

Reply in Spanish, short and direct, like a person would: no preambles, no padded lists, no restating
what was already said. The user prefers work to be done through subagents.

**Read `docs/PROGRESS.md` first.** It is the single source of project state: requirements (§1), tags
(§2), incidents (§3), resume point (§4), remaining work in order (§5), user decisions (§6), upstream
(§7), differences from Folia and Paper (§8).

## Commands

On Windows, `applyAllPatches` needs long paths:
`GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.longpaths GIT_CONFIG_VALUE_0=true ./gradlew applyAllPatches`

- Apply patches: `./gradlew applyAllPatches` (or `patch.sh`/`patch.bat`)
- Build jar: `./build.sh`/`build.bat` → `applyAllPatches` + `build createPaperclipJar`; the runnable jar
  is `folia-server/build/libs/legacyfolia-paperclip-<version>.jar` (version from `settings.gradle.kts`:
  `26.3.local-SNAPSHOT` without `BUILD_NUMBER`)
- Rebuild patches after editing generated trees: `./gradlew rebuildAllServerPatches` (`rb.sh`/`rb.bat`
  run the four `rebuild*Patches` tasks, API ones included)
- Compile: `./gradlew :folia-server:compileJava`
- Tests: `./gradlew :folia-server:test` — the task only runs classes matching `**TestSuite`; our tests
  are picked up by `LegacyFoliaTestSuite` (selects package `io.github.adrielorihuela.legacyfolia`), so
  test class names must end in `Test`/`Tests`. Because of the `**TestSuite` include, `--tests` on a
  single class doesn't select it; run one class from the IDE instead.
- Dev server: `./gradlew :folia-server:runDevServer` (runs in `run/`, `eula = true` already set there); wait for `Done (`.
- Update from upstream: `update.sh`/`update.bat` (fetch + merge `upstream/ver/26.3.x` + `applyAllPatches`;
  see `docs/UPDATING.md`).

## Hard constraints

- `folia-server/src/minecraft/java`, `paper-server` and `paper-api` are generated git repos; patches come from their commits and `applyAllPatches` resets them, so NMS/Paper work is serial. Details: `.claude/rules/patches.md`.
- Logic goes in `folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/`; patches stay thin hooks.
- Upstream's behaviour wins where it overlaps ours, ours goes on top (exception: whitelist off by default).
- With `combat.enabled = false` every combat hook must fall through to pure 26.3 behaviour.
- Never touch another region's player directly: queue work for the owner's tick.
- All TOML writes go through `properties/TomlText`; night-config's `save()` destroys comments.

## Repo conventions

- Folia commit style: short imperative English title, no prefix, one commit per change. Annotated tag per finished phase (`vX.Y.0`), pushed with `main`.
- Only `main` on origin; never force-push.
- Secrets (`forwarding.secret`, `rcon.secret`, `github.token`) never go into `server.properties`.

## Where things live

- `docs/ARCHITECTURE.md`: how it differs from Folia and Paper, package by package architecture
- `.claude/rules/patches.md`: patch workflow and patch map, loads on patch paths
- `.claude/skills/release/`: tagging and publishing pre-releases
- `.claude/lean-check.md`: weekly LeanFlow self-check
- Other docs: `docs/SETTINGS-MAP.md`, `docs/CONFIGURATION.md`, `docs/PVP-1.8.md`, `docs/PLUGIN-UPDATER.md`, `docs/UPDATING.md`, `docs/TESTING.md`
