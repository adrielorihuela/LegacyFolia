# Mapa de ajustes: de los archivos heredados a `config/*.toml`

Documento de diseño para la migración que hará que los seis (u ocho, ver §2.7) TOML de `config/`
**posean** todos los ajustes del servidor y los archivos heredados dejen de existir.

No es un ensayo: cada afirmación lleva la ruta donde se comprueba. Nada de lo que sigue está
implementado todavía; hoy los TOML sólo **anulan** a los heredados
(`docs/CONFIGURATION.md`, sección "It does not replace anything").

> **Números de línea de 26.2.** El fork ya está en Minecraft 26.3 (Paper `c927bd34`), pero las
> rutas con número de línea de este documento siguen siendo las de 26.2 (Paper `c9e894d3`) hasta
> que se refresquen. Los nombres de clase y de campo siguen valiendo; las líneas pueden haberse
> movido. Un default ya se sabe distinto: `white-list`, que 26.3 pone a `true` y el fork mantiene
> en `false` (parche 0031).

Invariante que gobierna todo el trabajo: **un plugin no debe poder distinguir este fork de Folia
leyendo configuración**. Los plugins no leen archivos, leen objetos en memoria
(`SpigotConfig.*`, `GlobalConfiguration.get()`, `Bukkit.spigot().getConfig()`,
`Server#getViewDistance`, `Server#getAllowNether`). La migración cambia **de dónde se llenan** esos
objetos, nunca su existencia ni sus tipos.

---

## 1. Inventario

### 1.1 Recuento por archivo

| Archivo heredado | Fuente de verdad | Nº de ajustes | Notas |
|---|---|---:|---|
| `config/server.properties` | `folia-server/src/minecraft/java/net/minecraft/server/dedicated/DedicatedServerProperties.java` | **71** | 2 son heredados muertos (`announce-player-achievements` L82, `resource-pack-hash` L158) → **69 vivos**. 14 de ellos son `MutableValue` (escribibles en caliente, §5.2) |
| `config/bukkit.yml` | `paper-server/src/main/resources/configurations/bukkit.yml` | **28** en la plantilla | + 3 claves no plantilladas que el código sí lee: `settings.world-container` (`folia-server/src/minecraft/java/net/minecraft/server/Main.java:205`), `settings.compatibility` (`paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java:522`, **código muerto**, §2.6), `worlds.<mundo>.generator` (`CraftServer.java:1782,1818`) |
| `config/spigot.yml` (global) | `paper-server/src/main/java/org/spigotmc/SpigotConfig.java` | **36** | +`config-version` (L71-72), que es metadato, no ajuste |
| `config/spigot.yml` (por mundo) | `paper-server/src/main/java/org/spigotmc/SpigotWorldConfig.java` | **104** | de las cuales 22 son `growth.<Cultivo>Modifier` y 22 son `seed-<estructura>` |
| `config/commands.yml` | `paper-server/src/main/resources/configurations/commands.yml` | **3** | `command-block-overrides`, `ignore-vanilla-permissions`, `aliases` |
| `config/paper-global.yml` | `paper-server/src/main/java/io/papermc/paper/configuration/GlobalConfiguration.java` | **87 hojas** en 30 secciones | 117 campos `public` − 30 clases `ConfigurationPart` anidadas |
| `config/paper-world-defaults.yml` (+ `<mundo>/paper-world.yml`) | `paper-server/src/main/java/io/papermc/paper/configuration/WorldConfiguration.java` | **173 hojas** en 43 secciones | `PaperConfigurations.java:94-96` fija los tres nombres de archivo |

**Total aproximado: 502 ajustes.** El grueso (277) es Paper; el segundo grueso (104) es
`spigot.yml` por mundo.

### 1.2 `server.properties` — 71 claves

Extraídas de las llamadas `this.get(...)`/`this.getMutable(...)` en
`DedicatedServerProperties.java` (L53-165).

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

### 1.3 `bukkit.yml` — 28 claves plantilladas

| Sección | Nº | Claves |
|---|---:|---|
| `settings` | 11 | `allow-end`, `warn-on-overload`, `permissions-file`, `update-folder`, `plugin-profiling`, `connection-throttle`, `query-plugins`, `deprecated-verbose`, `shutdown-message`, `minimum-api`, `use-map-color-cache` |
| `spawn-limits` | 7 | `monsters`, `animals`, `water-animals`, `water-ambient`, `water-underground-creature`, `axolotls`, `ambient` |
| `chunk-gc` | 1 | `period-in-ticks` |
| `ticks-per` | 8 | `animal-spawns`, `monster-spawns`, `water-spawns`, `water-ambient-spawns`, `water-underground-creature-spawns`, `axolotl-spawns`, `ambient-spawns`, `autosave` |
| raíz | 1 | `aliases` (puntero a `commands.yml`) |

Los nombres de `spawn-limits`/`ticks-per` se construyen en
`paper-server/src/main/java/org/bukkit/craftbukkit/util/CraftSpawnCategory.java:14-21`.

### 1.4 `spigot.yml` global — 36 claves

| Sección | Nº | Claves |
|---|---:|---|
| `settings` | 17 | `bungeecord`, `debug`, `netty-threads`, `timeout-time`, `restart-on-crash`, `restart-script`, `player-shuffle`, `sample-count`, `user-cache-size`, `save-user-cache-on-stop-only`, `moved-wrongly-threshold`, `moved-too-quickly-multiplier`, `log-named-deaths`, `log-villager-deaths`, `attribute.{attackDamage,maxAbsorption,maxHealth,movementSpeed}.max` |
| `commands` | 6 | `log`, `tab-complete`, `send-namespaced`, `silent-commandblock-console`, `spam-exclusions`, `replace-commands` |
| `messages` | 6 | `outdated-client`, `outdated-server`, `restart`, `server-full`, `unknown-command`, `whitelist` |
| `advancements` | 2 | `disabled`, `disable-saving` |
| `stats` | 2 | `disable-saving`, `forced-stats` |
| `players` | 1 | `disable-saving` |
| `world-settings.default` | 1 | `below-zero-generation-in-existing-chunks` (SpigotConfig L352, no SpigotWorldConfig) |
| legado | 1 | `replace-commands` en raíz, migrado a `commands.replace-commands` (SpigotConfig L273-274) |

