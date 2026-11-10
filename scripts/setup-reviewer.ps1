param([int]$FrontendPort = 16173, [int]$BackendPort = 19080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$reviewerDirectory = Join-Path $projectRoot '.local'
$reviewerFile = Join-Path $reviewerDirectory 'reviewer.env'
New-Item -ItemType Directory -Force -Path $reviewerDirectory | Out-Null
if (Test-Path -LiteralPath $reviewerFile) {
    Write-Output 'Existing .local/reviewer.env preserved. Use it with Compose project reconflow-reviewer.'
    exit 0
}
$randomBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
$databaseSecret = [Convert]::ToBase64String($randomBytes)
# These two credentials are intentionally public, isolated synthetic reviewer logins.
$lines = @(
    "DATABASE_PASSWORD=$databaseSecret",
    'DATABASE_USER=reconflow',
    'DEMO_FINANCE_PASSWORD=Review-Finance-2026!',
    'DEMO_FOUNDER_PASSWORD=Review-Founder-2026!',
    'OPENAI_API_KEY=',
    'OPENAI_MODEL=',
    'RETURN_GRACE_DAYS=14',
    'SESSION_COOKIE_SECURE=false',
    "FRONTEND_PORT=$FrontendPort",
    "BACKEND_PORT=$BackendPort"
)
# Compose supplies its own internal JDBC URL. This environment file is Docker-only.
[System.IO.File]::WriteAllLines($reviewerFile, $lines, (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'Created .local/reviewer.env with a private random database password and isolated demo accounts.'
Write-Output 'Start: docker compose --env-file .local/reviewer.env -p reconflow-reviewer up --build --wait'
