# Estado del proyecto

Este documento existe para que el trabajo pueda retomarse exactamente donde quedó, por una persona
o por otra sesión, sin depender de la memoria de nadie. Se actualiza al cerrar cada fase.

**Última actualización:** 2026-10-31 · **Último tag de fase:** `v0.19.0` · **Prerelease de prueba:**
`v1.0.0-rc.1` · **Base:** Folia `ver/26.3.x` en `73865e0` (2026-10-29, merge `e5e16b3`), Minecraft
26.3, Paper `804dd0c3`, Java 25.

---

## 1. Qué se pidió

LegacyFolia es un fork público de [PaperMC/Folia](https://github.com/PaperMC/Folia) (aparece como
fork, una sola rama `main`). Los requisitos originales, en el orden en que llegaron:

1. **Configuración reorganizada.** Todo lo que genera el servidor, en carpetas; en la raíz solo
   queda el jar. Ajustes agrupados por **lo que hacen** (servidor, mundo, red, rendimiento), no por
   el servidor del que vienen (bukkit, spigot, paper). Cada ajuste comentado: qué es, qué hace,
   cómo se usa. **Sin romper la compatibilidad con plugins.**
2. Los TOML de `config/` son siempre la fuente de verdad. `server.properties` es **opcional** y,
   si se activa, se sincroniza en las dos direcciones; si hay conflicto gana `server.properties`;
   al desactivarlo se borra. Los `bukkit.yml`, `spigot.yml` y `paper-*.yml` **desaparecen** (los
   plugins siguen viendo los valores). Whitelist, bans y ops viven en `config/`. El EULA pasa a ser
   una clave de configuración.
3. Activar o desactivar overworld, Nether y End (pensado para lobbies).
4. Secreto de Velocity en `config/forwarding.secret`, creado vacío.
5. Auto-updater de plugins: Hangar, Modrinth, GitHub y LuckPerms, con checksum, con fallback de
   release a beta y de beta a alpha, y filtrado por paper/folia. Token de GitHub opcional en un
   archivo vacío.
6. EULA interactivo: ENTER acepta y ESC cancela; si ya es `true`, no pregunta.
7. Arreglar la locator bar (`locator_bar`) en Folia, replicando lo que hace Mojang.
8. Integrar el código de ChunkScope (no como dependencia), para Java 25.
9. PvP 1.8 **exacto**, sacado del código fuente de 1.8.8, con bloqueo con espada. Todo
   configurable y **activado por defecto**.
10. Spawn protection a 0 por defecto. Opción de hacer spawn siempre en el spawn del mundo.
    Opción de anular el techo del Nether.
11. Jar con el nombre y la versión del proyecto.
12. Scripts `build` y `update`. Documentación optimizada para SEO.
13. Proceso: un commit por cambio, al estilo de Folia; un tag por fase; `v1.0.0` reservado para la
    release con el jar adjunto; `/init` al final.
14. (2026-10-31) Antes de `v1.0.0`, una prerelease con el jar en GitHub para probarlo; `v1.0.0` solo
    cuando el usuario lo diga. `/init` pasa a significar actualizar **toda** la documentación
    (`README.md`, cada `docs/*.md`, este documento y `CLAUDE.md`) contra el código, no solo
    `CLAUDE.md`.

## 2. Qué está hecho

| Tag | Fecha | Contenido |
|---|---|---|
| v0.1.0 | 08-22 | Esqueleto del fork: marca, scripts `build` y `update` |
| v0.2.0 | 08-22 | Primera configuración unificada con plantilla comentada y merge no destructivo |
| v0.3.0 | 08-22 | Interruptores de dimensiones: `[dimensions]` responde por `getAllowNether/End` |
| v0.4.0 | 08-22 | `config/forwarding.secret`, migrado desde `paper-global.yml` |
| v0.5.0 | 08-22 | EULA interactivo |
| v0.6.0 | 08-22 | Auto-updater: Hangar, Modrinth, GitHub y LuckPerms, con verificación de checksum |
| v0.7.0 | 08-22 | Locator bar restaurada con snapshots inmutables publicados en el hilo del emisor |
| v0.8.0 | 08-22 | Distancia de visión adaptativa (lo de ChunkScope) |
| v0.9.0 | 08-22 | Combate 1.8: cooldown, sweep, knockback, críticos, bloqueo |
| v0.9.1 | 08-23 | Correcciones: el prompt del EULA no salía, la locator bar no quitaba marcadores, faltaba `github.token` |
| v0.10.0 | 08-27 | TOML por área (`server`, `world`, `network`, `performance`, `combat`, `plugins`). En la raíz solo queda el jar; whitelist, bans, ops, EULA y yml van a `config/`, y la caché a `cache/` |
| v0.11.0 | 09-02 | PvP 1.8 completo: armadura, protección, regeneración, hambre, pociones, sharpness, fire aspect, velocidad. 26 ajustes |
| v0.12.0 | 09-02 | Lobby (spawn en el spawn del mundo), techo del Nether, spawn protection 0, jars `legacyfolia-<versión>.jar` |
| v0.13.0 | 10-18 | Los 430 ajustes heredados escritos en los ocho TOML, comentados; `worlds.toml` y `gameplay.toml` |
| v0.14.0 | 10-18 | Minecraft 26.3 (Folia `ver/26.3.x`), con todos los parches del fork |
| v0.15.0 | 10-31 | Enganche de la configuración: los TOML son la fuente, `server.properties` es un override opcional, los servidores antiguos se migran en el primer arranque; EULA como clave, `/legacyfolia properties`, lobby configurable |
| v0.16.0 | 10-31 | Daño de las armas de 1.8 (espadas, hachas, picos y palas), el último de los nueve enganches del PvP |
| v0.17.0 | 10-31 | El tooltip de las armas muestra el daño de 1.8: se reescribe al enviar el objeto y se quita de lo que devuelve un cliente en creativo |
| v0.18.0 | 10-31 | Locator bar idéntica a la de vanilla: solo la dimensión propia, la máquina de estados de conexiones de vanilla sobre snapshots, todos los paquetes en el hilo del receptor |
| v0.19.0 | 10-31 | PvP 1.8 idéntico a Spigot 1.8.8: daño, protección, bloqueo, knockback, alcance, proyectiles, caña, explosiones, pociones, comida, invulnerabilidad al entrar; 12 claves nuevas (parches 0038–0057) |
| v1.0.0-rc.1 | 10-31 | Prerelease para probar antes de `v1.0.0`: upstream al día (Paper `804dd0c3`), documentación entera revisada contra el código, jar paperclip adjunto en GitHub |

