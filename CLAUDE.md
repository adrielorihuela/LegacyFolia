# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Talking to the user

Reply in Spanish, short and direct, like a person would: no preambles, no padded lists, no restating
what was already said. The user prefers work to be done through subagents.

**`/init` means the whole documentation, not only this file.** When the user says `/init`, update
`README.md`, every `docs/*.md` (including `docs/PROGRESS.md`) and `CLAUDE.md` against the current
code: read the code, patches, TOML defaults and tests rather than the old docs, and cover how
LegacyFolia differs from Paper and Folia. Keep each doc's language (`PROGRESS.md` and
`SETTINGS-MAP.md` are Spanish, the rest English) and commit one doc per commit.

## What this is

LegacyFolia is a fork of PaperMC/Folia (Minecraft 26.3, Java 25, Gradle 9.8.1,
paperweight-patcher 2.0.0-beta.24). Upstream branch followed: `ver/26.3.x`; `paperRef` lives in
`gradle.properties`. Folia's regionised threading is kept as is; on top of it the fork adds 1.8 PvP,
a working locator bar, TOML configuration in `config/` with an optional `server.properties`,
migration from vanilla/Spigot/Paper/Folia, a plugin auto-updater, an interactive EULA, dimension
toggles, a lobby spawn, a Nether roof switch and adaptive view distance (ChunkScope's code).

**Read `docs/PROGRESS.md` first.** It is the single source of project state: requirements (§1), tags
(§2), incidents (§3), resume point (§4), remaining work in order (§5), user decisions (§6), upstream
(§7), differences from Folia and Paper (§8).
Other docs: `docs/SETTINGS-MAP.md` (every setting and where it came from), `docs/CONFIGURATION.md`,
`docs/PVP-1.8.md`, `docs/PLUGIN-UPDATER.md`, `docs/UPDATING.md`, `docs/TESTING.md` (manual checklist for a
release candidate).

## How it differs from Folia and Paper

- No `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml`, `paper-world-defaults.yml`,
  `paper-world.yml` or `eula.txt` on disk: eight TOML files in `config/` (`server`, `world`,
  `network`, `performance`, `gameplay`, `combat`, `plugins`, `worlds` for per-world overrides) are
  the source, and the YAML/Configurate/`DedicatedServerProperties` objects plugins read are built in
  memory. `/paper reload`, `/spigot reload` and Bukkit's reload re-read the TOML files.
- `config/server.properties` is optional (mode on by default): it holds only non-default values and
  wins over the TOML files. Whitelist, bans, ops and secrets live in `config/`, the user cache in
  `cache/`; the root keeps only the jar and the generated directories.
- Defaults that differ: `white-list = false` (26.3 has `true`), `spawn-protection-radius = 0`
  (vanilla 16), RCON on, bound to `127.0.0.1`, with a generated `config/rcon.secret`, and 1.8 combat
  on. Velocity's secret comes from `config/forwarding.secret`.
- API-visible changes: `getAllowNether/End` answer from `[dimensions]`; with 1.8 combat on, the NMS
  `Player.canCriticalAttack`, which Paper made public for its API in `bc68acd74`, follows the
  1.8 rule; weapon tooltips show 1.8 damage on the wire only.
- Upstream patches (minecraft 0001–0011, paper 0001–0006) are untouched; ours start at minecraft 0012
  and paper 0007.

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
- The CI in `.github/workflows/` is upstream's: `build.yml` builds pushes, `release-26.3.yaml` only
  runs on `ver/26.3.x`, so release jars are built locally and attached to the GitHub release.

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
- `folia-server/build.gradle.kts.patch` carries our build changes (jar names `legacyfolia*`, brand,
  night-config); it conflicts on most upstream dependency bumps.
- Upstream precedence: when upstream changes the same thing, upstream's behaviour wins and ours goes on
  top (exception: whitelist stays off by default).

Our minecraft patches: 0012–0015 config load/EULA, plugin update, locator bar, adaptive distance;
0016–0017, 0020–0027, 0035–0036, 0038–0057 combat; 0018–0019 file locations; 0028–0031 lobby, Nether
roof, spawn protection, whitelist default; 0032–0034 start from TOML, hot writes, lobby main world;
0037 vanilla locator bar. Paper: 0007 dimensions, 0008 forwarding secret, 0009 `canSee` by id,
0010–0012 server.properties/Bukkit/Spigot/Paper files in memory, 0013 `/legacyfolia`.

## Architecture (`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/`)

- `config/` — the configuration system. `ConfigHooks` is the single entry point the patches call
  (`start`, `reload`, `stop`, the in-memory objects, `vanillaChanged` for hot writes). Flow:
  `catalog/` (`SettingsCatalog`, data in `resources/legacyfolia/catalog.tsv`: 593 keys with type,
  default, per-world flag, legacy origin, server.properties name) → `ServerConfig` / `forWorld(name)`
  reading the eight TOML files (defaults in `resources/legacyfolia/*.toml`; `[worlds.<name>]`, then
  `[worlds.default]`, then global) → `ServerProperties` overlay (`/legacyfolia properties
  on|off|status`) → `bridge/` (`ServerConfigSettings`, `ServerPropertiesBridge`, `BukkitBridge`,
  `SpigotBridge`, `PaperBridge`) building the objects plugins see. `migration/LegacyImport` imports
  old server files on first start and moves them to `config/legacy-backup/<date>/` with an
  `import-report.txt`. All TOML writes go through `properties/TomlText` (night-config's `save()`
  destroys comments). `catalog/LegacyExclusions` + `LegacyCoverageTest` fail the build when Paper or
  Spigot add a setting we haven't catalogued.
- `combat/` — 1.8 PvP, matched against the 1.8.8 source mechanic by mechanic. `LegacyCombat` holds the
  1.8 formulas, `Combat` is what the NMS hooks call, `CombatConfig` reads `combat.toml`. With
  `combat.enabled = false` every hook must fall through to pure 26.3 behaviour. Weapon damage is
  applied at attack time; `WeaponTooltips` rewrites items only on the outgoing `ItemStack` codec and
  strips the injection on inbound creative items. To compare against 1.8.8, build its sources with
  `reference/fetch-1.8.8-source.sh` (needs `JAVA_8_HOME`; output is never committed). `docs/PVP-1.8.md`
  lists what is and isn't identical.
- `waypoint/` — locator bar, matching vanilla 26.3. `RegionisedWaypoints` is the per-level manager
  behind the patched `ServerWaypointManager`; transmitters publish immutable `WaypointSnapshot`s and
  `WaypointConnections` runs vanilla's per-receiver connection logic against them on the receiver's own
  tick (hook in `LivingEntity.tick()`). Never touch another region's player directly: queue work for
  the owner's tick. Leaving a level clears the player's bar.
- `update/` — plugin auto-updater (Hangar, Modrinth, GitHub, LuckPerms), off by default
  (`plugins.auto-update.enabled`), runs before the plugin loader reads the directory.
- `eula/` (interactive prompt, ENTER accepts), `world/` (`Dimensions`, `Lobby`, `NetherRoof`,
  `SpawnProtection`), `chunks/` (adaptive view distance, off by default), `network/`
  (`ForwardingSecret`), `command/` (`/legacyfolia`, console or op level 4).
- Tests mirror these packages under `folia-server/src/test/java/io/github/adrielorihuela/legacyfolia/`
  (325 cases); import fixtures for each server type are in `src/test/resources/legacyfolia-import/`.

## Repo conventions

- Folia commit style: short imperative English title, no prefix, one commit per change. Annotated tag
  per finished phase (`vX.Y.0`), pushed with `main`. Pre-releases for testing are `v1.0.0-rc.N` with
  the paperclip jar attached on GitHub; `v1.0.0` is tagged and released only when the user says so.
- Only `main` on origin; never force-push.
- Secrets (`forwarding.secret`, `rcon.secret`, `github.token`) never go into `server.properties`.
