# Load testing

`testing/load/` holds a tool that answers one question: how many players does this server take
before it falls behind? It adds bots in steps and, at every step, reads the server over RCON. It
stops at the first limit it hits and writes a CSV and a summary with the maximum sustained bot
count. It works against a local server or a remote one you own.

Only test servers you own or have permission to test, and check that your host allows bot traffic.

## What it does

1. Writes an account file with `--max` offline accounts (`lfb0001`, `lfb0002`, ...) and starts one
   [SoulFire](https://github.com/soulfiremc-com/SoulFire) CLI process (Java 25, protocol 26.3) with
   them. The bots join one every `JoinDelayMs`.
2. Teleports every new bot, over RCON, to one of `Areas` areas 1000 blocks apart (`effect give
   slow_falling` and `tp` to the top of the world, so the drop is harmless). Far-apart areas land in
   different Folia regions, so the load is spread the way real players spread.
3. Whenever the online count reaches a multiple of `Step` it waits `--settle-seconds` (15), then takes
   `--samples` (3) samples over `--hold-seconds` (30): `/tps` for the regions, and heap and GC.
4. Stops when any of these holds, and reports it:
   - a region's MSPT is over 50, or the lowest region TPS is under 18, in at least half of the
     samples of a step;
   - the heap is above 90 % of `-Xmx` in every sample of a step;
   - bots start being kicked (any bot that joined and is gone; `--kick-tolerance` allows some);
   - bots stop joining for `--stall-seconds` (120): server full, whitelist, connection throttle, or the
     bot machine is saturated;
   - SoulFire itself dies, which is reported as inconclusive and not as a server limit.

The steps are checkpoints, not pauses: SoulFire keeps joining bots at `JoinDelayMs` while a step is
measured, and the CSV records the real number online in every row.

## Running it

Requirements: Java 25 (SoulFire needs it) and RCON on the server. The first run downloads the
SoulFire CLI release (about 640 MB, pinned to 2.10.7, `-SoulFireVersion latest` to take the newest)
into `testing/.cache`, which is git-ignored. SoulFire also downloads its own runtime into `~/.soulfire`.

```powershell
# Windows
testing/load/run-load.ps1 -ServerHost play.example.org -Port 25565 -RconPort 25575 `
    -RconPasswordFile C:\path\rcon.secret -Step 50 -Max 400 -JoinDelayMs 1000 -BotRam 4G
```

```bash
# Linux, macOS, Git Bash
testing/load/run-load.sh --host play.example.org --port 25565 --rcon-port 25575 \
    --rcon-password-file ./rcon.secret --step 50 --max 400 --join-delay-ms 1000 --bot-ram 4G
```

| Parameter | Default | Meaning |
| --- | --- | --- |
| `-ServerHost` (alias `-Host`), `-Port` | `127.0.0.1`, `25565` | Game address of the server. |
| `-RconPort`, `-RconPasswordFile`, `-RconPassword` | `25575` | RCON. The password can also be in the `LOAD_RCON_PASSWORD` environment variable, which keeps it out of the process list. |
| `-Step` | `50` | Bots per measuring step. |
| `-Max` | `500` | Most bots to try. Limited to the server's `max-players`. |
| `-JoinDelayMs` | `1000` | Time between bot joins. |
| `-BotRam` | `2G` | `-Xmx` of the SoulFire process, kept apart from the server's. |
| `-Areas` | `16` | Areas the bots are spread over. More areas mean more regions. |
| `-CheckOnly` | off | Only checks that RCON and `/tps` work, and exits. Run this first. |

More options go straight to `LoadTest.java` (`--area-spacing`, `--hold-seconds`, `--samples`,
`--settle-seconds`, `--server-pid`, `--server-xmx-mb`, `--prefix`, `--protocol`).

## Preparing the server

- **Offline mode.** The bots are offline accounts, so the server must accept them: `online-mode =
  false` in `config/server.toml`, or `online-mode=false` in `server.properties`. If the server is
  behind a proxy, the proxy has to let offline bots through (a Velocity or BungeeCord setup that
  forwards offline players, or a test backend reached directly). Do not leave a public server in
  offline mode; use a test copy or a private port.
- **RCON.** In `config/network.toml`, section `[network.rcon]`, `enabled = true`, a `port`, and a
  password. LegacyFolia keeps the password in `config/rcon.secret`; give that file's content to the
  tool. RCON listens on `127.0.0.1` by default; to test from another machine set `ip` or use an SSH
  tunnel (`ssh -L 25575:127.0.0.1:25575 host`) and pass `-RconPort 25575` with `-ServerHost` set to the
  game address.
- **Player limit.** `max-players` must be at least `-Max`, or bots are refused and the run stops
  with "bots stopped joining".
- **Connection throttle.** The default `connection-throttle = 4000` in `config/network.toml`
  (`network.limits`) lets one IP join once every 4 s. With the bots all coming from one address, set
  it to `0`, or use a `-JoinDelayMs` of 4000 or more.
- **Operator rights.** RCON runs as the console, which can use `tp`, `effect`, `list` and `tps`.
- **Memory figures.** Folia disables the bundled spark, so `spark health` is not there. The tool reads
  heap and GC from `jstat` when the server runs on the same machine (it finds the process by its
  listening port, or give `--server-pid`). On a remote server, install the spark plugin if you want
  heap figures through RCON; without either, MSPT and TPS are still measured and the memory limit is
  not checked (the tool says so at the start). The `spark health` reading is a best-effort parser and has not been
  checked against a real spark output.

## Reading the results

Two files per run in `testing/results/`, both named `load-<date>-<time>`:

- `.csv`, one row per sample: `target_bots`, `bots_online`, `bots_lost`, `regions`, `util_pct`
  (share of time the regions are busy), `lowest_tps`, `median_tps`, `highest_tps`, `worst_mspt`
  (the slowest of the top regions), `worst_region_tps`, `worst_region_players`, `heap_used_mb`,
  `heap_max_mb`, `heap_pct`, `gc_count`, `gc_time_ms` (GC time spent since the previous sample),
  `server_rss_mb`, `load_host_cpu_pct`, `mem_source` and `violations`.
- `.summary.txt`, with the line to read: `RESULT: maximum sustained N bots. Limit hit at M bots: reason.`
  If it says "no limit reached", the server took `-Max` and can take more: raise `-Max`.

How to interpret the limits:

- **MSPT over 50 or TPS under 18** is the server running out of tick time in some region. The CSV shows
  whether one region or all of them are slow (`lowest_tps` against `median_tps`) and how many players
  the worst region had.
- **Heap over 90 %** means the `-Xmx` is the limit, not the CPU. Compare `heap_pct` with `gc_time_ms`:
  a heap near full with rising GC time is memory pressure.
- **Bots kicked** points at a rate limit (`network.limits.packet-limiter`), a timeout or a crash. Read the
  server log and the SoulFire log named in the summary.
- **Stopped joining** is usually configuration (throttle, `max-players`, whitelist), not capacity.

`util_pct` is the sum over regions of tick time as a share of the time available. `/tps` lists only the
three busiest regions, so the "worst MSPT" is the worst of those three, which is also the region that
matters.

## What limits a local measurement

- **The bots share the machine with the server.** Each bot decodes chunks and keeps a world, and
  SoulFire is a full Minecraft client runtime with its own heap (`-BotRam`). On one machine the bots
  take CPU the server would use, so a local result is a lower bound. `load_host_cpu_pct` in the CSV shows
  the CPU of the whole machine; near 100 means the measurement is partly measuring the bots.
  For a number you can trust, run the bots on a second machine against the server.
- **Anything else running on the machine** (other servers, clients, a browser) does the same. Close it.
- **Bots do little.** They stand and move a little (`anti-afk`), with no combat, redstone or
  building, so a real player costs more than a bot. Treat the figure as the count of mostly idle players.
- **Chunk generation.** Teleporting to new areas generates terrain at the start of each batch, which
  depresses TPS for a moment; the settle time is there for that. A second run over the same world
  starts with the chunks already generated and gives a higher number.
- **One address.** All the bots come from one IP, which is why the connection throttle has to be
  lowered, and a proxy or firewall may treat them as an attack.
- **Memory.** A JVM started with `-Xms` equal to `-Xmx` has its whole heap resident, so
  `server_rss_mb` is high from the start; use `heap_pct` for pressure. Heap use is a sawtooth between
  collections, which is why the 90 % rule needs every sample of a step above it.
- **The tool is only as stable as SoulFire.** On some Windows machines SoulFire's graphics start-up crashed
  with a GPU driver; the tool forces software rendering and restarts SoulFire if it dies before any
  bot has joined. A SoulFire crash later is reported as inconclusive.
