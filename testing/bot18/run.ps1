# 1.8.8 client checks for LegacyFolia (ViaVersion 5.12.2, ViaBackwards 5.12.2, ViaRewind 4.2.0).
#   .\testing\bot18\run.ps1 -Jar path\to\legacyfolia-paperclip.jar [-Port 25621] [-Keep]
# Downloads the Via plugins into testing/.cache/plugins, starts the server in a fresh temp folder
# (offline mode, EULA accepted only there), runs the bots and writes testing/results/bot18.json.
param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [int]$Port = 25621,
    [switch]$Keep
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$cache = Join-Path (Split-Path $here -Parent) '.cache'
$out = Join-Path (Split-Path $here -Parent) 'results\bot18.json'

if (-not (Test-Path (Join-Path $here 'node_modules\mineflayer'))) {
    npm install --no-audit --no-fund --prefix $here
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
}
$nodeArgs = @((Join-Path $here 'test.js'), '--jar', (Resolve-Path $Jar).Path, '--port', $Port, '--cache', $cache, '--out', $out)
if ($Keep) { $nodeArgs += '--keep' }
node @nodeArgs
exit $LASTEXITCODE
