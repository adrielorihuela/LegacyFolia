# Mapa de ajustes: de los archivos heredados a `config/*.toml`

Referencia de **dónde vive cada ajuste del servidor y de dónde vino**. LegacyFolia sustituye los
archivos de configuración de vanilla, Bukkit, Spigot y Paper/Folia por ocho TOML en `config/`
(más un `config/server.properties` opcional que sólo guarda lo que difiere del default). Este
documento explica la correspondencia, los conflictos entre archivos y cómo se resolvieron, el orden
en que el servidor lee cada cosa y lo que se dejó fuera a propósito. La guía de uso, en inglés, es
[CONFIGURATION.md](CONFIGURATION.md).

**Estado: implementado.** Los TOML **poseen** todos los ajustes; los YAML heredados no existen en
disco y se construyen en memoria (§4). Este archivo empezó como documento de diseño; lo que sigue
describe el código actual, no un plan.

**Base y números de línea.** Paper `804dd0c3` (Folia `ver/26.3.x`, Minecraft 26.3). Las rutas con
número de línea se refieren a los árboles generados tras `applyAllPatches` sobre esa base:
`folia-server/src/minecraft/java/` (vanilla parcheado), `paper-server/src/main/java/` (Paper) y
`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/` (abreviado `.../legacyfolia/`).
Tras una actualización de upstream las líneas pueden moverse; los nombres de clase y campo son
lo estable.

**Fuente de verdad: el catálogo.** `folia-server/src/main/resources/legacyfolia/catalog.tsv` tiene
una fila por clave de los TOML (593): tipo, si admite override por mundo, archivo y ruta heredados,
otras ubicaciones heredadas de la misma decisión (`also-from`), nombre vanilla en
`server.properties`, si es mutable en caliente, si es un secreto, valores permitidos, default y
notas. Los números de este documento salen de ahí. Tests que lo mantienen honesto:

- `SettingsCatalogTest`: toda clave de un TOML tiene fila, toda fila tiene clave, y los defaults
  coinciden.
- `LegacyCoverageTest`: toda clave que un servidor heredado escribe en una instalación nueva está
  en el catálogo o en `LegacyExclusions` (§7). No usa listas a mano: genera el `server.properties`
  de vanilla desde un archivo vacío, el `spigot.yml` de Spigot con un mundo cargado, el
  `paper-global.yml` y el `paper-world-defaults.yml` de Paper, y las plantillas de `bukkit.yml` y
  `commands.yml`, más las claves de Bukkit que la plantilla no escribe. Si una actualización de
  Paper añade una clave, falla hasta que se le da sitio (así se catalogaron las dos de `804dd0c3`).
- `ServerPropertiesBridgeTest`, `BukkitBridgeTest`, `SpigotBridgeTest`, `PaperBridgeTest`: con los
  TOML por defecto, cada objeto construido en memoria es igual al que escribe un servidor nuevo
  (§4.1, §9).

Invariante que gobierna todo: **un plugin no debe poder distinguir este fork de Folia leyendo
configuración por la API**. Los plugins no leen archivos, leen objetos (`SpigotConfig.*`,
`GlobalConfiguration.get()`, `Bukkit.spigot().getConfig()`, `Server#getViewDistance`,
`Server#getAllowNether`). La migración cambió **de dónde se llenan** esos objetos, nunca su
existencia ni sus tipos.

---

## 1. Inventario de los archivos heredados

| Archivo heredado | Dónde lo lee el código | Filas del catálogo (origen principal) | Notas |
|---|---|---:|---|
| `server.properties` | `folia-server/src/minecraft/java/net/minecraft/server/dedicated/DedicatedServerProperties.java` (claves en L53-164) | **69** | 73 claves en 26.3 (§1.1). 68 tienen nombre vanilla en el catálogo; `rcon.password` se convierte en `network.rcon.password-file`; 2 muertas y 2 secretos de gestión excluidos (§7). 19 son `MutableValue` (§5.1) |
| `bukkit.yml` | `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java` (ctor L404-456, `reload()` L949-980) y `folia-server/src/minecraft/java/net/minecraft/server/Main.java:199` | **30** | 28 en la plantilla, menos `aliases` (puntero a `commands.yml`) y `settings.compatibility` (excluida), más `settings.world-container` y las tablas `worlds.<mundo>.generator` y `worlds.<mundo>.biome-provider`, que la plantilla no escribe |
| `spigot.yml` global | `paper-server/src/main/java/org/spigotmc/SpigotConfig.java` | **35** | +`config-version` (L87-88), metadato. `replace-commands` en la raíz es la ubicación antigua de `commands.replace-commands` (L289-291) y va como `also-from` |
| `spigot.yml` por mundo | `paper-server/src/main/java/org/spigotmc/SpigotWorldConfig.java` | **105** | Más `view-distance` y `simulation-distance`, que van como `also-from` de las claves vanilla (§3.3). 22 `growth.*`, 22 `seed-*` (26.3 añade `seed-abandonedcamp`, L379) |
| `commands.yml` | plantilla `paper-server/src/main/resources/configurations/commands.yml`, leída en `CraftServer.java:416` | **3** | `command-block-overrides`, `ignore-vanilla-permissions`, `aliases` |
| `config/paper-global.yml` | `paper-server/src/main/java/io/papermc/paper/configuration/GlobalConfiguration.java` (`CURRENT_VERSION = 31`, L31) | **88** | Folia añade `threaded-regions.*` (L401-403). 26.3 quitó `misc.send-full-pos-for-item-entities` y `misc.fix-far-end-terrain-generation`. `chunk-loading-basic.autoconfig-send-distance` es ruta antigua (`also-from`) |
| `config/paper-world-defaults.yml` y `<mundo>/paper-world.yml` | `paper-server/src/main/java/io/papermc/paper/configuration/WorldConfiguration.java` (`CURRENT_VERSION = 31`, L75) | **182** | +15 `also-from` (los 14 límites e intervalos de spawn por categoría y `chunks.auto-save-interval`). 26.3 añade `chunks.min-chunk-unload-count`/`-fraction`; Paper `804dd0c3` añade `entities.behavior.inactive-mob-goal-selector-throttle-rate` (L341) y `command-blocks.time-format-pattern` (L581). Nombres de archivo en `PaperConfigurations.java:95-97` |
| `eula.txt` | — | **1** | `server.eula` |
| (propias del fork) | — | **80** | `[dimensions]`, `world.always-spawn-at-world-spawn`, `world.lobby-spawn-world`, `world.disable-nether-roof`, `[chunks.adaptive-view-distance]`, `[combat]` (58), `[plugins.auto-update]`, `server.eula-prompt-timeout-seconds`, `server.use-server-properties` |

Total: **593 claves TOML**, de las cuales **303 admiten override por mundo**. A ellas se suman
**21 ubicaciones `also-from`**: 15 de Paper por mundo, 2 de Spigot por mundo, 2 de Spigot global,
1 de Paper global y 1 de `server.properties` (`resource-pack-hash`).

### 1.1 `server.properties` — 73 claves

Las que `DedicatedServerProperties` lee con `get`, `getMutable`, `getLegacy*` o `getStringRaw`
(L53-164). La versión anterior de este documento decía 71 pero ya listaba estas 73.