### 1.5 `spigot.yml` por mundo — 104 claves

| Grupo | Nº | Comentario |
|---|---:|---|
| `entity-activation-range.*` | 24 | incluye el subárbol `wake-up-inactive.*` (12) |
| `growth.<Cultivo>Modifier` | 22 | Bamboo, Beetroot, Cactus, Cane, Carrot, CaveVines, Cocoa, GlowBerry, Kelp, Melon, Mushroom, NetherWart, PitcherPlant, Potato, Pumpkin, Sapling, SweetBerry, TorchFlower, TwistingVines, Vine, WeepingVines, Wheat |
| `seed-<estructura>` | 22 | ancientcity, buriedtreasure, desert, endcity, fossil, igloo, jungle, mansion, mineshaft, monument, nether, ocean, outpost, portal, shipwreck, slime, stronghold, swamp, trailruins, trialchambers, village |
| `hunger.*` | 7 | exhaustion por acción |
| `entity-tracking-range.*` | 6 | |
| `ticks-per.*`, `merge-radius.*`, `max-tick-time.*` | 6 | `hopper-check`/`hopper-transfer`, `item`/`exp`, `entity`/`tile` |
| sueltas | 17 | `view-distance`, `simulation-distance`, `mob-spawn-range`, `item-despawn-rate`, `arrow-despawn-rate`, `trident-despawn-rate`, `hanging-tick-frequency`, `hopper-amount`, `hopper-can-load-chunks`, `max-tnt-per-tick`, `nerf-spawner-mobs`, `thunder-chance`, `unload-frozen-chunks`, `verbose`, `enable-zombie-pigmen-portal-spawns`, `zombie-aggressive-towards-villager`, `{dragon-death,wither-spawn,end-portal}-sound-radius` |

### 1.6 `paper-global.yml` — 87 hojas / 30 secciones

| Sección | Nº | Sección | Nº |
|---|---:|---|---:|
| raíz (`is-first-start`, `version`) | 2 | `misc` (sin `chat-threads`) | 15 |
| `chunk-loading-basic` | 3 | `misc.chat-threads` | 1 |
| `chunk-loading-advanced` | 3 | `block-updates` | 4 |
| `messages` | 2 | `anticheat.obfuscation.items` | 4 |
| `messages.kick` | 4 | `unsupported-settings` | 9 |
| `spark` | 2 | `commands` | 2 |
| `proxies.bungee-cord` | 1 | `time` | 1 |
| `proxies.velocity` | 3 | `scoreboards` | 2 |
| `proxies.proxy-protocol` | 1 | `chunk-system` | 2 |
| `console` | 3 | `item-validation` (+`book`,`book-size`) | 8 |
| `watchdog` | 2 | `packet-limiter` | 2 |
| `spam-limiter` | 5 | `collisions` | 2 |
| `player-auto-save` | 1 | `update-checker` | 1 |
| `threaded-regions` | 3 | | |

### 1.7 `paper-world-defaults.yml` — 173 hojas / 43 secciones

| Sección | Nº | Sección | Nº |
|---|---:|---|---:|
| `entities.spawning` (+ 7 subsecciones) | 36 | `environment` (+ frosted-ice, treasure-maps) | 22 |
| `entities.behavior` (+ pillager-patrols…) | 24 | `chunks` | 7 |
| `entities.tracking-range-y` | 7 | `tick-rates` | 7 |
| `entities.*` (mob-effects, armor-stands, markers, sniffer) | 9 | `collisions` | 5 |
| `lootables` | 8 | `hopper` | 3 |
| `anticheat.anti-xray` | 8 | `misc` | 10 |
| `fixes` | 6 | `unsupported-settings` (+`ticking`) | 4 |
| `max-growth-height` | 4 | `maps`, `fishing-time-range`, `feature-seeds`, `command-blocks`, `scoreboards`, `spawn`, `version` | 12 |

---

## 2. Agrupación propuesta

El criterio es el que ya rige `ServerConfig` (`.../legacyfolia/config/ServerConfig.java:14-21`):
**la función manda, el origen no cuenta.**

### 2.1 `server.toml` — identidad, arranque, consola, moderación

| Sección propuesta | Contenido | Origen |
|---|---|---|
| `[server]` | `motd`, `max-players`, `difficulty`, `gamemode`, `force-gamemode`, `hardcore`, `level-name`, `pause-when-empty-seconds`, `player-idle-timeout`, `eula-prompt-timeout-seconds` (ya existe) | server.properties + TOML actual |
| `[server.status]` | `enable-status`, `hide-online-players`, `status-heartbeat-interval`, `settings.sample-count` (spigot), `settings.player-shuffle` (spigot) | server.properties + spigot.yml |
| `[server.console]` | `console.enable-brigadier-highlighting`, `console.enable-brigadier-completions`, `console.has-all-permissions`, `broadcast-console-to-ops`, `broadcast-rcon-to-ops`, `settings.debug`, `debug`, `settings.warn-on-overload`, `settings.deprecated-verbose` | paper-global + server.properties + spigot + bukkit |
| `[server.access]` | `online-mode`, `white-list`, `enforce-whitelist`, `enforce-secure-profile`, `op-permission-level`, `function-permission-level`, `log-ips`, `allow-flight` | server.properties |

**`spawn-protection` no aparece aquí a propósito.** Lo posee ya el TOML como
`server.spawn-protection-radius` (§3.7, `.../legacyfolia/world/SpawnProtection.java:26-29`).
Listarlo también en `[server.access]` recrearía exactamente el duplicado que §3.7 manda cerrar.

