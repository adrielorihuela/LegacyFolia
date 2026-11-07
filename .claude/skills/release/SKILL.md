---
name: release
description: "How to tag and publish a LegacyFolia pre-release or release with its jar on GitHub. Use whenever the user wants a release candidate, a pre-release, v1.0.0, or to attach the jar to a GitHub release."
---

Moved from CLAUDE.md § Commands and § Repo conventions on 2026-11-06.

- Pre-releases for testing are `v1.0.0-rc.N` with the paperclip jar attached on GitHub; `v1.0.0` is tagged and released only when the user says so.
- The CI in `.github/workflows/` is upstream's: `build.yml` builds pushes, `release-26.3.yaml` only runs on `ver/26.3.x`, so release jars are built locally and attached to the GitHub release.
- Build jar: `./build.sh`/`build.bat` → `applyAllPatches` + `build createPaperclipJar`; the runnable jar is `folia-server/build/libs/legacyfolia-paperclip-<version>.jar` (version from `settings.gradle.kts`: `26.3.local-SNAPSHOT` without `BUILD_NUMBER`).
- Publish with `gh release create <tag> --repo adrielorihuela/LegacyFolia --prerelease --latest=false`, the jar attached and its sha256 in the notes.