```
accepts-transfers allow-flight announce-player-achievements broadcast-console-to-ops
broadcast-rcon-to-ops bug-report-link chat-spam-threshold-seconds command-spam-threshold-seconds
debug difficulty enable-code-of-conduct enable-jmx-monitoring enable-query enable-rcon
enable-status enforce-secure-profile enforce-whitelist entity-broadcast-range-percentage
force-gamemode function-permission-level gamemode generate-structures generator-settings hardcore
hide-online-players initial-disabled-packs initial-enabled-packs level-name level-seed level-type
log-ips management-server-allowed-origins management-server-enabled management-server-host
management-server-port management-server-secret management-server-tls-enabled
management-server-tls-keystore management-server-tls-keystore-password max-chained-neighbor-updates
max-players max-tick-time max-world-size motd network-compression-threshold online-mode
op-permission-level pause-when-empty-seconds player-idle-timeout prevent-proxy-connections
query.port rate-limit rcon.ip rcon.password rcon.port region-file-compression require-resource-pack
resource-pack resource-pack-hash resource-pack-id resource-pack-prompt resource-pack-sha1 server-ip
server-port simulation-distance spawn-protection status-heartbeat-interval sync-chunk-writes
text-filtering-config text-filtering-version use-native-transport view-distance white-list
```

- 68 tienen clave TOML con su nombre vanilla, que es la clave que usan en el `server.properties`
  del modo (§5.3).
- `rcon.password` → `network.rcon.password-file` (secreto, `config/rcon.secret`).
- `resource-pack-hash` → `also-from` de `server.resource-pack.sha1` (vanilla cae a ella si `sha1`
  está vacía, L153 y L212-215).
- Excluidas (§7): `announce-player-achievements` (muerta), `management-server-secret`,
  `management-server-tls-keystore-password`.

### 1.2 `bukkit.yml`

| Sección | Claves | Clave TOML |
|---|---|---|
| `settings` | `allow-end` | `dimensions.end` |
| | `warn-on-overload`, `deprecated-verbose` | `server.console.*` |
| | `permissions-file`, `update-folder`, `plugin-profiling`, `minimum-api`, `use-map-color-cache` | `plugins.api.*` |
| | `connection-throttle` | `network.limits.connection-throttle` |
| | `query-plugins` | `network.query.expose-plugins` |
| | `shutdown-message` | `server.messages.shutdown` |
| | `world-container` (no plantillada, `Main.java:199`) | `world.storage.world-container` |
| | `compatibility` (no plantillada) | excluida (§7) |
| `spawn-limits.*` (7) | nombres de `CraftSpawnCategory` | `performance.spawning.spawn-limits.*` |
| `ticks-per.*-spawns` (7) | | `performance.spawning.ticks-per-spawn.*` |
| `ticks-per.autosave` | | `chunks.auto-save-interval` |
| `chunk-gc.period-in-ticks` | | `chunks.chunk-gc-period-in-ticks` |
| `worlds.<mundo>.generator`, `.biome-provider` (no plantilladas) | | `world.generators`, `world.biome-providers` (una tabla por nombre de mundo) |
| `aliases` | puntero `now-in-commands.yml` | no es ajuste; el puente lo escribe igual (§4.1) |

---

## 2. Dónde vive cada cosa ahora

Criterio de `ServerConfig` (`.../legacyfolia/config/ServerConfig.java:15-40`): **la función
manda, el origen no cuenta**. La primera parte de la ruta decide el archivo (`SECTIONS`,
L60-70). Columnas: claves / con override por mundo / orígenes.

### 2.1 `server.toml` (76 / 0)

| Sección | Claves | Orígenes |
|---|---:|---|
| `[server]` | 16 | fork (`eula-prompt-timeout-seconds`, `use-server-properties`), `eula.txt`, server.properties (`level-name`, `motd`, `max-players`, `difficulty`, `gamemode`, `force-gamemode`, `hardcore`, `pause-when-empty-seconds`, `player-idle-timeout`, `spawn-protection`→`spawn-protection-radius`, `bug-report-link`), spigot (`user-cache-size`, `save-user-cache-on-stop-only`) |
| `[server.status]` | 5 | server.properties + spigot (`sample-count`, `player-shuffle`) |
| `[server.console]` | 10 | paper-global (`console.*`), server.properties, bukkit, spigot (`log-*-deaths`); `debug` une `debug` de vanilla y `settings.debug` de Spigot |
| `[server.access]` | 9 | server.properties |
| `[server.messages]` | 14 | spigot (`messages.*`), bukkit (`shutdown-message`), paper-global (`messages.*`, `packet-limiter.kick-message`) |
| `[server.resource-pack]` | 5 | server.properties |
| `[server.watchdog]` | 6 | spigot (`timeout-time`, `restart-on-crash`, `restart-script`), paper-global (`watchdog.*`), server.properties (`max-tick-time`→`vanilla-timeout-ms`) |
| `[server.saving]` | 7 | spigot (`players`/`stats`/`advancements`), paper-global (`player-auto-save.*`) |
| `[server.data-packs]` | 4 | server.properties |

### 2.2 `world.toml` (40 / 24)

| Sección | Claves | Orígenes |
|---|---:|---|
| `[dimensions]` | 3 | `overworld` (fork), `nether` (paper-global `misc.enable-nether`), `end` (bukkit `settings.allow-end`) |
| `[world]` | 4 (+2 tablas) | fork (lobby, techo del Nether), spigot-mundo (`verbose`→`log-settings-on-load`, por mundo) |
| `[world.generation]` | 7 | server.properties, spigot (`below-zero-generation-in-existing-chunks`), paper-mundo (`feature-seeds.generate-random-seeds-for-all`→`random-feature-seeds`, por mundo) |
| `[world.structure-seeds]` | 22 | los 22 `seed-*` de spigot-mundo, con el nombre del conjunto de estructuras de vanilla; todos por mundo |
| `[world.storage]` | 2 | bukkit (`world-container`), server.properties (`sync-chunk-writes`) |
| `[world.generators]`, `[world.biome-providers]` | 2 | bukkit `worlds.<mundo>.*` |

### 2.3 `network.toml` (45 / 0)

| Sección | Claves | Orígenes |
|---|---:|---|
| `[network]` | 8 | server.properties (`server-ip`, `server-port`, `use-native-transport`, `network-compression-threshold`, `prevent-proxy-connections`, `accepts-transfers`), paper-global (`misc.compression-level`), spigot (`settings.netty-threads`) |
| `[network.velocity]` | 3 | paper-global `proxies.velocity.*`; `secret-file` nombra `config/forwarding.secret` |
| `[network.bungeecord]` | 2 | spigot `settings.bungeecord` + paper-global `proxies.bungee-cord.online-mode` |
| `[network.proxy-protocol]` | 1 | paper-global |
| `[network.rcon]` | 4 | server.properties; `password-file` nombra `config/rcon.secret` |
| `[network.query]` | 3 | server.properties + bukkit `settings.query-plugins` |
| `[network.management]` | 6 | `management-server-*` sin los dos secretos |
| `[network.limits]` | 14 | server.properties (`rate-limit`, `*-spam-threshold-seconds`), bukkit (`connection-throttle`), spigot (`commands.spam-exclusions`, `commands.enable-spam-exclusions`), paper-global (`spam-limiter.*`, `packet-limiter.all-packets.*`) |
| `[network.monitoring]` | 4 | server.properties (`enable-jmx-monitoring`), paper-global (`spark.*`, `update-checker.enabled`) |

