# Estado del proyecto

Este documento existe para que el trabajo pueda retomarse exactamente donde quedó, por una persona
o por otra sesión, sin depender de la memoria de nadie. Se actualiza al cerrar cada fase.

**Última actualización:** 2026-10-18 · **Último tag publicado:** `v0.12.0` · **Base:** Folia
`ver/26.2.x` en `95c3f27` (2026-08-19), Minecraft 26.2, Paper `c9e894d3`.

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

**Hecho tras `v0.12.0`** (forma `v0.13.0`):
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

**Tests:** 124 en `LegacyFoliaTestSuite`, sin fallos (verificado el 2026-10-18, antes de la
consolidación).

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

Al escribir esto (2026-10-18):

- `main` local va **por delante de `origin/main`** con los 14 commits de `v0.13.0` (de `e44a4d0` a
  `36a197e`, más el que añade este documento). Si aún no se ha hecho push, lo siguiente es:
  ```bash
  git push origin main
  git tag -a v0.13.0 -m "Every inherited setting written into config/, commented, one home each"
  git push origin v0.13.0
  ```
- **No se ha arrancado el servidor** con los TOML nuevos: `run/config/` no tiene todavía
  `worlds.toml` ni `gameplay.toml`. Comprobación pendiente: borrar `run/config/*.toml`, ejecutar
  `runDevServer` hasta `Done (`, confirmar que se generan los ocho y repetirlo con los archivos
  presentes para probar el merge.

## 5. Qué falta, en orden

1. **Cerrar `v0.13.0`:** push, tag y la comprobación de arranque de §4.
2. **Actualizar upstream antes del enganche.** Ver §7.
3. **Fase de enganche** (la que cumple el requisito 1 de verdad). Plan detallado en
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
4. **`server.properties` opcional y sincronizado** en las dos direcciones. Necesita un escritor que
   conserve los comentarios, porque `Properties.store()` los borra. Depende del punto 3.
5. **Daño base de espadas 1.8** (opcional). Es el único de los nueve enganches del PvP que falta.
   Obliga a cargar la configuración antes de `Bootstrap.bootStrap()`, porque `Items` es un registro
   estático.
6. **Release `v1.0.0`** con el jar `legacyfolia-paperclip-<versión>.jar` adjunto.
7. **`/init`** para generar `CLAUDE.md`, y un último commit.

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

Folia avanzó desde nuestra base:
- `ver/26.2.x`: 5 actualizaciones de Paper y cambios de CI y README (punta `acf6733`).
- `ver/26.3.x`, que ahora es la rama por defecto: **Minecraft 26.3** (`2a74dac`, 21 archivos, toca
  `0001-Region-Threading-Base`, que es la base de todos nuestros parches), más `10488af` (portales y
  `allow_entering_nether_using_portals`, que hay que revisar junto con `Dimensions`) y `cd92bab`
  (`/effect clear`). Paper pasa de `c9e894d3` a `c927bd34` (140 commits).

**Orden decidido:** cerrar `v0.13.0`, actualizar y solo entonces empezar el enganche. Todo lo de
`v0.13.0` vive en `src/main` y en recursos, así que no choca con upstream. El enganche, en cambio,
crea parches justo sobre `Main`, `DedicatedServer`, `CraftServer`, `ServerLevel` y `SpigotConfig`.
Hacerlo en 26.2 y luego subir obligaría a resolver los conflictos dos veces.

Pasos de la actualización, en una rama propia hasta que esté verificada:
1. Merge de `upstream/ver/26.2.x`, que es pequeño y sirve para probar el script `update`.
2. Merge de `upstream/ver/26.3.x`. Luego `applyAllPatches`, resolver los conflictos de nuestros
   parches (minecraft 0012–0030, paper 0007–0010) y `rebuildAllServerPatches`.
3. Comparar los defaults de los 430 ajustes con las clases de configuración nuevas
   (`GlobalConfiguration`, `WorldConfiguration`, `SpigotConfig`, `SpigotWorldConfig`,
   `DedicatedServerProperties`) entre los dos `paperRef`, y actualizar los números de línea del mapa.
4. Cambiar `update.sh`, `update.bat` y `UPDATING.md` a `ver/26.3.x`, y poner `mcVersion=26.3`.
5. `compileJava`, la suite de tests, `applyAllPatches` desde cero y `runDevServer`. Tag `v0.14.0`.