**Estado del código hoy** (comprobado leyendo el árbol, no los documentos):
- Parches propios: minecraft 0012–0057 (46) y paper 0007–0013 (7), encima de los de Folia (minecraft
  0001–0011 y paper 0001–0006, idénticos a upstream). El mapa por número está en `CLAUDE.md`.
- Catálogo (`legacyfolia/catalog.tsv`): **593 claves**; 303 admiten override por mundo, 19 las
  reescribe el servidor en caliente (`mutable`), 3 son secretos y 513 tienen origen heredado
  (Paper global 88, Paper por mundo 182, Spigot 35, Spigot por mundo 105, Bukkit 30, `commands.yml`
  3, `server.properties` 69, `eula.txt` 1); las 80 restantes son propias. Por archivo: gameplay 207,
  performance 132 (`[chunks]` 31 y `[performance.*]` 101), server 76, combat 62, network 45, world
  40 (`[dimensions]` 3 y `[world.*]` 37), plugins 31; `worlds.toml` solo tiene ejemplos.
- **Tests:** 325 en `LegacyFoliaTestSuite`, en 39 archivos de resultados, sin fallos ni omitidos
  (2026-10-31, tras el merge de Paper `804dd0c3`). La ejecución completa de `:folia-server:test`
  da 10461 tests con 86 omitidos, todos de upstream.
- El jar que sale de `./build.sh` es `folia-server/build/libs/legacyfolia-paperclip-<versión>.jar`;
  la versión la pone `settings.gradle.kts` (`26.3.local-SNAPSHOT` sin `BUILD_NUMBER`), es decir, la
  de Minecraft y no la de LegacyFolia.
- Defaults que difieren de Folia/Paper/vanilla, todos comprobables en los TOML de
  `folia-server/src/main/resources/legacyfolia/`: `white-list = false`, `spawn-protection-radius = 0`,
  `[network.rcon]` encendido en `127.0.0.1` con `config/rcon.secret`, `[combat] enabled = true`.
  Apagados por defecto: auto-updater, distancia de visión adaptativa, lobby
  (`always-spawn-at-world-spawn`) y techo del Nether.

**Contenido de `v0.13.0`:**
- `ServerConfig.forWorld(nombre)`: resuelve `[worlds.<nombre>]`, luego `[worlds.default]` y luego
  la sección global. Se calcula una vez por mundo y se invalida al recargar. Lo implementan
  `config/WorldConfig.java` y `config/WorldConfigCache.java`.
- Dos archivos nuevos, `worlds.toml` y `gameplay.toml`.
- **430 ajustes heredados nuevos** escritos en los ocho TOML, cada uno comentado y con el default
  leído del código. Contando las claves que ya existían quedan 435: gameplay 163, performance 115,
  server 66, world 15, network 45 y plugins 31.
  `worlds.toml` explica el mecanismo de override con ejemplos.
- Consolidación: `region-file-compression` estaba duplicado y ahora vive solo en `[chunks]`; los
  ejemplos de `worlds.toml` usan rutas que resuelven de verdad; hay un test que demuestra que los
  ocho archivos parsean.
- `docs/SETTINGS-MAP.md` corregido y completado, con los tipos no escalares, las claves omitidas y
  las decisiones pendientes.

**Historia del número de tests** (contando los XML de `folia-server/build/test-results/test`):
129 antes de la fase de enganche; 150, 189, 224 y 262 tras sus etapas 1 a 4; 269 tras la 5a
contando 5 suites vacías de upstream, que ejecuciones posteriores ya no producen (264 sin ellas), y
271 tras la 5b; 275 en
`v0.16.0`, 288 en `v0.17.0`, 303 en `v0.18.0` y 325 en `v0.19.0`. El merge de Paper `804dd0c3` no
añade tests: los dos ajustes nuevos los detectó `LegacyCoverageTest` y se catalogaron.