| `[server.messages]` | los 6 de `messages.*` de spigot + `messages.kick.*` y `messages.no-permission` de paper-global + `settings.shutdown-message` de bukkit | spigot + paper-global + bukkit |
| `[server.resource-pack]` | `resource-pack{,-id,-sha1,-prompt}`, `require-resource-pack` | server.properties |
| `[server.watchdog]` | `settings.timeout-time`, `settings.restart-on-crash`, `settings.restart-script`, `watchdog.early-warning-every`, `watchdog.early-warning-delay` | spigot + paper-global |
| `[server.saving]` | `players.disable-saving`, `stats.disable-saving`, `stats.forced-stats`, `advancements.disable-saving`, `advancements.disabled` — los cinco son de **`SpigotConfig`**, no de paper-global — y `player-auto-save.rate` | spigot + paper-global |
Aquí va **sólo el guardado de jugadores**. `ticks-per.autosave` (bukkit) y
`chunks.auto-save-interval` (paper-mundo) son el autoguardado de **chunks** y van a `[chunks]`.
La versión anterior de esta tabla ponía `ticks-per.autosave` aquí y se contradecía con §3.6;
manda §3.6, porque la periodicidad de guardado de chunks es un coste de E/S por mundo y la de
jugadores no lo es.

| `[server.data-packs]` | `initial-enabled-packs`, `initial-disabled-packs`, `text-filtering-config`, `text-filtering-version` | server.properties |

### 2.2 `world.toml` — qué mundos existen y cómo se generan

| Sección | Contenido | Origen |
|---|---|---|
| `[dimensions]` | ya existe: `overworld`/`nether`/`end`; **absorbe** `misc.enable-nether` y `settings.allow-end` (§3.1) | paper-global + bukkit |
| `[world]` | ya existe (`always-spawn-at-world-spawn`, `disable-nether-roof`) | fork |
| `[world.generation]` | `level-seed`, `level-type`, `generator-settings`, `generate-structures`, `max-world-size` | server.properties |
| `[world.storage]` | `settings.world-container`, `region-file-compression`, `sync-chunk-writes` | bukkit + server.properties |
| `[world.generators]` | `worlds.<mundo>.generator` de bukkit.yml | bukkit |

### 2.3 `network.toml` — todo lo que entra o sale por un socket

| Sección | Contenido | Origen |
|---|---|---|
| `[network]` | `server-ip`, `server-port`, `use-native-transport`, `network-compression-threshold`, `misc.compression-level`, `settings.netty-threads`, `prevent-proxy-connections`, `accepts-transfers` | server.properties + spigot + paper-global |
| `[network.velocity]` | ya existe `secret-file`; absorbe `proxies.velocity.{enabled,online-mode}` | paper-global |
| `[network.bungeecord]` | `settings.bungeecord` (spigot) + `proxies.bungee-cord.online-mode` (paper) — §3.2 | spigot + paper-global |
| `[network.proxy-protocol]` | `proxies.proxy-protocol` | paper-global |
| `[network.rcon]` | `enable-rcon`, `rcon.port`, `rcon.ip`, `rcon.password` | server.properties |
| `[network.query]` | `enable-query`, `query.port`, `settings.query-plugins` | server.properties + bukkit |
| `[network.management]` | las 8 `management-server-*` | server.properties |
| `[network.limits]` | `rate-limit`, `settings.connection-throttle`, `packet-limiter.*`, `spam-limiter.*`, `chat-spam-threshold-seconds`, `command-spam-threshold-seconds`, `commands.spam-exclusions` | mezcla de los cinco archivos |
| `[network.monitoring]` | `enable-jmx-monitoring`, `spark.*`, `update-checker.enabled` | server.properties + paper-global |

`management-server-secret` y `management-server-tls-keystore-password` **no** van al TOML: siguen la
regla de `SecretFiles` (`docs/CONFIGURATION.md`, "Secrets") y merecen sus propios
`config/*.secret`, como ya se hizo con `forwarding.secret`
(`.../legacyfolia/network/ForwardingSecret.java`).

### 2.4 `performance.toml` — coste de CPU, memoria y ancho de banda

| Sección | Contenido | Origen |
|---|---|---|
| `[chunks]` | ya existe `adaptive-view-distance`; añade `view-distance`, `simulation-distance` (§3.3), `chunk-loading-basic.*`, `chunk-loading-advanced.*`, `chunk-system.*`, `chunk-gc.period-in-ticks`, `misc.region-file-cache-size`, `chunks.*` de paper-world (auto-save-interval, delay-chunk-unloads-by, …), `unload-frozen-chunks` | los cinco archivos |
| `[performance.entities]` | `entity-activation-range.*` (24), `entity-tracking-range.*` (6), `entities.tracking-range-y.*`, `entity-broadcast-range-percentage`, `merge-radius.*`, `collisions.*`, `max-entity-collisions` | spigot-mundo + paper + server.properties |
| `[performance.spawning]` | `spawn-limits.*` (7), `ticks-per.*-spawns` (7), `entities.spawning.{spawn-limits,ticks-per-spawn,per-player-mob-spawns,despawn-ranges}`, `mob-spawn-range`, `nerf-spawner-mobs` | §3.4 |
| `[performance.ticking]` | `max-tick-time`, `max-tick-time.{entity,tile}`, `max-chained-neighbor-updates`, `tick-rates.*`, `misc.max-joins-per-tick`, `misc.catchup-ticks`, `hanging-tick-frequency` | server.properties + spigot + paper |
| `[performance.regions]` | `threaded-regions.{threads,grid-exponent,scheduler}` | paper-global (Folia) |

### 2.5 `combat.toml` — sin cambios

`[combat]` ya posee sus 18 claves (`folia-server/src/main/resources/legacyfolia/combat.toml`).
Absorbe además `settings.attribute.*.max` de spigot (4) y `misc.use-alternative-luck-formula`.

### 2.6 `plugins.toml` — sin cambios estructurales

`[plugins.auto-update]` ya existe. Absorbe:
`settings.update-folder`, `settings.permissions-file`, `settings.minimum-api`,
`settings.plugin-profiling`, `settings.use-map-color-cache`,
`misc.load-permissions-yml-before-plugins`, `scoreboards.track-plugin-scoreboards`,
`scoreboards.save-empty-scoreboard-teams`, `item-validation.*`,
`commands.suggest-player-names-when-null-tab-completions`, y los 3 de `commands.yml`
(`aliases`, `command-block-overrides`, `ignore-vanilla-permissions`)
más `commands.{log,tab-complete,send-namespaced,silent-commandblock-console,replace-commands}` de
spigot. Sugerencia: renombrar la sección a `[plugins]` con subsecciones
`[plugins.auto-update]`, `[plugins.commands]`, `[plugins.api]`.