### 2.4 `performance.toml` (132 / 98)

| Sección | Claves | Orígenes |
|---|---:|---|
| `[chunks]` | 21 (10 por mundo) | server.properties (distancias, `region-file-compression`), bukkit (`ticks-per.autosave`, `chunk-gc`), paper-global (`chunk-loading-*`, `chunk-system.*`, `misc.region-file-cache-size`), paper-mundo (`chunks.*`), spigot-mundo (`unload-frozen-chunks`) |
| `[chunks.adaptive-view-distance]` | 10 | fork |
| `[performance.entities]` (+ `activation-range`, `tracking-range`, `tracking-range-y`, `merge-radius`, `collisions`) | 52 (47) | spigot-mundo (`entity-activation-range.*`, `entity-tracking-range.*`, `merge-radius.*`, `max-entity-collisions`), paper-mundo, paper-global (`collisions.enable-player-collisions`, `misc.*`), server.properties (`entity-broadcast-range-percentage`) |
| `[performance.spawning]` (+ `spawn-limits`, `ticks-per-spawn`, `despawn-ranges`) | 35 (35) | bukkit + paper-mundo (§3.4), spigot-mundo (`mob-spawn-range`, `nerf-spawner-mobs`), paper-mundo (`despawn-ranges`, `per-player-mob-spawns`) |
| `[performance.ticking]` | 9 (6) | server.properties (`max-chained-neighbor-updates`), spigot-mundo (`max-tick-time.*`→`*-budget-millis`, `hanging-tick-frequency`), paper-global (`misc.max-joins-per-tick`, `misc.catchup-ticks`), paper-mundo (`entities.armor-stands.*`, `entities.markers.tick`) |
| `[performance.regions]` | 3 | Folia: paper-global `threaded-regions.*` |
| `[performance.threads]` | 2 | paper-global `misc.chat-threads.*` |

### 2.5 `gameplay.toml` (207 / 181)

| Sección | Claves | Orígenes |
|---|---:|---|
| `[gameplay.hunger]` | 7 | spigot-mundo `hunger.*` |
| `[gameplay.growth]` | 26 | 22 `growth.*` de spigot-mundo + `max-growth-height.*` de paper-mundo |
| `[gameplay.mobs]` | 40 | paper-mundo `entities.behavior.*`, `entities.mob-effects.*`, `entities.sniffer.*`; spigot-mundo; paper-global `misc.prevent-negative-villager-demand` |
| `[gameplay.mob-spawning]` | 17 | paper-mundo `entities.spawning.*`; paper-global `misc.use-dimension-type-for-custom-spawners` |
| `[gameplay.environment]` | 31 | paper-mundo `environment.*`; spigot-mundo `thunder-chance`; paper-global `time.affects-all-worlds` |
| `[gameplay.items]` | 27 | spigot-mundo (despawns, `hopper-*`, `max-tnt-per-tick`), paper-mundo (`lootables.*`, `hopper.*`), paper-global `misc.use-alternative-luck-formula` |
| `[gameplay.redstone]` | 7 | paper-mundo `misc.redstone-implementation`, `misc.alternate-current-update-order`; paper-global `block-updates.*` |
| `[gameplay.exploits]` | 37 | paper `unsupported-settings.*` (global y mundo), `fixes.*`, `anticheat.anti-xray.*`, `anticheat.obfuscation.items.*`, `entities.spawning.duplicate-uuid.*`; spigot `moved-*` |
| `[gameplay.sounds]` | 3 | spigot-mundo `*-sound-radius` |
| `[gameplay.players]` | 4 | paper-mundo `misc.disable-end-credits`, `misc.disable-sprint-interruption-on-attack`; paper-global `misc.client-interaction-leniency-distance`, `misc.strict-advancement-dimension-check` |
| `[gameplay.commands]` | 6 | paper-mundo `command-blocks.*`, `misc.show-sign-click-command-failure-msgs-to-player`, `spawn.allow-using-signs-inside-spawn-protection`; paper-global `commands.ride-command-allow-player-as-vehicle` |
| `[gameplay.scoreboards]` | 2 | paper-mundo `scoreboards.*` |

### 2.6 `combat.toml` (62 / 0) y `plugins.toml` (31 / 0)

- `[combat]` (58) es del fork: ver [PVP-1.8.md](PVP-1.8.md). `[combat.attribute-limits]` (4) son
  los `settings.attribute.*.max` de spigot.
- `[plugins.auto-update]` (6) es del fork: ver [PLUGIN-UPDATER.md](PLUGIN-UPDATER.md).
- `[plugins.api]` (16, con `scoreboards` e `item-validation`): bukkit `settings.*` de plugins,
  paper-global `misc.load-permissions-yml-before-plugins`, `scoreboards.*`, `item-validation.*`.
- `[plugins.commands]` (9, más la tabla `aliases`): spigot `commands.*`, paper-global
  `commands.suggest-player-names-when-null-tab-completions`, los 3 de `commands.yml`.

### 2.7 `worlds.toml` (sin claves propias)

`[worlds.default]` equivale a `world-settings.default` de Spigot y a `paper-world-defaults.yml`;
`[worlds.<mundo>]` a `world-settings.<mundo>` y a `<mundo>/paper-world.yml`. La resolución es
`[worlds.<mundo>]` → `[worlds.default]` → sección global → fallback del llamante
(`.../legacyfolia/config/WorldConfig.java:9-41`), aplanada una vez por mundo y cacheada hasta la
siguiente carga (`WorldConfigCache`, invalidado en `ServerConfig.load`, L252-268). Sólo se
consultan `gameplay.toml`, `performance.toml` y `world.toml` (`PER_WORLD_FILES`, L81), y de ellos
sólo las 303 claves marcadas por mundo llegan a los objetos por mundo de Spigot y Paper
(`SpigotBridge.addWorld`, `PaperBridge.world`).

Mundos nombrados por carpeta (`world_nether`); Spigot los nombra por clave de dimensión
(`minecraft:the_nether`) desde su `config-version` 13, así que el puente traduce
(`SpigotBridge.vanillaWorldKeys`, y `ConfigHooks.addSpigotWorld`, llamado al crear cada mundo en
`folia-server/src/minecraft/java/net/minecraft/world/level/Level.java:889`). Paper recibe el nombre
de carpeta por un `ContextKey` propio (`PaperConfigurations.java:366-377`,
`Configurations.java:402`).

---

## 3. Duplicados y conflictos

Cada fila es una decisión que estaba partida en dos archivos. Todas están resueltas; la columna
"cómo" dice dónde comprobarlo.

