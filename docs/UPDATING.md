# Updating to a new Folia build

```bash
./update.sh     # update.bat on Windows
./build.sh
```

That is the whole procedure. The rest of this page explains what it does and what to do when a
merge does not go cleanly.

## What `update.sh` does

1. Adds the `upstream` remote if it is missing, so a fresh clone from any machine works without
   setting anything up first.
2. Fetches `ver/26.3.x` from `PaperMC/Folia`.
3. Merges it.
4. Re-applies the patches.

Then `./build.sh` produces the jar in `folia-server/build/libs/`.

## Why merges are usually painless

Almost everything this fork adds is ordinary tracked source under
`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/`. Upstream never touches that
directory, so it cannot conflict.

What does touch Minecraft's own code is deliberately tiny — each patch is a handful of lines that
calls into that source. The dimension switches, for example, are two lines inside one method:

```java
// LegacyFolia - answered from the unified configuration, falling back to paper-global.yml
return Dimensions.netherEnabled(GlobalConfiguration.get().misc.enableNether);
```

A patch that small only conflicts if upstream edits the exact lines around it, and fixing it is
usually a matter of moving one call.

This is why the layout was chosen. The alternative — expressing the whole fork as patch files —
would mean every upstream release rewriting thousands of lines of diff.

## When a patch does not apply

`./gradlew applyAllPatches` stops and names the file and the hunk.

1. `cd` into the generated tree the patch belongs to: `folia-server/src/minecraft/java` for
   Minecraft's own code, `paper-server` for Paper's.
2. Resolve it there as a normal git conflict. Both are real git repositories.
3. Commit inside that repository. **The commit message becomes the patch file's name and header**,
   so write it as you want it to appear.
4. Back in the root, `./gradlew rebuildAllServerPatches` regenerates the patch files.
5. Commit the regenerated patches in the root repository.

The one thing that catches people out: editing the generated tree and running
`rebuildAllServerPatches` produces nothing unless you commit inside the inner repository first.
Feature patches are generated from its commits, not from its working tree.

## Adding a change of your own

Prefer writing it in `folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/` and adding
the smallest possible call into Minecraft's code. A one-line hook plus a class of your own will
survive upstream releases; a hundred-line patch will not.

Tests go in `folia-server/src/test/java/io/github/adrielorihuela/legacyfolia/`. Note that the test
task only runs classes matching `**TestSuite`, and upstream's suites select only their own packages
— `LegacyFoliaTestSuite` is what picks ours up. A test outside it compiles and silently never runs.

```bash
./gradlew :folia-server:test --tests '*LegacyFoliaTestSuite*'
```

## Moving to a new Minecraft version

When Folia opens a branch for the next version:

1. Change the branch in `update.sh` (`UPSTREAM_BRANCH`) and in `update.bat`.
2. Change `mcVersion` and `apiVersion` in `gradle.properties`.
3. Run `./update.sh` and expect to fix a few hooks by hand — a Minecraft version bump moves code
   that a point release does not.

The combat mechanics deserve a second look on any version bump, since they are the part that
depends most on how upstream's own combat code is shaped. `reference/fetch-1.8.8-source.sh` rebuilds
the 1.8.8 sources to compare against.