**Etapa 2 del enganche (hecha, 2026-10-30): el modo `server.properties`** (decisión 6 de §6).
- `config/ServerProperties.java`: `reconcile()`, `enable()`, `disable()`, `status()`, `isEnabled()`,
  `set(ruta, valor[, mundo])`, `reset(ruta[, mundo])`, `flush()` y `file()`. Lo describe su javadoc.
- `ServerConfig` contesta con los overrides ya aplicados (`getInt`, `forWorld`... sin cambiar ninguna
  llamada) y acepta un directorio (`load(Path)`) para que los tests no toquen `run/`.
- `config/properties/`: `PropertiesDocument` (lee como `Properties.load` y cambia línea a línea,
  conservando comentarios y orden), `TomlText` (cambia un valor en el texto del TOML sin reescribir el
  archivo), `TomlLiterals`, `PropertyValues` (texto de `server.properties` a valor tipado y vuelta,
  validado con `SettingReaders.convert`) y `AtomicFiles` (temporal y move).
- No se reescriben los TOML con night-config: su `save()` borra la cabecera y los ejemplos comentados
  de `worlds.toml` y re-indenta. Por eso `TomlText`.
- Arreglado de paso en `ConfigFile.merge`: no rellenaba desde la plantilla las tablas de datos
  (`plugins.commands.aliases`...), así que un alias por defecto borrado volvía en cada arranque.

**Etapa 3 del enganche (hecha, fusionada el 2026-10-31): el importador**, en `config/migration/`.
`LegacyImport.runIfNeeded(raíz, contenedor de mundos)` detecta un vanilla, Spigot, Paper o Folia,
lee sus archivos (también el `paper-world.yml` de cada mundo, en la raíz o en `config/`), resuelve
las colisiones como el servidor original (`ImportPlanner`), escribe los TOML, mueve los originales a
`config/legacy-backup/<fecha>/` con un `import-report.txt`, y si se interrumpe lo termina en el
siguiente arranque (un marcador en la carpeta de copia). Los secretos van a sus archivos, nunca a un
TOML.

**Etapa 4 del enganche (hecha, fusionada el 2026-10-31): los objetos en memoria**, en `config/bridge/`.
`ServerPropertiesBridge.build` da el `Properties` de `DedicatedServerProperties`;
`SpigotBridge.build`/`addWorld` el `YamlConfiguration` de `SpigotConfig`; `BukkitBridge.bukkit` y
`commands` los de `bukkit.yml` y `commands.yml`; `PaperBridge.global`, `worldDefaults` y `world` los
nodos de Configurate de Paper. Con los TOML por defecto, cada uno da exactamente lo que el servidor
original escribe en una instalación nueva (hay un test por objeto que lo compara). Además
`config/RconSecret` (clave de RCON en `config/rcon.secret`, 32 caracteres de `SecureRandom`).

**Etapa 5a del enganche (hecha, 2026-10-31): la preparación.**
- Fusionadas las etapas 3 y 4 (`ConfigFile.open(Path, String)` existía en las dos con el mismo
  cuerpo; queda una). Ramas y worktrees borrados.
- **94 claves heredadas sin sitio, catalogadas** (`SETTINGS-MAP.md` §8): el catálogo pasa de 482 a
  576 claves. Lo que queda fuera a propósito está en `config/catalog/LegacyExclusions` y en
  `SETTINGS-MAP.md` §8, y
  `LegacyCoverageTest` falla si aparece una clave heredada sin catalogar ni excluir.
- **Ningún TOML se reescribe con night-config.** `ConfigFile` (merge al arrancar y `set`, que usa
  `Dimensions` vía `ServerConfig.set`) y el importador editan el texto con `TomlText`; una clave o
  tabla nueva se inserta con su comentario sacado de la plantilla, en su sitio. Actualizar los
  archivos de la versión anterior da exactamente las plantillas nuevas, byte a byte.
- `SettingReaders.legacyMessage`: un `translate:` en un campo `String` de Spigot o Bukkit se
  renderiza en el idioma del servidor (inglés), que es lo que el jugador habría leído; el javadoc
  ya lo dice.

**Etapa 5b del enganche (hecha, 2026-10-31, `v0.15.0`): la conexión al servidor.** Los TOML son la
fuente de todo lo que lee el servidor.
- `config/ConfigHooks` es el único punto de entrada: cada parche es una línea que lo llama.
  `start()` (importador, `ServerConfig.load()`, `ServerProperties.reconcile()` con su informe en el
  log), `reload()`, los objetos de la etapa 4 y `vanillaChanged()` para las escrituras en caliente.
- `ServerConfigSettings` ya no pierde las tablas ni los mundos: responde desde los documentos
  cargados con los overrides del modo `server.properties` encima, con el mismo `TomlSettings` de los
  tests, y se reconstruye solo cuando cambia `ServerConfig.generation()`.
- Parches nuevos: minecraft 0032 (arranque desde los TOML, EULA como clave, `DedicatedServerSettings`
  sin leer ni guardar `server.properties`, sección de Spigot de cada mundo al cargarse, nombre del
  mundo para Paper), 0033 (escrituras en caliente en `Settings.MutableValue#update` y `flush()` al
  salir), 0034 (lobby `main-world`); paper 0011 (`SpigotConfig.init(YamlConfiguration)`), 0012
  (`CraftServer`, `SpigotCommand`, `/paper reload`, `Configurations`/`PaperConfigurations` y el
  cargador de plugins sin YAML en disco) y 0013 (registro de `/legacyfolia`).
