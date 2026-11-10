param([int]$FrontendPort = 5173, [int]$BackendPort = 8080, [string]$ProjectName = 'reconflow')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:FRONTEND_PORT = [string]$FrontendPort
$env:BACKEND_PORT = [string]$BackendPort
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is not installed or not on PATH. Docker startup checks have NOT run.' }
function Invoke-CheckedDocker {
    param([string[]]$Arguments)
    # Docker writes normal progress to stderr. Judge success by its exit code.
    $priorPreference = $ErrorActionPreference
    $dockerExit = 1
    try {
        $ErrorActionPreference = 'Continue'
        & docker @Arguments 2>&1 | ForEach-Object { Write-Output $_.ToString() }
        $dockerExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $priorPreference }
    if ($dockerExit -ne 0) { throw "Docker command failed with exit code $dockerExit." }
}
Invoke-CheckedDocker -Arguments @('compose','version')
Invoke-CheckedDocker -Arguments @('compose','--project-name',$ProjectName,'config','--quiet')
try {
    Invoke-CheckedDocker -Arguments @('compose','--project-name',$ProjectName,'up','--build','--wait','--wait-timeout','180')
} catch {
    & docker compose --project-name $ProjectName logs --tail 100
    throw
}
Invoke-CheckedDocker -Arguments @('compose','--project-name',$ProjectName,'ps')
$result = Invoke-RestMethod "http://127.0.0.1:$FrontendPort/api/health"
if ($result.status -ne 'UP') { throw 'Frontend-to-backend health probe failed.' }
Write-Output 'Docker services started and frontend-to-backend health probe passed.'
