# LegacyFolia configuration: TOML files, server.properties, migration and secrets

LegacyFolia replaces the six configuration files a Paper or Folia server keeps in three formats
(`server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml` and
`paper-world-defaults.yml`, plus a `paper-world.yml` in every world) with **eight commented TOML
files in `config/`**, grouped by what each setting does. Plugins notice nothing: every value they
read through the Bukkit, Spigot and Paper APIs is still there, built in memory from the TOML files.

This page explains how that works: the files, the optional `server.properties` mode for hosting
panels, the automatic migration from vanilla, Spigot, Paper and Folia, the secrets, RCON and the
lobby settings. Every key is documented in the file that holds it, so this page does not list them.

- [The TOML files](#the-toml-files)
- [Per-world settings: worlds.toml](#per-world-settings-worldstoml)
- [server.properties mode (for Pterodactyl and other panels)](#serverproperties-mode-for-pterodactyl-and-other-panels)
- [Migrating from vanilla, Spigot, Paper or Folia](#migrating-from-vanilla-spigot-paper-or-folia)
- [Plugin compatibility: bukkit.yml, spigot.yml and Paper's files in memory](#plugin-compatibility-bukkityml-spigotyml-and-papers-files-in-memory)
- [The EULA](#the-eula)
- [Secrets: rcon.secret, forwarding.secret, github.token](#secrets-rconsecret-forwardingsecret-githubtoken)
- [RCON, on and local by default](#rcon-on-and-local-by-default)
- [Lobby: always spawn at the world spawn](#lobby-always-spawn-at-the-world-spawn)
- [Turning the Nether and the End off](#turning-the-nether-and-the-end-off)
- [Reloading and commands that change settings](#reloading-and-commands-that-change-settings)
- [Upgrading LegacyFolia](#upgrading-legacyfolia)

## The TOML files

| File | What is in it |
|---|---|
| `config/server.toml` | Identity, start-up, the EULA, the console, moderation, the whitelist, messages, the watchdog, saving |
| `config/world.toml` | Which dimensions exist, world generation, storage, the lobby spawn, world generators |
| `config/network.toml` | Ports, proxies (Velocity, BungeeCord), RCON, query, the management API, connection limits |
| `config/performance.toml` | View and simulation distance, chunks, region threads, spawning, entity activation |
| `config/gameplay.toml` | Rules of play inherited from Spigot and Paper: hunger, items, redstone, anti-xray, fixes |
| `config/combat.toml` | 1.8 PvP, see [PVP-1.8.md](PVP-1.8.md) |
| `config/plugins.toml` | Plugin auto-updates, command aliases, API behaviour, see [PLUGIN-UPDATER.md](PLUGIN-UPDATER.md) |
| `config/worlds.toml` | Overrides for one world, or for every world |

A setting's home follows from what it is for, never from which server introduced it: the Nether
switch is in `world.toml`, not in a Paper file; the Velocity secret is with the network, not with
Spigot. Every key carries a comment saying what it does, what it accepts and what its default is.

A new server writes all eight files on its first start. A file that cannot be parsed stops the
server instead of being replaced: it is somebody's configuration, and starting with defaults would
apply a setup nobody chose.

## Per-world settings: worlds.toml

More than half of the inherited settings can differ per world, as Spigot's `world-settings` and
Paper's `paper-world.yml` allowed. `worlds.toml` holds only the differences:

```toml
[worlds.default]                 # every world
"chunks.view-distance" = 8

[worlds.world_nether]            # one world, by its folder name
"performance.spawning.spawn-limits.monsters" = 30
```

A world reads its own section, then `[worlds.default]`, then the global value in the file that owns
the setting. Worlds are named by their folder, the name you see on disk.

## server.properties mode (for Pterodactyl and other panels)

Panels such as Pterodactyl read and write `server.properties`, and operators have been editing it for
fifteen years. So LegacyFolia keeps one, in `config/server.properties`, **on by default**:

- It holds **only the settings that differ from their defaults**, and **it wins** over the TOML
  files. The TOML files stay complete, at their defaults, as a commented reference.
- A new server creates it with nothing but an explanatory header.
- Delete a line and that setting goes back to its default; add a line and it applies.
- Edit a TOML file instead, and the change is moved into `server.properties` at the next start or
  reload, and the TOML goes back to its default. If both say something different,
  `server.properties` wins and the log shows the value that was discarded.
- **Key names**: vanilla settings keep their vanilla names (`motd`, `max-players`, `server-port`,
  `white-list`...), so panels keep working. Every other setting uses its TOML path
  (`gameplay.hunger.jump-walk-exhaustion=0.1`), and a per-world one
  `worlds.<world>.<path>` (`worlds.world_nether.chunks.view-distance=6`).
- Invalid values are reported with their line number and the default is used; unknown keys are kept
  untouched and reported once.
- Files are compared at start-up and on reload, never while the server runs.
- Secrets, the EULA agreement and the switch itself never go into `server.properties`.

Turn it off with `use-server-properties = false` in `server.toml`, or at once with the console
command:

```
/legacyfolia properties off      # moves every value into the TOML files, then deletes server.properties
/legacyfolia properties on       # moves every non-default TOML value into a new server.properties
/legacyfolia properties status   # on or off, the file, and how many settings it changes, by file
```

The command is for the console and operators of level 4 only. Lines that cannot be moved into a TOML
file when the mode is turned off (unknown keys, invalid values) are kept in
`config/server.properties.unused` rather than deleted.

## Migrating from vanilla, Spigot, Paper or Folia

Drop the LegacyFolia jar where another server used to run and start it. Before anything reads a
setting, it finds every file that server kept its configuration in, in the server root or in
`config/`:

`server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`, `config/paper-global.yml`,
`config/paper-world-defaults.yml`, each world's `paper-world.yml` and `eula.txt`.

Each value goes to its TOML key, and per-world values to `worlds.toml`. Where two files decided the
same thing, the conflict is resolved the way the original server resolved it (for example, `-1` in
Paper means Bukkit's value applies). Only values that differ from LegacyFolia's defaults are written,
and with the `server.properties` mode on they end up in `server.properties`, where you would look
for them.

The originals are **moved, never deleted**, to `config/legacy-backup/<date>/`, with an
`import-report.txt` listing every file read, every value imported and where it came from, and the
few legacy keys that have no place in LegacyFolia. An import that is interrupted is finished on the
next start; a legacy file that cannot be parsed stops the server before anything is moved.

The whitelist, operators and bans (`whitelist.json`, `ops.json`, `banned-players.json`,
`banned-ips.json`) are data rather than settings: they are moved into `config/` and used as they
are.

## Plugin compatibility: bukkit.yml, spigot.yml and Paper's files in memory

`bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml`, `paper-world-defaults.yml` and the
`paper-world.yml` files **no longer exist on disk**, and nothing writes them. The objects plugins
read are built in memory from the TOML files (with `server.properties` applied) and hold exactly
what those files would hold:

- `Server#getMotd`, `#getMaxPlayers`, `#getViewDistance`, `#getSpawnLimit`, `#getAllowNether` and
  the rest of the API answer from the TOML files.
- `Bukkit.spigot().getConfig()` returns a populated `spigot.yml`, `Bukkit.spigot().getPaperConfig()`
  a populated Paper configuration, and `SpigotConfig`, `GlobalConfiguration` and each world's Paper
  and Spigot configuration are filled from the same values.

With every setting at its default, each object is identical to what a fresh Paper server writes;
tests check this for every file. `permissions.yml` and `help.yml` are data rather than settings and
are read as before.

## The EULA

The EULA agreement is the `eula` key of `server.toml`; `eula.txt` no longer exists. A server
migrated from another keeps its answer.

On a first start with a console, the server asks: **Enter** agrees and writes `eula = true` to
`server.toml`, **Escape** stops. A server with no console attached (systemd, Docker, `--noconsole`)
is never asked and stops with a message saying what to set. Once `eula = true`, it never asks again.

The agreement never goes to `server.properties`, even with the mode on: that file gets copied from
one server to another, and a copy must not agree to the EULA on behalf of another operator.

## Secrets: rcon.secret, forwarding.secret, github.token

Three files under `config/` hold one value each and nothing else. The whole content, trimmed, is the
value:

- **`rcon.secret`**: the RCON password, generated on the first start (32 random characters from a
  secure source). Write your own and it is used; delete the file and a new one is generated. The
  log only ever says where it is, never what it is.
- **`forwarding.secret`**: the Velocity modern-forwarding secret, the same file name Velocity uses,
  so the proxy's file can be copied straight across. `PAPER_VELOCITY_SECRET` overrides it.
- **`github.token`**: an optional GitHub token for the plugin updater. `GITHUB_TOKEN` overrides it.

The empty ones are created on the first start, so the file to fill in is there to be found.
Configuration gets pasted into issues and screenshots; keeping each secret in its own file keeps the
rest safe to share. Secrets are never written to a TOML file or to `server.properties`.

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
elsewhere only behind a firewall.

## Lobby: always spawn at the world spawn

```toml
[world]
always-spawn-at-world-spawn = true
lobby-spawn-world = "main-world"   # or "logout-world"
```

With `always-spawn-at-world-spawn`, every join behaves like a first join: the player arrives at the
world spawn, spread over the spawn radius, instead of where they logged out. `lobby-spawn-world`
chooses which world's spawn:

- `logout-world` (default): the spawn of the world they logged out in.
- `main-world`: the spawn of the main world, whatever world they left from. What a lobby with
  several worlds wants.

Nothing is erased: the saved position stays in the player's data, so turning the setting off puts
everybody back where they were on their next join. `server.spawn-protection-radius` is `0` by
default, and `world.disable-nether-roof` keeps players off the Nether roof.

## Turning the Nether and the End off

```toml
[dimensions]
nether = false
end = false
```

A dimension that is off is never created: no world folder read or written, no chunks in memory,
nothing ticking, no region threads spent on it. On an empty server that took start-up from 23.5s to
10.8s. Turning one off later does not delete its folder; turning it back on picks up where it left
off. Dimensions added by datapacks are always loaded.

## Reloading and commands that change settings

- **Reload** (`/reload`, `/spigot reload`, `/paper reload`): the TOML files and `server.properties`
  are read again and compared, and the in-memory Bukkit, Spigot and Paper objects are rebuilt.
  Settings that are consumed once at start-up, such as the ports and the region file compression,
  still need a restart.
- **Commands that change a setting** (`/whitelist on|off`, `Server#setMaxPlayers`,
  `Server#setMotd`, and the rest of vanilla's nineteen changeable settings) still work. The change is saved where the setting lives:
  `server.properties` with the mode on, its TOML file with the mode off, keeping every comment.
  The file is written off the region threads, and the last change is flushed before the server
  stops. `/difficulty` is the exception, as in Paper: it changes the world's difficulty, saved with
  the world, and leaves the `difficulty` setting, which only applies to new worlds, alone.

## Upgrading LegacyFolia

When a release adds settings, they appear in your files with their comments on the next start, in
their place. Your own values and comments are never touched, and a key the template no longer knows
is never deleted.
