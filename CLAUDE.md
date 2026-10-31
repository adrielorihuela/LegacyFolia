# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Talking to the user

Reply in Spanish, short and direct, like a person would: no preambles, no padded lists, no restating
what was already said. The user prefers work to be done through subagents.

## What this is

LegacyFolia is a fork of PaperMC/Folia (Minecraft 26.3, Java 25, Gradle, paperweight-patcher). It adds
1.8 PvP, a working locator bar, TOML configuration in `config/` with an optional `server.properties`,
migration from vanilla/Spigot/Paper/Folia, a plugin auto-updater, an interactive EULA and dimension
toggles. Upstream branch followed: `ver/26.3.x`; `paperRef` lives in `gradle.properties`.

**Read `docs/PROGRESS.md` first.** It is the single source of project state: requirements (§1), tags
(§2), incidents (§3), resume point (§4), remaining work in order (§5), user decisions (§6), upstream (§7).
Other docs: `docs/SETTINGS-MAP.md` (every setting and where it came from), `docs/CONFIGURATION.md`,
`docs/PVP-1.8.md`, `docs/PLUGIN-UPDATER.md`, `docs/UPDATING.md`.

## Commands

On Windows, `applyAllPatches` needs long paths:
`GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.longpaths GIT_CONFIG_VALUE_0=true ./gradlew applyAllPatches`

- Apply patches: `./gradlew applyAllPatches` (or `patch.sh`/`patch.bat`)
- Build jar: `./build.sh` → `applyAllPatches` + `build createPaperclipJar`; jar in `folia-server/build/libs/`, named `legacyfolia-…`
- Rebuild patches after editing generated trees: `./rb.sh` (or `./gradlew rebuildAllServerPatches`)
- Compile: `./gradlew :folia-server:compileJava`
- Tests: `./gradlew :folia-server:test` — the task only runs classes matching `**TestSuite`; our tests
  are picked up by `LegacyFoliaTestSuite` (selects package `io.github.adrielorihuela.legacyfolia`), so
  test class names must end in `Test`/`Tests`. Because of the `**TestSuite` include, `--tests` on a
  single class doesn't select it; run one class from the IDE instead.
- Dev server: `./gradlew :folia-server:runDevServer` (runs in `run/`, EULA already accepted there); wait for `Done (`.
- Update from upstream: `update.sh`/`update.bat` (see `docs/UPDATING.md`).

## Patch workflow (the part that bites)

- `folia-server/src/minecraft/java`, `paper-server` and `paper-api` are **generated, git-ignored trees,
  each its own git repo**. Patches in `folia-server/minecraft-patches/features` and
  `folia-server/paper-patches/features` are produced from **their commits**: edit there, commit inside
  that tree, then rebuild patches.
- `applyAllPatches` **resets** those trees — uncommitted edits there are lost. Hence NMS/Paper changes
  must be done serially; work only in `folia-server/src/main` (tracked) can run in parallel (git worktrees,
  each with its own `applyAllPatches`).
- `rebuildAllServerPatches` stages everything in the main repo; commit with `git commit --only <paths>`.
- Keep patches thin hooks; put logic in `folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/`.
- Upstream precedence: when upstream changes the same thing, upstream's behaviour wins and ours goes on
  top (exception: whitelist stays off by default).

## Architecture (`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/`)

- `config/` — the configuration system. Flow: `catalog/` (`SettingsCatalog`, data in
  `resources/legacyfolia/catalog.tsv`: every key, type, default, legacy origin, server.properties name)
  → `ServerConfig` / `forWorld(name)` reading the eight TOML files (defaults in
  `resources/legacyfolia/*.toml`) → `ServerProperties` overlay (on by default: holds only non-defaults
  and wins over TOML; `/legacyfolia properties on|off|status`) → `bridge/EffectiveSettings` → bridges
  that build Bukkit/Spigot/Paper YAML, Configurate nodes and `DedicatedServerProperties` **in memory**,
  so plugins see unchanged APIs. `migration/LegacyImport` imports old server files on first start and backs
  them up to `config/legacy-backup/<date>/`. All TOML writes go through `properties/TomlText`
  (night-config's `save()` destroys comments).
- `combat/` — 1.8 PvP. `Combat.attackDamage` is called from `Player.attack` and `Mob.doHurtTarget`;
  weapon damage is applied at attack time. `WeaponTooltips` rewrites items only on the outgoing
  `ItemStack` codec and strips the injection on inbound creative items.
- `waypoint/` — locator bar via immutable per-player snapshots (region-thread safe).
- `update/` — plugin auto-updater (Hangar, Modrinth, GitHub, LuckPerms).
- `eula/`, `world/` (dimensions, lobby spawn, nether roof), `chunks/` (ChunkScope), `network/`, `command/` (`/legacyfolia`, console/op 4).

## Repo conventions

- Folia commit style: short imperative English title, no prefix, one commit per change. Annotated tag
  per finished phase (`vX.Y.0`), pushed with `main`.
- Only `main` on origin; never force-push.
- Secrets (`forwarding.secret`, `rcon.secret`) never go into `server.properties`.
