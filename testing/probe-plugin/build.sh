#!/usr/bin/env bash
# Builds LegacyProbe.jar. Usage: build.sh <server folder that has run once> (for libraries/), default ./
set -e
cd "$(dirname "$0")"
LIBS="${1:-.}/libraries"
CP=$(find "$LIBS" -name '*.jar' | tr '\n' ';' | sed 's/;$//')
rm -rf build && mkdir -p build/classes
javac --release 25 -cp "$CP" -d build/classes src/probe/LegacyProbe.java
cp plugin.yml build/classes/
jar cf build/LegacyProbe.jar -C build/classes .
echo built build/LegacyProbe.jar
