$ErrorActionPreference = "Stop"

$DbName = "shutdown_tracker"
$DbUser = "shutdown_tracker"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$ComposeFile = Join-Path $RepoRoot "infra\docker\docker-compose.postgres.yml"
$MigrationsDir = Join-Path $RepoRoot "infra\migrations"
$Migrations = @(Get-ChildItem -Path $MigrationsDir -Filter "V*.sql" | Sort-Object Name)

# Only V007 is pinned, because the export-integrity suite this calls builds a V006-to-V007
# upgrade scenario by that filename. The total count is deliberately not pinned: doing so
# meant every migration added after V007 failed this script instead of being validated by it.
if ($Migrations.Count -lt 7) {
    throw "Expected at least V001-V007 in $MigrationsDir; found $($Migrations.Count)."
}

if ($Migrations[6].Name -ne "V007__enforce_export_candidate_integrity.sql") {
    throw "Expected V007__enforce_export_candidate_integrity.sql as the seventh migration."
}

# The tables the migrations create, derived from the migrations rather than transcribed.
#
# This was a hand-written list, and it named 33 tables when the migrations created 35 -- missing
# project_resource_links and candidate_schedule_runs. A per-name check cannot notice a table it has
# never heard of, so the list passed against a database missing the two most recent migrations.
$ExpectedTables = @(
    Get-ChildItem -Path $MigrationsDir -Filter "V*.sql" |
        Select-String -Pattern "CREATE TABLE (IF NOT EXISTS )?([a-z_.]+)" -AllMatches |
        ForEach-Object { $_.Matches } |
        ForEach-Object { $_.Groups[2].Value -replace "^public\.", "" } |
        Sort-Object -Unique
)

$DockerCandidates = @(@(
    (Get-Command docker -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
    (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\resources\bin\docker.exe"),
    "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
) | Where-Object { $_ -and (Test-Path $_) })

if (-not $DockerCandidates) {
    throw "Docker is required for migration validation."
}

$Docker = $DockerCandidates[0]

function Invoke-Compose {
    param(
        [string[]] $ComposeArgs
    )

    & $Docker compose -f $ComposeFile @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($ComposeArgs -join ' ')"
    }
}

function Test-PostgresSqlReady {
    $PreviousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & $Docker compose -f $ComposeFile exec -T postgres psql -U $DbUser -d $DbName -tAc "SELECT 1;" *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $PreviousErrorActionPreference
    }
}

$CleanupRequired = $false

try {
Write-Host "Resetting local PostgreSQL validation database..."
Invoke-Compose @("down", "-v")
$CleanupRequired = $true
Invoke-Compose @("up", "-d")

Write-Host "Waiting for PostgreSQL to become ready..."
$ready = $false
$stable = 0
for ($attempt = 1; $attempt -le 90; $attempt++) {
    if (Test-PostgresSqlReady) {
        $stable++
    } else {
        $stable = 0
    }
    if ($stable -ge 3) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 1
}

if (-not $ready) {
    & $Docker compose -f $ComposeFile logs postgres
    throw "PostgreSQL did not become ready in time."
}

Write-Host "Applying every migration in order..."
foreach ($Migration in $Migrations) {
    Write-Host "Applying $($Migration.Name)"
    Invoke-Compose @("exec", "-T", "postgres", "psql", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-U", $DbUser, "-d", $DbName, "-f", "/migrations/$($Migration.Name)")
}

Write-Host "Verifying expected tables..."
foreach ($Table in $ExpectedTables) {
    $Output = & $Docker compose -f $ComposeFile exec -T postgres psql -U $DbUser -d $DbName -tAc "SELECT to_regclass('public.$Table') IS NOT NULL;"
    if ($LASTEXITCODE -ne 0) {
        throw "Table verification query failed for $Table."
    }

    $Exists = (($Output -join "") -replace "`r", "").Trim()
    if ($Exists -ne "t") {
        throw "Expected table missing: $Table"
    }

    Write-Host "Verified table: $Table"
}

Write-Host "Running populated-upgrade and PostgreSQL export-integrity validation..."
Invoke-Compose @(
    "exec",
    "-T",
    "postgres",
    "sh",
    "-c",
    "tr -d '\015' < /validation/validation/run-export-integrity-suite.sh | sh"
)

Write-Host "Migration validation passed."
} finally {
    if ($CleanupRequired) {
        try {
            Invoke-Compose @("down", "-v")
        } catch {
            Write-Warning "Failed to remove the migration-validation container and volume: $($_.Exception.Message)"
        }
    }
}