**`settings.compatibility` NO se migra: es código muerto.** `CraftServer.loadCompatibilities()`
empieza con `if (true) return;`
(`paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java:520-521`, marcado
"Paper - Big nope"), así que la clave se lee de un archivo que nadie consulta y
`activeCompatibilities` queda siempre vacío. Migrarla habría creado un ajuste que no hace nada,
que es peor que no tenerlo: un operador lo cambia, no ve efecto, y concluye que la configuración
unificada está rota.

### 2.7 Dos archivos nuevos

**`gameplay.toml` (sección `gameplay`) — obligatorio.** Sin él, ~200 ajustes de reglas de juego
(hambre, crecimiento de cultivos, comportamiento de mobs, redstone, exploits) tendrían que caber en
`world.toml`, que dejaría de ser "qué mundos existen" para ser un cajón. Secciones:

| Sección | Contenido | Origen |
|---|---|---|
| `[gameplay.hunger]` | los 7 `hunger.*` | spigot-mundo |
| `[gameplay.growth]` | los 22 `growth.<Cultivo>Modifier` + `max-growth-height.*` | spigot-mundo + paper-mundo |
| `[gameplay.mobs]` | `entities.behavior.*` (24), `entities.mob-effects.*`, `zombie-aggressive-towards-villager`, `enable-zombie-pigmen-portal-spawns` | paper-mundo + spigot-mundo |
| `[gameplay.environment]` | `environment.*` (22), `thunder-chance`, `fishing-time-range`, `tick-rates` de cultivos | paper-mundo + spigot-mundo |
| `[gameplay.items]` | `item-despawn-rate`, `arrow-despawn-rate`, `trident-despawn-rate`, `alt-item-despawn-rate`, `lootables.*` (8), `hopper.*`, `hopper-amount`, `max-tnt-per-tick` | §3.5 |
| `[gameplay.redstone]` | `misc.redstone-implementation`, `misc.alternate-current-update-order`, `block-updates.*` | paper |
| `[gameplay.exploits]` | `unsupported-settings.*` global (9) y por mundo (4), `fixes.*` (6), `anticheat.anti-xray.*` (8), `anticheat.obfuscation.items.*` (4) | paper |
| `[gameplay.sounds]` | los 3 `*-sound-radius` | spigot-mundo |

**`worlds.toml` (sección `worlds`) — obligatorio si se quiere paridad.** `spigot.yml` y
`paper-world-defaults.yml` son configuraciones **por mundo**; un TOML global no las representa
(§5.1). Forma propuesta, que reproduce exactamente la resolución de
`SpigotWorldConfig.java:46-86` (mundo → default) y la de Paper (`<mundo>/paper-world.yml` →
`paper-world-defaults.yml`):

```toml
[worlds.default]            # equivale a world-settings.default y a paper-world-defaults.yml
"performance.entities.activation-range.animals" = 32

[worlds."world_nether"]     # equivale a world-settings.<mundo> y a <mundo>/paper-world.yml
"performance.entities.activation-range.animals" = 16
```

Regla: **toda clave de `[gameplay.*]` y de `[performance.*]` marcada "por mundo" admite override en
`[worlds.<nombre>]`**; `ServerConfig` resuelve mundo → `[worlds.default]` → sección global → default
del código. Esto exige un `ServerConfig.forWorld(name)` que hoy no existe
(`ServerConfig.java:100-160` sólo tiene acceso global).

---

## 3. Duplicados y conflictos

El caso conocido no es el único. Cada fila es una decisión que hoy está partida en dos archivos;
juntas son la justificación entera del trabajo.

