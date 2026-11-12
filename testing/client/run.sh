#!/usr/bin/env bash
# Real client smoke test: HeadlessMC with Fabric 26.3 in offline mode joins a local LegacyFolia server
# on port 25622, reads chat and leaves. It never uses a Microsoft or Minecraft account.
#   testing/client/run.sh --jar path/to/legacyfolia-paperclip.jar [--port 25622] [--keep] [--verbose]
# Downloads go to testing/.cache (headlessmc.jar and hmc-specifics from GitHub releases; the game files
# are fetched by HeadlessMC from Mojang's servers, about 600 MB the first time). Writes
# testing/results/client.json.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
testing="$(dirname "$here")"
exec node "$here/test.js" --cache "$testing/.cache" --out "$testing/results/client.json" "$@"
