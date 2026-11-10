param([string]$PostgresBin, [int]$Port = 55432)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'env.ps1')
Import-ReconflowEnvironment $projectRoot
if (-not $PostgresBin) {
    $downloadedPostgres = Get-ChildItem (Join-Path $env:USERPROFILE 'Downloads/postgresql-*-windows-x64-binaries/pgsql/bin/pg_ctl.exe') -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($downloadedPostgres) { $PostgresBin = Split-Path $downloadedPostgres.FullName -Parent }
}
if (-not $PostgresBin) { throw 'Pass -PostgresBin pointing to a portable PostgreSQL bin directory.' }
if ([string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) { throw 'Set DATABASE_PASSWORD in .env.' }
$dataDirectory = Join-Path $projectRoot '.local/postgres'
$logPath = Join-Path $projectRoot '.local/postgres.log'
[IO.Directory]::CreateDirectory((Split-Path $dataDirectory -Parent)) | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $dataDirectory 'PG_VERSION'))) {
    $passwordFile = Join-Path $projectRoot '.local/init-password'
    [IO.File]::WriteAllText($passwordFile, $env:DATABASE_PASSWORD)
    try {
        & (Join-Path $PostgresBin 'initdb.exe') -D $dataDirectory -U reconflow -A scram-sha-256 "--pwfile=$passwordFile" --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initialization failed.' }
    } finally { Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue }
}
& (Join-Path $PostgresBin 'pg_ctl.exe') -D $dataDirectory status
if ($LASTEXITCODE -ne 0) {
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $dataDirectory -l $logPath -o "-p $Port -h 127.0.0.1" -w start
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed.' }
}
$env:PGPASSWORD = $env:DATABASE_PASSWORD
$exists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U reconflow -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='reconflow'"
if ($LASTEXITCODE -ne 0) { throw 'Could not authenticate to local PostgreSQL.' }
if ($exists -ne '1') {
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U reconflow reconflow
    if ($LASTEXITCODE -ne 0) { throw 'Database creation failed.' }
}
Write-Output "PostgreSQL is running. Set DATABASE_URL=jdbc:postgresql://127.0.0.1:$Port/reconflow?reWriteBatchedInserts=true in .env."
