# Testing a release candidate

What the automated tests cannot establish has to be checked by hand, on a real server with real
clients, before `v1.0.0`. This is the checklist. Each item says how to set it up, what to do and what
should happen; the expected results come from the code and from the other documents, which are
linked where they explain the reason.

The tests of `LegacyFoliaTestSuite` check formulas, file handling and packet contents. None of
them has a person holding a mouse. In particular nobody has yet tried the locator bar, the 1.8 PvP or
the weapon tooltip with real clients (see `PROGRESS.md` §5).

## What you need

- **Java 25.**
- **The jar.** `legacyfolia-paperclip-<version>.jar` from the GitHub pre-release. The paperclip jar
  is the server; it downloads the Minecraft server jar on first start. Nothing else from the build
  is needed.
- **Two Minecraft 26.3 clients**, on two accounts, for everything that involves two players.
- **A 1.8.9 client** for the ViaVersion items, with ViaVersion, ViaBackwards and ViaRewind on the
  server in builds that support Minecraft 26.3 and Folia. The plugin updater can fetch them; see
  item 9.
- **An RCON client**, such as `mcrcon`, for item 8.
- **A Paper, Spigot or vanilla server directory** to migrate from, for item 6. Use a copy.

Start each test from a fresh, empty directory unless the item says otherwise, and keep the
directory of a failing test as it is.

In-game commands need operator level 4. From the console: `op <name>`. `/legacyfolia` is for the
console and level 4 operators only.

## How to report results

Keep one line per item:

```
LegacyFolia <pre-release tag>, `version`: <what the console's version command prints>
OS: <Windows 11 / Linux ...>   Java: <java -version>
Clients: <26.3, 1.8.9 via ViaVersion x.y.z / ViaBackwards x.y.z / ViaRewind x.y.z>

 1a  PASS
 1b  FAIL   B's dot stayed after B went to the Nether; see below
 2c  SKIP   no second account
```

For every FAIL, add:

1. What you did, step by step, from server start.
2. What you expected (copy it from this page) and what happened instead.
3. `logs/latest.log` from the server, whole. The `version` line in it records the commit the jar was
   built from.
4. The files of `config/` involved. The TOML files hold no secrets and can be shared as they are.
   **Never share `config/rcon.secret`, `config/forwarding.secret` or `config/github.token`.**
5. The plugin list (`plugins` in the console) and a video or screenshots when it is visual.

Send the report to the maintainer who published the pre-release. The fork's GitHub issue tracker is
currently off, and PaperMC does not support forks, so problems with anything on this page must not be
reported to Paper or Folia. Results are recorded in `docs/PROGRESS.md`; any FAIL is fixed, and its
item retested, before `v1.0.0`.

---

## 1. Locator bar

Folia ships the locator bar disabled in effect: `/gamerule locator_bar true` does nothing. LegacyFolia
makes it work as vanilla 26.3's does, with each player's packets built on that player's own region
thread ([README](../README.md), `waypoint/`). Distances matter because Folia gives far-apart players
different regions, which is exactly what the fix has to survive. Vanilla switches a far transmitter to
direction-only at more than 332 blocks.

Setup: players A and B, both 26.3 clients, in the overworld. `locator_bar` is on by default; check
with `/gamerule locator_bar`.

| # | Do | Expect |
|---|---|---|
| 1a | Stand a few blocks apart. | Each sees the other's dot. |
| 1b | Teleport B 1000 blocks away (`/tp B ~1000 ~ ~`). A turns around. | B's dot stays on A's bar and moves with the direction to B. |
| 1c | With B 1000 blocks away, A stands still and B walks a circle around A. | A's bar follows B, although A is not moving. |
| 1d | B walks back towards A, crossing 332 blocks. | The dot stays the whole way; no duplicate and no dot left frozen where it was. |
| 1e | B crouches, then stands. | B disappears from A's bar while crouching and comes back. |
| 1f | B wears a carved pumpkin or a mob head, then takes it off. Then B drinks a potion of Invisibility. | Hidden while worn and while invisible; back afterwards. |
| 1g | B goes to the Nether. | B disappears from A's bar at once, and A's bar shows nothing of B while B is in the Nether. A in the overworld and B in the Nether never see each other. |
| 1h | A follows B into the Nether, then both go to the End and back. | They see each other in whichever dimension they share. |
| 1i | `/gamerule locator_bar false`, then `true`. Nobody moves. | The dots go at once, and come back without anyone having to move. |
| 1j | `/team add red`, `/team modify red color red`, `/team join red B`. Then `/waypoint modify B color blue`. | B's dot changes colour each time. |
| 1k | B dies and respawns, and B logs out and back in. | The bar keeps working for both. |

