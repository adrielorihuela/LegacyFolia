@echo off
rem Apply patches and build the server jar.
rem The finished jar is written to folia-server\build\libs\.
setlocal
cd /d "%~dp0"

call gradlew applyAllPatches || exit /b 1
call gradlew build createPaperclipJar || exit /b 1

echo.
echo Build finished. Jar:
dir /b folia-server\build\libs\*.jar
