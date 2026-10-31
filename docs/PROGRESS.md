# Estado del proyecto

Este documento existe para que el trabajo pueda retomarse exactamente donde quedó, por una persona
o por otra sesión, sin depender de la memoria de nadie. Se actualiza al cerrar cada fase.

**Última actualización:** 2026-10-31 · **Último tag publicado:** `v0.15.0` · **Base:** Folia
`ver/26.3.x` en `597e413` (2026-10-14), Minecraft 26.3, Paper `c927bd34`.

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

**Tests:** 271 en `LegacyFoliaTestSuite`, en 36 archivos de resultados, sin fallos ni omitidos
(verificado el 2026-10-31 al cerrar la etapa 5b, contando los XML de
`folia-server/build/test-results/test` tras `./gradlew :folia-server:test --tests
'io.github.adrielorihuela.legacyfolia.LegacyFoliaTestSuite'`). Con ese mismo comando, antes de la
etapa 5b eran 264: la cifra de 269 que figuraba aquí sumaba las 5 suites vacías de upstream, que esa
ejecución ya no produce. La etapa 5b aporta 7: `ServerConfigSettingsTest` (1), el EULA fuera de
`server.properties` en `ServerPropertiesTest` (1), `LegacyFoliaCommandTest` (2) y tres casos nuevos
en `LobbyTest`. Antes de la etapa 1 de la fase de enganche eran 129;
tras ella, 150; tras la etapa 2, 189. La etapa 3 aporta 35 (224 al fusionarla), la etapa 4 otros 38
(262) y la etapa 5a otros 7: `LegacyCoverageTest` (2), cuatro casos nuevos en `ConfigFileMergeTest`
y uno en `SettingReadersTest`. La etapa 2 había hecho que corriera `BundledTomlParseTest`, que con su
nombre anterior (`...TestSuite`) el filtro de JUnit dejaba fuera.

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
- **94 claves heredadas sin sitio, catalogadas** (`SETTINGS-MAP.md` §8.1): el catálogo pasa de 482 a
  576 claves. Lo que queda fuera a propósito está en `config/catalog/LegacyExclusions` y en §8, y
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

Al escribir esto (2026-10-31), `main` y el tag `v0.15.0` están publicados y `main` es la única rama.
**La fase de enganche (§5, punto 1) está terminada**: las etapas 1 a 5b están en `main`. Lo siguiente
es el punto 2 de §5 (opcional) o directamente el 3. Lo que sigue en este apartado es el historial de
la fase de enganche. **La etapa 1 está hecha:** las decisiones del usuario
(§6) escritas y con sus claves en los TOML, los defaults de 26.3 aplicados, y el catálogo maestro de
claves en `config/catalog/` (`SettingsCatalog`, cargado de `legacyfolia/catalog.tsv`: 482 claves con
su tipo, su default, su origen heredado y su nombre en `server.properties`) con los lectores tipados
de `config/types/SettingReaders`.

**La etapa 2 está hecha** (2026-10-30, §2): el modo `server.properties` en
`config/ServerProperties.java`, con sus tests, todavía **sin enganchar** al arranque. **Las etapas 3
(importador), 4 (objetos en memoria) y 5a (preparación) también están hechas** (2026-10-31, §2) y
en `main`. **La etapa 5b también está hecha** (2026-10-31, §2, tag `v0.15.0`): todo conectado al
servidor.

La comprobación de arranque que quedó pendiente en `v0.13.0` está hecha, ya sobre 26.3: con
`run/config/*.toml` borrados, `runDevServer` llega a `Done (` y genera los ocho TOML; un segundo
arranque con los archivos presentes no duplica ninguna clave, no cambia ningún archivo y conserva
dos valores editados a mano (`motd` en `server.toml` y `jump-walk-exhaustion` en `gameplay.toml`).

## 5. Qué falta, en orden

Las etapas van **en fila**: cada una empieza cuando la anterior termina y está subida.

