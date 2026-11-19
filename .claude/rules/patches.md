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

Our minecraft patches: 0015–0018 config load/EULA, plugin update, locator bar, adaptive distance;
0019–0020, 0023–0030, 0038–0039, 0041–0061 combat; 0021–0022 file locations; 0031–0034 lobby, Nether
roof, spawn protection, whitelist default; 0035–0037 start from TOML, hot writes, lobby main world;
0040 vanilla locator bar. Paper: 0008 dimensions, 0009 forwarding secret, 0010 `canSee` by id,
0011–0013 server.properties/Bukkit/Spigot/Paper files in memory, 0014 `/legacyfolia`.
