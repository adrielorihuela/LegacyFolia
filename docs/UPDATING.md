# Updating LegacyFolia to a new Folia build

LegacyFolia follows Folia's `ver/26.3.x` branch. Paper reaches it only through Folia: Folia's
"Update Paper" commits move `paperRef` in `gradle.properties`, and a merge brings that along with
everything else. Never move `paperRef` by hand. Folia's own patches are written against the Paper
commit Folia chose, and a different one would break them before ours were even reached.

```bash
./update.sh     # update.bat on Windows
./build.sh      # build.bat on Windows
```

When the merge is clean and every patch applies, that is the whole job. The rest of this page
explains what the scripts do, what to check after an update, and how to repair a merge or a patch
that does not go cleanly.

## The scripts

| Script | What it runs | Notes |
|---|---|---|
| `update.sh` / `update.bat` | adds the `upstream` remote if it is missing, `git fetch upstream ver/26.3.x`, `git merge upstream/ver/26.3.x`, `./gradlew applyAllPatches` | Stops at the first failing step. A conflicted merge stops it before the patches are applied. |
| `build.sh` / `build.bat` | `./gradlew applyAllPatches`, then `./gradlew build createPaperclipJar`, then lists `folia-server/build/libs/*.jar` | `build` also runs every test of every subproject, Paper's and Folia's included, which takes a while. |
| `patch.sh` / `patch.bat` | `./gradlew applyAllPatches` | Inherited from Folia. |
| `rb.sh` / `rb.bat` | `rebuildPaperPatches`, `rebuildPaperServerPatches`, `rebuildServerPatches`, `rebuildMinecraftPatches` | Inherited from Folia. `./gradlew rebuildAllServerPatches` does the same for the server patches, which are the only ones this fork adds. |

`update.txt` is Folia's own list of things to recheck on an update, not ours.

**Windows.** `applyAllPatches` writes paths longer than Windows allows by default, and neither
`update.bat` nor `build.bat` turns on git's long-path support. Either set it once for your user
(`git config --global core.longpaths true`) or run the Gradle step with it in the environment:

```bash
GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.longpaths GIT_CONFIG_VALUE_0=true ./gradlew applyAllPatches
```

**Which jar to run.** The build writes three jars to `folia-server/build/libs/`, named after the
project and the version: `legacyfolia-<version>.jar`, `legacyfolia-bundler-<version>.jar` and
`legacyfolia-paperclip-<version>.jar`. The paperclip jar is the server; the others are build
by-products. A local build is versioned `26.3.local-SNAPSHOT`. The manifest records the commit it
was built from (`Implementation-Version` is `26.3-DEV-<short hash>` without a `BUILD_NUMBER`).

## Why merges are usually painless

Almost everything this fork adds is tracked source under
`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/` and its tests under
`folia-server/src/test/java/io/github/adrielorihuela/legacyfolia/`. Upstream never touches either
directory, so they cannot conflict.

What does touch Minecraft's and Paper's code is a set of feature patches, each kept as thin as
possible: a call into the fork's own classes. The dimension switch, for example, is one line in
`CraftServer` (paper patch 0007):

```java
return io.github.adrielorihuela.legacyfolia.world.Dimensions.netherEnabled(GlobalConfiguration.get().misc.enableNether);
```

A patch that small only conflicts if upstream edits the exact lines around it.

### Where the fork sits in the patch sets