1. **Fase de enganche. TERMINADA (2026-10-31, `v0.15.0`).** Lo que queda abajo es el plan con el que
   se hizo, conservado como referencia; la etapa 5b lo cumplió entero, con dos matices que el usuario
   conoce: `/difficulty` no escribe `server.properties`, porque en vanilla y en Paper cambia la
   dificultad del mundo y no el ajuste (regla de upstream; el ejemplo correcto es `/whitelist on`), y
   `help.yml` y `permissions.yml` siguen leyéndose del directorio de trabajo, como antes.
   Plan detallado en `SETTINGS-MAP.md` §4.1 y §6, y decisiones en §6 de este
   documento. Hechas: la etapa 1 (catálogo de claves y lectores de tipos), la 2 (modo
   `server.properties`, `config/ServerProperties`), la 3 (importador, `config/migration/`), la 4
   (objetos en memoria, `config/bridge/`, y `config/RconSecret`) y la 5a (fusión, las 94 claves que
   faltaban, la escritura de los TOML sin night-config y `legacyMessage`). Queda:
   - **Etapa 5b: conexión al servidor**, en serie porque toca los árboles generados. Los cinco puntos
     de inyección, el comando `/legacyfolia properties on|off|status`, las escrituras en caliente
     (`/whitelist on`, `/difficulty`...), el lobby configurable (`world.lobby-spawn-world`), el EULA
     como clave y el borrado de los yml.

     Lo que la etapa 5b tiene que enganchar de las etapas 3, 4 y 5a:
     - **Importador**, en `Main`, después de leer la línea de comandos y antes de `ServerConfig.load()`:
       `LegacyImport.runIfNeeded(raíz, contenedor)`, con `--universe` como contenedor si se dio. Su
       javadoc explica por qué ese orden.
     - **`DedicatedServerProperties`** se construye con `new DedicatedServerProperties(
       ServerPropertiesBridge.build(ajustes, RconSecret.resolve(ServerPropertiesBridge.rconPasswordFile(ajustes))), null)`
       en lugar de leer `server.properties`, y no vuelve a guardar el archivo (`forceSave()`).
     - **`SpigotConfig.config`** = `SpigotBridge.build(ajustes, SpigotBridge.vanillaWorldKeys(levelName))`
       antes de `readConfig`, sin cargar ni guardar `spigot.yml`; cada mundo que no sea uno de los
       tres de vanilla se añade al cargarse con `SpigotBridge.addWorld`.
     - **`CraftServer`**: `configuration` = `BukkitBridge.bukkit(ajustes)` y `commandsConfiguration`
       = `BukkitBridge.commands(ajustes)`, en el constructor y en `reload()`; las plantillas siguen
       debajo como defaults. `Main` lee `settings.world-container` de `bukkit.yml` antes de que
       exista `CraftServer`: tiene que pasar a leer `world.storage.world-container` del TOML.
     - **Paper**: en `PaperConfigurations`, el nodo global, el de defaults de mundo y el de cada mundo
       se copian de `PaperBridge.global`, `worldDefaults` y `world` a un nodo del loader de Paper
       (`loader.createNode().from(nodo)`) y no se guardan.
     - **De dónde salen los ajustes**: `ServerConfigSettings` lee `ServerConfig`, pero devuelve las
       tablas como ausentes (`world.generators`, `world.biome-providers`, los alias de comandos,
       `forced-stats`, `alt-item-despawn-rate-items`) y no sabe listar los mundos. Antes de usarlo en
       el servidor hay que darle acceso a las tablas (`ConfigFile.raw()` las tiene) y a la lista de
       `[worlds.*]`, o construir un `TomlSettings` de los archivos cargados y los overrides del modo
       `server.properties`. Sin eso, un servidor con generadores o alias propios los pierde.
     - **Recarga**: `ServerConfig.load()`, `ServerProperties.reconcile()` y volver a construir los
       objetos de la etapa 4, en ese orden.
     - **Escrituras**: `ServerConfig.set` y `ServerProperties.set` ya editan el texto del TOML sin
       reescribirlo; no hace falta nada más para conservar los comentarios.

     Lo que la etapa 5 tiene que enganchar del modo `server.properties` (etapa 2):
     - **Arranque**, en `Main` (parche que hoy llama a `ServerConfig.load()`, línea ~127 de
       `net/minecraft/server/Main.java`): `ServerConfig.load()`, después el importador de la etapa 3, y
       después `ServerProperties.reconcile()`. Ese orden importa: hoy `config/server.properties` es el
       archivo vanilla completo, y si `reconcile()` lo viera tal cual borraría las líneas que valen el
       default y avisaría de las claves vanilla que no están en el catálogo. Además,
       `DedicatedServerProperties` no debe volver a leer ni guardar ese archivo (`forceSave()` lo
       reescribe entero): tiene que alimentarse del `Properties` en memoria de la etapa 4.
     - **Recarga**: `ServerConfig.load()` y luego `ServerProperties.reconcile()`, siempre juntos
       (`load()` descarta los overrides).
     - **Comando** `/legacyfolia properties on|off|status`: `ServerProperties.enable()`,
       `disable()` y `status()`; los dos primeros guardan el interruptor y devuelven un `Report`.
     - **Escrituras en caliente**: cada `MutableValue` de `DedicatedServerProperties` (`/difficulty`,
       `/whitelist on`, `setMaxPlayers`, `/defaultgamemode`...) llama a
       `ServerProperties.set("server.difficulty", "hard")` con la ruta TOML y el valor como lo tendría
       el TOML (`String` para los enum, `Integer`, `Boolean`). Los 19 ajustes `mutable` del catálogo
       son los candidatos. Vale desde cualquier hilo de región: el valor se ve al volver y el archivo
       se escribe en un hilo propio.
     - **Parada**: `ServerProperties.flush()` antes de salir, para que la última escritura llegue al
       disco.
