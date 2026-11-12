# Real client smoke test: HeadlessMC with Fabric 26.3 in offline mode joins a local LegacyFolia server
# on port 25622, reads chat and leaves. It never uses a Microsoft or Minecraft account.
#   .\testing\client\run.ps1 -Jar path\to\legacyfolia-paperclip.jar [-Port 25622] [-Keep] [-ShowOutput]
# Downloads go to testing/.cache (headlessmc.jar and hmc-specifics from GitHub releases; the game files
# are fetched by HeadlessMC from Mojang's servers, about 600 MB the first time). Writes
# testing/results/client.json.
param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [int]$Port = 25622,
    [switch]$Keep,
    [switch]$ShowOutput
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$testing = Split-Path $here -Parent
$nodeArgs = @((Join-Path $here 'test.js'), '--jar', (Resolve-Path $Jar).Path, '--port', $Port, '--cache', (Join-Path $testing '.cache'), '--out', (Join-Path $testing 'results\client.json'))
if ($Keep) { $nodeArgs += '--keep' }
if ($ShowOutput) { $nodeArgs += '--verbose' }
node @nodeArgs
exit $LASTEXITCODE
