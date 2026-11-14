<#
.SYNOPSIS
  Load test for a LegacyFolia or Folia server: adds offline bots in steps and watches the server over RCON.
  See docs/LOAD-TESTING.md. Needs Java 25.
#>
param(
    [Alias('Host')][string]$ServerHost = '127.0.0.1',
    [int]$Port = 25565,
    [int]$Step = 50,
    [int]$Max = 500,
    [int]$JoinDelayMs = 1000,
    [string]$BotRam = '2G',
    [int]$RconPort = 25575,
    [string]$RconPasswordFile = '',
    [string]$RconPassword = '',
    [int]$Areas = 16,
    [string]$SoulFireVersion = '2.10.7',
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$cache = Join-Path $root 'testing\.cache'
New-Item -ItemType Directory -Force $cache | Out-Null

$javaVersion = ((cmd /c 'java -version 2>&1' | Select-Object -First 1) -as [string]) -replace '.*"(\d+).*', '$1'
if ([int]$javaVersion -lt 25) { throw "Java 25 or newer is required (found $javaVersion)." }

if ($SoulFireVersion -eq 'latest') {
    $SoulFireVersion = (Invoke-RestMethod 'https://api.github.com/repos/soulfiremc-com/SoulFire/releases/latest').tag_name
}
$jar = Join-Path $cache "SoulFireCLI-$SoulFireVersion.jar"
if (-not (Test-Path $jar) -and -not $CheckOnly) {
    $url = "https://github.com/soulfiremc-com/SoulFire/releases/download/$SoulFireVersion/SoulFireCLI-$SoulFireVersion.jar"
    Write-Host "Downloading $url (about 640 MB)"
    Invoke-WebRequest $url -OutFile "$jar.part"
    Move-Item "$jar.part" $jar
}

$a = @('--host', $ServerHost, '--port', $Port, '--rcon-port', $RconPort, '--step', $Step, '--max', $Max,
       '--join-delay-ms', $JoinDelayMs, '--bot-ram', $BotRam, '--areas', $Areas, '--soulfire-jar', $jar,
       '--out-dir', (Join-Path $root 'testing\results'), '--work-dir', (Join-Path $cache 'load-work'))
if ($RconPasswordFile) { $a += @('--rcon-password-file', $RconPasswordFile) }
if ($RconPassword) { $a += @('--rcon-password', $RconPassword) }
if ($CheckOnly) { $a += '--check' }
& java (Join-Path $PSScriptRoot 'LoadTest.java') @a
exit $LASTEXITCODE
