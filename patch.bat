@echo off
rem Windows: long paths on, and LF checkouts whatever the global core.autocrlf says.
set GIT_CONFIG_COUNT=2
set GIT_CONFIG_KEY_0=core.longpaths
set GIT_CONFIG_VALUE_0=true
set GIT_CONFIG_KEY_1=core.autocrlf
set GIT_CONFIG_VALUE_1=false
gradlew applyAllPatches
