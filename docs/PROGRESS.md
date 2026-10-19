# Estado del proyecto

Este documento existe para que el trabajo pueda retomarse exactamente donde quedó, por una persona
o por otra sesión, sin depender de la memoria de nadie. Se actualiza al cerrar cada fase.

**Última actualización:** 2026-10-18 · **Último tag publicado:** `v0.14.0` · **Base:** Folia
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

**Tests:** 150 en `LegacyFoliaTestSuite` (20 clases; 145 propios más las 5 suites vacías de upstream
que Gradle también cuenta), sin fallos (verificado el 2026-10-18 sobre 26.3, contando los XML de
`build/test-results`). Antes de la etapa 1 de la fase de enganche eran 129.

**Importante:** los 430 ajustes **todavía no los lee el servidor**. Hoy los TOML se generan y se
documentan, pero los valores efectivos siguen saliendo de `server.properties`, `bukkit.yml`,
`spigot.yml` y `paper-*.yml`, que viven en `config/`. Conectarlos es la fase siguiente (§5).

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

Al escribir esto (2026-10-18), `main` y el tag `v0.14.0` están publicados y `main` es la única rama.
Estamos en la fase de enganche (§5, punto 1). **La etapa 1 está hecha:** las decisiones del usuario
(§6) escritas y con sus claves en los TOML, los defaults de 26.3 aplicados, y el catálogo maestro de
claves en `config/catalog/` (`SettingsCatalog`, cargado de `legacyfolia/catalog.tsv`: 482 claves con
su tipo, su default, su origen heredado y su nombre en `server.properties`) con los lectores tipados
de `config/types/SettingReaders`. Tres agentes pueden trabajar ahora en paralelo contra esa API.

La comprobación de arranque que quedó pendiente en `v0.13.0` está hecha, ya sobre 26.3: con
`run/config/*.toml` borrados, `runDevServer` llega a `Done (` y genera los ocho TOML; un segundo
arranque con los archivos presentes no duplica ninguna clave, no cambia ningún archivo y conserva
dos valores editados a mano (`motd` en `server.toml` y `jump-walk-exhaustion` en `gameplay.toml`).

## 5. Qué falta, en orden

Las etapas van **en fila**: cada una empieza cuando la anterior termina y está subida.

1. **Fase de enganche.** Plan detallado en `SETTINGS-MAP.md` §4.1 y §6, y decisiones en §6 de este
   documento. La etapa 1 (catálogo de claves y lectores de tipos) está hecha. Quedan:
   - **Etapa 2: modo `server.properties`.** Calcular las diferencias respecto al default, conservar
     los nombres vanilla, escribir y leer el archivo con su cabecera, y devolver los cambios a los TOML
     al apagarlo. Antes, arreglar que `BundledTomlParseTestSuite` no se ejecute con
     `LegacyFoliaTestSuite`.
   - **Etapa 3: importador.** Leer vanilla, Spigot, Paper o Folia, incluido el `paper-world.yml` de
     cada mundo, resolver las colisiones como el servidor original y hacer copia de los originales en
     `config/legacy-backup/<fecha>/`.
   - **Etapa 4: objetos en memoria.** `Properties` para `DedicatedServerProperties`, `YamlConfiguration`
     para `SpigotConfig`, `bukkit.yml` y `commands.yml`, y `ConfigurationNode` para Paper global y
     por mundo. Además la clave de RCON en `config/rcon.secret` y los mensajes MiniMessage.
   - **Etapa 5: conexión al servidor**, en serie porque toca los árboles generados. Los cinco puntos de
     inyección, el comando `/legacyfolia properties on|off|status`, las escrituras en caliente
     (`/whitelist on`, `/difficulty`...), el lobby configurable (`world.lobby-spawn-world`), el EULA
     como clave y el borrado de los yml.
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
en el catálogo `config/catalog/`, pero ningún código las lee todavía): `server.eula`,
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
