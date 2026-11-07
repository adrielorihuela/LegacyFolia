---
paths:
  - "folia-server/minecraft-patches/**"
  - "folia-server/paper-patches/**"
  - "folia-server/src/minecraft/**"
  - "folia-server/build.gradle.kts.patch"
  - "paper-server/**"
  - "paper-api/**"
---

Moved from CLAUDE.md § Patch workflow on 2026-11-06.

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