A difference from vanilla that is expected: what a transmitter does reaches a receiver up to one
tick later, and several moves in one tick arrive as one update. Nobody should be able to see that.

## 2. 1.8 PvP

Enabled by default (`[combat] enabled = true` in `config/combat.toml`). Every rule is described in
[PVP-1.8.md](PVP-1.8.md). To read damage exactly, look at the victim's health before and after a
hit: `/data get entity <victim> Health` (20 is full, 1 is half a heart). Test with no armour, no
enchantments and no effects unless the item says otherwise, in survival, on a world with PvP on.

Two 26.3 clients first, then repeat 2a, 2b, 2c and 2e with a 1.8.9 client through ViaVersion.

| # | Do | Expect |
|---|---|---|
| 2a | Hit each other as fast as possible with a diamond sword. | No attack-cooldown indicator; every hit does full damage (8 health, no crit), no sweep. |
| 2b | Combo: keep hitting a player who is already in the air. | Each hit lifts them again (1.8 knockback), so a combo can keep them up. Sprinting adds extra knockback. |
| 2c | Sprint, jump, and hit while falling. | A critical, 1.5 times the damage, also while sprinting (modern Minecraft refuses it while sprinting). |
| 2d | Hit from about 3.5 blocks, and hit through tall grass or flowers. | Range as in 1.8: feet within 6 blocks with a clear line, 3 without. Grass and flowers block the line as in 1.8. Record how it feels next to a real 1.8 server. |
| 2e | B holds right-click with an iron sword; A hits B with a diamond sword (8). | B takes 4.5 instead of 8 (`(1 + 8) * 0.5`). A 1.8 client shows B's blocking animation; a modern client shows no animation, which is expected. |
| 2f | Try to raise a shield, put anything in the off-hand (swap key, drag, shift-click), and hold a Totem of Undying while dying. | The shield does nothing, the off-hand stays empty, the totem saves nobody. |
| 2g | Throw a snowball or egg at a player, and hook them with a fishing rod. | They flash red and are knocked back away from the thrower or angler, for 0 damage. Reeling in never pulls a hooked player. |
| 2h | Throw ender pearls one after another; shoot arrows. | No pearl cooldown (a modern client may draw one for a moment). Arrows do not take on the shooter's motion. |
| 2i | Stand near primed TNT and a creeper explosion. | Damage and push follow 1.8; Blast Protection does not reduce the push. Record how it compares with 1.8. |
| 2j | Splash Instant Damage and Instant Health; drink Strength I and hit with a diamond sword. | Strength I diamond sword: 18.4. Splash strength falls off over 4 blocks from where the potion broke. |
| 2k | Eat an enchanted golden apple; craft one from 8 gold blocks around an apple. | Absorption I 2:00, Regeneration V 0:30, Resistance and Fire Resistance 5:00. The recipe works. |
| 2l | Drop from the top of a slab whose top is 3.5 blocks above the ground. | 1 damage: fall damage rounds up, as in 1.8. |
| 2m | B joins the server and A hits B within 3 seconds; then B dies, respawns and A hits at once. | No damage for 3 seconds after joining. After a respawn the hit lands at once, as on Spigot 1.8.8. |
| 2n | Repeat 2a with `enabled = false` in `combat.toml` and a restart. | Pure 26.3 combat: cooldown indicator, sweeping, modern damage, shields work. |

Not identical to 1.8 by design (listed in [PVP-1.8.md](PVP-1.8.md)): the sneaking hitbox, beds and
honey, and the blocking animation on a modern client. Do not report those as failures.

## 3. Weapon tooltips

The server rewrites a weapon's attack damage on the way out to the client, so a modern client shows
the 1.8 figure; the item stored on the server keeps its modern components.

| # | Do | Expect |
|---|---|---|
| 3a | 26.3 client: hover a diamond sword, a diamond axe, an iron pickaxe and a wooden shovel. | 8, 7, 5 and 2 Attack Damage (26.3 would show 7, 9, 4 and 2.5). |
| 3b | Same items held by another player, in a chest, in an item frame, in a villager trade and dropped on the ground. | The same figures everywhere. |
| 3c | Creative mode: take a diamond sword from the creative menu, move it around, put it in a chest. Then `/data get entity @s Inventory`. | Tooltip shows 8. The stored item has no `attribute_modifiers` component added. No item flickers or resyncs. |
| 3d | 1.8.9 client through ViaVersion: hover a diamond sword. | The figure a 1.8 server shows for it: the modifier, +7, without the player's own 1. Record exactly what is shown. |
| 3e | Set `legacy-weapon-damage = false` in `combat.toml` and run `/paper reload` (or restart). | The open inventory refreshes and the tooltips show 26.3's figures. |

