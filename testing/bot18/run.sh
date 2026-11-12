#!/usr/bin/env bash
# 1.8.8 client checks for LegacyFolia (ViaVersion 5.12.2, ViaBackwards 5.12.2, ViaRewind 4.2.0).
#   testing/bot18/run.sh --jar path/to/legacyfolia-paperclip.jar [--port 25621] [--keep]
# Downloads the Via plugins into testing/.cache/plugins, starts the server in a fresh temp folder
# (offline mode, EULA accepted only there), runs the bots and writes testing/results/bot18.json.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
testing="$(dirname "$here")"

if [ ! -d "$here/node_modules/mineflayer" ]; then
  npm install --no-audit --no-fund --prefix "$here"
fi
exec node "$here/test.js" --cache "$testing/.cache" --out "$testing/results/bot18.json" "$@"
