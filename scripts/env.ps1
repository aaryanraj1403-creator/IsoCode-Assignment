function Import-ReconflowEnvironment {
    param([string]$ProjectRoot)
    $configuration = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $configuration)) { throw 'Copy .env.example to .env and set the three passwords first.' }
    foreach ($line in [IO.File]::ReadAllLines($configuration)) {
        if ($line.TrimStart().StartsWith('#') -or -not $line.Contains('=')) { continue }
        $pair = $line -split '=', 2
        [Environment]::SetEnvironmentVariable($pair[0].Trim(), $pair[1], 'Process')
    }
}
