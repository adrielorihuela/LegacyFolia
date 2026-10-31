# Plugin auto-updates

Paper and Folia have no plugin updater: a jar is replaced by hand, or a plugin updates itself through
the `update` folder. LegacyFolia can keep a list of plugins current from Hangar, Modrinth, GitHub
releases and LuckPerms' own download service.

It is off by default. Turn it on under `[plugins.auto-update]` in `config/plugins.toml` and list what
you want kept current:

```toml
[plugins.auto-update]
enabled = true
hangar = ["ViaVersion", "ViaBackwards"]
modrinth = ["chunky"]
github = ["ViaVersion/ViaRewind"]
luckperms = true
github-token-file = "github.token"
```

| Key | Default | What it is |
|---|---|---|
| `enabled` | `false` | Whether anything below is checked |
| `hangar` | `[]` | Hangar project slugs |
| `modrinth` | `[]` | Modrinth project slugs: the name in the page's address, not the display name |
| `github` | `[]` | `owner/repo`, or `owner/repo:regex` |
| `luckperms` | `false` | LuckPerms from its own metadata service |
| `github-token-file` | `"github.token"` | File holding an optional GitHub token, relative to `config/` unless absolute |

## When it runs

Once at every start, inside the server's start-up, right before the plugin loader reads the plugin
directory (`plugins/`, or whatever `--plugins` names). By the time the loader looks, every jar it
should find is there. All the listed plugins are checked at the same time and start-up waits for the
last one.

It never runs while the server is up. Changing the list takes effect on the next start.

`config/github.token` is created empty at every start, whether or not the updater is on, so the place
for the token is there before anyone needs it.

### Folia still has to accept the plugin

Folia only loads a plugin whose `plugin.yml` says `folia-supported: true`. The updater asks for Paper
builds, because that is how Folia plugins are published, so it can download a plugin that Folia then
refuses to load. That is a property of the plugin, not of the updater; check the plugin supports
Folia before listing it.

## How "up to date" is decided

By the **checksum the source publishes**, and by nothing else wherever there is one. A matching file
name proves only that something of that name is there: not that it is this build, not that it is
intact, and not that it is what the source is serving today.

| Source | Checksum |
|---|---|
| Hangar | SHA-256, when the build has one |
| Modrinth | SHA-512, or SHA-1 when there is no SHA-512 |
| GitHub | SHA-256, when the release asset carries a `digest` (newer releases do) |
| LuckPerms | **none** |

The same checksum verifies the download, so a truncated or corrupted file is never put in place.

Without a checksum the file name is all there is: a file of the same name already in the plugin
directory is kept, and a new download is installed unverified. That is always the case for
LuckPerms, whose file name carries the build number, and it is the case for any GitHub release
without a digest. The log says so instead of letting it look like a verification that happened:

```
[LegacyFolia] luckperms publishes no checksum, so LuckPerms-Bukkit-5.5.77.jar was installed unverified.
```

The updater **does not compare versions.** It installs whatever the source currently offers. If
that is older than the jar you have, for example a development build you dropped in by hand, yours
is replaced with the source's.

## What each source does

### Hangar

By project slug. It asks for a `PAPER` build for the running Minecraft version, one channel at a
time: `Release`, then `Beta`, `Alpha` and `Snapshot`, taking the newest build of the first channel
that has one. Unfiltered, Hangar would answer with whatever is newest, which for an actively
developed plugin is a snapshot; stopping at Release would be just as wrong, because some plugins go
months publishing only betas. A build from a lower channel is labelled in the log, so a beta never
reads as stable.

If no channel has a build for this Minecraft version, the plugin's newest Paper build for any
version is used, with a warning. An operator who listed a plugin wants it installed, and point
releases rarely break one.

Only channels with those four names are looked at. A project that publishes on a channel it named
differently is reported as having no Paper build. A project that only links to a file hosted
elsewhere cannot be installed: there is no file name or checksum to work from.

### Modrinth

By project slug. It asks for the `paper` and `folia` loaders together, since nearly everything
that runs on Folia is published as a Paper plugin and asking for one would miss part of them. Among
the builds for the running Minecraft version, the newest release wins; failing that the newest
beta, then the newest alpha. Stability beats recency: a release is taken over a newer beta. If
nothing is built for this version, the newest build for any version is used, with a warning. The
file marked primary is downloaded, or the first file when none is.

