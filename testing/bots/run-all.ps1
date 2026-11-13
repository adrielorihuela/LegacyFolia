<#
.SYNOPSIS
  Runs the functional bot bench against a LegacyFolia jar and writes testing/results/bots.json.
.DESCRIPTION
  Starts the jar in fresh temporary folders (offline mode, port 25611, RCON 25612/25613), connects protocol bots,
  runs every scenario of docs/TESTING.md that can be automated and records PASS, FAIL or NOT_RUN for each.
  The EULA is accepted only inside those temporary folders (eula = true in their config/server.toml).
.PARAMETER Jar        Server jar. Default: the newest testing/.cache/*.jar.
.PARAMETER Out        Report path. Default: testing/results/bots.json.
.PARAMETER Port       Game port. Default 25611.
.PARAMETER Only       Comma-separated groups: locator,pvp,tooltips,off,fresh,dims,lobby,migration,eula.
.PARAMETER Properties 'config' (RC 1: config/server.properties) or 'root' (server.properties beside the jar).
.PARAMETER Keep       Keep the temporary server folders.
#>
param(
    [string]$Jar,
    [string]$Out,
    [int]$Port = 25611,
    [string]$Only = "",
    [ValidateSet("config", "root")][string]$Properties = "config",
    [switch]$Keep
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$testing = Split-Path -Parent $here
$repo = Split-Path -Parent $testing
if (-not $Jar) {
    $newest = Get-ChildItem (Join-Path $testing ".cache") -Filter *.jar -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $newest) { throw "No jar given and none in testing/.cache. Download one, for example: gh release download v1.0.0-rc.1 --repo adrielorihuela/LegacyFolia -D testing/.cache" }
    $Jar = $newest.FullName
}
if (-not $Out) { $Out = Join-Path $testing "results/bots.json" }

# Build the bench (Gradle from PATH, else the repository wrapper; the project is standalone).
$gradle = if (Get-Command gradle -ErrorAction SilentlyContinue) { "gradle" } else { Join-Path $repo "gradlew.bat" }
& $gradle -p $here installDist --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "Building the bench failed" }

$javaArgs = @("-cp", (Join-Path $here "build/install/legacyfolia-bots/lib/*"), "io.github.adrielorihuela.legacyfolia.bots.Main",
    "--jar", $Jar, "--out", $Out, "--port", $Port, "--props", $Properties)
if ($Only) { $javaArgs += @("--only", $Only) }
if ($Keep) { $javaArgs += "--keep" }
& java @javaArgs
exit $LASTEXITCODE
