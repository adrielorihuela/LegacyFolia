#!/usr/bin/env bash
# Pull in new Folia commits and re-apply our patches.
#
# Safe to run on a fresh clone: the upstream remote is created if missing, so a
# machine that has only ever seen origin can update without any manual setup.
set -euo pipefail

cd "$(dirname "$0")"

# Windows: long paths on, and LF checkouts whatever the global core.autocrlf says.
export GIT_CONFIG_COUNT=2 GIT_CONFIG_KEY_0=core.longpaths GIT_CONFIG_VALUE_0=true GIT_CONFIG_KEY_1=core.autocrlf GIT_CONFIG_VALUE_1=false

UPSTREAM_URL="https://github.com/PaperMC/Folia.git"
UPSTREAM_BRANCH="ver/26.3.x"

if ! git remote get-url upstream >/dev/null 2>&1; then
    echo "Adding the upstream remote..."
    git remote add upstream "$UPSTREAM_URL"
fi

echo "Fetching $UPSTREAM_BRANCH from upstream..."
git fetch upstream "$UPSTREAM_BRANCH"

echo "Merging upstream/$UPSTREAM_BRANCH..."
git merge "upstream/$UPSTREAM_BRANCH"

echo "Re-applying patches..."
./gradlew applyAllPatches

echo
echo "Done. Build with ./build.sh, then commit the merge."
