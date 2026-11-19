# Testing a release candidate

What the automated tests cannot establish has to be checked by hand, on a real server with real
clients, before `v1.0.0`. This is the checklist. Each item says how to set it up, what to do and what
should happen; the expected results come from the code and from the other documents, which are
linked where they explain the reason.

The tests of `LegacyFoliaTestSuite` check formulas, file handling and packet contents. None of
them has a person holding a mouse. In particular nobody has yet tried the locator bar, the 1.8 PvP or
the weapon tooltip with real clients (see `PROGRESS.md` §5). Bots now cover most of the list (see
[the automated benches](#the-automated-benches)); this page is for what they cannot judge.

## What you need

- **Java 25.**
- **The jar.** `legacyfolia-paperclip-<version>.jar` from the GitHub pre-release
  (`legacyfolia-paperclip-1.0.0-rc.3.jar` for RC 3). The paperclip jar is the server; it downloads
  the Minecraft server jar on first start. Nothing else from the build is needed.
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
   **Never share `config/rcon.secret`, `config/forwarding.secret`, `config/github.token` or
   `config/management-keystore.secret`.**
5. The plugin list (`plugins` in the console) and a video or screenshots when it is visual.

Send the report to the maintainer who published the pre-release. The fork's GitHub issue tracker is
currently off, and PaperMC does not support forks, so problems with anything on this page must not be
reported to Paper or Folia. Results are recorded in `docs/PROGRESS.md`; any FAIL is fixed, and its
item retested, before `v1.0.0`.

---

## The automated benches

Since `v1.0.0-rc.2` four programs in `testing/` run part of this list without people. They start the
jar in fresh temporary folders (offline mode, the EULA accepted only there), never touch a Minecraft
account and write their results to `testing/results/` (git-ignored). Pass them the RC 3 jar
(`legacyfolia-paperclip-1.0.0-rc.3.jar`, from the pre-release or from `./build.sh`); where the
tables below say **bench**, the item has a PASS, FAIL or NOT_RUN of its own in the report. They need
Java 25; `bot18` and `client` also need Node 24 (`load` runs `java LoadTest.java`).

| Bench | What it is | Run it | Ports | Result |
|---|---|---|---|---|
| `testing/bots` | Protocol bots (MCProtocolLib 26.3-2) and the console: one result per item of this page, with the groups `locator`, `pvp`, `tooltips`, `off`, `fresh`, `dims`, `lobby`, `migration` and `eula` | `pwsh testing/bots/run-all.ps1 -Jar <jar> -Properties root [-Only locator,pvp] [-Keep]`<br>`testing/bots/run-all.sh -j <jar> -P root [-O locator,pvp] [-k]` | 25611 (game), 25612 and 25613 (RCON) | `bots.json` |
| `testing/bot18` | Two Minecraft 1.8.8 clients (mineflayer 4.37.1) through ViaVersion 5.12.2, ViaBackwards 5.12.2 and ViaRewind 4.2.0: join, damage, no cooldown, sword blocking, the weapon tooltip | `pwsh testing/bot18/run.ps1 -Jar <jar> [-Port 25621] [-Keep]`<br>`testing/bot18/run.sh --jar <jar> [--port 25621] [--keep]` | 25621 | `bot18.json` |
| `testing/client` | A real 26.3 client (HeadlessMC 3.0.0-RC8 with Fabric, offline) joins, reads and sends a chat line, and leaves | `pwsh testing/client/run.ps1 -Jar <jar> [-Port 25622] [-Keep] [-ShowOutput]`<br>`testing/client/run.sh --jar <jar> [--port 25622] [--keep] [--verbose]` | 25622 | `client.json` |
| `testing/load` | Adds bots in steps and reads the server over RCON; see [LOAD-TESTING.md](LOAD-TESTING.md) | `testing/load/run-load.ps1` or `run-load.sh`, with the options of that page | the server's | `load-<date>.csv` |

Notes on running them:

- **`-Properties root` (`-P root`) is needed for RC 2.** The bots bench looks for `server.properties` in
  `config/` by default, which is where RC 1 kept it; RC 2 keeps it in the server root.
- Without a jar the bots bench takes the newest `testing/.cache/*.jar`. It builds itself with Gradle
  (from the PATH, or the repository's wrapper).
- The first run of each downloads what it needs into `testing/.cache`: the Via plugins and
  mineflayer (`npm install`) for `bot18`, and for `client` HeadlessMC and the game files, about
  600 MB from Mojang's servers. `-Keep` keeps the temporary server folder for a failing run.

Results on the final RC 2 jar:

| Bench | Result |
|---|---|
| bots | 81 PASS, 0 FAIL, 16 NOT_RUN |
| bot18 | 5 of 5 (`join`, `melee_damage`, `no_attack_cooldown`, `sword_blocking`, `tooltip_damage`) |
| client | 4 of 4 (`join`, `read_chat`, `send_chat`, `leave`) |
| load | No capacity figure: SoulFire on Windows crashes and its bots drop at about 14. At 10 to 14 bots the server held TPS 20 and MSPT 13 to 23. Details in [LOAD-TESTING.md](LOAD-TESTING.md) |

The 16 items the bots bench cannot do on its own (the results of the unattended ones are in [the unattended run](#unattended-run-of-the-not_run-items-rc-3)): `1j` with a team (Folia has no
`/team`), `2h` arrows (the shooter's motion belongs to the client), `3d` (`bot18` only checks the
modifier, not what the client draws), `4a`, `4b`, `4c` and `4e` (the EULA prompt needs a real
terminal), `5i` (needs a plugin), `9b` to `9h` (the updater downloads from Hangar and GitHub) and
`10c` (a soak with ViaVersion). A PASS from a bot is a PASS of the packets it reads; nobody has felt
the combat or watched the bar yet.

Folia has no `/data`, `/item`, `/loot` or `/team`, so some checks differ from what the vanilla commands
would allow: read health from the client's bar (the bots read the health packets), compare stored
items by their tooltip, and colour the locator dot with `/waypoint` only.

---

## Unattended run of the NOT_RUN items (RC 3)

The items the bots bench cannot do, run on 2026-11-19 without people or real clients, on
`legacyfolia-paperclip-1.0.0-rc.3.jar` (`version`: LegacyFolia 26.3-DEV-main@9c76923).
OS: Windows 11 (10.0.26300). Java: Temurin 25.0.2. Node 24.19. Offline mode, ports 25670 to 25689, one
server at a time, `-Xmx2G`. Linux was not available (WSL has no distribution), so there was no Linux
TTY. Still yours: `2h`, `3d`, `10c`, the Linux TTY of item 4, and a person looking at the bar and the
combat.

| # | Result | Evidence |
|---|---|---|
| 4a | PASS | Real Windows pseudo-terminal (ConPTY through node-pty 1.1.0), fresh directory. The prompt is "Press ENTER to agree, or ESC to stop the server. (Agreeing writes eula = true to config/server.toml.)", under the aka.ms link |
| 4b | PASS | ESC and Ctrl-C (`\x03`) each print "Not agreed, so the server will not start. config/server.toml still says eula = false."; exit code 0, `eula = false` kept |
| 4c | PASS | ENTER: "Agreed. config/server.toml now says eula = true; you will not be asked again." and "Done (20s)" follows |
| 4d | PASS | Next start: no prompt, "Done (14s)" |
| 4e | PASS | `eula = false`, `eula-prompt-timeout-seconds = 10`: "Nobody answered, so nothing was agreed to.", and the server stops about 10 s after the prompt. The non-default timeout moves into `server.properties` on that start, as in 5c |
| 4f | PASS | Piped input (`echo \| java -jar`) and `--noconsole`: "You need to agree to the EULA (https://aka.ms/MinecraftEULA) in order to run the server. Set eula = true in config/server.toml to agree." |
| 5i | PASS, with notes | `testing/probe-plugin` (LegacyProbe) logs `Bukkit.getMotd()`, `getMaxPlayers()`, `getViewDistance()`, `hasWhitelist()`, the whitelist, ops and bans, the spawn limits, and `Bukkit.spigot().getBukkitConfig()`, `getSpigotConfig()` and `getPaperConfig()`. At start-up every value equals the TOML or `server.properties` value (motd "Probe One", max-players 23, view-distance 8, spawn-limits.monsters 44, mob-spawn-range 6, merge-radius.item 1.5, Velocity enabled). Of the old files the root holds only `server.properties` (and `config/server.toml`): no `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-*.yml`, `whitelist.json`, `ops.json` or `eula.txt`. `/legacyfolia properties status`, `off` and `on` and `/whitelist on` and `off` (`hasWhitelist` true and false, `white-list=true` appears in `server.properties`) behave as in 5e to 5g. Notes: (1) after `/paper reload` only the Paper object changes (`proxies.velocity.enabled` true to false) and after `/spigot reload` only the Spigot one (mob-spawn-range 6 to 7, merge-radius 1.5 to 2.5); the `bukkit.yml` values, the MOTD and the view distance that a plugin sees stay at their start-up values until a restart, and `/reload` does not exist on Folia. [CONFIGURATION.md](CONFIGURATION.md) ("Reloading") says all the objects are rebuilt on any reload. (2) The Velocity secret is not in `getPaperConfig()` (`proxies.velocity.secret` is empty): it lives in `config/forwarding.secret` |
| 5j | PASS | `config/server.properties` with `motd=Old` only: "Moved config\server.properties to the server root", MOTD "Old". With a root `server.properties` (`motd=Root`) as well: a warning that both exist, the root one wins and the other stays |
| 6a to 6h | PASS | Real Paper 26.3 build 256 and Folia 26.3 build 26 jars from fill.papermc.io (SHA-256 checked against the API), each run once with the EULA accepted only there. Then `whitelist add Alice`, `op Bob`, `ban Mallory`, `ban-ip`, and `motd`, `max-players`, `difficulty`, `view-distance`, `spawn-protection`, an RCON password, `spigot.yml`, `bukkit.yml`, `paper-global.yml` (Velocity on, with a secret), `paper-world-defaults.yml` and a per-world `paper-world.yml` changed. LegacyFolia then logs "Found the configuration of an older server" and "Imported 19 setting(s) from 10 file(s)"; 10 are listed as not importable in `import-report.txt` (maps with no TOML form, `management-server-secret` and the like). The values are in `server.properties` (18 lines, the per-world one as `worlds.world.chunks.max-auto-save-chunks-per-tick=7`); the lists in `config/players.toml`; the JSON files, `eula.txt`, the old YAML and `server.properties` in `config/legacy-backup/<date_time>/` beside `import-report.txt`; `usercache.json` in `cache/`; the secrets in `rcon.secret` and `forwarding.secret`, and `grep` finds them in no TOML file and not in `server.properties`. The root holds the jar, `server.properties` and the generated directories. A second start changed no file (checksums of everything outside `logs`, `world` and `cache` equal). Folia with `misc.enable-nether: false` in `paper-global.yml`: "The Nether is disabled in the configuration, so its world will not be loaded.", `dimensions.nether=false`, the folder kept. `allow-nether=false` alone in the old `server.properties` is not imported, because `paper-global.yml` wins "as the original server read it" (`import-report.txt`); that was not checked against the real server |
| 1j-team | FAIL (Folia) | A team colour does not reach the dot. Folia's `Scoreboard#registerNewTeam` throws `UnsupportedOperationException`, so `probeteam` makes the team on the NMS scoreboard by reflection. The bots bench (`LEGACYFOLIA_PROBE_PLUGIN=testing/probe-plugin/build/LegacyProbe.jar`, scenario `1j-team`) finds B in team `pt_red`, colour RED, and A receives TRACK and UPDATE packets for B, all with an empty colour, also after B moves and after the team is recoloured. Cause: Folia's patch makes `Entity#getTeam()` return null (`Entity.java`, "Folia start - region threading") and `Waypoint.Icon#cloneAndAssignStyle` reads the team from it. `/waypoint modify ... color` works (1j) |
| 7a to 7i | PASS | `bots -Only dims,lobby`: 9 PASS. New scenarios `7a-portal` and `7b-portal` put a creative bot in a lit nether portal and in an end portal: with the dimension on it travels (the control), with `nether = false` or `end = false` it stays in the overworld. `-Only dims`: 8 PASS |
| 9a | PASS | `config/github.token` is created empty at the first start |
| 9b | FAIL | `hangar = ["ViaBackwards"]`, `modrinth = ["viaversion"]`, `github = ["ViaVersion/ViaRewind"]`. Each is downloaded and checksummed into `plugins/` ("Updating viaversion to 5.12.2 from modrinth...", "ViaBackwards 5.12.2 installed.") but is **not loaded in that start**: the plugin directory was read before ("PluginInitializerManager: Initialized 1 plugin" at 15:54:13, the updater at 15:54:26), so ViaRewind fails with "Unknown/missing dependency plugins: [ViaVersion, ViaBackwards]" and `plugins` lists only it. They load on the next start. LuckPerms was not enabled (it installs unverified) |
| 9c | PASS | Start again: "viaversion 5.12.2 is up to date.", "ViaBackwards 5.12.2 is up to date.", "ViaVersion/ViaRewind 4.2.0 is up to date."; nothing downloaded; all three load |
| 9d | FAIL (Windows) | An old `ViaRewind-4.1.1.jar` (a real release, SHA-256 equal to the one GitHub publishes) in `plugins/`: the updater downloads 4.2.0 but cannot remove the old jar ("could not be removed ... being used by another process") and keeps the old one. The jar is already open because the directory was read first (9b). Same with a corrupted jar (right name, wrong bytes): the checksum mismatch is noticed, "Updating ViaBackwards...", but the file cannot be replaced. Deleting the old jar by hand and starting again installs 4.2.0. Linux not tried |
| 9e | PASS | A made-up Modrinth slug: "Could not install no-such-plugin-zzz-404: Modrinth has no project called ...", "Could not update plugins: ...", and start-up stops (it also writes a crash report) |
| 9f | PASS | All traffic sent to a closed proxy port: a warning per plugin ("could not reach api.modrinth.com (null)"), and the server starts with the jars it has |
| 9g | PASS | Same, with `ViaVersion-5.12.2.jar` deleted: "Could not install viaversion: could not reach api.modrinth.com", and start-up stops |
| 9h | PASS | With neither a token in `github.token` nor `GITHUB_TOKEN`, the log says GitHub allows sixty requests an hour; with `GITHUB_TOKEN` set, and with a token in the file, that line is gone. A made-up token was used, so GitHub answered 401 and the check of ViaRewind failed with a warning; no real token was used |

To run the new checks again: `pwsh testing/bots/run-all.ps1 -Jar <jar> -Properties root -Only dims`; for
`1j-team`, build the plugin with `testing/probe-plugin/build.sh <a server folder that has started once>`
(it compiles against that folder's `libraries/`), set `LEGACYFOLIA_PROBE_PLUGIN` to
`testing/probe-plugin/build/LegacyProbe.jar` and run with `-Only locator`. Without the variable
`1j-team` stays NOT_RUN.

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
| 1j | `/waypoint modify B color blue`. (Folia has no `/team`, so a team colour cannot be set; skip that part.) | B's dot changes colour. **Bench:** the `/waypoint` part; the team part is FAIL on Folia, see [the unattended run](#unattended-run-of-the-not_run-items-rc-3). |
| 1k | B dies and respawns, and B logs out and back in. | The bar keeps working for both. |

A difference from vanilla that is expected: what a transmitter does reaches a receiver up to one
tick later, and several moves in one tick arrive as one update. Nobody should be able to see that.

## 2. 1.8 PvP

Enabled by default (`[combat] enabled = true` in `config/combat.toml`). Every rule is described in
[PVP-1.8.md](PVP-1.8.md). To read damage exactly, look at the victim's health before and after a
hit (20 is full, 1 is half a heart; Folia has no `/data`, `/item`, `/loot` or `/team`, so read health from the client's bar; the bot bench reads the health packets). Mind the regeneration: under 26.3 combat a player on a full stomach heals 1 every half second, which eats a point of what you read; under 1.8 combat it does not. Test with no armour, no
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
| 2i | Stand near primed TNT and a creeper explosion. | Damage and push follow 1.8; Blast Protection does not reduce the push. Primed TNT explodes at half its height, as in 1.8: at 6 blocks and a block up, on normal difficulty, it does 9 (easy: `min(d / 2 + 1, d)`, hard: 1.5 times). Record how it compares with 1.8. |
| 2j | Splash Instant Damage and Instant Health; drink Strength I and hit with a diamond sword. | Strength I diamond sword: 18.4. Splash strength falls off over 4 blocks from where the potion broke. |
| 2k | Eat an enchanted golden apple; craft one from 8 gold blocks around an apple. | Absorption I 2:00, Regeneration V 0:30, Resistance and Fire Resistance 5:00. The recipe works. |
| 2l | Drop from the top of a slab whose top is 3.5 blocks above the ground. | 1 damage: fall damage rounds up, as in 1.8. |
| 2m | B joins the server and A hits B within 3 seconds; then B dies, respawns and A hits at once. | No damage for 3 seconds after joining. After a respawn the hit lands at once, as on Spigot 1.8.8 (what is left of the three seconds from joining still protects, as the player object is the same: kill B only after 3 seconds). |
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
| 3c | Creative mode: take a diamond sword from the creative menu, move it around, put it in a chest. Check what is stored (Folia has no `/data`; the bot bench compares the stored item's modifiers with 26.3's default using `execute if items`). | Tooltip shows 8. The stored item has no `attribute_modifiers` component added. No item flickers: the server sends the creative slot back once, with the same 1.8 figure the client already shows, because it cannot tell a stack the client was shown from one it built itself ([PVP-1.8.md](PVP-1.8.md), the creative-mode safeguard). |
| 3d | 1.8.9 client through ViaVersion: hover a diamond sword. | The figure a 1.8 server shows for it: the modifier, +7, without the player's own 1. Record exactly what is shown. **Bench:** `bot18` checks that the 1.8 client receives the modifier 7; what it draws is still NOT_RUN. |
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

On by default. `server.properties`, in the server root beside the jar, holds only the settings that
differ from their defaults, and wins over the TOML files. Details: [CONFIGURATION.md](CONFIGURATION.md).

| # | Do | Expect |
|---|---|---|
| 5a | Fresh server, start once. | `server.properties` exists in the root, not in `config/`, with only its explanatory header and no settings. |
| 5b | Stop, add `motd=Test` and `gameplay.hunger.jump-walk-exhaustion=0.1` to it, start. | The server list shows "Test"; jumping costs the new figure. The TOML files still show the defaults. |
| 5c | Stop, change `max-players` in `config/server.toml` to 5, start. | The change is moved into `server.properties` as `max-players=5`, and `server.toml` goes back to the default. |
| 5d | Stop, delete the `motd` line, start. | The MOTD is back to the default. |
| 5e | In game: `/whitelist on`, `/defaultgamemode creative`, `/setidletimeout 5`. | `white-list=true` and `player-idle-timeout=5` appear in `server.properties` without a restart. `/defaultgamemode` does not write `gamemode`: in vanilla it only sets the level's game type (the dedicated server's `setGameMode`, which would write the file, is called only by the management protocol, not by the command). `/difficulty` changes the world's difficulty only, as in vanilla and Paper. |
| 5f | Console: `legacyfolia properties status`, then `legacyfolia properties off`. | `off` moves every setting into the TOML files and deletes `server.properties`; lines that are not settings are kept in `server.properties.unused`, in the root. |
| 5g | `/whitelist off` with the mode off, then `legacyfolia properties on`. | The change is written to `server.toml`; `on` moves every non-default back into a new `server.properties`. |
| 5h | Look through `server.properties` at every step. | Never an `eula` line, and never a password, secret or token. |
| 5i | Run a plugin that reads `getServer().getMotd()`, `Bukkit.spigot().getConfig()` or Paper's configuration (any plugin that shows the server's settings). | It sees the values from the TOML files and `server.properties`. There is no `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml` or `paper-world-defaults.yml` on disk. |
| 5j | Stop a server, put an RC 1 `config/server.properties` with `motd=Old` into `config/` (and none in the root), start. | The log says it moved `config/server.properties` to the server root; the MOTD is "Old". With a `server.properties` in the root as well, nothing is moved and the log says the one in `config/` is ignored. |

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
| 6c | The noted settings are in effect. With the mode on, the non-default ones are in `server.properties` in the root; per-world ones under `worlds.<world>.` |
| 6d | The whitelist, the op and the ban are in `config/players.toml` (`[[whitelist]]`, `[[ops]]`, `[[banned-players]]`, `[[banned-ips]]`) and in effect; the JSON files are gone from the root and are in the same `config/legacy-backup/<date_time>/` as the other originals; `usercache.json` is in `cache/`. |
| 6e | The RCON password is in `config/rcon.secret`, the Velocity secret in `config/forwarding.secret`, and neither appears in any TOML file or in `server.properties`. |
| 6f | An `eula.txt` that said `true` means no prompt. |
| 6g | The worlds load as they were. The Nether and the End stay off if the old server had them off, including `allow-nether=false` in an old `server.properties`. |
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
| 9h | Put a GitHub token in `github.token` and add a `github` entry. Then empty the file and start with the `GITHUB_TOKEN` environment variable set to a token. | No message about the sixty-request limit in either case; with neither, the message appears. |

## 10. General

| # | Do | Expect |
|---|---|---|
| 10a | Fresh directory, start, accept the EULA, stop. | `server.properties` is in the root, beside the jar. No `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-*.yml`, `eula.txt`, `whitelist.json`, `ops.json`, ban lists or `usercache.json` in the root: they are in `config/` and `cache/`, or not written at all. `help.yml` and `permissions.yml` are still read from the root, as before. |
| 10b | `config/` | Nine TOML files (`server`, `world`, `network`, `performance`, `gameplay`, `combat`, `plugins`, `worlds`, and `players`), the eight settings files with every setting commented, and `players.toml`, which holds the whitelist, ops and bans as data. |
| 10c | Play normally for a while with several players spread far apart, with ViaVersion loaded. | No errors in the log other than ones a plugin causes. |