| # | Decisión | Sitio A | Sitio B | Clave TOML | Cómo |
|---|---|---|---|---|---|
| 3.1 | ¿Existe la dimensión? | `misc.enable-nether` (`GlobalConfiguration.java:342`) | `settings.allow-end` (bukkit) | `dimensions.nether`, `dimensions.end` | `.../legacyfolia/world/Dimensions.java`; `CraftServer.java:743,749` responden `getAllowEnd`/`getAllowNether` desde ahí. Se fijan una vez por arranque; en la ejecución que crea `world.toml` adoptan el valor heredado |
| 3.2 | ¿BungeeCord? | `settings.bungeecord` (`SpigotConfig.java:209-212`) | `proxies.bungee-cord.online-mode` (`GlobalConfiguration.java:111`) | `network.bungeecord.enabled` + `.online-mode` | Spigot sigue escribiendo `false` si ve una configuración incoherente (L209), pero ya no persiste nada (`SpigotConfig.java:114`) |
| 3.3 | Distancias | `view-distance`/`simulation-distance` (server.properties) | las mismas por mundo (`SpigotWorldConfig.java:162-185`) | `chunks.view-distance`, `chunks.simulation-distance` (por mundo) | El valor global va a `server.properties`; el de Spigot queda `"default"` salvo que `[worlds.*]` diga otro número (`SpigotBridge.build`). Spigot sigue cayendo a `Bukkit.getViewDistance()` con `<= 0` y acota la de visión a 3..32 (L168-172) |
| 3.4 | Límites e intervalos de spawn | `spawn-limits.*`, `ticks-per.*-spawns` (bukkit, `CraftServer.java:468-474`, L838) | `entities.spawning.spawn-limits`/`ticks-per-spawn` (`WorldConfiguration.java:183,188`) | `performance.spawning.spawn-limits.*`, `.ticks-per-spawn.*` (por mundo) | Global → bukkit; el nodo de Paper queda en `-1` ("pregunta a Bukkit", `CraftWorld.java:1564-1574`) salvo que `[worlds.*]` difiera (`PaperBridge.worldDefaults/world`) |
| 3.5 | Despawn de flechas | `arrow-despawn-rate` (`SpigotWorldConfig.java:294`) | `entities.spawning.{non-player,creative}-arrow-despawn-rate` (`WorldConfiguration.java:174-175`, `ArrowDespawnRate.def(spigotConfig)`) | `gameplay.items.arrow-despawn-rate`, `.non-player-arrow-despawn-rate`, `.creative-arrow-despawn-rate` | Las de Paper por defecto son `"default"`, que sigue cayendo al valor de Spigot del mundo (`FallbackValue`). `trident-despawn-rate` cae a `arrow-despawn-rate` si falta (L295) |
| 3.6 | Autoguardado | `ticks-per.autosave` (bukkit, `CraftServer.java:447`) | `chunks.auto-save-interval` (`WorldConfiguration.java:515`) y `player-auto-save.rate` (`GlobalConfiguration.java:287`) | `chunks.auto-save-interval` (por mundo) y `server.saving.player-save-interval-ticks` | Paper con valor negativo pregunta a Bukkit; el puente escribe `-1` en `player-auto-save.rate` cuando coincide con el autoguardado de chunks (`PaperBridge.global`) |
| 3.7 | Protección del spawn | `spawn-protection` (server.properties) | — | `server.spawn-protection-radius`, default **0** | `DedicatedServerProperties.java:90` toma el default de `.../legacyfolia/world/SpawnProtection.java`; nombre vanilla `spawn-protection` en el modo |
| 3.8 | Secreto de Velocity | `proxies.velocity.secret` (`GlobalConfiguration.java:120`) | — | `network.velocity.secret-file` → `config/forwarding.secret` | `.../legacyfolia/network/ForwardingSecret.java`, llamado en `GlobalConfiguration.java:127`; un secreto en YAML se mueve al archivo y se vacía; `PAPER_VELOCITY_SECRET` gana |
| 3.9 | Colisiones de jugadores | `collisions.enable-player-collisions` (paper-global) | `collisions.only-players-collide` (paper-mundo) | `performance.entities.collisions.*` | La primera global, la segunda por mundo; no son sinónimos |
| 3.10 | Caché de usuarios | `settings.user-cache-size`, `save-user-cache-on-stop-only` (`SpigotConfig.java:299`) | ubicación del archivo | `server.user-cache-size`, `server.save-user-cache-on-stop-only` | El archivo vive en `cache/` (`ConfigPaths.cache`, `Main.java:194`), no es configuración |
| 3.11 | Watchdog | `settings.timeout-time`, `restart-on-crash` (spigot, `DedicatedServer.java:248`) | `watchdog.early-warning-*` (`GlobalConfiguration.java:156`) | `[server.watchdog]` | Una sección |
| 3.12 | Estrangulamiento de conexión | `settings.connection-throttle` (bukkit) | `messages.kick.connection-throttle` (paper-global) | `network.limits.connection-throttle`, `server.messages.kick-connection-throttle` | El umbral con la red, el texto con los mensajes |
| 3.13 | Generación bajo cero | `world-settings.default.below-zero-generation-in-existing-chunks` leída **global** (`SpigotConfig.java:370`) | la misma por mundo (`SpigotWorldConfig`) | `world.generation.below-zero-generation-in-existing-chunks` | Una clave global para los dos lectores |
| 3.14 | Semillas | 22 `seed-*` (spigot-mundo) | `feature-seeds.*` (`WorldConfiguration.java:567`) | `world.structure-seeds.*` (por mundo), `world.generation.random-feature-seeds` | `feature-seeds.features` excluida (§7); `mineshafts` y `strongholds` son texto (`"default"` o un número, `SpigotWorldConfig.getSeed`, L356) |
| 3.15 | Presupuestos de tick | `max-tick-time` (server.properties, watchdog de vanilla) | `max-tick-time.entity`/`.tile` (spigot-mundo) | `server.watchdog.vanilla-timeout-ms` y `performance.ticking.entity-budget-millis`/`tile-entity-budget-millis` | Renombradas: nombres casi iguales, propósitos distintos. El watchdog de vanilla lo desactiva Spigot; el valor sólo se informa |

Otras dos claves con dos lectores: `server.console.debug` (`debug` de vanilla y `settings.debug`
de Spigot; la migración toma `true` si cualquiera lo es) y `server.resource-pack.sha1`
(`resource-pack-sha1` gana a `resource-pack-hash`).

---

## 4. Quién lee qué, y cuándo

### 4.1 Arranque