- Nada escribe ya `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml`,
  `paper-world-defaults.yml`, ningún `paper-world.yml` ni `eula.txt`. Sin `ConfigHooks.start()`
  (solo en los tests que usan los archivos de Paper como referencia) Paper se comporta como upstream.
- `server.eula` nunca va a `server.properties` (`SettingEntry.EULA`).
- Verificado a mano con un servidor nuevo, con un plugin de prueba antes y después de editar
  `server.properties`, con los comandos y con una migración desde un `run/` de Paper 26.3; los
  resultados, con cifras, están en el informe de cierre de la etapa y en los mensajes de sus commits.

## 3. Incidencias que conviene recordar

- **Cuenta de GitHub marcada** (agosto de 2026). El perfil y los repos daban 404 a quien no
  estuviera logueado, y no se podía hacer fork. La causa fue el README de otro repo del usuario. Se
  apeló con el ticket #4690674, se levantó la marca y después se creó el fork.
- **Árboles generados.** `folia-server/src/minecraft/java`, `paper-server` y `paper-api` son
  repositorios git propios e ignorados; los parches salen de **sus commits**. `applyAllPatches`
  los **resetea**. Por eso dos agentes no pueden tocar código NMS a la vez: todo lo que modifique
  esos árboles va en serie. Lo que vive en `folia-server/src/main` (trackeado) sí admite trabajo en
  paralelo.
- **`rebuildAllServerPatches` hace stage de todo.** Para mantener un commit por cambio, usar
  `git commit --only <ruta>`.
- **Defaults que no son números.** Ver `SETTINGS-MAP.md` §7.
- **Cortes por límite de uso.** Dos agentes murieron a medias; el trabajo se recuperó porque estaba
  en archivos del árbol trackeado. Por eso hay que commitear pronto y a menudo.

## 4. Punto exacto de reanudación

Al escribir esto (2026-10-31), `main` es la única rama y está publicada. Todo lo que había que hacer
antes de `v1.0.0` está hecho: la fase de enganche (`v0.15.0`), el daño de las armas y su tooltip
(`v0.16.0`, `v0.17.0`), la locator bar (`v0.18.0`), el PvP 1.8 idéntico (`v0.19.0`), la
actualización de upstream a Paper `804dd0c3` (§7) y el `/init`, que revisó la documentación entera
contra el código.

**Lo siguiente es probar `v1.0.0-rc.1`**, la prerelease de GitHub con el jar adjunto (§5, punto 1).
Lo que se encuentre se corrige en `main` y, si hace falta otra ronda, sale un `rc.2`.
**`v1.0.0` solo se publica cuando el usuario lo diga** (§5, punto 2).

La comprobación de arranque está hecha sobre 26.3: con `run/config/*.toml` borrados,
`runDevServer` llega a `Done (` y genera los ocho TOML; un segundo arranque con los archivos
presentes no duplica ninguna clave, no cambia ningún archivo y conserva dos valores editados a mano
(`motd` en `server.toml` y `jump-walk-exhaustion` en `gameplay.toml`). Tras el merge de `804dd0c3`
vuelve a llegar a `Done (` y añade las dos claves nuevas a los TOML existentes.

## 5. Qué falta, en orden

1. **Probar `v1.0.0-rc.1`.** Lo que no se ha podido comprobar sin clientes reales:
   - **PvP 1.8:** golpes, combos y knockback entre dos jugadores; bloqueo con espada y su animación;
     el indicador de cooldown oculto; alcance a través de hierba; caña, perlas y flechas; explosiones
     de TNT y creeper; pociones y manzana de Notch; reaparecer y recibir un golpe al momento; el
     tooltip de las armas en un cliente moderno y en uno 1.8 vía ViaVersion; cómo se siente
     comparado con un servidor 1.8 real.
   - **Locator bar:** dos clientes a más de 332 bloques, en dimensiones distintas, agachados y con
     cabeza puesta.
   - **EULA:** el prompt en Linux con una TTY real (ENTER acepta, ESC cancela).
   - **Configuración:** un servidor nuevo (en la raíz solo el jar y las carpetas que genera; los ocho
     TOML y `config/server.properties` con solo la cabecera), una migración desde un Paper o Folia
     real, `/legacyfolia properties on|off|status`, `/whitelist on` y `/paper reload` con un plugin
     que lea los valores de Bukkit, Spigot y Paper.
   - **Auto-updater** con al menos una fuente activada, y los interruptores de dimensiones, lobby y
     techo del Nether.
   - ViaVersion 5.11.0 da un error al arrancar porque no soporta 26.3 (§7); para probar con clientes
     1.8 hace falta 5.12.1 o posterior.
2. **Release `v1.0.0`**, con el jar paperclip adjunto, **solo cuando el usuario lo indique**.

### Hecho (de lo que había en esta lista)

