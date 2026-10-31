# LegacyFolia configuration: TOML files, server.properties, migration and secrets

Paper and Folia keep their settings in a properties file and five YAML files: `server.properties`,
`bukkit.yml`, `spigot.yml`, `commands.yml`, `config/paper-global.yml` and
`config/paper-world-defaults.yml`, plus a `paper-world.yml` in every world and the EULA answer in
`eula.txt`. LegacyFolia replaces
all of them with **eight commented TOML files in `config/`**, grouped by what each setting does,
and an optional `config/server.properties` that holds only what you changed. Plugins notice
nothing: every value they read through the Bukkit, Spigot and Paper APIs is still there, built in
memory from the TOML files.

This page explains how that works. Every key is documented in the file that holds it, with what
it does, what it accepts and its default, so this page does not repeat them. Where each key came
from (which legacy file, which path, which fallbacks) is in [SETTINGS-MAP.md](SETTINGS-MAP.md)
(Spanish).

- [Differences from Paper and Folia](#differences-from-paper-and-folia)
- [The TOML files](#the-toml-files)
- [Per-world settings: worlds.toml](#per-world-settings-worldstoml)
- [server.properties mode (for Pterodactyl and other panels)](#serverproperties-mode-for-pterodactyl-and-other-panels)
- [/legacyfolia properties](#legacyfolia-properties)
- [Migrating from vanilla, Spigot, Paper or Folia](#migrating-from-vanilla-spigot-paper-or-folia)
- [Plugin compatibility: the legacy files in memory](#plugin-compatibility-the-legacy-files-in-memory)
- [The EULA](#the-eula)
- [Secrets: rcon.secret, forwarding.secret, github.token](#secrets-rconsecret-forwardingsecret-githubtoken)
- [RCON, on and local by default](#rcon-on-and-local-by-default)
- [Lobby: always spawn at the world spawn](#lobby-always-spawn-at-the-world-spawn)
- [Spawn protection and the Nether roof](#spawn-protection-and-the-nether-roof)
- [Turning the Nether and the End off](#turning-the-nether-and-the-end-off)
- [Reloading, restarts and commands that change settings](#reloading-restarts-and-commands-that-change-settings)
- [Upgrading LegacyFolia](#upgrading-legacyfolia)

## Differences from Paper and Folia

| | Paper / Folia | LegacyFolia |
|---|---|---|
| Configuration files | `server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml` in the root; `paper-global.yml`, `paper-world-defaults.yml` in `config/`; `paper-world.yml` per world | eight TOML files in `config/`, plus an optional `config/server.properties` holding only non-defaults. No YAML configuration file is read or written |
| Per-world settings | `world-settings.<world>` in `spigot.yml` and `<world>/paper-world.yml` | `[worlds.<world>]` in `config/worlds.toml` |
| EULA | `eula.txt` | `eula` in `config/server.toml`, asked at the console on a first start |
| Whitelist, ops, bans | JSON files in the root | the same JSON files, in `config/` |
| User cache | `usercache.json` in the root | `cache/usercache.json` |
| Velocity secret | `proxies.velocity.secret` in `paper-global.yml` | `config/forwarding.secret` |
| RCON | off, password in `server.properties` | on, bound to `127.0.0.1`, password generated into `config/rcon.secret` |
| Whitelist default | on for a new server (vanilla 26.3) | off |
| Spawn protection default | 16 | 0 |
| 1.8 PvP | not available | on by default (`combat.enabled = true`), see [PVP-1.8.md](PVP-1.8.md) |
| Fork-only settings | none | dimensions, lobby spawn, Nether roof, adaptive view distance, 1.8 combat, plugin auto-updater, EULA prompt timeout, the server.properties switch |
| Migration | none | imports an older server's files on the first start and backs them up |

Everything else defaults to what a fresh Paper/Folia server writes; the bridge tests check that
setting by setting (see [Plugin compatibility](#plugin-compatibility-the-legacy-files-in-memory)).

## The TOML files

| File | Sections | Keys | Per world | What is in it |
|---|---|---:|---:|---|
| `config/server.toml` | `[server]`, `.status`, `.console`, `.access`, `.messages`, `.resource-pack`, `.watchdog`, `.saving`, `.data-packs` | 76 | 0 | Identity, the EULA, the server.properties switch, the console, access and moderation, messages, the watchdog, player and statistics saving, data packs |
| `config/world.toml` | `[dimensions]`, `[world]`, `.generation`, `.structure-seeds`, `.storage`, `.generators`, `.biome-providers` | 40 | 24 | Which dimensions exist, lobby spawn, Nether roof, world generation, Spigot's structure seeds, world container, plugin world generators |
| `config/network.toml` | `[network]`, `.velocity`, `.bungeecord`, `.proxy-protocol`, `.rcon`, `.query`, `.management`, `.limits`, `.monitoring` | 45 | 0 | Address and port, compression, proxies, RCON, query, the management server, spam and packet limits, JMX, spark, the update checker |
| `config/performance.toml` | `[chunks]`, `[chunks.adaptive-view-distance]`, `[performance.entities]`, `.spawning`, `.ticking`, `.regions`, `.threads` | 132 | 98 | View and simulation distance, chunk saving and loading, adaptive view distance, entity activation and tracking, collisions, spawn limits and despawn ranges, tick budgets, Folia's region threads |
| `config/gameplay.toml` | `[gameplay.hunger]`, `.growth`, `.mobs`, `.mob-spawning`, `.environment`, `.items`, `.redstone`, `.exploits`, `.sounds`, `.players`, `.commands`, `.scoreboards` | 207 | 181 | Rules of play inherited from Spigot and Paper: hunger, crop growth, mob behaviour, weather and fluids, item despawn and loot, redstone, anti-xray and exploit fixes |
| `config/combat.toml` | `[combat]`, `[combat.attribute-limits]` | 62 | 0 | 1.8 PvP, see [PVP-1.8.md](PVP-1.8.md); also Spigot's attribute caps |
| `config/plugins.toml` | `[plugins.auto-update]`, `[plugins.api]`, `[plugins.commands]` | 31 | 0 | The plugin auto-updater ([PLUGIN-UPDATER.md](PLUGIN-UPDATER.md)), plugin API behaviour, item validation, command handling and `commands.yml`'s aliases |
| `config/worlds.toml` | `[worlds.default]`, `[worlds.<world>]` | 0 | | Overrides for every world, or for one world |

593 keys in all (counted from `folia-server/src/main/resources/legacyfolia/catalog.tsv`, which
`SettingsCatalogTest` keeps in step with the files). 80 of them are LegacyFolia's own; the rest
come from `server.properties` (69), `bukkit.yml` (30), `spigot.yml` (35 global, 105 per world),
`commands.yml` (3), `paper-global.yml` (88), `paper-world-defaults.yml` (182) and `eula.txt` (1),
with 21 more legacy locations folded into keys that already existed (Paper's per-world spawn
limits beside Bukkit's, Spigot's per-world view distance beside vanilla's, and the like).

A setting's home follows from what it is for, never from which server introduced it: the Nether
switch is in `world.toml`, not in a Paper file; the Velocity secret is with the network, not with
Paper; the watchdog's Spigot timeout and Paper's early warnings are one `[server.watchdog]`
section. The few names that changed on the way, and why, are listed in SETTINGS-MAP.md.

**How the files are read and written.**

- A new server writes all eight files on its first start, from templates in the jar.
- A file that cannot be parsed stops the server instead of being replaced: it is somebody's
  configuration, and starting with defaults would apply a setup nobody chose.
- The server only ever writes these files as text. A change (a command, the server.properties
  mode, an upgrade) replaces the one value it is about; every comment, blank line and other key
  stays exactly as it was.
- Values that are not plain numbers or booleans keep Paper's forms: `"default"` or `"disabled"`
  in place of a number where Paper allows it, durations such as `"12h"`, messages in MiniMessage
  with `"translate:<key>"` for a translation key. Each key's comment says which applies.

## Per-world settings: worlds.toml

About half of the settings can differ per world, as Spigot's `world-settings` and Paper's
`paper-world.yml` allowed: 303 keys, all in `gameplay.toml`, `performance.toml` or `world.toml`.
`performance.toml` and `world.toml` mark them **PER WORLD** in the key's or the section's comment;
in `gameplay.toml` almost everything is per world, and the exceptions are marked server-wide.
`worlds.toml` holds only the differences:

```toml
[worlds.default]                                   # every world
"chunks.view-distance" = 8

[worlds.world_nether]                              # one world, by its folder name
"performance.spawning.spawn-limits.monsters" = 30

[worlds.world_the_end.performance.entities.activation-range]   # the same thing, nested
monsters = 24
```

- A world reads `[worlds.<world>]`, then `[worlds.default]`, then the value in the file that owns
  the setting. That is the order Spigot and Paper each used, so a value resolves the way it did
  before.
- The path is the key's full path inside its file, starting at the section header and without the
  file name: `"chunks.view-distance"`, not `"performance.chunks.view-distance"`. Quote it when it is
  written as one key; the nested-table form means the same.
- Worlds are named by their folder name (`world`, `world_nether`, `world_the_end`, or what a plugin
  created). A name with anything but letters, digits, `_` or `-` is quoted: `[worlds."my world"]`.
  A world literally called `default` cannot have its own section, as in Spigot.
- A key that is not per world (a port, a message, anything in `server.toml`, `network.toml`,
  `combat.toml` or `plugins.toml`) is never read from `worlds.toml`.
- Each world's view is worked out once and kept until the next reload, so edits take effect on
  reload, not while the server runs.
- With the server.properties mode on, the overrides of per-world settings move out of
  `worlds.toml` into `server.properties` as `worlds.<world>.<path>` lines, like every other
  non-default value (next section).

## server.properties mode (for Pterodactyl and other panels)

Panels such as Pterodactyl read and write `server.properties`, and operators have been editing it
for fifteen years. So LegacyFolia keeps one, **on by default**, at `config/server.properties`:

- It holds **only the settings that differ from their defaults**, and **it wins** over the TOML
  files. The TOML files stay complete, at their defaults, as a commented reference.
- A new server creates it with nothing but an explanatory header.
- Delete a line and that setting goes back to its default; add a line and it applies. A line that
  says the default is removed at the next start or reload.
- Edit a TOML file instead, and the change is moved into `server.properties` at the next start or
  reload, and the TOML goes back to its default. If both say something different,
  `server.properties` wins and the log shows the TOML value that was discarded.
- **Key names**: vanilla settings keep their vanilla names (`motd`, `max-players`, `server-port`,
  `white-list`, `view-distance`...; 68 of them), so panels keep working. Every other setting uses
  its TOML path (`gameplay.hunger.jump-walk-exhaustion=0.1`), and a per-world override
  `worlds.<world>.<path>` (`worlds.world_nether.chunks.view-distance=6`, or `worlds.default.` for
  every world).
- Values are written the way vanilla writes them: booleans and numbers plainly, lists comma
  separated; a table, or a list whose items contain commas, as TOML.
- New lines are grouped under a comment naming the TOML file they belong to (or the world). Your
  own comments and the order of your lines are kept; the file is never rewritten wholesale.
- Invalid values are reported with their line number and the default is used. Unknown keys are
  kept untouched and reported once; when the key is a TOML path that has a vanilla name, the
  warning suggests it.
- Files are compared at start-up and on reload, never while the server runs.
- Never in `server.properties`: the secrets (their file names live in the TOML files), the EULA
  agreement and the switch itself.

The file is `config/server.properties`, not the one in the server root: point the panel's
configuration editor there. A `server.properties` in the server root is not read; if it looks like
an older server's, the first start imports it (see [Migrating](#migrating-from-vanilla-spigot-paper-or-folia)),
otherwise it is left alone.

Turn the mode off with `use-server-properties = false` in `server.toml` (applied at the next start
or reload), or at once with the command below. Turning it off writes every value of
`server.properties` into its TOML file, then deletes `server.properties`. Lines that cannot be
moved (unknown keys, invalid values) are kept in `config/server.properties.unused` rather than
deleted (`.unused.2`, `.unused.3`... if that exists).

## /legacyfolia properties

```
/legacyfolia properties on       # moves every non-default TOML value into a new server.properties
/legacyfolia properties off      # moves every value into the TOML files, then deletes server.properties
/legacyfolia properties status   # on or off, the file, and how many settings it changes, by file
```

For the console and operators of level 4 only. The work runs off the region threads and the
answer arrives when it is done; a failure deletes nothing and is logged in full on the console.

## Migrating from vanilla, Spigot, Paper or Folia

Drop the LegacyFolia jar where another server used to run and start it. Before anything reads a
setting, it looks for every file that server kept its configuration in, in the server root and in
`config/`:

`server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`, `eula.txt`,
`config/paper-global.yml`, `config/paper-world-defaults.yml`, and every world's
`paper-world.yml` (both the old `world_nether/` folders and Paper 26's
`<level>/dimensions/<namespace>/<path>/` layout).

- When a file is both in the root and in `config/`, the one in `config/` is imported; the other is
  backed up but not read.
- A `server.properties` is imported only if it is an older server's: it sits beside a `bukkit.yml`
  or `spigot.yml`, or starts with vanilla's `#Minecraft server properties`, or has a key the mode's
  own file never holds (such as `rcon.password`). One whose header mentions LegacyFolia is the
  mode's own and is left where it is.
- Each value goes to its TOML key, and per-world values to `worlds.toml`. Where two files decided
  the same thing, the conflict is resolved the way the original server resolved it (Paper's `-1`
  spawn limit means Bukkit's applies; Spigot's `default` view distance means `server.properties`';
  `pause-when-empty-seconds` is not imported from a Bukkit-family server, because Paper forces
  `-1`).
- Only values that differ from LegacyFolia's defaults are written. With the server.properties mode
  on, the mode then moves them into `server.properties`, where you would look for them.
- Secrets are not written to the TOML files: `rcon.password` goes to `config/rcon.secret` and
  `proxies.velocity.secret` to `config/forwarding.secret`, unless that file already holds a
  different secret.
- The EULA answer in `eula.txt` becomes `eula` in `server.toml`.

The originals are **moved, never deleted**, to `config/legacy-backup/<yyyy-MM-dd_HH-mm-ss>/`,
keeping their relative paths, beside an `import-report.txt` that lists every file read, every value
imported and where it came from, every collision and how it was resolved, and the legacy keys that
have no place in LegacyFolia, each with the reason. A legacy file that cannot be parsed stops the
server before anything is moved. An import that is interrupted leaves a marker in its backup folder
and is finished on the next start.

The whitelist, operators and bans (`whitelist.json`, `ops.json`, `banned-players.json`,
`banned-ips.json`) are data rather than settings: they are moved from the root into `config/` the
first time the server opens them, and used as they are. `usercache.json` moves to `cache/`. If a
copy already exists in `config/`, it wins and the root copy is left alone, with a warning.

## Plugin compatibility: the legacy files in memory

`bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml`, `paper-world-defaults.yml` and the
`paper-world.yml` files **no longer exist on disk**, and nothing writes them. The objects plugins
read are built in memory from the TOML files, with `server.properties` applied, and hold what
those files would hold:

- `DedicatedServerProperties`, and with it `Server#getMotd`, `#getMaxPlayers`, `#getViewDistance`
  and the rest of vanilla's settings, is built from a `Properties` made from the TOML files instead
  of reading `server.properties`.
- `CraftServer`'s `bukkit.yml` and `commands.yml` (`Server#getSpawnLimit`, `#getUpdateFolder`,
  `#getShutdownMessage`, the command aliases...) are `YamlConfiguration`s built in memory, with
  Bukkit's templates laid under them as defaults, as for a loaded file.
- `Bukkit.spigot().getConfig()` returns a populated `spigot.yml`, and `SpigotConfig` and every
  `SpigotWorldConfig` read it. Worlds are under `world-settings.<dimension key>`
  (`minecraft:the_nether`), as Spigot names them; `world-settings.default` takes `[worlds.default]`
  over the global values.
- `GlobalConfiguration`, the world defaults and each world's `WorldConfiguration` are mapped by
  Paper's own code from Configurate nodes built from the TOML files. Where Paper writes "use the
  other setting" (`-1`, `"default"`), the node says so too, so Paper's fallbacks to Bukkit and
  Spigot keep working.
- `Bukkit.spigot().getPaperConfig()` returns the global settings with the world defaults and each
  loaded world under `__________WORLDS__________`, as Paper assembled it from its files.
- Saves are skipped: `SpigotConfig` has no file to save to, Paper's loaders do not save, and a
  plugin that changes `Bukkit.spigot().getConfig()` changes it until the next reload.

With every setting at its default, `server.properties`, `bukkit.yml`, `commands.yml`, `spigot.yml`
(and the Spigot values it produces), `paper-global.yml` and `paper-world-defaults.yml` are each
identical to what a fresh server writes, except for the RCON settings described below; the bridge
tests in `folia-server/src/test/java/io/github/adrielorihuela/legacyfolia/config/bridge/` check
this key by key. `permissions.yml` and `help.yml` are data rather than settings and are read as
before.

Two things a plugin can still notice:

- A plugin that opens `spigot.yml` or `bukkit.yml` with `new File(...)` instead of the API finds no
  file.
- The command-line options that named those files (`--config`, `--bukkit-settings`,
  `--spigot-settings`, `--commands-settings`, `--paper-settings`) are still accepted, but the
  configuration comes from `config/` whatever they say. `--universe` and `--world` still work.

## The EULA

The EULA agreement is the `eula` key of `server.toml`; `eula.txt` no longer exists. A server
migrated from another keeps its answer.

On a start with `eula = false` and a console attached, the server asks: **Enter** agrees and
writes `eula = true` to `server.toml`; **Escape**, Ctrl-C or end of input stops it; no answer
within `server.eula-prompt-timeout-seconds` (120) stops it too, since agreement is never assumed. A
server with no console attached (systemd, Docker, `--noconsole`) is never asked and stops with a
message saying what to set. Paper's `-Dcom.mojang.eula.agree=true` is still honoured.

The agreement never goes to `server.properties`, even with the mode on: that file gets copied from
one server to another, and a copy must not agree to the EULA on behalf of another operator.

## Secrets: rcon.secret, forwarding.secret, github.token

Three files hold one value each and nothing else. The whole content, trimmed, is the value. The
TOML files only name them (`network.rcon.password-file`, `network.velocity.secret-file`,
`plugins.auto-update.github-token-file`): a name is relative to `config/`, an absolute path is used
as given, so a secret can be mounted from elsewhere.

- **`rcon.secret`**: the RCON password. Generated on the first start: 32 letters and digits from
  `SecureRandom`. Write your own and it is used; delete the file and a new one is generated at the
  next start or reload. The log only ever says where it is, never what it is.
- **`forwarding.secret`**: the Velocity modern-forwarding secret, the same file name Velocity uses,
  so the proxy's file can be copied straight across. The `PAPER_VELOCITY_SECRET` environment
  variable overrides it. A secret still in an old `paper-global.yml` is moved into this file. With
  Velocity enabled and no secret, Velocity forwarding is turned off with an error, as in Paper.
- **`github.token`**: an optional GitHub token for the plugin updater. `GITHUB_TOKEN` overrides it.

`forwarding.secret` and `github.token` are created empty whenever they are missing, so the file to
fill in is there to be found. Configuration gets pasted into issues and screenshots; keeping each
secret in its own file keeps the rest safe to share.

The management server's `management-server-secret` and `management-server-tls-keystore-password`
are not settings here: vanilla generates the first anew on every start, and the second always
takes vanilla's default (empty), so a keystore protected by a password cannot be used yet.

## RCON, on and local by default

```toml
[network.rcon]
enabled = true
port = 25575
ip = "127.0.0.1"            # loopback only; "" follows [network] ip
password-file = "rcon.secret"
```

RCON has full console authority over a plaintext protocol, so it listens only on `127.0.0.1`:
tools on the same machine (backups, panels, scripts) can reach it and nothing else can. Bind it
elsewhere only behind a firewall. If the password file cannot be written, RCON stays off rather
than start with a password nobody can read.

## Lobby: always spawn at the world spawn

```toml
[world]
always-spawn-at-world-spawn = false   # true: every join behaves like a first join
lobby-spawn-world = "logout-world"    # or "main-world"
```

With `always-spawn-at-world-spawn = true`, every join behaves like a first join: the player arrives
at the world spawn, spread over the `respawn_radius` game rule, instead of where they logged out.
`lobby-spawn-world`, read only while that is on, chooses which world's spawn:

- `logout-world` (default): the spawn of the world they logged out in. Any value other than
  `main-world` means this.
- `main-world`: the spawn of the main world, whatever world they left from. What a lobby with
  several worlds wants.

Nothing is erased: the saved position stays in the player's data, so turning the setting off puts
everybody back where they were on their next join.

## Spawn protection and the Nether roof

`server.spawn-protection-radius` (`spawn-protection` in `server.properties`) is `0` by default,
where vanilla has 16: protecting an area is a claim or region plugin's job. Set it to 16 for
vanilla's behaviour.

`world.disable-nether-roof = true` stops blocks being placed above the Nether's bedrock roof. The
dimension keeps its real height, nobody is moved, and blocks already up there stay.

## Turning the Nether and the End off

```toml
[dimensions]
nether = false
end = false
```

These replace Paper's `misc.enable-nether` and Bukkit's `settings.allow-end`, and
`Server#getAllowNether()` / `#getAllowEnd()` answer from them. A dimension that is off is never
created: no world folder read or written, no chunks in memory, nothing ticking, no region threads
spent on it. Turning one off later does not delete its folder; turning it back on picks up where it
left off. The switches are read once, at start-up, so a change needs a restart.

- `overworld = false` is refused with a warning: the server has nowhere to put players without it.
- Dimensions added by datapacks are always loaded.
- On the run that creates `world.toml`, each switch takes the value the old setting had, so a
  server that had the Nether off keeps it off.

## Reloading, restarts and commands that change settings

- **Reload** (`/reload`, `/spigot reload`, `/paper reload`, and `Server#reloadCommandAliases`): the
  TOML files and `server.properties` are read again and compared, the in-memory Bukkit, Spigot and
  Paper objects are rebuilt, and every world's view is worked out again. The adaptive view
  distance and the 1.8 combat settings are re-read too. The migration never runs on a reload.
- **Restart only**: settings consumed once at start-up, such as `chunks.region-file-compression`,
  `network.netty-threads`, `world.storage.world-container` and the `[dimensions]` switches.
- **Commands that change a setting** (`/whitelist on|off`, `Server#setMaxPlayers`,
  `Server#setMotd`, and the rest of vanilla's 19 changeable settings) still work. The change is
  saved where the setting lives: `server.properties` with the mode on (a value set back to its
  default removes the line), its TOML file with the mode off, keeping every comment. It is visible
  at once; the file is written on a dedicated thread, in order, never from a region thread, and
  the last change is flushed before the server stops.
- `/difficulty` is the exception, as in Paper: it changes the world's difficulty, saved with the
  world, and leaves the `difficulty` setting, which only applies to new worlds, alone.

## Upgrading LegacyFolia

When a release adds settings, they appear in your files with their comments on the next start, in
their place. Your own values and comments are never touched, a key the template no longer knows is
never deleted, and a key LegacyFolia renamed (such as `combat.legacy-sword-damage`, now
`combat.legacy-weapon-damage`) takes its new name with your value. The log lists what was added
and renamed.

`--initSettings` writes the eight files (and imports an older server's, if any) and exits.
