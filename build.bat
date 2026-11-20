@echo off
rem Apply patches and build the server jar.
rem The finished jar is written to folia-server\build\libs\.
setlocal
cd /d "%~dp0"
rem Windows: long paths on, and LF checkouts whatever the global core.autocrlf says.
set GIT_CONFIG_COUNT=2
set GIT_CONFIG_KEY_0=core.longpaths
set GIT_CONFIG_VALUE_0=true
set GIT_CONFIG_KEY_1=core.autocrlf
set GIT_CONFIG_VALUE_1=false

call gradlew applyAllPatches || exit /b 1
call gradlew build createPaperclipJar || exit /b 1

echo.
echo Build finished. Jar:
dir /b folia-server\build\libs\*.jar
