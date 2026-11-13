# Functional bot bench

Starts a LegacyFolia jar in fresh temporary folders and checks it with Minecraft 26.3 protocol bots
(MCProtocolLib 26.3-2) and the console. One PASS, FAIL or NOT_RUN per scenario of `docs/TESTING.md`,
written to `testing/results/bots.json` (gitignored).

```
pwsh testing/bots/run-all.ps1 [-Jar path] [-Only locator,pvp] [-Properties root]
testing/bots/run-all.sh [-j jar] [-O groups] [-P root]
```

Default jar: the newest `testing/.cache/*.jar`. For RC 1:
`gh release download v1.0.0-rc.1 --repo adrielorihuela/LegacyFolia -D testing/.cache`.
Ports: 25611 (game), 25612 and 25613 (RCON). The EULA is accepted only inside the temporary folders.
Needs Java 25; Gradle comes from PATH or the repository wrapper.

Groups: `locator`, `pvp`, `tooltips` (one flat-world server, bots A, B, C), `off` (combat disabled), `fresh`
(properties mode, RCON, files, four restarts), `dims`, `lobby`, `migration` (vanilla, Spigot, Paper), `eula`.
`-Properties root` expects `server.properties` beside the jar (RC 2) instead of in `config/` (RC 1).

How it works: bots never simulate physics. Tests place them with `/tp` and the console, send explicit
packets (attack, use item, input, movement) and read what the victim is sent: health, velocity, waypoint,
effect and item packets. Expected values are 1.8.8's from `docs/PVP-1.8.md` or vanilla's, and every check
can fail. Not automated, and reported as NOT_RUN with the reason: the EULA prompt (needs a real terminal),
the plugin updater (live downloads), arrows inheriting the shooter's motion, a plugin reading the settings,
and the 1.8 client items, which belong to `testing/bot18`.