### GitHub

`owner/repo`, or `owner/repo:regex` when a release attaches several jars and you need one of them.
The owner cannot be left out: nothing in the API can guess it. The regex is matched anywhere in the
file name, case-sensitively, and the first asset of the release that matches is taken.

The 30 most recent releases are walked newest first, rather than asking for `/releases/latest`,
which never returns a pre-release. Many projects ship betas for months between stable tags, and one
whose newest tag happens to be a pre-release would otherwise look as though it had published nothing.
Drafts are skipped; a pre-release is labelled as one in the log. Only `.jar` assets are considered,
and any whose name contains `sources` or `javadoc` is ignored.

**The token is optional.** Without one GitHub allows sixty requests an hour per address, which is
plenty unless the server restarts constantly; with one it is five thousand. Put it in
`config/github.token`, on one line. A `GITHUB_TOKEN` environment variable overrides the file. With
GitHub entries and an empty `github.token`, the log says once that the low limit applies; it says
so even when `GITHUB_TOKEN` is set, which is then the token actually used.

The token is also what lets this reach a private repository, and a public one belonging to an
account GitHub has flagged, where `github.com` paths answer 404 to everybody while the API keeps
serving the file. Downloads go through the API's asset URL with the same token for that reason.

The token lives in a file of its own, like `rcon.secret` and `forwarding.secret`, so the TOML files
can be shared without it. It is never written to `server.properties`.

### LuckPerms

A switch rather than a list: there is one LuckPerms, served by `metadata.luckperms.net`. The Bukkit
build is installed; it is the one LuckPerms publishes for Paper and Folia.

## Which jars belong to a plugin

The updater removes old jars of a plugin so that only one is left. Two jars claiming the same plugin
make the loader refuse whichever it reads second, and which one that is is not something anyone
controls.

A plugin's jars are found by name. The base name is the downloaded file's name up to its first dash
(`ViaVersion-5.12.0.jar` gives `ViaVersion`), and every `<base>.jar` or `<base>-<anything>.jar` in
the plugin directory, ignoring case, is taken to be the same plugin. Old jars are removed even when
nothing needed updating: a stale jar dropped in by hand is not made harmless by the current one being
correct.

Consequences worth knowing:

- Do not list the same plugin under two sources. Each would remove the other's jar.
- A jar of an unrelated plugin whose name starts with the same word and a dash is removed too.
- When the source cannot be reached, the updater looks for an installed jar by the **entry** you
  wrote instead, since it has no file name to go on: the slug, the repository part of a GitHub entry
  (`ViaRewind` for `ViaVersion/ViaRewind:Bukkit`), or `luckperms`. If the jar on disk is named
  differently from the entry, it is not recognised and an outage stops start-up as if the plugin were
  missing.

## When something goes wrong

A failure stops the server only when it would leave the server without a plugin somebody listed.

| What happened | A jar of the plugin is already there | No jar |
|---|---|---|
| Source unreachable, or it answered with an error | Warning; the installed jar is used | Start-up stops |
| No such project, or no build to install | Warning; the installed jar is used | Start-up stops |
| The download does not match the published checksum | It is discarded; warning; the installed jar is used | Start-up stops |
| The old jar cannot be removed | The update is refused; warning; the old jar is used | (not possible) |

Carrying on without a listed plugin is not safe: on a server, that plugin may be the one granting
permissions. When start-up stops, the log names each plugin that could not be installed and why;
fix the entry or set `enabled = false`.

The old jar usually cannot be removed on Windows, where a jar a running process has loaded is held
open. Stop the server and delete it by hand.

Every request carries the user agent `LegacyFolia`. A lookup times out after 30 seconds and a
download after five minutes.

## What it deliberately does not do

- **It does not run while the server is up.** Only at start, before plugins load. Swapping a jar
  under a running plugin is not something to do quietly.
- **It does not install dependencies.** What you list is what you get.
- **It does not remove a plugin you take off the list.** Delete the jar yourself.
- **It does not use the `update` folder** (`plugins.api.update-folder` in `plugins.toml`, Bukkit's
  `settings.update-folder`). That mechanism is still there for plugins that update themselves; this
  writes straight into the plugin directory.

The code is in `folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/update/`, hooked into
start-up by minecraft patch 0013. Manual checks for a release are in [TESTING.md](TESTING.md).