2. **Daño base de espadas 1.8** (opcional). Es el único de los nueve enganches del PvP que falta.
   Obliga a cargar la configuración antes de `Bootstrap.bootStrap()`, porque `Items` es un registro
   estático.
3. **Locator bar idéntica a vanilla y Paper.** Comparar línea a línea nuestra implementación
   (`waypoint/RegionisedWaypoints`, parche 0014) con el código de vanilla
   (`ServerWaypointManager`, `WaypointTransmitter` y el atributo `WAYPOINT_TRANSMIT_RANGE`) y con el de
   Paper, y después corregirla hasta que se comporte **exactamente** igual. Error conocido: se ven
   jugadores de otras dimensiones (overworld, Nether y End). Solo deben verse los de la dimensión
   propia. Revisar también los cambios de dimensión, el agacharse, las cabezas, la invisibilidad, las
   desconexiones y el rango.
4. **PvP 1.8 idéntico al original.** Hacer la misma comparación entre nuestro combate (`combat/` y los
   parches 0016, 0017 y 0020–0027) y el código fuente de 1.8.8 (`reference/fetch-1.8.8-source.sh`),
   cubriendo todas las mecánicas y no solo las fórmulas: orden de los modificadores, knockback, bloqueo
   con espada, críticos, invulnerabilidad, hambre, pociones y encantamientos. Después corregir hasta
   que sea **exactamente** igual.
5. **Release `v1.0.0`** con el jar `legacyfolia-paperclip-<versión>.jar` adjunto. Los puntos 3 y 4 son
   lo último que se hace antes.
6. **`/init`** para generar `CLAUDE.md`, y un último commit.

## 6. Decisiones del usuario (decididas)

Son del usuario y son definitivas (2026-10-18). Sustituyen a las tres preguntas abiertas que había
aquí (mensajes traducibles, `rcon.password` y lobby en multimundo) y a las recomendaciones de
`SETTINGS-MAP.md` §9, que quedan resueltas así:

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

**Verificaciones manuales pendientes:** el prompt del EULA en Linux con una TTY real; la locator bar
con dos clientes a más de 332 bloques, en dimensiones distintas, agachados y con cabeza puesta; y
cómo se siente el PvP 1.8 comparado con un servidor 1.8 real.

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

Pendiente de esta actualización, para la fase siguiente: comparar los defaults de los 430 ajustes
con las clases de configuración de `c927bd34` y refrescar los números de línea de `SETTINGS-MAP.md`.
Las versiones de 26.2 de `DedicatedServerProperties`, `Main`, `DedicatedServer`, `ServerLevel`,
`GlobalConfiguration`, `WorldConfiguration`, `SpigotConfig` y `SpigotWorldConfig` se guardaron
fuera del repo para esa comparación; en `DedicatedServerProperties` la única diferencia de default
es `white-list`.

**Orden decidido:** primero la actualización (hecha) y solo entonces el enganche, que crea parches
justo sobre `Main`, `DedicatedServer`, `CraftServer`, `ServerLevel` y `SpigotConfig`.