| Patch set | Folia's own | LegacyFolia's |
|---|---|---|
| `folia-server/minecraft-patches/features` (Minecraft's code) | 0001–0011 | 0012–0057 |
| `folia-server/paper-patches/features` (Paper's server code) | 0001–0006 | 0007–0013 |
| `folia-api/paper-patches/features` (the API) | 0001–0004 | none |

The fork has no file patches, only feature patches. Folia's patches should stay byte-identical to
upstream's after a merge; only ours should change.

### Files outside the patches the fork changes

These are where merge conflicts come from:

| File | What the fork changes | On a conflict |
|---|---|---|
| `README.md` | Entirely the fork's own | Keep ours |
| `gradle.properties` | `group=io.github.adrielorihuela.legacyfolia` | Keep our `group`; take upstream's `mcVersion`, `apiVersion`, `channel` and `paperRef` |
| `settings.gradle.kts` | `rootProject.name = "legacyfolia"` | Keep ours |
| `folia-server/build.gradle.kts.patch` | Jar and brand names, manifest titles, the night-config dependency | Take upstream's hunks and keep our lines |
| `.gitattributes` | `*.sh` and `*.patch` checked out with LF | Keep ours |
| `PROJECT_DESCRIPTION.md` | The fork's description | Keep ours |
| `.github/` | Unchanged from upstream | Take upstream's |

`REGION_LOGIC.md` is Folia's and is left as upstream has it.

## After every update

1. **Build and test.** `./gradlew :folia-server:compileJava`, then `./gradlew :folia-server:test`.
   The test task only runs classes whose name matches `**TestSuite`, and upstream's suites select
   only their own packages; `LegacyFoliaTestSuite` is what runs ours. A test of ours outside the
   package `io.github.adrielorihuela.legacyfolia`, or whose class name does not end in `Test` or
   `Tests`, compiles and silently never runs. To run only ours:

   ```bash
   ./gradlew :folia-server:test --tests 'io.github.adrielorihuela.legacyfolia.LegacyFoliaTestSuite'
   ```

2. **If `paperRef` moved, check the settings.** The fork replaces Paper's, Spigot's and Bukkit's
   configuration files with TOML files and builds the old objects in memory, so a setting Paper adds
   or a default it changes has to reach the TOML files:
   - `LegacyCoverageTest` fails when a key that vanilla, Spigot, Paper or Bukkit writes for a fresh
     install has no row in `folia-server/src/main/resources/legacyfolia/catalog.tsv` and no entry
     in `config/catalog/LegacyExclusions`. Give the key a row, put it with its comment in the right
     TOML file under `resources/legacyfolia/`, and add it to `docs/SETTINGS-MAP.md` (the counts at
     the top included). `SettingsCatalogTest` fails until the catalog and the TOML files agree.
   - `PaperBridgeTest`, `SpigotBridgeTest`, `BukkitBridgeTest` and `ServerPropertiesBridgeTest`
     compare what the bundled TOML files produce with what the original server writes on a fresh
     install, so a changed default fails one of them.
   - Paper occasionally moves a class the bridge imports. In the `804dd0c3` update, `Duration` and
     `DurationOrDisabled` moved to `io.papermc.paper.configuration.type.time`.
3. **Start a server.** `./gradlew :folia-server:runDevServer` runs in `run/`, where the EULA is
   already accepted; wait for `Done (`. Existing TOML files in `run/config/` receive any new keys
   with their comments, and nothing else in them changes.
4. **Record it** in `docs/PROGRESS.md` §7: the new `paperRef`, the conflicts, the patches that
   needed work and anything upstream changed that touches the fork.

Keep the merge, the refreshed patch files and any source change needed to compile in the merge
commit (as `e5e16b3` does), with a short title in Folia's style. Nothing is ever force-pushed.

## When a patch does not apply

`applyAllPatches` stops and names the patch. The tree it was applying to is left in the middle of a
`git am`:

- Minecraft patches apply to `folia-server/src/minecraft/java`.
- Paper server patches apply to `paper-server`.

Both are git repositories of their own, generated and ignored by the outer repository. Their history
is upstream's code, tagged `base`, then one commit per feature patch.

1. `cd` into that tree and fix the conflict there as usual, then `git add` the files and
   `git am --continue`. Before continuing, make sure no conflict markers are left
   (`git diff --check`, or search for `<<<<<<<`). In the `804dd0c3` update a resolution that failed
   on line endings let `git am` continue with the markers still in the file.
2. If a patch went in wrong, fix it with a fixup commit and squash it into the right commit:
   `git commit --fixup <commit>`, then
   `GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash base`.
3. Back in the root, `./gradlew rebuildAllServerPatches` regenerates the patch files from those
   commits. Then run a full `applyAllPatches` to prove they apply from scratch.
4. Commit the regenerated patches in the root repository. `rebuildAllServerPatches` stages
   everything it touched, so name the paths: `git commit --only <paths>`.

Two things catch people out:

- **The patch files come from the inner repository's commits, not its working tree.** Editing the
  generated tree and running `rebuildAllServerPatches` without committing inside it first produces
  nothing.
- **`applyAllPatches` resets those trees.** Uncommitted work inside them is lost. For the same
  reason two people, or two agents, cannot change Minecraft or Paper code at the same time; work in
  `folia-server/src/main` can run in parallel.

A commit's message becomes its patch file's name and header, so write it as it should appear.

## Adding a change of your own

Write the logic in `folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/` and add the
smallest possible call into Minecraft's or Paper's code. A one-line hook plus a class of your own
survives upstream releases; a hundred-line patch does not. Mark the hook with a
`// LegacyFolia` comment, or a `// LegacyFolia start` / `// LegacyFolia end` pair around several
lines, the way Paper marks its own changes.

When upstream starts doing the same thing the fork does, upstream's code wins and the fork's goes
on top or is removed. The one deliberate exception is the whitelist, which stays off on a new server
(minecraft patch 0031) although 26.3 turns it on.

## Moving to a new Minecraft version

When Folia opens the branch for the next version:

1. Change the branch in `update.sh` (`UPSTREAM_BRANCH`) and in `update.bat`, where it is written
   out in full on every line that uses it.
2. Run the update. `mcVersion`, `apiVersion` and `paperRef` come from upstream in the merge; do not
   edit them by hand.
3. Expect hooks to need moving by hand: a Minecraft version moves code that a point release does
   not. On the move to 26.3 the 1.8 combat patch 0017 had to follow the rename of
   `invulnerableTime` to `damageCooldownTime`, `ServerboundSwingPacket` became
   `ServerboundPunchPacket`, and the mache context in `build.gradle.kts.patch` changed. Paper or
   Mojang changing a default can collide with a fork decision too: 26.3 turned the whitelist on and
   patch 0031 had to be added to keep it off.
4. Update the version in `README.md`, `CLAUDE.md` and the docs.

The combat mechanics deserve a second look on any version, since they depend most on the shape of
upstream's own combat code. `reference/fetch-1.8.8-source.sh` rebuilds the 1.8.8 sources to compare
against; it needs `JAVA_8_HOME` and its output is never committed.

## How this differs from updating Paper or Folia

- A Paper or Folia server is updated by replacing the jar. LegacyFolia is updated the same way by
  the people who run it; this page is for whoever builds the jar.
- Updating the jar never rewrites a server's configuration. New settings are added to the TOML files
  in `config/` with their comments, and values someone edited stay as they are. Paper instead
  rewrites its YAML files on start; here those files are never written (see
  [CONFIGURATION.md](CONFIGURATION.md)).
- Folia itself is a fork of Paper kept as patches in the same way, so the merge brings Folia's
  changes and, through `paperRef`, Paper's.
