#!/usr/bin/env bash
# Reproduce the Minecraft 1.8.8 sources the combat mechanics were read from.
#
# They are not committed to this repository and never will be: they are Mojang's
# code, decompiled, and not ours to redistribute. That is the same reason
# CraftBukkit ships BuildTools instead of sources. This script builds them
# locally so anybody can check how faithful the 1.8 combat really is.
#
# The refs below are pinned to Spigot's 1.8.8 build 582b (hotfix 3), which is
# exactly what io.github.adrielorihuela.legacyfolia.combat.LegacyCombat was
# written against.
set -euo pipefail

cd "$(dirname "$0")"

OUT="mc-1.8.8"
BUILDTOOLS="https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"

# BuildTools needs Java 8. SpecialSource reads the JDK's own class files with a
# version of ASM that rejects anything newer, so running it on a modern JDK dies
# with an IllegalArgumentException that explains nothing.
if [[ -z "${JAVA_8_HOME:-}" ]]; then
    cat >&2 <<'MSG'
Set JAVA_8_HOME to a Java 8 installation first.

BuildTools cannot produce 1.8.8 on a modern JDK: SpecialSource reads the
runtime's class files with an ASM that refuses anything past Java 8, and fails
with a bare IllegalArgumentException.

A portable JDK 8 is enough, it does not have to be installed:
  https://adoptium.net/temurin/releases/?version=8
MSG
    exit 1
fi

JAVA="$JAVA_8_HOME/bin/java"
[[ -x "$JAVA" ]] || { echo "no java at $JAVA" >&2; exit 1; }

mkdir -p "$OUT"
cd "$OUT"

if [[ ! -f BuildTools.jar ]]; then
    echo "Downloading BuildTools..."
    curl -fsSL -o BuildTools.jar "$BUILDTOOLS"
fi

echo "Building 1.8.8 (this takes a few minutes)..."
# --compile NONE: the sources are the point, not a jar. The 1.8.8 build itself
# would need a Maven toolchain nobody has any more.
"$JAVA" -jar BuildTools.jar --rev 1.8.8 --compile NONE --disable-java-check --nogui

cat <<'MSG'

Done. What was read for the combat mechanics:

  work/decompile-*/net/minecraft/server/    the full decompiled 1.8.8 server
  CraftBukkit/src/main/java/net/minecraft/server/
                                            the same, with CraftBukkit's patches

The methods the formulas came from:

  EntityLiving.a(Entity, float, double, double)   knockback
  EntityHuman.attack(Entity)                      criticals, sprint knockback
  EntityLiving.damageEntity(DamageSource, float)  the invulnerability window

Compare them against
folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/combat/LegacyCombat.java
MSG
