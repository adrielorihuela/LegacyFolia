#!/usr/bin/env bash
# Apply patches and build the server jar.
#
# The finished jar is written to folia-server/build/libs/.
set -euo pipefail

cd "$(dirname "$0")"

# Windows: long paths on, and LF checkouts whatever the global core.autocrlf says.
export GIT_CONFIG_COUNT=2 GIT_CONFIG_KEY_0=core.longpaths GIT_CONFIG_VALUE_0=true GIT_CONFIG_KEY_1=core.autocrlf GIT_CONFIG_VALUE_1=false

./gradlew applyAllPatches
./gradlew build createPaperclipJar

echo
echo "Build finished. Jar:"
ls -1 folia-server/build/libs/*.jar 2>/dev/null || echo "  (none found in folia-server/build/libs)"
