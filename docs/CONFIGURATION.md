# Configuration

Everything this fork adds lives in **`config/server.config`**, in TOML, grouped by what settings do
rather than by which server introduced them. Every key carries a comment explaining what it is for,
so this page is about how the file behaves rather than a list of keys you already have.

## It does not replace anything

`bukkit.yml`, `spigot.yml`, `config/paper-global.yml`, `config/paper-world-defaults.yml`,
`server.properties`, `permissions.yml`, `help.yml` and `commands.yml` are all still generated, still
read, and still what plugins see. Nothing was removed and nothing stopped being loaded.

What `server.config` does is **override them at start-up**. A key set here wins; a key left out
leaves the original file's value exactly as it was.

This matters more than it sounds. `Bukkit.spigot().getConfig()`, `SpigotConfig`,
`GlobalConfiguration.get()`, `Server#getViewDistance`, `Server#getAllowNether` — all of them keep
answering what they always answered. **A plugin cannot tell this fork from Folia by reading
configuration.**

The dimension switches are the clearest example of why the file exists at all. Upstream can already
turn the Nether and the End off, but the Nether switch is `misc.enable-nether` in
`config/paper-global.yml` and the End switch is `settings.allow-end` in `bukkit.yml` — two files,
two formats, two names, for one decision. `[dimensions]` presents both as one section and answers on
their behalf, so `Bukkit.getAllowNether()` still tells plugins the truth about which worlds exist.

## Upgrading

When a release adds settings, they appear in your file with their comments the next time you start.
Your own edits are never touched, and a key the template no longer knows about is never deleted — an
unrecognised key is far more likely to come from a newer build than to be worth throwing away.

Settings that used to live elsewhere migrate on the run that creates the file. If you had turned the
End off in `bukkit.yml` years ago, `server.config` is created with `end = false` rather than the
template's default, and the log says so:

```
[LegacyFolia] The End keeps the setting it already had; config/server.config now says dimensions.end = false.
```

A file that cannot be parsed stops the server rather than being replaced. It is somebody's
configuration, and quietly starting with defaults would apply a setup nobody chose.

## Sections

| Section | What it covers |
|---|---|
| `[dimensions]` | Whether the Nether and the End exist at all |
| `[network.velocity]` | Where the forwarding secret is kept |
| `[server]` | The EULA prompt |
| `[plugins.auto-update]` | Keeping plugins current — see [PLUGIN-UPDATER.md](PLUGIN-UPDATER.md) |
| `[chunks.adaptive-view-distance]` | Giving each player only the chunks they use |
| `[combat]` | 1.8 PvP — see [PVP-1.8.md](PVP-1.8.md) |

## Secrets

Two files under `config/` hold one value each and nothing else — no comments, no syntax, the whole
contents trimmed is the value:

- **`forwarding.secret`** — the Velocity modern-forwarding secret. The same name Velocity uses on
  the proxy side, so its file can be copied straight across. `PAPER_VELOCITY_SECRET` still overrides
  it, for containers that would rather not mount a file.
- **`github.token`** — an optional GitHub token for the plugin updater. `GITHUB_TOKEN` overrides it.

Both are created empty on first start, whether or not anything needs them yet: a file that is there
and blank says what to fill in, and one that is missing has to be guessed at from documentation.

They exist separately because configuration files get pasted into issues, quoted in support channels
and photographed in screenshots, and a secret three lines below whatever was being asked about goes
with them. Keeping each in its own file means the configuration is safe to share and the one file
that is not is obvious from its name.

If you are upgrading and already had a Velocity secret in `paper-global.yml`, it is moved into
`forwarding.secret` and the YAML key blanked, once, with a note in the log.

## Turning dimensions off

```toml
[dimensions]
overworld = true    # cannot be turned off; refused and logged if you try
nether = false
end = false
```

A dimension that is off is never created: no world folder read or written, no chunks in memory,
nothing ticking, no region threads spent on it. On an empty server that took start-up from 23.5s to
10.8s.

Turning one off later does not delete a world folder that already exists; it stops loading it.
Turning it back on picks up where it left off. Portals to a dimension that is not there do nothing,
which is behaviour upstream already had, because `allow-end` has always been able to produce it.

Only the vanilla Nether and End have switches. A dimension a datapack adds is always loaded — it was
added on purpose, and there is no sensible default for something the server knows nothing about.