| # | Decisión | Sitio A | Sitio B | Quién gana |
|---|---|---|---|---|
| 3.1 | ¿Existe la dimensión? | `misc.enable-nether` (paper-global, `GlobalConfiguration.java` § Misc) | `settings.allow-end` (bukkit.yml) | **`[dimensions]`**. Ya resuelto en `.../legacyfolia/world/Dimensions.java`; consumido en `CraftServer.java:787,793` |
| 3.2 | ¿BungeeCord? | `settings.bungeecord` (SpigotConfig L190-196) | `proxies.bungee-cord.online-mode` (paper-global L112-117) | **`[network.bungeecord]`**: `enabled` (era Spigot) + `online-mode` (era Paper). Nótese que SpigotConfig L192 **escribe** `false` si detecta configuración incoherente |
| 3.3 | Distancia de visión | `view-distance`/`simulation-distance` (server.properties) | `view-distance`/`simulation-distance` por mundo (`SpigotWorldConfig.java:161-186`) | **`[chunks]`** global + override en `[worlds.<n>]`. Ojo: hoy el valor spigot `<= 0` cae a `Bukkit.getViewDistance()` (L170,L184), es decir depende del otro (§5.4) |
| 3.4 | Límites de spawn | `spawn-limits.*` y `ticks-per.*-spawns` (bukkit.yml, vía `CraftSpawnCategory.java:14-21`, leídos en `CraftServer.java:499,885`) | `entities.spawning.spawn-limits` / `ticks-per-spawn` (paper-mundo, `WorldConfiguration.java:182,187`) | **`[performance.spawning]`**. Paper ya gana cuando su valor es `>= 0`; con `-1` cae a bukkit (`CraftWorld.java:307,1554-1557`). Un solo valor elimina el `-1` mágico |
| 3.5 | Despawn de flechas | `arrow-despawn-rate` (spigot-mundo) | `entities.spawning.{non-player,creative}-arrow-despawn-rate` (paper-mundo, `WorldConfiguration.java:173-174`, `ArrowDespawnRate.def(spigotConfig)`) | **Paper**; el valor spigot pasa a ser el default del de Paper. Es un `FallbackValue` explícito (`.../configuration/type/fallback/FallbackValue.java:19`) |
| 3.6 | Autoguardado | `ticks-per.autosave` (bukkit, `CraftServer.java:467,1005`) | `chunks.auto-save-interval` (paper-mundo) y `player-auto-save.rate` (paper-global) | **`[server.saving]`** para jugadores, **`[chunks]`** para chunks. Tres claves para "cada cuánto se guarda" es el segundo peor caso del inventario |
| 3.7 | Protección del spawn | `spawn-protection` (server.properties) | `server.spawn-protection-radius` (TOML, `.../world/SpawnProtection.java:26-29`, default **0** frente al vanilla 16) | **El TOML**, que ya es el dueño. Duplicado que este trabajo debe cerrar, no crear |
| 3.8 | Secreto de Velocity | `proxies.velocity.secret` (paper-global) | `config/forwarding.secret` (`ForwardingSecret.java`, `GlobalConfiguration.java:128-134`) | **El archivo de secreto**. La migración ya existe y vacía la clave YAML |
| 3.9 | Colisiones de jugadores | `collisions.enable-player-collisions` (paper-global) | `collisions.only-players-collide` (paper-mundo) | **`[performance.entities]`**, uno global y otro por mundo; hay que documentar que no son sinónimos aunque lo parezcan |
| 3.10 | Caché de usuarios | `settings.user-cache-size`, `settings.save-user-cache-on-stop-only` (spigot L281-289) | ubicación del archivo, ya en `cache/` (`ConfigPaths.java:44-56`) | **`[server]`**; ojo, el archivo ya no es configuración (`ConfigPaths.cache`, `Main.java:200`) |
| 3.11 | Watchdog | `settings.timeout-time`, `settings.restart-on-crash` (spigot, consumidos en `DedicatedServer.java:249`) | `watchdog.early-warning-every`, `watchdog.early-warning-delay` (paper-global) | **`[server.watchdog]`** |
| 3.12 | Estrangulamiento de conexión | `settings.connection-throttle` (bukkit, `CraftServer.java:876`) | `messages.kick.connection-throttle` (paper-global) | **`[network.limits]`** el umbral, **`[server.messages]`** el texto. Hoy están en archivos distintos por accidente histórico |
| 3.13 | Generación bajo cero | `world-settings.default.below-zero-generation-in-existing-chunks` (SpigotConfig L352, **global** pese al nombre) y el homónimo por mundo (`SpigotWorldConfig`) | — | Clave con el mismo nombre leída desde dos clases distintas; hay que decidir una y sólo una |
| 3.14 | Semillas de estructuras | 22 `seed-<estructura>` (spigot-mundo) | `feature-seeds.{generate-random-seeds-for-all,features}` (paper-mundo) | **Paper**, con las 22 como `[worlds.<n>]` heredadas |
| 3.15 | Ticking de entidades | `max-tick-time` (server.properties, watchdog) | `max-tick-time.entity` / `.tile` (spigot-mundo) | Nombres casi idénticos, propósitos distintos (watchdog vs. presupuesto de tick). Renombrar al migrar es obligatorio, no cosmético |

---

## 4. Quién lee qué, y cuándo

Orden real de arranque, comprobable línea a línea:

| Orden | Punto | Archivo:línea | Qué queda poblado |
|---:|---|---|---|
| 1 | `ServerConfig.load()` | `folia-server/src/minecraft/java/net/minecraft/server/Main.java:127` | **los ocho TOML**, antes que nada más |
| 2 | `AdaptiveDistance.reload()`, `Combat.reload()` | `Main.java:128-129` | los dos módulos del fork que ya leen TOML |
| 3 | `new DedicatedServerSettings(options)` + `forceSave()` | `Main.java:132-133`; ruta en `DedicatedServerSettings.java:12` | `DedicatedServerProperties` (y **reescribe** `server.properties`) |
| 4 | `RegionFileVersion.configure(...regionFileComression)` | `Main.java:140` | compresión de regiones, desde `server.properties` |
| 5 | `PaperConfigurations.loadLegacyConfigFile(bukkit/spigot)` | `Main.java:144-145` | dos `YamlConfiguration` sueltas |
| 6 | `SpigotConfig.disabledAdvancements` | `Main.java:196` | parche temprano de SPIGOT-5885 |
| 7 | `settings.world-container` | `Main.java:205` | `universePath` |
| 8 | `SpigotConfig.init(spigot.yml)` | `folia-server/src/minecraft/java/net/minecraft/server/dedicated/DedicatedServer.java:234`; también `paper-server/src/main/java/org/spigotmc/SpigotCommand.java:40` y `CraftServer.java:1007` | **`SpigotConfig.*` estáticos + `SpigotConfig.config`** |
| 9 | `initializeGlobalConfiguration` / `initializeWorldDefaultsConfiguration` | `DedicatedServer.java:238-239` | **`GlobalConfiguration.get()`** y defaults de mundo |
| 10 | `WatchdogThread.doStart(SpigotConfig.timeoutTime, ...)` | `DedicatedServer.java:248` | watchdog |
| 11 | `createWorldConfig(...)` por mundo | `folia-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:597` | **`level.paperConfig()`** y `SpigotWorldConfig` |
| 12 | `CraftServer` ctor: lee bukkit.yml y commands.yml | `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java:428-472` | `this.configuration`, `this.commandsConfiguration`, `spawnCategoryLimit`, `warningState`, `minimumAPI`, `autosavePeriod` |
| 13 | `CraftServer.reload()` | `CraftServer.java:985-1050` + `paperConfigurations.reloadConfigs` (`PaperConfigurations.java:332-337`, `ReloadCommand.java:30`) | **repite 8, 9, 12 en caliente** |

**Corrección respecto de la versión anterior de esta tabla.** Decía que `ServerConfig.load()` ocurría
en el paso 3, después de `DedicatedServerSettings` y después de `RegionFileVersion.configure`. Ya no
es así: `Main.java` carga los TOML **primero**, en la línea 127, precisamente para que nada lea un
ajuste antes de que el archivo que lo posee exista. Esto elimina el peligro que §5.6 describía y
cambia el orden de todas las filas siguientes. Las rutas con nombre de archivo suelto llevan ahora
su directorio: las clases de `org.spigotmc` viven en `paper-server/src/main/java/org/spigotmc/`,
no bajo `folia-server/src/minecraft/`.