1. **Fase de enganche. TERMINADA (2026-10-31, `v0.15.0`).** Etapas 1 a 5b (§2). Dos matices que el
   usuario conoce: `/difficulty` no escribe `server.properties`, porque en vanilla y en Paper cambia
   la dificultad del mundo y no el ajuste (regla de upstream; el ejemplo correcto es `/whitelist
   on`), y `help.yml` y `permissions.yml` siguen leyéndose del directorio de trabajo, como antes. Cómo
   quedó (orden de arranque, recarga, modo `server.properties` y migración) está en
   `SETTINGS-MAP.md` §4 a §6.
2. **Daño de las armas de 1.8. HECHO (2026-10-31, `v0.16.0`).** El último de los nueve enganches
   del PvP. Sacado de `ItemSword`, `ItemTool` y `EnumToolMaterial` de 1.8.8: base por tipo (espada
   4, hacha 3, pico 2, pala 1) más el bonus del material, sobre el 1 del jugador. Las espadas y los
   picos hacen 1 más que en 26.3, las palas 0,5 menos y las hachas de 2 a 4 menos (en 1.8 el hacha
   hacía menos que la espada); las azadas ya coinciden. Cobre y netherite siguen la misma regla; el
   tridente, el mazo y las lanzas se quedan como en 26.3. No se toca el registro `Items`: se
   sustituye el modificador del arma al leer el atributo en `Player.attack` y `Mob.doHurtTarget`
   (parche 0035, `Combat.attackDamage`), así que sigue un reload y no hay que cargar la
   configuración antes de `Bootstrap`. Un arma con modificadores propios no se toca. **El tooltip
   (`v0.17.0`)** muestra la cifra de 1.8: `combat/WeaponTooltips` la pone en el codificador de objetos
   al enviar (parche 0036) y la quita al decodificar lo que manda un cliente en creativo, así que
   ningún objeto guardado cambia; detalles en `PVP-1.8.md`. Claves:
   `legacy-weapon-damage` y `sword-`, `axe-`, `pickaxe-` y `shovel-base-damage`; sustituyen a
   `legacy-sword-damage`, que no leía nada; desde `v0.19.0` `ConfigFile` la renombra a
   `legacy-weapon-damage` al arrancar (punto 4). Comprobado en un servidor con un plugin de prueba (cifras en `PVP-1.8.md`).
3. **Locator bar idéntica a vanilla y Paper. HECHO (2026-10-31, `v0.18.0`).** Paper no cambia la
   lógica de vanilla (solo añade `canSee`, la distancia al cuadrado y no hacer nada con la regla
   apagada). Diferencias encontradas y corregidas (parche 0037 y `waypoint/`):
   - **Otras dimensiones:** al salir de un mundo (`removePlayer`) se olvidaban las conexiones del
     receptor sin mandar `remove` al cliente, y los marcadores se quedaban congelados. Ahora se
     quitan todos, como vanilla, y el mundo nuevo añade los suyos. Cada mundo tiene su propio gestor.
   - Un emisor que se movía no llegaba a un receptor quieto (solo se recalculaba al moverse el
     receptor). Ahora el receptor se pone al día en su tick.
   - No existían las reglas de vanilla para cambiar de tipo: la conexión de bloque solo se rompe con
     un salto de más de un bloque o al dejar de verse, la de chunk con un salto de más de un chunk o
     cuando el chunk llega al cliente, la de azimut cuando el chunk es visible o ya no está lejos.
     Los 332 bloques se comparan en `float`, como `Entity.distanceTo`.
   - Faltaba `firstTick` (vanilla no conecta a nadie en su primer tick).
   - `trackWaypoint` de un emisor ya seguido, `remakeConnections` (equipos) y `/waypoint modify`
     no reenviaban el `add`; `untrack`+`track` no mandaba el `remove` previo.
   - Apagar la regla no mandaba `remove` y borraba los snapshots; al encenderla nadie aparecía hasta
     moverse. Ahora es `breakAllConnections` y `updatePlayer` como en vanilla.
   - El icono iba con el estilo del momento del envío; ahora el de la creación de la conexión.
   - Un `trackWaypoint` de un emisor sin alcance (agachado) se descartaba; vanilla lo sigue y un
     espectador lo ve.
   - `remakeConnections` y el `updatePlayer` de la regla se llamaban desde otro hilo y leían el
     jugador de otra región; ahora se apuntan y se hacen en el tick del dueño.
   Todos los paquetes de waypoint a un jugador salen de su propio hilo. Diferencias que quedan, por el
   modelo de hilos: lo que hace el emisor llega al receptor hasta un tick más tarde, y varios
   movimientos en ese tick llegan como un solo `update`; el reaparecer de Folia reutiliza el mismo
   `ServerPlayer`, así que no hay el primer tick que vanilla tiene tras reaparecer; y un
   `remakeConnections` sobre un emisor no seguido (agachado) no conecta a los espectadores como en
   vanilla. **Sin comprobar con un cliente real.**
