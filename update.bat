@echo off
rem Pull in new Folia commits and re-apply our patches.
rem Safe to run on a fresh clone: the upstream remote is created if missing.
setlocal
cd /d "%~dp0"

git remote get-url upstream >nul 2>&1
if errorlevel 1 (
    echo Adding the upstream remote...
    git remote add upstream https://github.com/PaperMC/Folia.git || exit /b 1
)

echo Fetching ver/26.2.x from upstream...
git fetch upstream ver/26.2.x || exit /b 1

echo Merging upstream/ver/26.2.x...
git merge upstream/ver/26.2.x || exit /b 1

echo Re-applying patches...
call gradlew applyAllPatches || exit /b 1

echo.
echo Done. Build with build.bat, then commit the merge.