## 4. EULA prompt

The server asks at the console instead of stopping to make you edit a file. Only ENTER agrees.
The answer is `eula` in `config/server.toml`; there is no `eula.txt`.

| # | Do | Expect |
|---|---|---|
| 4a | Fresh directory, start the jar in a terminal. | The prompt: "Press ENTER to agree, or ESC to stop the server." |
| 4b | Press ESC. | "Not agreed, so the server will not start." `eula = false` stays. Ctrl-C does the same. |
| 4c | Start again, press ENTER. | "Agreed. config/server.toml now says eula = true; you will not be asked again." Start-up continues. |
| 4d | Start again. | No prompt. |
| 4e | Set `eula = false` and `eula-prompt-timeout-seconds = 10`, start, wait. | After 10 seconds: "Nobody answered, so nothing was agreed to." The server stops. |
| 4f | `eula = false`, start with `--noconsole`, and once more with input piped (`echo | java -jar ...`). | No prompt in either: it stops with "You need to agree to the EULA ... Set eula = true in config/server.toml to agree." |

Run 4a to 4e on Windows (cmd, PowerShell or Windows Terminal) and on Linux in a real TTY; the Linux
TTY has not been tried yet. Under systemd or in a container 4f is what should happen.

## 5. server.properties mode

On by default. `config/server.properties` holds only the settings that differ from their defaults,
and wins over the TOML files. Details: [CONFIGURATION.md](CONFIGURATION.md).

| # | Do | Expect |
|---|---|---|
| 5a | Fresh server, start once. | `config/server.properties` exists with only its explanatory header and no settings. |
| 5b | Stop, add `motd=Test` and `gameplay.hunger.jump-walk-exhaustion=0.1` to it, start. | The server list shows "Test"; jumping costs the new figure. The TOML files still show the defaults. |
| 5c | Stop, change `max-players` in `config/server.toml` to 5, start. | The change is moved into `server.properties` as `max-players=5`, and `server.toml` goes back to the default. |
| 5d | Stop, delete the `motd` line, start. | The MOTD is back to the default. |
| 5e | In game: `/whitelist on`, `/defaultgamemode creative`, `/setidletimeout 5`. | `white-list=true`, `gamemode=creative` and `player-idle-timeout=5` appear in `server.properties` without a restart. `/difficulty` changes the world's difficulty only, as in vanilla and Paper. |
| 5f | Console: `legacyfolia properties status`, then `legacyfolia properties off`. | `off` moves every setting into the TOML files and deletes `server.properties`; lines that are not settings are kept in `config/server.properties.unused`. |
| 5g | `/whitelist off` with the mode off, then `legacyfolia properties on`. | The change is written to `server.toml`; `on` moves every non-default back into a new `server.properties`. |
| 5h | Look through `server.properties` at every step. | Never an `eula` line, and never a password, secret or token. |
| 5i | Run a plugin that reads `getServer().getMotd()`, `Bukkit.spigot().getConfig()` or Paper's configuration (any plugin that shows the server's settings). | It sees the values from the TOML files and `server.properties`. There is no `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml` or `paper-world-defaults.yml` on disk. |

## 6. Migration from Paper, Spigot and vanilla

On the first start in a directory another server used, its files are imported into the TOML files
and the originals moved to `config/legacy-backup/<date_time>/` beside an `import-report.txt`.

For each of a Paper (or Folia), a Spigot and a vanilla server directory, on a copy:

1. Note a few non-default settings first: the MOTD, a whitelist with a player in it, an op, a ban,
   a per-world setting in a `paper-world.yml`, a changed `spigot.yml` value, the RCON password, a
   Velocity secret in `paper-global.yml` (Paper).
2. Put the LegacyFolia jar in, start it.