### 4.1 Objeto a poblar por grupo

| Grupo TOML | Objeto que los plugins ven | Cómo se llena hoy | Punto de inyección al migrar |
|---|---|---|---|
| `[server]`, `[server.access]`, `[server.status]`, `[server.resource-pack]`, `[world.generation]` | `DedicatedServerProperties` (y su API: `Server#getMotd`, `#getMaxPlayers`, `#getViewDistance`) | `Settings.get*` sobre un `java.util.Properties` (`Settings.java:180-256`) | construir el `Properties` desde los TOML antes de `new DedicatedServerSettings` (`Main.java:124`), en lugar de leer el archivo |
| `[server.messages]`, `[server.watchdog]`, `[server.saving]`, `[network.bungeecord]`, `[network.limits]` parcial, `[combat]` (atributos) | `SpigotConfig.*` estáticos y `Bukkit.spigot().getConfig()` (`CraftServer.java:2778`) | `SpigotConfig.init` → `readConfig` reflexivo (`SpigotConfig.java:73`) | rellenar `SpigotConfig.config` (una `YamlConfiguration` en memoria) desde los TOML antes de `readConfig`, **sin escribir a disco** |
| `[network.*]`, `[chunks]`, `[performance.regions]`, `[gameplay.redstone]`, `[gameplay.exploits]` global | `GlobalConfiguration.get()` | Configurate, `initializeGlobalConfiguration` (`DedicatedServer.java:238`) | alimentar el `ConfigurationNode` desde los TOML en `PaperConfigurations` |
| `[plugins.*]`, `[performance.spawning]` (parte bukkit), `[world.generators]` | `this.configuration` / `this.commandsConfiguration` de `CraftServer` (expuestas vía `Server#getUpdateFolder`, `#getSpawnLimit`, `#getShutdownMessage`…) | `YamlConfiguration` cargadas en `Main.java:144` y `CraftServer.java:428` | construir esas dos `YamlConfiguration` **en memoria** desde los TOML |
| `[gameplay.*]`, `[performance.entities]`, `[worlds.*]` | `level.paperConfig()` y `SpigotWorldConfig` | `ServerLevel.java:597` | `createWorldConfig` recibe los valores del TOML resueltos para ese mundo |

Nota clave: **ninguno de estos objetos desaparece.** El trabajo consiste en cambiar el origen de sus
datos, no su interfaz. `Bukkit.spigot().getConfig()` debe seguir devolviendo una `YamlConfiguration`
poblada, aunque ya no exista ningún `spigot.yml` en disco.

---

## 5. Riesgos

### 5.1 Ajustes por mundo contra un TOML global
`SpigotWorldConfig.java:46-86` resuelve `world-settings.<mundo>` → `world-settings.default`, y Paper
hace lo propio con `<mundo>/paper-world.yml` → `paper-world-defaults.yml`
(`PaperConfigurations.java:94-96, 318-321`). **277 ajustes** (104 spigot + 173 paper) son por mundo.
Aplanarlos a un TOML global **rompe** cualquier servidor que hoy afine el Nether distinto del
overworld, y lo hace en silencio. Mitigación: el `worlds.toml` de §2.7 y un
`ServerConfig.forWorld(String)` con la misma cadena de resolución, escrito y probado **antes** de
migrar la primera clave por mundo.

### 5.2 Escritura en caliente
Tres mecanismos escriben configuración en tiempo de ejecución. Un TOML que sólo se lea al arrancar
los rompe:

- **`MutableValue`** (`Settings.java:258-270`, `DedicatedServerSettings.update` L25-28): 14 claves de
  `server.properties` (`difficulty`, `white-list`, `max-players`, `view-distance`,
  `simulation-distance`, `motd`, `allow-flight`, `force-gamemode`, `enforce-whitelist`,
  `spawn-protection`, `player-idle-timeout`, `enable-status`, `hide-online-players`,
  `entity-broadcast-range-percentage`, `status-heartbeat-interval`, `pause-when-empty-seconds`,
  `accepts-transfers`, `gamemode`, `op-permission-level`) se cambian con comandos vanilla
  (`/difficulty`, `/whitelist on`) **y se persisten a disco**. `ServerConfig.set` ya sabe escribir
  (`ServerConfig.java:160-166`); hay que enlazarlo, o esos comandos empezarán a mentir.
- **`SpigotConfig.set` + `save`**: `SpigotConfig.java:72,145,147,166-167,192,250-262,273-274`.
  Dos casos concretos:
  - L192 **desactiva** `settings.bungeecord` sola cuando detecta configuración incoherente.
  - `commands.enable-spam-exclusions` (L250-262) **no existe en la plantilla**. Si falta,
    `spamExclusions()` la deduce de `commands.spam-exclusions` — `false` si la lista sigue siendo
    el `["/skill"]` de fábrica, `true` si el operador la tocó — y la **escribe** al arrancar.
    Es decir: la primera ejecución de un servidor nuevo ya escribe una clave que nadie puso.
    Cualquier lado TOML tiene que reproducir esa deducción, no sólo el valor.
- **`Bukkit.spigot().getConfig()`** (`CraftServer.java:2778`): devuelve un objeto que un plugin puede
  mutar. Nada garantiza que no lo haga.

### 5.3 Lecturas por ruta de archivo
- `Main.java:123` construye `Paths.get("server.properties")` a mano — hoy sólo para el mensaje de log
  de `--initSettings` (L161), pero es un literal que quedará mintiendo.
- `CraftServer.java:1150`: `new File(this.configuration.getString("settings.permissions-file"))`,
  ruta relativa al directorio de trabajo, no a `config/`.