4. **PvP 1.8 idéntico al original. HECHO (2026-10-31, `v0.19.0`).** Se compararon todas las mecánicas
   con el código de Spigot/CraftBukkit 1.8.8 y se corrigieron en los parches 0038–0057, cada uno con
   su clave o con `enabled`: Fuerza y Debilidad, las cinco protecciones (redondeo hacia abajo, vacío),
   bloqueo como modificador `BLOCKING` (solo jugadores, no contra daño que ignora armadura) y la
   ventana de invulnerabilidad con el daño bruto, golpes de 0 de daño a jugadores, casco, agotamiento,
   dificultad, todo el knockback (dirección, resistencia, empuje extra, precisión, restore solo de
   jugadores), sharpness con smite/bane y en mobs, bane, fire aspect, sonidos/estadística/inclinación/
   críticos, alcance, proyectiles, caña, explosiones, pociones, manzana de Notch (efectos y receta),
   comida, mano secundaria/escudos/tótems, caídas/fuego/espinas/gólem, invulnerabilidad al entrar.
   Claves nuevas (12) y `legacy-sword-damage` renombrada al arrancar (también escrita con punto o
   entre comillas; si no se puede, avisa en vez de decir que lo hizo). La tabla de ajustes de
   `PVP-1.8.md` está completa.
   Tras la revisión del código se corrigieron además: la TNT se empuja desde los pies (0056); los
   rayos de las explosiones y la línea de visión del alcance se paran en hierba, flores, antorchas y
   carteles, como `World.rayTrace` de 1.8, con la cabeza agachada a 1,54; una explosión de bola de
   fuego, cráneo de wither o cristal del End no tiene causante (sin empuje base hacia el ghast, el
   wither o quien rompió el cristal, y escala con la dificultad); la invulnerabilidad de 3 s solo al
   entrar al servidor, no al reaparecer ni al salir del End (0057), y deja pasar las caídas en un mundo
   con PvP. Se rechazó volver a sumar la espera de carga del cliente moderno: vanilla también la corta
   a los 60 ticks, y 1.8 no la tenía.
   Lo que no se pudo igualar (hitbox agachado, camas/miel/carga de viento, animación de bloqueo en
   cliente moderno; la caña en el agua y clavada mantiene la lógica moderna) está en `PVP-1.8.md`.
   Probado con 325 tests y arrancando el servidor (`Done (`).
   **Sin comprobar con clientes reales**: es lo primero del punto 1 de arriba.
5. **Actualización de upstream a Paper `804dd0c3`. HECHO (2026-10-31).** Detalles en §7.
6. **`/init`. HECHO (2026-10-31).** Documentación entera revisada contra el código; desde ahora
   `/init` significa eso (`CLAUDE.md`, «Talking to the user»).

## 6. Decisiones del usuario (decididas)

Son del usuario y son definitivas (2026-10-18). Sustituyen a las tres preguntas abiertas que había
aquí (mensajes traducibles, `rcon.password` y lobby en multimundo) y a las recomendaciones de
`SETTINGS-MAP.md` (hoy §11), que quedan resueltas así:

1. **Whitelist DESACTIVADA por defecto.** Es una excepción a la regla de prioridad de upstream
   (punto 2), porque 26.3 la activa. Ya está aplicado en el parche 0031.
2. **Regla de upstream.** Si un cambio nuestro choca con 26.x, manda upstream y lo nuestro va
   encima; si upstream hace lo mismo que nosotros, se usa lo suyo y se quita lo nuestro.
3. **Lobby:** un ajuste configurable con dos modos. Volver al spawn del mundo donde se salió (lo
   actual) o volver siempre al spawn del mundo principal.
4. **RCON:** encendido por defecto. Escucha solo en `127.0.0.1` por defecto, configurable con `ip`
   en `[network.rcon]`. La clave se genera en el primer arranque (32 caracteres, `SecureRandom`) en
   `config/rcon.secret`; si se borra se regenera; si el usuario escribe una propia se respeta; nunca
   se escribe en el log, que solo dice dónde está.
5. **Mensajes al jugador:** todos en MiniMessage. Las claves de traducción se escriben con el
   prefijo `translate:` (p. ej. `"translate:multiplayer.disconnect.flying"`).
6. **Modo `server.properties`:**
   - ENCENDIDO por defecto. Se cambia con `use-server-properties` en `[server]` de `server.toml`, o
     con el comando de consola `/legacyfolia properties on|off|status` (consola y operadores de
     nivel 4), que aplica el cambio al instante y guarda el interruptor.
   - Encendido: `server.properties` contiene SOLO los ajustes que difieren del default, y manda. Los
     TOML se mantienen completos con los valores por defecto, como referencia. Si se borra una línea
     de `server.properties`, ese ajuste vuelve al default; si se añade una, se aplica. Si alguien
     edita un TOML, el cambio se mueve a `server.properties` y el TOML vuelve al default. Se detecta
     al arrancar y al recargar, no en caliente. Un servidor nuevo crea `server.properties` solo con
     una cabecera explicativa.
   - Apagado: los TOML mandan y no hay `server.properties`. Al apagar el modo, los cambios de
     `server.properties` se pasan a los TOML y después se borra el archivo.
   - Nombres de clave: los ajustes vanilla conservan su nombre de `server.properties` (`motd`,
     `max-players`, `server-port`...), para que sigan funcionando paneles como Pterodactyl. El resto
     usa su ruta completa (`gameplay.hunger.jump-walk-exhaustion`). Lo que es por mundo usa
     `worlds.<mundo>.<ruta>`.
   - Los comandos que cambian ajustes (`/whitelist on`, `/difficulty`...) funcionan siempre; solo
     cambia dónde escriben: en `server.properties` con el modo encendido, en el TOML con el modo
     apagado.
   - Los secretos (`rcon.secret`, `forwarding.secret`, `github.token`) nunca van a
     `server.properties`.