| Orden | Punto | Archivo:línea | Qué queda poblado |
|---:|---|---|---|
| 1 | `ConfigHooks.start` | `folia-server/src/minecraft/java/net/minecraft/server/Main.java:130` → `.../legacyfolia/config/ConfigHooks.java:79-86` | `LegacyImport.runIfNeeded` (§6), `ServerConfig.load()` (los ocho TOML, creándolos o completándolos desde la plantilla), `ServerProperties.reconcile()` (§5.3). Un fallo detiene el servidor sin borrar nada |
| 2 | `AdaptiveDistance.reload()`, `Combat.reload()` | `Main.java:135-136` | los dos módulos propios que cachean configuración |
| 3 | `new DedicatedServerSettings(options)` | `Main.java:138` → `DedicatedServerSettings.java:13` | `DedicatedServerProperties` desde `ConfigHooks.serverProperties()` (`ServerPropertiesBridge` + `RconSecret`), **sin leer archivo**; `forceSave()` es un no-op (`DedicatedServerSettings.java:21-30`) |
| 4 | `RegionFileVersion.configure(...)` | `Main.java:145` | compresión de regiones, ya desde el TOML (antes no hacía nada) |
| 5 | `ConfigHooks.bukkit()`, `ConfigHooks.spigot(levelName)` | `Main.java:149-150` | `bukkit.yml` y `spigot.yml` en memoria para lo que `Main` lee pronto |
| 6 | `--initSettings` | `Main.java:153-156` | sale tras escribir/importar los TOML |
| 7 | EULA | `Main.java:165-172` | `server.eula`; si es `false`, `EulaConsole.askAndRecord()` |
| 8 | `SpigotConfig.disabledAdvancements` | `Main.java:190` | parche temprano de SPIGOT-5885 |
| 9 | `settings.world-container` | `Main.java:199` | `universePath` (`--universe` gana) |
| 10 | `SpigotConfig.init(ConfigHooks.spigot(...))` | `folia-server/src/minecraft/java/net/minecraft/server/dedicated/DedicatedServer.java:234` → `SpigotConfig.java:73-80` | `SpigotConfig.*` y `SpigotConfig.config`, sin archivo (`CONFIG_FILE = null`) |
| 11 | `initializeGlobalConfiguration` / `initializeWorldDefaultsConfiguration` | `DedicatedServer.java:238-239` → `paper-server/src/main/java/io/papermc/paper/configuration/Configurations.java:127-145, 177-201` | `GlobalConfiguration.get()` y defaults de mundo desde nodos de `PaperBridge`; nunca se guardan |
| 12 | Watchdog | `DedicatedServer.java:248` | `SpigotConfig.timeoutTime`, `restartOnCrash` |
| 13 | Actualizador de plugins | `DedicatedServer.java:296-310` | `[plugins.auto-update]`; crea `github.token` |
| 14 | `CraftServer` ctor | `CraftServer.java:404, 416, 446-456` | `configuration` (bukkit) y `commandsConfiguration` en memoria, con las plantillas como defaults |
| 15 | Cada mundo | `Level.java:889` (`addSpigotWorld`), `Configurations.java:237-298` (`ConfigHooks.paperWorld`) | `SpigotWorldConfig` y `level.paperConfig()` |

El cargador de plugins lee `settings.update-folder` antes del paso 1
(`paper-server/src/main/java/io/papermc/paper/plugin/PluginInitializerManager.java:42`):
`ConfigHooks.earlyUpdateFolder` lo resuelve como lo hará el servidor, en este orden: un `bukkit.yml`
heredado aún sin importar, una línea `plugins.api.update-folder` en `config/server.properties`,
`plugins.toml`, y `"update"`.

### 4.2 Recarga y parada

| Disparador | Archivo:línea | Qué hace |
|---|---|---|
| `/reload`, `Bukkit.reload()` | `CraftServer.java:949-980` | `ConfigHooks.reloadOrWarn()` (TOML + reconcile + `AdaptiveDistance`/`Combat`), reconstruye bukkit/commands, `DedicatedServerSettings`, `SpigotConfig.init(spigotForLoadedWorlds())`, `paperConfigurations.reloadConfigs` |
| `/spigot reload` | `paper-server/src/main/java/org/spigotmc/SpigotCommand.java:40-41` | ídem para Spigot |
| `/paper reload` | `paper-server/src/main/java/io/papermc/paper/command/subcommands/ReloadCommand.java:30` | ídem para Paper |
| `Server#reloadCommandAliases` | `CraftServer.java:2925-2933` | `ConfigHooks.reload()` y `commands.yml` de nuevo |
| Parada | `DedicatedServer.java:541` | `ConfigHooks.stop()` vacía la cola de escrituras |

`ServerConfig.load()` siempre descarta los overrides del modo `server.properties`, así que va
siempre seguido de `reconcile()` (`ConfigHooks.reload`, L105-110). La migración sólo corre al
arrancar. Lo que se consume una sola vez (`region-file-compression`, `netty-threads`,
`world-container`, `[dimensions]`) sigue necesitando reinicio.

### 4.3 Objetos que ven los plugins

| Objeto | Construido por | Detalles |
|---|---|---|
| `DedicatedServerProperties` (`Server#getMotd`, `#getMaxPlayers`, `#getViewDistance`...) | `.../legacyfolia/config/bridge/ServerPropertiesBridge.java` | Cada clave vanilla del catálogo en el formato que parsea vanilla. `rcon.password` viene de `RconSecret`; `rcon.ip` vacía se omite (vanilla caería a `server-ip` sólo si falta; vacía escucharía en todas las interfaces). Los dos secretos de gestión no se escriben y vanilla les da su default |
| `bukkit.yml`, `commands.yml` de `CraftServer` | `BukkitBridge.java` | Valores globales; `aliases: now-in-commands.yml` se escribe como en el archivo de fábrica; `world-container` sólo si no es `"."`; la tabla de alias se escribe aunque esté vacía (si no, Bukkit repondría `icanhasbukkit`) |
| `spigot.yml` (`SpigotConfig.config`, `Bukkit.spigot().getConfig()`) | `SpigotBridge.java` | `config-version: 13`; `world-settings.default` = `[worlds.default]` sobre lo global; `world-settings.<clave de dimensión>` sólo con lo que difiere; distancias en `"default"` salvo override; `replace-commands` en la raíz no se escribe |
| `GlobalConfiguration`, defaults de mundo, `WorldConfiguration` | `PaperBridge.java` + `Configurations.java` | Nodos Configurate con `_version` = `CURRENT_VERSION` (las transformaciones no hacen nada), copiados a un nodo del loader de Paper (`loader.createNode().from(...)`) para usar sus serializadores, y nunca guardados. El global y los defaults llevan todas las claves; un mundo sólo lo que difiere. Conversiones: `chunks.delay-chunk-unloads-by` ticks→`Duration`; `despawn-ranges` escribe `"default"` cuando coincide con el default de la categoría y siempre ambos extremos; ids de registro sin `minecraft:`; el secreto de Velocity nunca entra |
| `Bukkit.spigot().getPaperConfig()` | `ConfigHooks.paperLegacyObject` (`PaperConfigurations.java:474`, `CraftServer.java:2764`) | global + `__________WORLDS__________` con `__defaults__` y cada mundo cargado |

Ninguno de estos objetos desaparece ni cambia de tipo. `SpigotConfig.save` no escribe nada
(`SpigotConfig.java:114`); un plugin que mute `Bukkit.spigot().getConfig()` lo hace hasta la
siguiente recarga.

---

## 5. Comportamientos delicados, ya resueltos

### 5.1 Escritura en caliente

- **Los 19 `MutableValue` de vanilla** (`allow-flight`, `motd`, `force-gamemode`,
  `enforce-whitelist`, `spawn-protection`, `view-distance`, `simulation-distance`, `max-players`,
  `enable-status`, `hide-online-players`, `player-idle-timeout`, `status-heartbeat-interval`,
  `white-list`, `pause-when-empty-seconds`, `accepts-transfers`, `difficulty`, `gamemode`,
  `op-permission-level`, `entity-broadcast-range-percentage`; `mutable = true` en el catálogo).
  `Settings.MutableValue.update` llama a `ConfigHooks.vanillaChanged`
  (`folia-server/src/minecraft/java/net/minecraft/server/dedicated/Settings.java:277`), que pasa por
  `ServerProperties.set`: el valor es visible al volver la llamada y el archivo (el
  `server.properties` del modo, o el TOML con el modo apagado) se escribe en un hilo propio, en
  orden, editando sólo esa línea. `/difficulty` no pasa por aquí: Paper cambia la dificultad del
  mundo (`MinecraftServer.java:2151`).
