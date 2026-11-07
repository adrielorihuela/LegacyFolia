# Architecture

Moved from CLAUDE.md on 2026-11-06.

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
