#!/usr/bin/env bash
# Windows: long paths on, and LF checkouts whatever the global core.autocrlf says.
export GIT_CONFIG_COUNT=2 GIT_CONFIG_KEY_0=core.longpaths GIT_CONFIG_VALUE_0=true GIT_CONFIG_KEY_1=core.autocrlf GIT_CONFIG_VALUE_1=false
./gradlew applyAllPatches