- **`SpigotConfig.set`**: sigue ocurriendo en memoria (la deducción de
  `commands.enable-spam-exclusions`, L267-276; el `false` de BungeeCord, L209), pero no se guarda
  nada. `network.limits.enable-spam-exclusions` es una clave TOML explícita (default `false`); la
  migración reproduce la deducción de Spigot.

### 5.2 Defaults que dependen de otro ajuste

| Caso | Dónde | Cómo se respeta |
|---|---|---|
| Distancia de Spigot `<= 0` o `"default"` → la del servidor | `SpigotWorldConfig.java:166-182` | El puente escribe `"default"` (§3.3) |
| Límite/intervalo de spawn de Paper `-1` → Bukkit | `CraftWorld.java:1564-1574` | El puente escribe `-1` (§3.4) |
| Flechas de Paper `"default"` → Spigot del mundo | `WorldConfiguration.java:174-175` | Se mantiene `"default"` (§3.5) |
| `trident-despawn-rate` ausente → `arrow-despawn-rate` | `SpigotWorldConfig.java:295` | El TOML escribe 1200, correcto mientras la otra sea 1200 (nota del catálogo) |
| `player-auto-save.rate = -1` → autoguardado de chunks | `GlobalConfiguration.java:287` | §3.6 |
| `rcon.ip` ausente → `server-ip` | `DedicatedServerProperties.java:161-164` | `""` en el TOML omite la clave |
| `sync-chunk-writes` = clave **AND** propiedad de sistema `Paper.enable-sync-chunk-writes` | `DedicatedServerProperties.java:112` | Sin `-DPaper.enable-sync-chunk-writes=true` la escritura síncrona queda apagada aunque el TOML diga `true`, como en Paper |
| `threaded-regions.threads = -1` → automático | `GlobalConfiguration.java:403` | Se conserva `-1` |
| `prevent-moving-into-unloaded-chunks` forzado a `true` por Folia | `WorldConfiguration.java:535` | Excluida (§7) |

### 5.3 El modo `server.properties`

`.../legacyfolia/config/ServerProperties.java`. Encendido por defecto
(`server.use-server-properties = true`); vive en `config/server.properties`.

- Sólo contiene lo que difiere del default y gana a los TOML. `reconcile()` (arranque y recarga)
  mueve al archivo lo que se editó en un TOML y devuelve el TOML a su default; si ambos dicen algo
  distinto gana `server.properties` y se registra el valor descartado; borra las líneas que dicen
  el default; avisa de valores inválidos (con número de línea; se usa el default) y de claves
  desconocidas (se conservan, un aviso por clave).
- Claves: el nombre vanilla si lo hay (68), la ruta TOML si no, y `worlds.<mundo>.<ruta>` para un
  override por mundo (sólo claves por mundo; vanilla no tiene claves por mundo, así que aquí nunca
  se usa el nombre vanilla). Los overrides de `worlds.toml` de claves catalogadas por mundo también
  se mueven al archivo; lo no catalogado se queda en `worlds.toml`.
- Nunca entran: los secretos (`secret = true`: `network.rcon.password-file`,
  `network.velocity.secret-file`, `plugins.auto-update.github-token-file`), `server.eula` y el propio
  interruptor (`SettingEntry.allowedInServerProperties`).
- Apagarlo (`/legacyfolia properties off`, o `use-server-properties = false` a mano) escribe cada
  línea válida en su TOML, guarda en `config/server.properties.unused` lo que no se pudo mover y
  sólo entonces borra el archivo. Encenderlo mueve al archivo todo lo que no es default.
- El archivo se escribe con `PropertiesDocument`, que conserva comentarios y orden y agrupa las
  líneas nuevas bajo un comentario con su archivo TOML o su mundo. Los TOML se escriben con
  `TomlText` (el `save()` de night-config borra comentarios).
- La migración no reconoce como heredado un `server.properties` cuya cabecera mencione LegacyFolia
  (`.../legacyfolia/config/migration/LegacyFiles.java:30-50`).

### 5.4 Lecturas por ruta de archivo

- Las opciones `--config`, `--bukkit-settings`, `--spigot-settings`, `--commands-settings` y
  `--paper-settings` se siguen aceptando (sus defaults apuntan a `config/`,
  `paper-server/src/main/java/org/bukkit/craftbukkit/Main.java:41,93,99,123`), pero nada lee esos
  archivos salvo `earlyUpdateFolder` con `--bukkit-settings`.
- `settings.permissions-file` es una ruta relativa al directorio del servidor, como en Bukkit.
- Un plugin que abra `spigot.yml`/`bukkit.yml` con `new File(...)` no encuentra nada. No se
  regeneran copias de sólo lectura.

---

## 6. Migración desde un servidor heredado

`.../legacyfolia/config/migration/`. Corre en `ConfigHooks.start`, antes de `ServerConfig.load()`.

- **Búsqueda** (`LegacyFiles`): `bukkit.yml`, `spigot.yml`, `commands.yml`, `eula.txt` y
  `server.properties` en la raíz y en `config/` (si está en los dos, el de `config/` se importa y
  el otro se respalda sin leerse); `paper-global.yml` y `paper-world-defaults.yml` en `config/`;
  cada `paper-world.yml` del contenedor de mundos, en las dos disposiciones (`world_nether/` y la de
  Paper 26, `<nivel>/dimensions/<ns>/<ruta>/`, nombrada como `PaperWorldLoader`). Los JSON de
  moderación no se buscan: los mueve `ConfigPaths` a `config/` al abrirlos.
- **Planificación** (`ImportPlanner`): tres capas (servidor, todos los mundos, un mundo). Una clave
  que sólo existía por mundo pone su valor "todos los mundos" en la sección global del TOML y el de
  un mundo en `[worlds.<mundo>]`. Las que tenían valor de servidor y de todos los mundos
  (distancias, límites e intervalos de spawn, autoguardado) ponen el de servidor en la sección
  global y el otro, si difiere, en `[worlds.default]`. Colisiones resueltas como el servidor
  original: `-1` de Paper → Bukkit, `"default"` de Spigot → `server.properties`, `sha1` gana a
  `hash`, `debug` es `true` si cualquiera lo es, `replace-commands` antiguo, deducción de
  `enable-spam-exclusions`, `pause-when-empty-seconds` no se importa de la familia CraftBukkit
  (Paper fuerza `-1`).
- **Escritura** (`LegacyImport`): sólo valores distintos del default, sólo en los TOML (nunca en
  `server.properties`; el modo los mueve después). Los secretos van a su archivo, salvo que ya
  tenga otro valor. Se verifica leyendo de vuelta.
- **Respaldo**: `config/legacy-backup/<yyyy-MM-dd_HH-mm-ss>/` con las rutas relativas originales,
  marcador `.import-in-progress` mientras dura e `import-report.txt` (ASCII). Un archivo ilegible
  detiene el servidor antes de mover nada; una importación interrumpida se reanuda en el siguiente
  arranque sobre la misma carpeta.

Tests: `LegacyFilesTest`, `LegacyImportServersTest`, `LegacyImportCollisionsTest`,
`LegacyImportRecoveryTest`.

---

## 7. Claves omitidas a propósito

