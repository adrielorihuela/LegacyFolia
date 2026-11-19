# LegacyProbe

A test plugin for LegacyFolia (`docs/TESTING.md`, items 5i and 1j-team).

- `/probe <tag>` logs `PROBE[tag] key=value` lines: what a plugin reads through `Bukkit.getServer()`, `Bukkit.spigot().getBukkitConfig()`, `getSpigotConfig()` and `getPaperConfig()`, and which old config files exist in the server root.
- `/probeteam add <team> <color> <player...>`, `color`, `which`, `remove`, `list`: scoreboard teams on the NMS main scoreboard, by reflection, because Folia's `Scoreboard#registerNewTeam` throws `UnsupportedOperationException` (`/probeteam bukkit` shows it).

Build: `testing/probe-plugin/build.sh <server folder that has started once>` (compiles against its `libraries/`), giving `build/LegacyProbe.jar`. For the bots bench, set `LEGACYFOLIA_PROBE_PLUGIN` to that jar.