| # | Expect |
|---|---|
| 6a | The log says "Found the configuration of an older server; importing it into config/" and then how many settings were imported from how many files. |
| 6b | `config/legacy-backup/<date_time>/` holds the original files and `import-report.txt`, which names every setting that could not be imported and why. |
| 6c | The noted settings are in effect. With the mode on, the non-default ones are in `config/server.properties`; per-world ones under `worlds.<world>.` |
| 6d | `whitelist.json`, `ops.json`, `banned-players.json` and `banned-ips.json` are in `config/` with their contents; `usercache.json` is in `cache/`. |
| 6e | The RCON password is in `config/rcon.secret`, the Velocity secret in `config/forwarding.secret`, and neither appears in any TOML file or in `server.properties`. |
| 6f | An `eula.txt` that said `true` means no prompt. |
| 6g | The worlds load as they were. The Nether and the End stay off if the old server had them off. |
| 6h | A second start imports nothing and changes no file. |

## 7. Dimension toggles, lobby and Nether roof

In `config/world.toml`. Dimension changes take effect on the next start.

| # | Do | Expect |
|---|---|---|
| 7a | `[dimensions] nether = false`, start. | The log says the Nether is disabled and its world is not loaded. Entering a nether portal does nothing. |
| 7b | Same with `end = false`. | The End is not loaded; an end portal does nothing. |
| 7c | `overworld = false`. | Refused with a warning; the overworld loads anyway. |
| 7d | Turn them back on. | The existing Nether and End folders are used again, nothing was deleted. |
| 7e | `[world] always-spawn-at-world-spawn = true`, `lobby-spawn-world = "logout-world"`. Log out in the Nether, log back in. | You arrive at the Nether's spawn. Inventory, experience and health are kept. |
| 7f | Same with `lobby-spawn-world = "main-world"`. | You arrive at the main world's spawn, whichever world you left from. |
| 7g | `always-spawn-at-world-spawn = false`. | You arrive where you logged out. |
| 7h | `disable-nether-roof = true`; try to place a block above y=127 in the Nether. | Refused. Blocks placed there before stay. |
| 7i | On a fresh server with at least one op, build next to the world spawn as a non-op. | Allowed: spawn protection is 0 by default. |

## 8. RCON

On by default, bound to `127.0.0.1:25575`, with a generated password in `config/rcon.secret`.

| # | Do | Expect |
|---|---|---|
| 8a | Fresh server, start. | The log says "Generated an RCON password in config/rcon.secret. Read it from there; it is never logged." The password, 32 characters, is in that file and nowhere in the log. |
| 8b | `mcrcon -H 127.0.0.1 -P 25575 -p <password> list` on the same machine. | Works. |
| 8c | The same from another machine, using the server's address. | Refused: nothing listens there. |
| 8d | Write your own password into `rcon.secret`, restart. | Yours works. |
| 8e | Delete `rcon.secret`, restart. | A new one is generated. |
| 8f | `[network.rcon] enabled = false`, restart. | Port 25575 is closed. |

## 9. Plugin auto-updater

Off by default. Rules in [PLUGIN-UPDATER.md](PLUGIN-UPDATER.md).

| # | Do | Expect |
|---|---|---|
| 9a | Fresh server, start. | `config/github.token` exists, empty. |
| 9b | In `config/plugins.toml`: `enabled = true`, `hangar = ["ViaVersion", "ViaBackwards"]`, `github = ["ViaVersion/ViaRewind"]`, `luckperms = true`. Start. | Each is downloaded into `plugins/` before the plugins load and then loaded. The log names the version and, for a build that is not a release, its channel. LuckPerms is reported as installed unverified. |
| 9c | Start again. | "is up to date" for each; nothing downloaded. |
| 9d | Put an old jar of one of them (for example `ViaVersion-5.0.0.jar`) beside the current one, start. | The old jar is removed. |
| 9e | Add a slug that does not exist, start. | Start-up stops and the log names the entry and the reason. |
| 9f | Remove that entry; start once with the network off. | Warnings for each plugin, and the server starts with the jars it has. |
| 9g | With the network off, delete one of the jars, start. | Start-up stops: a listed plugin is missing. |
| 9h | Put a GitHub token in `github.token` and add a `github` entry. | No message about the sixty-request limit. |

## 10. General

| # | Do | Expect |
|---|---|---|
| 10a | Fresh directory, start, accept the EULA, stop. | No `server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-*.yml`, `eula.txt`, `whitelist.json`, `ops.json`, ban lists or `usercache.json` in the root: they are in `config/` and `cache/`, or not written at all. `help.yml` and `permissions.yml` are still read from the root, as before. |
| 10b | `config/` | Eight TOML files (`server`, `world`, `network`, `performance`, `gameplay`, `combat`, `plugins`, `worlds`), every setting commented. |
| 10c | Play normally for a while with several players spread far apart, with ViaVersion loaded. | No errors in the log other than ones a plugin causes. |
