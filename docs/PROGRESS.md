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

**Tests:** 129 en `LegacyFoliaTestSuite` (18 clases), sin fallos (verificado el 2026-10-18 sobre
26.3, contando los XML de `build/test-results`).

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
Lo siguiente es la fase de enganche (§5, punto 1).

La comprobación de arranque que quedó pendiente en `v0.13.0` está hecha, ya sobre 26.3: con
`run/config/*.toml` borrados, `runDevServer` llega a `Done (` y genera los ocho TOML; un segundo
arranque con los archivos presentes no duplica ninguna clave, no cambia ningún archivo y conserva
dos valores editados a mano (`motd` en `server.toml` y `jump-walk-exhaustion` en `gameplay.toml`).

## 5. Qué falta, en orden

1. **Fase de enganche** (la que cumple el requisito 1 de verdad). Plan detallado en
   `SETTINGS-MAP.md` §4.1 y §6. Son cinco puntos de inyección; ningún objeto desaparece, solo cambia
   de dónde lee:
   - `DedicatedServerProperties`, construido desde los TOML antes de `new DedicatedServerSettings`.
   - `SpigotConfig.config`, una `YamlConfiguration` en memoria, rellenada antes de `readConfig`
     **sin escribir a disco**. `Bukkit.spigot().getConfig()` sigue funcionando.
   - `GlobalConfiguration` de Paper, alimentando el `ConfigurationNode` en `PaperConfigurations`.
   - Las dos `YamlConfiguration` de `CraftServer` (`bukkit.yml` y `commands.yml`), en memoria.
   - `createWorldConfig` por mundo, con los valores de `forWorld()`.

   Después: dejar de escribir los yml y borrarlos; el EULA como clave de configuración (hoy sigue
   siendo `config/eula.txt`); las escrituras en caliente (`MutableValue`, `/whitelist on`). Esto va
   en serie, con un solo agente.
2. **`server.properties` opcional y sincronizado** en las dos direcciones. Necesita un escritor que
   conserve los comentarios, porque `Properties.store()` los borra. Depende del punto 1.
3. **Daño base de espadas 1.8** (opcional). Es el único de los nueve enganches del PvP que falta.
   Obliga a cargar la configuración antes de `Bootstrap.bootStrap()`, porque `Items` es un registro
   estático.
4. **Release `v1.0.0`** con el jar `legacyfolia-paperclip-<versión>.jar` adjunto.
5. **`/init`** para generar `CLAUDE.md`, y un último commit.

## 6. Decisiones abiertas (del usuario)

- **Cómo se escriben los mensajes traducibles** (`SETTINGS-MAP.md` §9.1). Recomendación: MiniMessage,
  con el prefijo `translate:` para las claves de traducción.
- **`rcon.password`**: inline o en `config/rcon.secret` (§9.2). Recomendación: archivo aparte.
- **Lobby en multimundo:** hoy se reaparece en el spawn del mundo donde se salió. ¿Debería ser
  siempre el del mundo principal?

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
