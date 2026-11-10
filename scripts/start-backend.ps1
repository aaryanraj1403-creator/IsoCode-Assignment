param([string]$MavenPath, [string]$JavaHome, [switch]$NoBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'env.ps1')
Import-ReconflowEnvironment $projectRoot
if ($env:DATABASE_URL -and $env:DATABASE_URL -notmatch '(?i)([?&])reWriteBatchedInserts=') {
    $querySeparator = if ($env:DATABASE_URL.Contains('?')) { '&' } else { '?' }
    $env:DATABASE_URL += $querySeparator + 'reWriteBatchedInserts=true'
}
if (-not $JavaHome) {
    $portableJava = Get-ChildItem (Join-Path $projectRoot '.local/jdk17') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portableJava) { $JavaHome = $portableJava.FullName }
}
if ($JavaHome) { $env:JAVA_HOME = $JavaHome; $env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path }
if (-not $MavenPath) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mavenCommand) { $MavenPath = $mavenCommand.Source }
    else {
        $downloadedMaven = Get-ChildItem (Join-Path $env:USERPROFILE 'Downloads/apache-maven-*-bin/apache-maven-*/bin/mvn.cmd') -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($downloadedMaven) { $MavenPath = $downloadedMaven.FullName }
    }
}
if (-not $MavenPath -and -not $NoBuild) { throw 'Maven is required. Add Maven to PATH or pass -MavenPath with the full mvn.cmd path.' }
Set-Location (Join-Path $projectRoot 'backend')
$env:DEBUG = 'false'
if (-not $env:MAVEN_OPTS) { $env:MAVEN_OPTS = '-Xms32m -Xmx256m -XX:ActiveProcessorCount=4' }
if (-not $env:JAVA_TOOL_OPTIONS) { $env:JAVA_TOOL_OPTIONS = '-Xms64m -Xmx512m -XX:ActiveProcessorCount=4' }
if (-not $NoBuild) {
    & $MavenPath -B "-Dmaven.repo.local=$projectRoot/.local/m2" package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
}
$runtimeDirectory = Join-Path $projectRoot '.local/runtime'
[IO.Directory]::CreateDirectory($runtimeDirectory) | Out-Null
$runtimeJar = Join-Path $runtimeDirectory ('reconflow-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.jar')
Copy-Item -LiteralPath 'target/reconflow-0.1.0.jar' -Destination $runtimeJar
& java -jar $runtimeJar
if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE. See the console output for details." }