- `bukkit.yml` apunta a `commands.yml` por nombre (`aliases: now-in-commands.yml`).
- **Plugins de terceros** que abran `spigot.yml`/`bukkit.yml` con `new File(...)` en lugar de la API.
  Si los archivos desaparecen, esos plugins ven un archivo inexistente. Mitigación posible: dejar los
  YAML como **artefactos de sólo lectura regenerados desde los TOML**, marcados con una cabecera que
  diga que editarlos no hace nada. Es la única salvaguarda real, y debe decidirse antes de la fase 5.

### 5.4 Defaults que dependen de otro ajuste
- `SpigotWorldConfig.java:167-174`: `view-distance <= 0` → `Bukkit.getViewDistance()`, luego
  `clamp(3,32)`. Lo mismo con `simulation-distance` (L179-186).
- `CraftWorld.java:1554-1557`: límite de spawn del mundo `< 0` → el del servidor
  (`CraftServer.getSpawnLimitUnsafe`, L2341).
- `WorldConfiguration.java:173-174`: los despawn de flechas se inicializan **desde**
  `spigotConfig` (`FallbackValue`, `.../type/fallback/FallbackValue.java:19`). Si `spigot.yml` deja de
  existir sin sustituir el `FallbackValue`, el default cambia sin aviso.
- `DedicatedServerProperties.java:160-162`: `rcon.ip` ausente → `server-ip`.
- `DedicatedServerProperties.java:112`: `sync-chunk-writes` es el AND de la propiedad **y** de la
  propiedad de sistema `Paper.enable-sync-chunk-writes`.
- `GlobalConfiguration` § ThreadedRegions: `threads = -1` significa "auto" con `@PostProcess`
  (`GlobalConfiguration.java:405-411`).
- `Dimensions.java:88-119` ya documenta el patrón correcto para estos casos: en la ejecución que
  **crea** el TOML se adopta el valor heredado, no el de la plantilla.

### 5.5 Recarga
`/spigot reload` (`SpigotCommand.java:40`), `/paper reload` (`ReloadCommand.java:30`) y
`Bukkit.reload()` (`CraftServer.java:1001-1056`) reejecutan los pasos 7, 8 y 11 de §4. Los TOML
tienen que participar de esa recarga o quedarán obsoletos respecto de lo que el operador acaba de
editar. `ServerConfig.load()` hoy se llama **una sola vez** a propósito (`Main.java:134-137`, por la
lógica de `wasCreated`): hará falta separar "cargar" de "recargar".

### 5.6 Orden de lectura
Ya **no** es cierto que `RegionFileVersion.configure` (`Main.java:140`) se ejecute antes que
`ServerConfig.load()`: la carga de los TOML es la línea 127 y es lo primero que ocurre (§4). El
riesgo residual es otro y sigue siendo real:

- `region-file-compression` se toma de `settings.getProperties()`, es decir de `server.properties`,
  no del TOML. La clave existe en `performance.toml` bajo `[chunks]` y **hoy no hace nada**; el
  comentario del archivo lo dice. Enlazarla es trabajo de la fase 2.
- Se consume una sola vez y nada la vuelve a leer, así que ninguna recarga la mueve. Cualquier
  ajuste con esta forma tiene que decirlo en su comentario, porque el fallo — editar, recargar, no
  ver cambio — es silencioso.
- La regla general que sustituye a la anterior: un ajuste puede migrarse al TOML si su primer lector
  está **por debajo** de `Main.java:127`. Para todo lo que corre por encima de esa línea (poco:
  arranque de la JVM, hilos de DataConverter) no hay TOML posible.

---

## 6. Orden de ejecución

Criterio: **en ningún momento puede existir un estado en el que un objeto en memoria quede con su
valor por defecto porque su archivo ya no existe y el TOML todavía no lo posee.** Por eso ningún
archivo heredado se borra hasta la fase 7.

| Fase | Qué | Riesgo | Por qué aquí |
|---|---|---|---|
| **0. Andamiaje** | `ServerConfig.forWorld(String)` con resolución mundo→default→global; separar `load()` de `reload()`; enlazar `ServerConfig.set` a los `MutableValue`; un test que falle si un ajuste está declarado en un TOML pero nadie lo inyecta | Ninguno (no cambia comportamiento) | Sin esto, cualquier clave por mundo o escribible en caliente que se migre después se rompe en silencio |
| **1. Inversión, no migración** | Que los archivos heredados se generen **desde** las estructuras en memoria en vez de al revés, en un solo punto por objeto (§4.1). Los TOML aún no poseen nada | Bajo; el disco queda igual | Convierte el resto del trabajo en "cambiar la fuente", que es una línea por grupo |
| **2. Ajustes globales, no escribibles, sin fallback** | `[network.rcon]`, `[network.query]`, `[network.management]`, `[network.monitoring]`, `[server.console]`, `[server.watchdog]`, `[server.messages]`, `[server.resource-pack]`, `[plugins.*]`, `[gameplay.redstone]` | Bajo | Sin resolución por mundo, sin escritura en caliente, sin defaults cruzados. Es el bloque grande y aburrido: **empezar por aquí** |
| **3. Duplicados ya identificados** | Las 15 filas de §3, una a una, cada una con el patrón `Dimensions.settle` (adoptar el valor heredado en la ejecución que crea el archivo) | Medio | Cada fila es un cambio de comportamiento visible; hacerlas de una en una permite revertir una sola |
| **4. Escribibles en caliente** | Los `MutableValue` de `server.properties` y las claves que `SpigotConfig` reescribe (§5.2) | Medio-alto | Requiere la fase 0 completa; un `/whitelist on` que no persista es un incidente de moderación |
| **5. Por mundo** | `worlds.toml`; los 104 de `spigot.yml` y los 173 de `paper-world-defaults.yml` | **Alto** | El bloque más grande y el único que puede cambiar el comportamiento de un mundo concreto sin tocar su archivo. Va después de todo lo demás |
| **6. Fallbacks cruzados** | `view-distance`/`simulation-distance` (§5.4), despawn de flechas, límites de spawn, `rcon.ip` | Alto | Sólo tiene sentido cuando ambos lados del fallback ya viven en el TOML |
| **7. Retirada** | Dejar de escribir los YAML, o degradarlos a artefactos regenerados de sólo lectura (§5.3); enlazar `region-file-compression` al TOML (la carga ya va primero, `Main.java:127`; hoy la clave se sigue leyendo de `server.properties`, §5.6) | Alto | Es el único paso irreversible para los plugins que leen por ruta. Requiere una decisión explícita sobre §5.3 |