7. **Migración:** si el jar arranca donde antes había vanilla, Spigot, Paper o Folia, lee todos los
   archivos antiguos: `server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`,
   `config/paper-global.yml`, `paper-world-defaults.yml`, el `paper-world.yml` de cada mundo,
   `eula.txt`, whitelist, ops y bans, estén en la raíz o en `config/`. Lo importa a los TOML, y lo
   que es por mundo a `worlds.toml`. Las colisiones se resuelven igual que el servidor original
   (p. ej. `-1` en Paper significa que manda bukkit). Los originales se mueven a
   `config/legacy-backup/<fecha>/`. Con el modo encendido, lo que no es default acaba en
   `server.properties`.
8. **EULA:** pasa a ser una clave de configuración y deja de existir `eula.txt`.
9. Los `bukkit.yml`, `spigot.yml` y `paper-*.yml` **desaparecen del disco**, pero los plugins siguen
   viendo los valores a través de objetos en memoria.

Consecuencias ya aplicadas en la etapa 1 de la fase de enganche (las claves existen en los TOML y
en el catálogo `config/catalog/`; desde la etapa 5b el servidor las lee todas): `server.eula`,
`server.use-server-properties`, `world.lobby-spawn-world`, `[network.rcon]` con `enabled = true`,
`ip = "127.0.0.1"` y `password-file = "rcon.secret"` en lugar de `password`, y los mensajes de
`[server.messages]` en MiniMessage con `translate:`.

**Verificaciones manuales pendientes:** están en §5, punto 1.

## 7. Upstream

**Hecho (`v0.14.0`): LegacyFolia está en Minecraft 26.3.** Dos merges en `main`: primero
`ver/26.2.x` (`acf6733`: dos actualizaciones de Paper y leafpile 1.2.0), después `ver/26.3.x`
(`597e413`: `2a74dac` "Update to 26.3", `10488af`, `cd92bab`, otra actualización de Paper y CI).
Paper pasa de `c9e894d3` a `c927bd34`; Gradle a 9.8.1 y paperweight a 2.0.0-beta.24. Java sigue
siendo 25.

Lo que se encontró:
- **Numeración:** 26.3 no añade parches de upstream (siguen 0001–0011 en minecraft y 0001–0006 en
  paper), así que los nuestros no se renumeraron. Se añade uno nuevo, el 0031.
- **Conflictos de merge:** `README.md` (se queda el del fork), `gradle.properties` (nuestro `group`
  con `mcVersion`, `apiVersion`, `channel` y `paperRef` de upstream), `build.gradle.kts.patch`
  (las dependencias de upstream, leafpile como BOM, más nuestra línea de night-config) y
  `.github/workflows/build.yml` (se toma el de upstream, que vuelve a los runners de GitHub). En
  el merge de 26.2 se había dejado fuera el paso de CI a Namespace, porque ese runner no existe
  para un fork; en 26.3 upstream lo deshizo, así que el CI vuelve a ser idéntico al suyo.
- **Parches:** solo uno no aplicó, el 0017 (combate 1.8), porque 26.3 renombra `invulnerableTime`
  a `damageCooldownTime` e inicializa `invulnerableDuration` con `DAMAGE_COOLDOWN_DURATION`. El
  gancho parte ahora de esa constante (`Combat.invulnerableDuration(int)` la devuelve tal cual con
  el combate desactivado). En `build.gradle.kts.patch` hubo que cambiar el contexto de mache a
  `26.3+build.1`. El resto solo movió líneas; `ServerboundSwingPacket` pasó a llamarse
  `ServerboundPunchPacket` (`handlePunch`), y el gancho de distancia adaptativa lo sigue sin cambios.
- **`white-list`:** 26.3 cambia su default en `server.properties` de `false` a `true`. Por decisión
  del fork sigue en `false`: el parche 0031 cambia solo ese default. Comprobado con un
  `server.properties` generado desde cero.
- **`10488af`** (portales y `allow_entering_nether_using_portals`): es una regla de juego que la
  ruta asíncrona de portales de Folia no consultaba. No se solapa con `[dimensions]`: con una
  dimensión desactivada el mundo no existe, `getLevel` devuelve `null` y
  `netherPortalLogicAsync`/`endPortalLogicAsync` salen sin teletransportar antes de llegar a la
  comprobación nueva. Los dos mecanismos se suman; no hubo que tocar nada.
- **Arranque:** aparece un error nuevo de ViaVersion 5.11.0 ("Server version has not been loaded
  yet"), que no soporta 26.3; hay 5.12.1. No es del servidor. Los errores de oshi (contadores de
  Windows) ya salían en 26.2.

