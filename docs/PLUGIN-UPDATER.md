# Plugin auto-updates

Off by default. Turn it on under `[plugins.auto-update]` in `config/server.config` and list what you
want kept current.

```toml
[plugins.auto-update]
enabled = true
hangar = ["ViaVersion", "SimpleVoiceChat"]
modrinth = ["viabackwards"]
github = ["ViaVersion/ViaRewind", "adrielorihuela/ChunkScope"]
luckperms = true
github-token-file = "github.token"
```

Everything listed is checked once at every start, **before the plugin loader reads the directory**,
so by the time it looks every jar it should find is there.

## How "up to date" is decided

By the **checksum the source publishes**, and by nothing else wherever there is one. A matching file
name proves only that something of that name is there — not that it is this build, not that it is
intact, and not that it is what the source is serving today.

| Source | Checksum |
|---|---|
| Hangar | SHA-256 |
| Modrinth | SHA-512, or SHA-1 on older files |
| GitHub | SHA-256, when the release publishes a digest |
| LuckPerms | **none** |

The same checksum verifies the download afterwards, so a truncated or corrupted file is never put in
place.

LuckPerms publishes no checksum anywhere, in any format, for any platform. There the file name is
all there is, and it carries the build number, so it is a weaker answer rather than a meaningless
one — and the log says so out loud rather than letting it look like a verification that happened:

```
[LegacyFolia] luckperms publishes no checksum, so LuckPerms-Bukkit-5.5.77.jar was installed unverified.
```

## What each source does

### Hangar

By project slug. Asks for a Paper build of the running Minecraft version, falling back to the
plugin's newest build with a warning rather than refusing — an operator who listed a plugin wants it
installed, and point releases rarely break one.

Channels are tried `Release`, then `Beta`, `Alpha`, `Snapshot`. Both directions matter: unfiltered,
Hangar answers with whatever is newest, which for an actively developed plugin is a snapshot; but
stopping at Release is just as wrong, because plugins have gone months publishing only betas. A
build off a lower channel is labelled in the log so a beta never reads as stable.

### Modrinth

By project slug — the name from its page's address, not its display name. Asks for the `paper` and
`folia` loaders together, since nearly everything that runs on Folia is published as a Paper plugin
and asking for one would miss half of them. Stability beats recency: a release is preferred over a
newer beta.

### GitHub

`owner/repo`, or `owner/repo:regex` when a release attaches several jars and you need one of them.
The owner cannot be left out — nothing in the API can guess it. Sources and javadoc jars are always
ignored.

The whole release list is walked newest first rather than asking for `/releases/latest`, which never
returns a pre-release. Plenty of projects ship betas for months between stable tags, and one whose
newest tag happens to be a pre-release would otherwise look as though it had published nothing at
all. Drafts are skipped; a pre-release is labelled.

**The token is optional.** Without one GitHub allows sixty requests an hour per address, which is
plenty unless the server restarts constantly; with one it is five thousand. It lives in
`config/github.token`, created empty for you, one line, nothing else. `GITHUB_TOKEN` in the
environment overrides it.

A token is also what lets this reach a private repository — and a public one belonging to an account
GitHub has flagged, where `github.com` paths answer 404 to everybody while the API keeps serving the
file.

### LuckPerms

A switch rather than a list: there is one of it, from its own metadata service.

## When something goes wrong

The rule is that a failure is only fatal when it would leave the server without a plugin.

- **Source unreachable, jar already there** — a warning, and the installed version is used. An
  outage upstream should not stop your server.
- **Source unreachable, no jar** — start-up stops. Carrying on means running without a plugin
  somebody listed on purpose, and on a server that plugin may be the one granting permissions.
- **Checksum mismatch** — the download is discarded and treated as a failure. A file that does not
  match what the source said it would be is never installed.
- **Old jar cannot be removed** — the update is refused with an explanation. Installing the new jar
  alongside one that will not go leaves two jars claiming the same plugin, and the loader refuses
  whichever it reads second. This happens on Windows, where a loaded jar is held open; stop the
  server and delete it by hand.

Old jars of a plugin are always removed, even when nothing needed updating, because a stale jar
dropped in by hand is not made harmless by the current one being correct.

## What it deliberately does not do

- **It does not run while the server is up.** Only at start, before plugins load. Swapping a jar
  under a running plugin is not something to do quietly.
- **It does not install dependencies.** What you list is what you get.
- **It does not downgrade.** If the source is serving something different from what you have, that
  is what gets installed.
