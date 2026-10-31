# LegacyFolia — multithreaded Folia server with 1.8 PvP, TOML configuration and plugin auto-updates

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-brightgreen)](https://minecraft.net)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net)
[![Upstream](https://img.shields.io/badge/fork%20of-PaperMC%2FFolia-blue)](https://github.com/PaperMC/Folia)
[![Status](https://img.shields.io/badge/status-v1.0.0--rc.1%20(testing)-yellow)](https://github.com/adrielorihuela/LegacyFolia/releases)
[![Licence](https://img.shields.io/badge/licence-GPL--3.0-lightgrey)](PATCHES-LICENSE)

**LegacyFolia** is a fork of [Folia](https://github.com/PaperMC/Folia), PaperMC's multithreaded
Minecraft server, for Minecraft **26.3**. It keeps everything Folia is and adds:

- **1.8 PvP** (old combat: no attack cooldown, 1.8 knockback, sword blocking, 1.8 weapon damage,
  armour, potions and more), read from the 1.8.8 source and on by default;
- the **locator bar** working again, which Folia disables;
- **eight commented TOML files** in `config/` instead of `server.properties`, `bukkit.yml`,
  `spigot.yml`, `commands.yml` and Paper's YAML files, with an optional `server.properties` for
  hosting panels such as Pterodactyl;
- **automatic migration** of an existing vanilla, Spigot, Paper or Folia server;
- **plugin auto-updates** from Hangar, Modrinth, GitHub releases and LuckPerms;
- switches to turn the **Nether and the End off**, a **lobby spawn**, a **Nether roof** block, an
  **EULA prompt** at the console and an optional **adaptive view distance**.

It is a Folia server first: regionised multithreading, the Paper and Bukkit plugin API, and every
upstream commit merged in. Plugins keep reading Bukkit, Spigot and Paper configuration through the
usual APIs. Every addition can be switched off.

> **Status: release candidate `v1.0.0-rc.1`, for testing.** The code for 1.0.0 is complete and the
> automated tests pass, but the 1.8 combat, the locator bar and the EULA prompt on a Linux terminal
> have not yet been tried with real players. Download the jar from
> [Releases](https://github.com/adrielorihuela/LegacyFolia/releases), try it on a test server, and
> please [open an issue](https://github.com/adrielorihuela/LegacyFolia/issues) for anything that
> behaves differently from what is described here. `v1.0.0` follows once it has been tested.

---

## LegacyFolia vs Folia vs Paper

| | Paper | Folia | LegacyFolia |
|---|---|---|---|
| Ticking | One main thread | Regionised: each region of the world ticks on its own thread | Same as Folia, unchanged |
| Plugins | Bukkit, Spigot and Paper plugins | Only plugins declaring `folia-supported: true` | Same as Folia |
| Combat | Modern (1.9+) | Modern (1.9+) | **1.8**, on by default; every mechanic has its own switch, and with `combat.enabled = false` it is modern combat again |
| Locator bar (`/gamerule locator_bar`) | Works (vanilla) | Does nothing ([Folia#370](https://github.com/PaperMC/Folia/issues/370)) | **Works**, behaving as vanilla's |
| Configuration on disk | `server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`, `config/paper-global.yml`, `config/paper-world-defaults.yml`, one `paper-world.yml` per world, `eula.txt` | Same as Paper | **Eight TOML files in `config/`**, every key commented, plus an optional `config/server.properties` with only what differs from the defaults. The YAML files are built in memory for plugins and never written |
| Per-world settings | `spigot.yml` `world-settings` and each world's `paper-world.yml` | Same as Paper | One file, `config/worlds.toml`, using the same precedence |
| Moving an existing server | — | — | Imported on first start; originals backed up to `config/legacy-backup/<date>/` with an `import-report.txt` |
| EULA | Stops; you edit `eula.txt` and start again | Same as Paper | Asks at the console (Enter agrees, Escape stops); stored as `eula` in `server.toml` |
| Turning off the Nether / the End | `misc.enable-nether` in `paper-global.yml` and `settings.allow-end` in `bukkit.yml` | Same as Paper | One `[dimensions]` section answering for both |
| Velocity forwarding secret | `proxies.velocity.secret` in `paper-global.yml` | Same as Paper | `config/forwarding.secret` (moved there automatically) |
| RCON | Off; password in `server.properties` | Same as Paper | **On, bound to `127.0.0.1`**, password generated into `config/rcon.secret` |
| Whitelist on a new server | On (26.3 default) | On | **Off** |
| Spawn protection | 16 blocks | 16 blocks | **0** |
| Plugin updates | Manual | Manual | Optional, checked at every start and verified by checksum |
| View distance per player | Sends each client what it asked for; loads and ticks the server's distance | Same as Paper | Optional: load and simulation distance follow each client, and drop for players who are away |
| Lobby spawn, Nether roof | — | — | Optional switches in `world.toml` |

---

## Features

### 1.8 PvP, from the original source

Not "close to 1.8": every formula was read out of the Spigot 1.8.8 source and reproduced, and each
one has a test against the figures 1.8.8 gives. On by default; everything lives in
`config/combat.toml` under `[combat]`, with one switch per mechanic.

- **No attack cooldown** and **no sweeping arc**. Swing as fast as you can click.
- **1.8 knockback.** Modern knockback only lifts a target standing on the ground; 1.8 lifted them on
  every hit, which is what lets a combo hold somebody in the air. Sprint (W-tap) knockback, the
  sprint reset and the restore of the target's motion after the velocity packet are all there.
- **Sprint-jump criticals.** Modern Minecraft refuses a critical to a sprinting attacker; 1.8 did not.
- **1.8 weapon damage.** Swords hit harder than axes again (diamond sword 8, diamond axe 7), and
  tooltips show the 1.8 figure without changing any stored item.
- **Sword blocking.** Right-click a sword to block: damage becomes `(1 + damage) × 0.5`, before
  armour, as the `BLOCKING` modifier of the damage event.
- **1.8 armour, Protection's random roll, Sharpness, Strength and Weakness**, the 1.8
  invulnerability window, **reach**, **projectiles and the fishing rod**, **explosions**,
  **potions and splash potions**, the **1.8 enchanted golden apple** (craftable again), **food and
  regeneration**, falls and fire.
- **No shields, no off-hand, no totems.**

The full list, the formulas, what was verified and what could not be made identical:
**[docs/PVP-1.8.md](docs/PVP-1.8.md)**

### The locator bar, fixed

`/gamerule locator_bar` does nothing on Folia. Its supporting methods are emptied upstream with the
note *"Need to implement waypoints correctly"* ([PaperMC/Folia#370](https://github.com/PaperMC/Folia/issues/370)),
because the bar shows players far away, who under regionised threading are usually on another
thread.

LegacyFolia implements it safely and the bar behaves as vanilla's (Paper does not change it): only
players in your own dimension, the same azimuth / chunk / block waypoints with the same 332-block
switch, hidden while crouching, invisible or wearing a mob head or carved pumpkin, team colours,
`/waypoint`, `waypoint_transmit_range` and `waypoint_receive_range`. Each player publishes an
immutable snapshot on their own thread and each receiver runs vanilla's connection logic against
those snapshots on its own thread, so nothing reads another region's entities.

### TOML configuration

`config/` holds eight TOML files grouped by what settings **do**, not by which server introduced
them, with a comment on every key:

| File | What it holds |
|---|---|
| `server.toml` | identity, EULA, console, access, messages, watchdog |
| `world.toml` | `[dimensions]`, lobby spawn, Nether roof, generation, storage |
| `network.toml` | ports, proxies (Velocity, BungeeCord), RCON, query, limits |
| `performance.toml` | chunks, adaptive view distance, entities, spawning, ticking, threads |
| `gameplay.toml` | hunger, growth, mobs, environment, items, redstone, exploits, commands |
| `combat.toml` | the 1.8 combat, attribute limits |
| `plugins.toml` | plugin loading and the auto-updater |
| `worlds.toml` | per-world overrides of any setting above |

Nearly 600 settings in total, every one from `server.properties`, `bukkit.yml`, `spigot.yml`,
`commands.yml` and Paper's files included. Those files **are not written to disk any more**: the
Bukkit, Spigot and Paper configuration objects plugins read, including `Bukkit.spigot().getConfig()`,
are built in memory from the TOML files.

- **`server.properties` mode**, on by default, for Pterodactyl and other panels:
  `config/server.properties` holds only the settings that differ from their defaults and wins over
  the TOML files. Vanilla keys keep their names (`motd`, `max-players`, `server-port`); everything
  else uses its TOML path. `/legacyfolia properties on|off|status` (console or op level 4) switches
  it at once. Secrets and the EULA never go into it.
- **Automatic migration**: start the jar where a vanilla, Spigot, Paper or Folia server used to run
  and its configuration is imported into the TOML files, conflicts resolved as the original server
  resolved them.
- **Secrets in their own files**: `config/rcon.secret` (generated), `config/forwarding.secret`,
  `config/github.token`.
- **Whitelist, bans and ops** live in `config/`, the user cache in `cache/`.

```toml
# world.toml
[dimensions]
nether = false          # never loaded: no world folder, no chunks, no ticking
end = false

# combat.toml
[combat]
enabled = true          # 1.8 PvP
no-attack-cooldown = true

# plugins.toml
[plugins.auto-update]
enabled = true
hangar = ["ViaVersion"]
modrinth = ["viabackwards"]
github = ["ViaVersion/ViaRewind"]
luckperms = true
```

Reference: **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**. Where every inherited setting went:
[docs/SETTINGS-MAP.md](docs/SETTINGS-MAP.md) (in Spanish).

### Plugin auto-updates

Off by default; turn on `[plugins.auto-update]` in `plugins.toml` and list your plugins. They are
checked at every start, before they load, from **Hangar**, **Modrinth**, **GitHub releases** and
**LuckPerms**.

- Whether a plugin is current is decided by the **checksum its source publishes**, not by the file
  name, and the same checksum verifies the download.
- Older jars of the same plugin are removed, so the loader never sees two.
- A source being down is a warning if the jar is already there, and **stops the server** if it is
  not, since the missing plugin may be the one granting permissions.
- Release channels fall back to beta and alpha when a plugin publishes nothing else, and GitHub
  pre-releases are accepted.

Detail: **[docs/PLUGIN-UPDATER.md](docs/PLUGIN-UPDATER.md)**

### Turn off the Nether and the End

For a lobby, a minigame server or anything that never leaves the overworld. A dimension that is off
is never created: no world folder, no chunks, nothing ticking, and its portals do nothing. In one
measurement on an empty server, start-up went from 23.5 s to 10.8 s with both off. Turning one off
later does not delete its folder.

### Lobby spawn and the Nether roof

`always-spawn-at-world-spawn` in `world.toml` sends every joining player to the world spawn,
either of the world they left (`lobby-spawn-world = "logout-world"`) or of the main world
(`"main-world"`). Nothing about the player is deleted. `disable-nether-roof` stops blocks being
placed above y=127 in the Nether.

### Adaptive view distance

Off by default (`[chunks.adaptive-view-distance]` in `performance.toml`). Paper already sends each
client only what it asked for, but still loads and ticks the server's full distance around
everybody. With this on, each player's load and simulation distance follow their client's request,
and drop further while they are away, with anti-AFK macro and auto-clicker detection so that "away"
means away. Players with `legacyfolia.chunkscope.unlimited` may go past the server's view distance.

### An EULA prompt instead of a stop

On first start the server asks at the console: **Enter** agrees, **Escape** stops. The answer is
`eula = true` in `config/server.toml`, which replaces `eula.txt`. A server with no console attached
(systemd, Docker, `--noconsole`) is never asked and stops with a message saying what to set, as
upstream does.

---

## Quick start

1. Install **Java 25**.
2. Download `legacyfolia-paperclip-<version>.jar` from
   [Releases](https://github.com/adrielorihuela/LegacyFolia/releases), or build it (below).
3. Start it in an empty folder, or in the folder of the server you want to migrate:

   ```bash
   java -Xms4G -Xmx4G -jar legacyfolia-paperclip-<version>.jar nogui
   ```

4. Answer the EULA prompt. The server writes `config/`, generates `config/rcon.secret`, and creates
   `config/forwarding.secret` and `config/github.token` empty for you to fill in when needed. An
   existing server's configuration is imported first and its files backed up.
5. Open `config/`: every setting is there, and every one is commented. Restart to apply an edit;
   what a reload picks up is in [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

To play the 1.8 PvP with 1.8 clients, install [ViaVersion](https://hangar.papermc.io/ViaVersion/ViaVersion),
ViaBackwards and ViaRewind, in versions that support 26.3; the auto-updater can keep them current.

## What is not identical to 1.8 or vanilla

LegacyFolia aims to match 1.8.8 (Spigot) for combat and vanilla 26.3 for everything else. Known
differences:

- **Sword blocking** has no animation on a modern client (there is none left in it to trigger); 1.8
  clients through ViaVersion see it.
- **Sneaking hitbox**: a modern client crouches to 1.5 blocks with eyes at 1.27 and predicts its own
  collisions, so the 1.8 box (1.8 tall, eyes at 1.54) is not kept; projectiles thrown while sneaking
  do leave from 1.54.
- **Beds, honey and wind charges** keep their modern fall handling; the fishing rod keeps modern
  logic in water and when stuck in a block.
- **Things only the client draws**, such as the hurt camera tilt and a brief pearl cooldown on a
  modern client.
- **Locator bar**: under regionised threading another player's move can reach your bar up to one
  tick later than in vanilla, and several moves in one tick arrive as one update.
- Everything Folia itself changes compared with Paper and vanilla (see
  [Folia's README](https://github.com/PaperMC/Folia) and [REGION_LOGIC.md](REGION_LOGIC.md)).

Details: [docs/PVP-1.8.md](docs/PVP-1.8.md#what-could-not-be-made-identical).

## Building

```bash
./build.sh          # build.bat on Windows
```

Applies the patches and builds the jars into `folia-server/build/libs/`; the one to run is
`legacyfolia-paperclip-<version>.jar`. Requires Java 25. On Windows, applying the patches needs Git
long paths (`git config core.longpaths true`).

## Updating to a new Folia build

```bash
./update.sh         # update.bat on Windows
```

Adds the upstream remote if it is missing, fetches Folia's `ver/26.3.x`, merges it and re-applies
the patches. See **[docs/UPDATING.md](docs/UPDATING.md)**. Almost all of this fork is ordinary
source in `folia-server/src/main/java`; the patches into Minecraft and Paper are thin hooks that call
into it, so merges stay cheap.

## Documentation

| Document | Contents |
|---|---|
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | The TOML files, `server.properties` mode, migration, secrets, RCON, lobby, dimensions |
| [docs/PVP-1.8.md](docs/PVP-1.8.md) | Every 1.8 combat mechanic, its formula, its setting, and what was verified |
| [docs/PLUGIN-UPDATER.md](docs/PLUGIN-UPDATER.md) | The plugin auto-updater and each source |
| [docs/UPDATING.md](docs/UPDATING.md) | Merging new Folia builds and handling patches |
| [docs/SETTINGS-MAP.md](docs/SETTINGS-MAP.md) | Where every inherited setting went (Spanish) |
| [docs/PROGRESS.md](docs/PROGRESS.md) | Project state and history (Spanish) |

## Compatibility

- **Plugins**: everything that runs on Folia runs here; declare `folia-supported: true` as you would
  for Folia. The Bukkit, Spigot and Paper configuration objects hold the same values the YAML files
  would.
- **Clients**: 26.3 natively; older clients, including 1.8, through ViaVersion, ViaBackwards and
  ViaRewind.
- **Proxies**: Velocity modern forwarding and BungeeCord, as upstream.

## Licence

GPL-3.0, as Folia and Paper are. Patches are licensed per [`PATCHES-LICENSE`](PATCHES-LICENSE).

The 1.8.8 reference sources are **not** distributed here: they are Mojang's, decompiled, and not
ours to redistribute. `reference/fetch-1.8.8-source.sh` rebuilds them from the exact Spigot build
the combat was read from, for anybody who wants to check the fidelity themselves.

## Credits

- [PaperMC](https://github.com/PaperMC) for Paper and Folia, which this is a fork of.
- The adaptive view distance began as [ChunkScope](https://github.com/adrielorihuela/ChunkScope) and
  the plugin updater as the one in [HybridVelocity](https://github.com/adrielorihuela/HybridAuth).