No están en ningún TOML: ninguna tenía un default que se pudiera escribir sin inventarlo, o
exponerla habría engañado al operador. La lista vive en código,
`.../legacyfolia/config/catalog/LegacyExclusions.java`, con el motivo de cada una; el importador lo
copia en `import-report.txt` y `LegacyCoverageTest` falla si aparece una clave que no esté ni en el
catálogo ni aquí. Una ruta excluye también todo lo que cuelga de ella.

| Archivo | Clave | Por qué no está |
|---|---|---|
| paper-mundo | `entities.behavior.door-breaking-difficulty` | mapa `EntityType -> List<Difficulty>` calculado en runtime desde la regla de cada mob |
| paper-global | `anticheat.obfuscation.items.model-overrides` | indexado por identificadores de modelo de ítem, no por una clave estable; vacío por defecto |
| paper-mundo | `tick-rates.sensor`, `tick-rates.behavior` | tabla bidimensional (tipo de entidad × nombre → ticks), sin forma plana |
| paper-mundo | `chunks.entity-per-chunk-save-limit`, `entities.spawning.despawn-time` | mapas por tipo de entidad, todos `-1`/disabled por defecto |
| paper-global | `packet-limiter.overrides` | indexado por **clase** Java de paquete |
| server.properties | `management-server-secret` | secreto que vanilla genera de nuevo en cada arranque |
| server.properties | `management-server-tls-keystore-password` | secreto sin archivo propio todavía; el puente no lo escribe, así que vale el default de vanilla (`""`) |
| paper-mundo | `chunks.prevent-moving-into-unloaded-chunks` | Folia lo fuerza a `true` (`WorldConfiguration.java:535`) |
| bukkit | `settings.compatibility` | código muerto: `CraftServer.loadCompatibilities()` empieza con `if (true) return;` (`CraftServer.java:476-477`) |
| paper-mundo | `feature-seeds.features` | mapa `Reference2LongMap<Holder<Feature>>` (`WorldConfiguration.java:567`) que Paper rellena él mismo cuando `world.generation.random-feature-seeds` está activo |
| server.properties | `announce-player-achievements` | clave muerta: vanilla no la lee desde 1.12, cuando pasó a ser regla de juego |

No excluidas pero sin clave propia: `resource-pack-hash` (también-de `server.resource-pack.sha1`),
`rcon.password` (va a `config/rcon.secret`), `replace-commands` en la raíz de `spigot.yml` y
`chunk-loading-basic.autoconfig-send-distance` (rutas antiguas, también-de
`plugins.commands.replace-commands` y `chunks.auto-config-send-distance`).

Ya no es configuración en Paper `804dd0c3`: el límite de canales de plugin es la propiedad de
sistema `-Dpaper.pluginMessageChannelLimit` (default 128,
`paper-server/src/main/java/io/papermc/paper/connection/PluginMessageBridgeImpl.java:16`).

---

## 8. Tipos que no son escalares

`SettingKind` (`.../legacyfolia/config/catalog/SettingKind.java`) es la forma en el TOML;
`config/types/SettingReaders` la convierte al tipo que guarda el servidor, y el modo
`server.properties` valida cada línea con el mismo conversor.

| Forma en el TOML | `SettingKind` | Nº | Ejemplos |
|---|---|---:|---|
| `true`/`false` | `BOOLEAN` | 211 | |
| entero / `long` / número | `INT` / `LONG` / `DOUBLE` | 223 / 1 / 50 | |
| texto | `STRING` | 27 | `level-type`, `restart-script` |
| lista de textos | `STRING_LIST` | 15 | `initial-enabled-packs` (en `server.properties`, separada por comas) |
| `"disabled"` o entero | `INT_OR_DISABLED` | 9 | `network.limits.incoming-packet-threshold`, `performance.entities.inactive-mob-goal-selector-throttle-rate` |
| `"default"` o entero | `INT_OR_DEFAULT` | 16 | `network.compression-level`, `performance.ticking.catchup-ticks` |
| `"disabled"`/`"default"` o número | `DOUBLE_OR_DISABLED` / `DOUBLE_OR_DEFAULT` | 1 / 4 | `gameplay.environment.void-damage-amount` / `gameplay.mobs.zombie-villager-infection-chance` |
| `"default"`, `true` o `false` | `BOOLEAN_OR_DEFAULT` | 2 | `server.console.deprecated-verbose` (Bukkit: `WarningState`) |
| `"12h"`, `"2d"` / o `"disabled"` | `DURATION` / `DURATION_OR_DISABLED` | 2 / 1 | `gameplay.items.lootables-refresh-min`, `lootables-restrict-player-reloot-time`. Desde Paper `804dd0c3` los tipos están en `io.papermc.paper.configuration.type.time` |
| entero 0-4 | `PERMISSION_LEVEL` | 2 | `op-permission-level` (4 = OWNER), `function-permission-level` (2 = GAMEMASTER) |
| MiniMessage o `"translate:<clave>"` | `MESSAGE` | 13 | `[server.messages]` |
| uno de una lista | `ENUM` | 10 | `server.difficulty` (vanilla acepta también 0-3), `world.lobby-spawn-world` |
| 1, 2 o 3 | `ENGINE_MODE` | 1 | `gameplay.exploits.anti-xray-engine-mode` (1 HIDE, 2 OBFUSCATE, 3 OBFUSCATE_LAYER) |
| tabla de datos | `TABLE` | 5 | `plugins.commands.aliases`, `world.generators`, `world.biome-providers`, `server.saving.forced-stats`, `gameplay.items.alt-item-despawn-rate-items` |

`command-block-time-format` (de `command-blocks.time-format-pattern`, `DateTimeFormatterOrDefault`)
es `STRING` con `"default"` como valor por defecto.

---

## 9. Claves que cambiaron de nombre

La mayoría conserva la última parte de su nombre. Estas no, o cambiaron de sección de forma poco
obvia:

