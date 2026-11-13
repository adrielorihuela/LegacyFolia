#!/usr/bin/env bash
# Load test for a LegacyFolia or Folia server. See docs/LOAD-TESTING.md. Needs Java 25.
# Usage: run-load.sh --host H --port P --rcon-password-file F [--step 50] [--max 500]
#        [--join-delay-ms 1000] [--bot-ram 2G] [--rcon-port 25575] [--areas 16] [--check]
#        [--soulfire-version 2.10.7]
# All options are passed to LoadTest.java; only --soulfire-version is handled here.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cache="$root/testing/.cache"
mkdir -p "$cache"

version=2.10.7
args=()
while [ $# -gt 0 ]; do
  if [ "$1" = "--soulfire-version" ]; then version="$2"; shift 2; else args+=("$1"); shift; fi
done
if [ "$version" = latest ]; then
  version="$(curl -fsSLI -o /dev/null -w '%{url_effective}' https://github.com/soulfiremc-com/SoulFire/releases/latest | sed 's#.*/##')"
fi
major="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
[ "$major" -ge 25 ] || { echo "Java 25 or newer is required (found $major)." >&2; exit 1; }

jar="$cache/SoulFireCLI-$version.jar"
if [ ! -f "$jar" ] && [[ " ${args[*]} " != *" --check "* ]]; then
  echo "Downloading SoulFire $version (about 640 MB)"
  curl -fL -o "$jar.part" "https://github.com/soulfiremc-com/SoulFire/releases/download/$version/SoulFireCLI-$version.jar"
  mv "$jar.part" "$jar"
fi
exec java "$here/LoadTest.java" "${args[@]}" --soulfire-jar "$jar" \
  --out-dir "$root/testing/results" --work-dir "$cache/load-work"
