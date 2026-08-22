#!/usr/bin/env bash
# Apply patches and build the server jar.
#
# The finished jar is written to folia-server/build/libs/.
set -euo pipefail

cd "$(dirname "$0")"

./gradlew applyAllPatches
./gradlew build createPaperclipJar

echo
echo "Build finished. Jar:"
ls -1 folia-server/build/libs/*.jar 2>/dev/null || echo "  (none found in folia-server/build/libs)"