La comparación de defaults con las clases de configuración de 26.3 se hizo en `bfe9616` ("Apply the
26.3 configuration defaults"): `chunks.min-chunk-unload-count` y `min-chunk-unload-fraction` nuevos
en `[chunks]`, recuentos de `SETTINGS-MAP.md` al día, y en `DedicatedServerProperties` la única
diferencia de default es `white-list`. Además, los tests de `config/bridge/` comparan cada objeto
en memoria con lo que el servidor original escribe en una instalación nueva, así que un default que
cambie en upstream rompe un test. Para los números de línea que cita `SETTINGS-MAP.md`, ver el aviso
al principio de ese documento.

**Orden decidido:** primero la actualización (hecha) y solo entonces el enganche, que crea parches
justo sobre `Main`, `DedicatedServer`, `CraftServer`, `ServerLevel` y `SpigotConfig`.

**Actualización de 2026-10-31 (Paper `804dd0c3`).** Merge `e5e16b3` de `ver/26.3.x`: tres
actualizaciones de Paper (`05dca87`, `8cffa90`, `8664923`; Paper pasa de `c927bd34` a `804dd0c3`) y
el cambio de insignia del README de upstream (`73865e0`), que se deja fuera porque el README es el
del fork. Lo que nos tocó:
- **Conflictos de merge:** `README.md` (se queda el nuestro) y `build.gradle.kts.patch` (leafpile
  1.3.2 e `ioutil` de upstream, más nuestra línea de night-config; los hunks se movieron una línea).
- **Parches:** solo el 0017 no aplicó: Paper hizo público `Player.canCriticalAttack` para su nueva
  API (`bc68acd74`). Se queda público con nuestro gancho de críticos 1.8 dentro, así que con el
  combate 1.8 encendido la API responde con la regla de 1.8. El resto solo movió líneas.
- **Código:** Paper movió `Duration` y `DurationOrDisabled` a `configuration.type.time`; el puente y
  `SettingReaders` importan de ahí.
- **Defaults:** `GlobalConfiguration`, `SpigotConfig`, `SpigotWorldConfig` y
  `DedicatedServerProperties` no cambian. `WorldConfiguration` añade dos claves, catalogadas en
  `8a8eef0`: `performance.entities.inactive-mob-goal-selector-throttle-rate` (`"disabled"`) y
  `gameplay.commands.command-block-time-format` (`"default"`), las dos por mundo. Un servidor ya
  existente las recibe en sus TOML al arrancar.
- Otros cambios de Paper que rozan lo nuestro sin conflicto: `EntityDamageByBlockEvent` para el daño
  por fuego (`2e549d651`), `PotionSplashEvent` cancelado ya no apaga fuegos (`ff31a2c99`), límite de
  canales de plugin configurable con `-Dpaper.pluginMessageChannelLimit` (`e8a1f8fd6`, propiedad del
  sistema, no ajuste) y regeneración de los ajustes de generación del mundo desde
  `server.properties` (`e3ba7b1dc`), que con nuestro parche 0032 los toma de los TOML.
- Resolución del 0017: el primer intento dejó marcas de conflicto por los finales de línea CRLF; se
  arregló con un commit `fixup` dentro del árbol generado, `rebuildMinecraftFeaturePatches` y un
  `applyAllPatches` completo limpio. Los parches de upstream quedan idénticos a los suyos.
- 325 tests propios en verde (10461 en total, 86 omitidos de upstream); `runDevServer` llega a
  `Done (` con solo los errores ya conocidos (oshi en Windows y ViaVersion 5.11.0).

## 8. Diferencias con Folia y Paper

LegacyFolia mantiene el modelo de hilos por regiones de Folia sin tocarlo; los parches de Folia
(minecraft 0001–0011, paper 0001–0006) son los de upstream. Lo que cambia para quien lo usa:
- **Archivos:** no hay `bukkit.yml`, `spigot.yml`, `commands.yml`, `paper-global.yml`,
  `paper-world-defaults.yml`, `paper-world.yml` ni `eula.txt`. Los ocho TOML de `config/` son la
  fuente, y los objetos que leen los plugins (`YamlConfiguration` de Bukkit y Spigot, nodos de
  Configurate de Paper, `DedicatedServerProperties`) se construyen en memoria (paper 0010–0012,
  minecraft 0032). `/paper reload`, `/spigot reload` y el reload de Bukkit releen los TOML.
- **`server.properties`** es opcional y vive en `config/`: solo lo que difiere del default, y manda
  sobre los TOML (§6, decisión 6). Whitelist, bans, ops y secretos en `config/`, la caché de usuarios
  en `cache/` (minecraft 0018–0019).
- **Migración** automática desde vanilla, Spigot, Paper o Folia en el primer arranque (§6,
  decisión 7).
- **Defaults distintos:** whitelist apagada (26.3 la enciende), spawn protection 0 (vanilla 16),
  RCON encendido solo en `127.0.0.1` con clave generada (vanilla lo trae apagado), combate 1.8
  encendido.
- **Jugabilidad:** PvP 1.8 (`PVP-1.8.md`), locator bar que funciona con regiones, interruptores de
  dimensiones, lobby, techo del Nether, distancia de visión adaptativa.
- **API:** `getAllowNether/End` responden desde `[dimensions]` (paper 0007); el secreto de Velocity
  sale de `config/forwarding.secret` (paper 0008); `canSee` por id de entidad (paper 0009); con el
  combate 1.8 encendido, `Player.canCriticalAttack` de NMS, que Paper hizo público para su API,
  sigue la regla de 1.8; el tooltip de las armas solo cambia en lo que se envía al cliente. Comando
  propio `/legacyfolia` (paper 0013).

