#!/usr/bin/env bash
# Runs the functional bot bench against a LegacyFolia jar and writes testing/results/bots.json.
#
#   run-all.sh [-j jar] [-o out.json] [-p port] [-O groups] [-P config|root] [-k]
#
# Starts the jar in fresh temporary folders (offline mode, port 25611, RCON 25612/25613), connects protocol bots and
# records PASS, FAIL or NOT_RUN for every scenario of docs/TESTING.md. The EULA is accepted only inside those
# temporary folders. Groups: locator,pvp,tooltips,off,fresh,dims,lobby,migration,eula.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
testing="$(dirname "$here")"
repo="$(dirname "$testing")"
jar=""; out="$testing/results/bots.json"; port=25611; only=""; props="config"; keep=""
while getopts "j:o:p:O:P:k" opt; do
  case "$opt" in
    j) jar="$OPTARG" ;; o) out="$OPTARG" ;; p) port="$OPTARG" ;; O) only="$OPTARG" ;; P) props="$OPTARG" ;; k) keep="--keep" ;;
    *) exit 2 ;;
  esac
done
if [ -z "$jar" ]; then
  jar="$(ls -t "$testing"/.cache/*.jar 2>/dev/null | head -n1 || true)"
  [ -n "$jar" ] || { echo "No jar given and none in testing/.cache. Example: gh release download v1.0.0-rc.1 --repo adrielorihuela/LegacyFolia -D testing/.cache" >&2; exit 1; }
fi
if command -v gradle >/dev/null 2>&1; then gradle -p "$here" installDist --console=plain -q; else "$repo/gradlew" -p "$here" installDist --console=plain -q; fi
args=(--jar "$jar" --out "$out" --port "$port" --props "$props")
[ -n "$only" ] && args+=(--only "$only")
[ -n "$keep" ] && args+=("$keep")
exec java -cp "$here/build/install/legacyfolia-bots/lib/*" io.github.adrielorihuela.legacyfolia.bots.Main "${args[@]}"
