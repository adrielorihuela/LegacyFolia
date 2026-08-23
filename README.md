# LegacyFolia — a Folia fork with 1.8 PvP, one config file, and plugin auto-updates

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)](https://minecraft.net)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net)
[![Upstream](https://img.shields.io/badge/fork%20of-PaperMC%2FFolia-blue)](https://github.com/PaperMC/Folia)
[![Licence](https://img.shields.io/badge/licence-GPL--3.0-lightgrey)](PATCHES-LICENSE)

**LegacyFolia** is a fork of [Folia](https://github.com/PaperMC/Folia) — the multithreaded Minecraft
server — that puts back **1.8 PvP**, fixes the **locator bar** Folia disabled, replaces the scattered
configuration with **one commented file**, and **keeps your plugins up to date on its own**.

It is a Folia server first: regionised multithreading, the full Paper and Bukkit plugin API, and
every upstream commit merged in. Everything below is added on top, and every part of it can be
switched off.

---

## Why this fork exists

Folia scales a Minecraft server across CPU cores by giving each region of the world its own tick
loop. That is excellent, and this fork does not change any of it. What it changes is everything
around it that makes a Folia server tedious to run:

| Problem | What LegacyFolia does |
|---|---|
| Modern combat killed 1.8 PvP | Restores it, from 1.8.8's own source, mechanic by mechanic |
| `/gamerule locator_bar` does nothing on Folia | Fixed properly, region-safe |
| Settings spread across six files in three formats | One commented `config/server.config` |
| No way to turn off the Nether or the End | One `[dimensions]` section — and a **2× faster start-up** |
| Velocity secret sits in plain text in a shared YAML | Its own `config/forwarding.secret` |
| The server stops on first start to make you edit `eula.txt` | Asks you at the console instead |
| Updating plugins is a manual chore | Checked and updated at every start, verified by checksum |
| Every player is sent chunks they never look at | Each gets only the chunks they use |

---

## Features

### 1.8 PvP, from the original source

Not "close to 1.8" — read out of the 1.8.8 sources and reproduced. Enabled by default; every knob
lives under `[combat]`.

- **No attack cooldown.** Swing as fast as you can click. No damage scaling, no charge indicator.
- **No sweeping arc.**
- **1.8 knockback.** The one that matters most: modern knockback only lifts a target standing on the
  ground, so hitting somebody already airborne does nothing to their height. In 1.8 it lifted them
  every time — which is what lets a combo hold somebody up.
- **Sprint-jump criticals.** Modern Minecraft refuses a critical to a sprinting attacker. 1.8 did
  not. That single condition is most of what makes 1.8 combat read differently, and it is restored.
- **W-tap knockback.** Sprinting adds knockback, cancels your sprint and cuts your own speed.
- **Sword blocking.** Right-click to raise a sword and halve incoming damage.
- **No shields, no off-hand.**

Full detail, including what was verified against the original and what was not:
**[docs/PVP-1.8.md](docs/PVP-1.8.md)**

### The locator bar, fixed

`/gamerule locator_bar` does nothing on Folia. Its four supporting methods are emptied out upstream
with the note *"Need to implement waypoints correctly"* ([PaperMC/Folia#370](https://github.com/PaperMC/Folia/issues/370)),
because the locator bar only shows somebody past 332 blocks — which under regionised threading means
they are almost always on another thread, and reading their entity from yours is a crash waiting to
happen.

LegacyFolia fixes it rather than working around it. Transmitters publish an immutable snapshot on
their own thread; receivers read only snapshots. Nothing crosses a region boundary, and the whole
cost is one map write per moving player.

### One configuration file

`config/server.config` — TOML, grouped by what settings **do** rather than which server introduced
them, with a comment on every key explaining what it is for.

It does not replace `bukkit.yml`, `spigot.yml` or `paper-global.yml`. Those are still generated,
still read, and still what plugins see; this file overrides them at start-up. **No plugin can tell
the difference.**

```toml
[dimensions]
nether = false          # never loaded: no world folder, no chunks, no ticking
end = false

[combat]
enabled = true          # 1.8 PvP
no-attack-cooldown = true

[plugins.auto-update]
enabled = true
hangar = ["ViaVersion"]
modrinth = ["viabackwards"]
github = ["ViaVersion/ViaRewind"]
luckperms = true
```

Reference for every key: **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**

### Plugin auto-updates

Listed plugins are checked at every start, before they load, from **Hangar**, **Modrinth**,
**GitHub releases** and **LuckPerms**.

- Whether a plugin is current is decided by the **checksum its source publishes**, never by the file
  name, and the same checksum verifies the download.
- Old jars of the same plugin are removed, because two jars claiming one plugin id make the loader
  refuse whichever it reads second.
- A source being down is a warning if the jar is already there, and a **hard stop** if it is not —
  the missing plugin may be the one granting permissions.
- GitHub walks the whole release list, so a project whose newest tag is a pre-release still works.

Detail: **[docs/PLUGIN-UPDATER.md](docs/PLUGIN-UPDATER.md)**

### Turn off the Nether and the End

For a lobby, a minigame server or anything that never leaves the overworld. A dimension that is off
is never created: no world folder, no chunks in memory, nothing ticking, no region threads.

Measured on an empty server: **23.5s → 10.8s to start**.

### Adaptive view distance

A server's view distance has to suit whoever wants the most, so everybody else is sent and ticked
chunks they throw away on arrival. Each player gets what their client actually asked for, and a
player who is away is reduced further — with anti-AFK macro and auto-clicker detection, so
"away" means away.

### An EULA prompt instead of a wall

First start asks at the console. Press **Enter** to agree, **Escape** to stop. A server with no
console attached — systemd, Docker, `--noconsole` — is never asked and stops exactly as before,
because a prompt nobody can see is indistinguishable from a hang.

### The forwarding secret in its own file

`config/forwarding.secret`, the same name Velocity uses, so you can copy the proxy's file straight
across. If you already had one in `paper-global.yml` it is moved for you and the old key blanked —
configurations get pasted into issues and photographed in screenshots, and secrets go with them.

---

## Getting started

```bash
java -Xms4G -Xmx4G -jar legacyfolia.jar nogui
```

Requires **Java 25**. On first start it asks about the EULA, writes `config/server.config`, and
creates `config/forwarding.secret` and `config/github.token` empty for you to fill in when needed.

Then open `config/server.config` — everything is in there, and everything is commented.

## Building

```bash
./build.sh
```

Applies the patches and builds the jar into `folia-server/build/libs/`. On Windows, `build.bat`.

## Updating to a new Folia build

```bash
./update.sh
```

Adds the upstream remote if it is missing, fetches, merges, and re-applies the patches. Safe on a
fresh clone from any machine. See **[docs/UPDATING.md](docs/UPDATING.md)**.

Merges stay cheap by design: almost all of this fork is ordinary source in
`folia-server/src/main/java`, and the patches into Minecraft are a handful of lines each that call
into it.

---

## Compatibility

- **Plugins**: everything that runs on Folia runs here. The Bukkit, Spigot and Paper configuration
  files are untouched and still authoritative for what plugins read. Declare `folia-supported: true`
  as you would for Folia.
- **Clients**: 26.2 natively; 1.8 clients through [ViaVersion](https://hangar.papermc.io/ViaVersion/ViaVersion)
  and ViaBackwards, which is the intended way to play the 1.8 PvP.
- **Proxies**: Velocity modern forwarding and BungeeCord, as upstream.

## Licence

GPL-3.0, as Folia and Paper are. Patches are licenced per `PATCHES-LICENSE`.

The 1.8.8 reference sources are **not** distributed here — they are Mojang's, decompiled, and not
ours to redistribute, which is why CraftBukkit ships BuildTools rather than sources.
`reference/fetch-1.8.8-source.sh` reproduces them from the exact Spigot build the combat mechanics
were read from, for anybody who wants to check the fidelity themselves.

## Credits

- [PaperMC](https://github.com/PaperMC) for Paper and Folia, which this is a fork of.
- The adaptive view distance began as [ChunkScope](https://github.com/adrielorihuela/ChunkScope) and
  the plugin updater as the one in [HybridVelocity](https://github.com/adrielorihuela/HybridAuth).