**Se puede hacer ya, con riesgo casi nulo:** fases 0, 1 y 2, y dentro de la 3 las filas 3.7
(protección del spawn, donde el TOML ya es dueño) y 3.8 (secreto de Velocity, ya migrado).

**Debe ir al final, sin excepción:** la fase 5 (por mundo), la 6 (fallbacks cruzados) y la 7
(retirada de los YAML). Migrar una clave por mundo antes de que exista `forWorld` produce
exactamente el fallo que este plan existe para evitar: un mundo que pierde su ajuste y nadie se
entera hasta que los mobs dejan de aparecer.

---

## 7. Tipos que no son escalares

Los TOML se escribieron con el valor que el código usa hoy, pero varios de esos valores no son un
número o un booleano simple. El código de enganche (fases 1 a 6) tiene que aceptar estas formas;
un lector que asuma `int` falla con la primera de ellas.

| Forma en el TOML | Tipo real | Ejemplos | Qué tiene que hacer el lector |
|---|---|---|---|
| `"disabled"` o un entero | `IntOr.Disabled` | `spam-limiter.incoming-packet-threshold` (300), `item-validation.book-size.page-max` (2560), `max-arrow-despawn-invulnerability` | aceptar cadena **o** entero; `"disabled"` es un valor legal, no un error |
| `"default"` o un entero | `IntOr.Default` | `misc.compression-level` | igual; `"default"` significa "lo que decida el servidor" |
| `"default"`, `true` o `false` | `BooleanOrDefault` | `treasure-maps-find-already-discovered-loot-tables` | tres estados, no dos |
| `"12h"`, `"2d"`, `"disabled"` | `Duration` / `DurationOrDisabled` | `despawn-time`, `lootables.refresh-*` | parsear con el mismo serializador de Paper, no a mano |
| entero `1`, `2` o `3` | enum `EngineMode` serializado como número | `anti-xray-engine-mode` | convertir 1→HIDE, 2→OBFUSCATE, 3→OBFUSCATE_LAYER |
| entero `4`, `2` | `LevelBasedPermissionSet` | `op-permission-level` (OWNER), `function-permission-level` (GAMEMASTER) | `deserializePermission`; no pasar el entero tal cual |
| clave de traducción | `Component.translatable` | `kick.authentication-servers-down`, `kick.flying-player`, `kick.flying-vehicle` | ver decisión pendiente 9.1 |
| `[]` | `Set` envuelto | `unsupported-settings.oversized-item-component-sanitizer` | una lista, no un booleano |
| `""` | ausente | `rcon.ip` | **no** pasar `""` como dirección explícita: con cadena vacía RCON tiene que seguir heredando `[network].ip` |

Defaults acoplados que el TOML aplana y el enganche tiene que respetar: `trident-despawn-rate`
vale lo que valga `arrow-despawn-rate` (`SpigotWorldConfig` L295); el 1200 escrito sólo es
correcto mientras el otro no cambie. `sync-chunk-writes` es el AND de la clave y de la propiedad de
sistema `Paper.enable-sync-chunk-writes`.

## 8. Claves omitidas a propósito

No están en ningún TOML, y no es un descuido. Ninguna tenía un default que se pudiera escribir sin
inventarlo, o exponerla habría sido engañar al operador.

| Clave | Por qué no está |
|---|---|
| `entities.behavior.door-breaking-difficulty` | mapa `EntityType -> List<Difficulty>` calculado en runtime desde `Zombie/Vindicator.DOOR_BREAKING_PREDICATE` |
| `anticheat.obfuscation.items.model-overrides` | indexado por el `Identifier` de un modelo de ítem, no por una clave estable |
| `tick-rates.sensor`, `tick-rates.behavior` | tabla bidimensional (`Table<EntityType, String, Integer>`), sin forma plana |
| `entity-per-chunk-save-limit`, `despawn-time` | mapas por tipo de entidad, todos `-1`/disabled por defecto |
| `packet-limiter.overrides` | indexado por **clase** de paquete |
| `packet-limiter.kick-message` | `Component.translatable`, a la espera de 9.1 |
| `management-server-secret`, `management-server-tls-keystore-password` | secretos; el primero además es aleatorio en cada arranque |
| `chunks.prevent-moving-into-unloaded-chunks` | Folia lo fuerza a `true` en `@PostProcess` (`WorldConfiguration.java:528-531`) |
| `settings.compatibility` | código muerto: `CraftServer.loadCompatibilities()` empieza con `if (true) return;` |

Pendiente de asignar: `messages.use-display-name-in-quit-message` (GlobalConfiguration, `false`)
existe en el código y ningún archivo lo recoge. Candidato natural: `[server.messages]`.

## 9. Decisiones pendientes

### 9.1 Cómo se escriben los mensajes traducibles
Hoy los tres mensajes de expulsión que son `Component.translatable` se escribieron como su clave
de traducción. Hace falta una regla para toda `[server.messages]`. **Recomendación:** texto
MiniMessage en todos, y una clave de traducción sólo cuando el valor empieza por `translate:`.
MiniMessage es lo que Paper ya usa para el resto de mensajes, permite colores, y el prefijo deja
claro cuál de las dos formas es cada valor.

### 9.2 `rcon.password` dentro del TOML o en un archivo aparte
`rcon.password` quedó dentro de `network.toml`, mientras el secreto del servidor de gestión se manda
a un archivo `.secret`, igual que `config/forwarding.secret`. Es el mismo tipo de dato con el
criterio contrario. **Recomendación:** archivo aparte, `config/rcon.secret`, creado vacío y con
RCON apagado mientras esté vacío. Así un TOML compartido en un foro para pedir ayuda nunca lleva
una contraseña dentro.

> Los números de línea citados en este documento corresponden a la base 26.2
> (`paperRef c9e894d3`). Hay que revisarlos después de actualizar a 26.3.