| Origen | Clave heredada | Clave TOML |
|---|---|---|
| server.properties | `max-tick-time` | `server.watchdog.vanilla-timeout-ms` (§3.15) |
| server.properties | `spawn-protection` | `server.spawn-protection-radius` |
| server.properties | `server-ip`, `server-port`, `network-compression-threshold` | `network.ip`, `network.port`, `network.compression-threshold` |
| server.properties | `enable-rcon`, `enable-query`, `query.port`, `rcon.*` | `network.rcon.enabled`, `network.query.enabled`, `network.query.port`, `network.rcon.*` |
| server.properties | `resource-pack`, `resource-pack-*`, `require-resource-pack` | `server.resource-pack.url`, `.id`, `.sha1`, `.prompt`, `.required` |
| server.properties | `management-server-*` | `network.management.*` |
| bukkit.yml | `settings.query-plugins` | `network.query.expose-plugins` |
| bukkit.yml | `settings.shutdown-message` | `server.messages.shutdown` |
| bukkit.yml | `worlds.<mundo>.generator`, `.biome-provider` | `world.generators`, `world.biome-providers` (tablas por mundo) |
| spigot.yml | `settings.attribute.{maxHealth,maxAbsorption,movementSpeed,attackDamage}.max` | `combat.attribute-limits.*` |
| spigot.yml | `settings.moved-wrongly-threshold`, `settings.moved-too-quickly-multiplier` | `gameplay.exploits.*` |
| spigot.yml | `settings.log-villager-deaths`, `settings.log-named-deaths` | `server.console.*` |
| spigot.yml | `players.disable-saving`, `stats.*`, `advancements.*` | `server.saving.disable-player-saving`, `disable-stat-saving`, `forced-stats`, `disable-advancement-saving`, `disabled-advancements` |
| spigot.yml (mundo) | `max-tick-time.entity`, `.tile` | `performance.ticking.entity-budget-millis`, `tile-entity-budget-millis` |
| spigot.yml (mundo) | `hanging-tick-frequency` | `performance.ticking.hanging-tick-frequency` |
| spigot.yml (mundo) | `verbose` | `world.log-settings-on-load` |
| spigot.yml (mundo) | los 22 `seed-*` | `world.structure-seeds.*`, con el nombre del conjunto de estructuras de vanilla (`villages`, `nether-complexes`...) |
| paper-global | `messages.use-display-name-in-quit-message` | `server.messages.use-display-name-in-quit-message` |
| paper-global | `packet-limiter.kick-message` | `server.messages.kick-packet-limit` (MiniMessage con `<lang:...>`, porque `translate:` no lleva color) |
| paper-global | `player-auto-save.rate`, `.max-per-tick` | `server.saving.player-save-interval-ticks`, `max-players-per-tick` |
| paper-global | `watchdog.early-warning-every`, `-delay` | `server.watchdog.early-warning-interval-ms`, `early-warning-delay-ms` |
| paper-global | `misc.max-joins-per-tick`, `misc.catchup-ticks` | `performance.ticking.*` |
| paper-global | `misc.chat-threads.*` | `performance.threads.*` |
| paper-global | `misc.xp-orb-groups-per-area`, `misc.max-tracking-combat-entries` | `performance.entities.*` |
| paper-global | `misc.use-alternative-luck-formula` | `gameplay.items.use-alternative-luck-formula` |
| paper-global | `misc.prevent-negative-villager-demand` | `gameplay.mobs.*` |
| paper-global | `misc.use-dimension-type-for-custom-spawners` | `gameplay.mob-spawning.*` |
| paper-global | `misc.client-interaction-leniency-distance`, `misc.strict-advancement-dimension-check` | `gameplay.players.*` |
| paper-global | `commands.ride-command-allow-player-as-vehicle` | `gameplay.commands.*` |
| paper-global | `time.affects-all-worlds` | `gameplay.environment.time-affects-all-worlds` |
| paper-global | `threaded-regions.*` (Folia) | `performance.regions.*` |
| paper-mundo | `entities.armor-stands.*`, `entities.markers.tick` | `performance.ticking.armor-stands-tick`, `armor-stand-collision-lookups`, `markers-tick` |
| paper-mundo | `maps.*` | `performance.entities.item-frame-map-cursor-*` |
| paper-mundo | `entities.behavior.inactive-mob-goal-selector-throttle-rate` | `performance.entities.inactive-mob-goal-selector-throttle-rate` |
| paper-mundo | `entities.spawning.count-all-mobs-for-spawning` | `performance.spawning.*` |
| paper-mundo | `entities.sniffer.*`, `misc.allow-remote-ender-dragon-respawning`, `misc.max-leash-distance` | `gameplay.mobs.*` |
| paper-mundo | `entities.spawning.filter-bad-tile-entity-nbt-from-falling-blocks`, `filtered-entity-tag-nbt-paths`, `duplicate-uuid.*` | `gameplay.exploits.*` |
| paper-mundo | `misc.disable-relative-projectile-velocity`, `misc.legacy-ender-pearl-behavior` | `gameplay.items.*` |
| paper-mundo | `misc.disable-end-credits`, `misc.disable-sprint-interruption-on-attack` | `gameplay.players.*` |
| paper-mundo | `command-blocks.*`, `misc.show-sign-click-command-failure-msgs-to-player`, `spawn.allow-using-signs-inside-spawn-protection` | `gameplay.commands.command-block-*`, `show-sign-click-command-failures`, `allow-signs-in-spawn-protection` |
| paper-mundo | `command-blocks.time-format-pattern` | `gameplay.commands.command-block-time-format` |
| paper-mundo | `scoreboards.*` | `gameplay.scoreboards.*` |
| paper-mundo | `feature-seeds.generate-random-seeds-for-all` | `world.generation.random-feature-seeds` |

La columna `legacy-path` del catálogo tiene la correspondencia exacta de las 593 claves.

**Por qué las semillas son una sección de `world.toml` y no una tabla de `[worlds.*]`.** En
`spigot.yml` cuelgan de `world-settings`, pero son ajustes por mundo corrientes: todos los mundos
los leen, tienen un default fijo (la sal de vanilla) y un mundo que quiere otro lo dice, como
cualquier otro ajuste por mundo.

Renombres del propio fork: `combat.legacy-sword-damage` → `combat.legacy-weapon-damage`;
`ConfigFile` (`RENAMES`) lo renombra en su sitio conservando el valor.

---

## 10. Diferencias de defaults con Paper y Folia

Con todo en su default, los objetos construidos en memoria son idénticos a los de un servidor
nuevo, salvo lo que el fork cambia a propósito:

| Ajuste | Paper/Folia | LegacyFolia | Dónde |
|---|---|---|---|
| `enable-rcon` | `false` | `true` | `network.rcon.enabled`; `ServerPropertiesBridgeTest.FORK_DIFFERENCES` |
| `rcon.ip` | ausente (→ `server-ip`) | `127.0.0.1` | `network.rcon.ip` |
| `rcon.password` | `""` | 32 caracteres aleatorios en `config/rcon.secret` | `RconSecret` |
| `white-list` | `true` (vanilla 26.3) | `false` | parche 0031, `DedicatedServerProperties.java:128` |
| `spawn-protection` | 16 | 0 | parche 0030, `SpawnProtection` |
| combate 1.8 | — | activado (`combat.enabled = true`) | `combat.toml` |

`white-list` y `spawn-protection` no aparecen en `FORK_DIFFERENCES` porque los parches ya cambian
el default en `DedicatedServerProperties`, así que el servidor de referencia del test coincide con
el TOML. `pause-when-empty-seconds` es `-1` en los dos: el default vanilla es 60, pero Paper lo
fuerza a `-1`.

Diferencias de ubicación (no de valor): `config/` para los JSON de moderación
(`PlayerList.java:108-113`), `cache/` para `usercache.json`, `config/forwarding.secret` para el
secreto de Velocity (`VelocityProxy.java:49`), `server.eula` en lugar de `eula.txt`.

---

## 11. Decisiones

Decididas el 2026-10-18 ([PROGRESS.md](PROGRESS.md) §6):

- **Mensajes traducibles**: MiniMessage en toda `[server.messages]`, y clave de traducción sólo
  cuando el valor empieza por `translate:`.
- **Contraseña de RCON**: archivo aparte, `config/rcon.secret`, generado con 32 caracteres
  aleatorios en el primer arranque, con RCON encendido y escuchando en `127.0.0.1`. Así un TOML
  compartido en un foro nunca lleva una contraseña.
- **`server.properties`**: modo encendido por defecto, sólo con lo que difiere, ganando a los TOML.
- **EULA**: en `server.toml`, nunca en `server.properties`.
- **Lobby**: `world.lobby-spawn-world`, con `logout-world` (default) y `main-world`.
- **Whitelist**: apagada por defecto pese a vanilla 26.3 (única excepción a "el comportamiento de
  upstream gana").
