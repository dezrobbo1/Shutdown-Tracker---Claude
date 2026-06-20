#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ApiBaseUrl = "http://localhost:8080",
    [string]$FixturePath = "fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml",
    [string]$ProjectId,
    [switch]$AllowWrites,
    [switch]$RunProjectUpload,
    [switch]$RunParseHandoff,
    [string]$ImportBatchId,
    [switch]$ListImportSnapshots,
    [string]$SnapshotId,
    [switch]$ReadImportSnapshot,
    [switch]$CreateExportPreview,
    [string]$ImportedTaskId,
    [string]$SourceEntityType = "review_smoke",
    [string]$SourceEntityId,
    [string]$ExportFieldName = "percent_complete",
    [string]$ExportNewValue = "50",
    [string]$ExportBatchId,
    [switch]$ReadExportPreview,
    [switch]$ApproveExportBatch,
    [switch]$GenerateExportArtifact,
    [string]$ActorUserId = "00000000-0000-0000-0000-000000000001",
    [switch]$SkipHealth,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Show-Help {
    @"
Source/import/export review smoke script.

Default behavior:
  - GET /actuator/health
  - GET /api/version
  - POST /api/source-files/validate with the approved synthetic MSPDI fixture

Optional write behavior requires -AllowWrites:
  -RunProjectUpload       POST /api/projects/{projectId}/source-files
  -RunParseHandoff        POST /api/projects/{projectId}/import-batches/{importBatchId}/request-parse-summary
  -CreateExportPreview    POST /api/projects/{projectId}/export-preview
  -ApproveExportBatch     POST /api/projects/{projectId}/export-preview/{exportBatchId}/approve
  -GenerateExportArtifact POST /api/projects/{projectId}/export-preview/{exportBatchId}/generate-artifact

Examples:
  .\scripts\review\source-import-export-smoke.ps1
  .\scripts\review\source-import-export-smoke.ps1 -ApiBaseUrl http://localhost:8080 -AllowWrites -ProjectId <uuid> -RunProjectUpload
  .\scripts\review\source-import-export-smoke.ps1 -ProjectId <uuid> -ListImportSnapshots

This script uses only synthetic fixture input by default. It does not seed data,
commit generated artifacts, automate Microsoft Project, or write back to Microsoft Project.
"@
}

if ($Help) {
    Show-Help
    exit 0
}

$script:BaseUrl = $ApiBaseUrl.TrimEnd("/")
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message"
}

function Write-Ok {
    param([string]$Message)
    Write-Host "OK: $Message"
}

function Write-Skip {
    param([string]$Message)
    Write-Host "SKIP: $Message"
}

function Resolve-RepoPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path $Path).Path
    }
    return (Resolve-Path (Join-Path $script:RepoRoot $Path)).Path
}

function Join-ApiUri {
    param([string]$Path)
    if ($Path.StartsWith("/")) {
        return "$script:BaseUrl$Path"
    }
    return "$script:BaseUrl/$Path"
}

function Encode-PathSegment {
    param([string]$Value)
    return [System.Uri]::EscapeDataString($Value)
}

function Assert-Required {
    param(
        [string]$Name,
        [string]$Value
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name is required for this smoke step."
    }
}

function Assert-WritesAllowed {
    param([string]$Step)
    if (-not $AllowWrites) {
        throw "$Step is a write step. Re-run with -AllowWrites if this is an intentional local/review smoke run."
    }
}

function Convert-JsonContent {
    param([string]$Content)
    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $null
    }
    return $Content | ConvertFrom-Json
}

function As-Array {
    param([object]$Value)
    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return $Value
    }
    return @($Value)
}

function Invoke-SmokeRequest {
    param(
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )

    $uri = Join-ApiUri $Path
    $parameters = @{
        Uri = $uri
        Method = $Method
        UseBasicParsing = $true
    }

    if ($null -ne $Body) {
        $parameters["ContentType"] = "application/json"
        $parameters["Body"] = ($Body | ConvertTo-Json -Depth 20 -Compress)
    }

    try {
        $response = Invoke-WebRequest @parameters
        return Convert-JsonContent $response.Content
    } catch {
        $message = "HTTP $Method $uri failed."
        if ($_.Exception.Response) {
            $message = "$message Status: $([int]$_.Exception.Response.StatusCode)."
        }
        throw "$message $($_.Exception.Message)"
    }
}

function Invoke-MultipartFile {
    param(
        [string]$Path,
        [string]$FilePath
    )

    Add-Type -AssemblyName System.Net.Http
    $uri = Join-ApiUri $Path
    $client = [System.Net.Http.HttpClient]::new()
    $stream = [System.IO.File]::OpenRead($FilePath)
    $content = [System.Net.Http.MultipartFormDataContent]::new()
    $fileContent = [System.Net.Http.StreamContent]::new($stream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/xml")

    try {
        $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))
        $response = $client.PostAsync($uri, $content).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "HTTP POST $uri failed. Status: $([int]$response.StatusCode). $body"
        }
        return Convert-JsonContent $body
    } finally {
        $fileContent.Dispose()
        $content.Dispose()
        $stream.Dispose()
        $client.Dispose()
    }
}

$resolvedFixturePath = Resolve-RepoPath $FixturePath

if (-not $SkipHealth) {
    Write-Step "Checking API health"
    $health = Invoke-SmokeRequest -Method GET -Path "/actuator/health"
    Write-Ok "Health status: $($health.status)"
}

Write-Step "Checking API version"
$version = Invoke-SmokeRequest -Method GET -Path "/api/version"
Write-Ok "Version response service: $($version.service), status: $($version.status)"

Write-Step "Validating synthetic source file without storage"
$validation = Invoke-MultipartFile -Path "/api/source-files/validate" -FilePath $resolvedFixturePath
if (-not $validation.accepted) {
    throw "Expected source-file validation to accept the synthetic fixture. Rejection: $($validation.rejectionReason)"
}
Write-Ok "Validation accepted $($validation.originalFilename); message: $($validation.message)"

if ($RunProjectUpload) {
    Assert-WritesAllowed "Project source-file upload"
    Assert-Required "ProjectId" $ProjectId
    Write-Step "Uploading synthetic source file to project $ProjectId"
    $uploadPath = "/api/projects/$(Encode-PathSegment $ProjectId)/source-files"
    $upload = Invoke-MultipartFile -Path $uploadPath -FilePath $resolvedFixturePath
    if (-not $upload.accepted) {
        throw "Expected project upload to accept the synthetic fixture. Rejection: $($upload.rejectionReason)"
    }
    $ImportBatchId = $upload.importBatch.id
    Write-Ok "Stored source file $($upload.sourceFile.id); pending import batch $ImportBatchId"
}

if ($RunParseHandoff) {
    Assert-WritesAllowed "Import parse handoff"
    Assert-Required "ProjectId" $ProjectId
    Assert-Required "ImportBatchId" $ImportBatchId
    Write-Step "Requesting worker parse summary for import batch $ImportBatchId"
    $parsePath = "/api/projects/$(Encode-PathSegment $ProjectId)/import-batches/$(Encode-PathSegment $ImportBatchId)/request-parse-summary"
    $parse = Invoke-SmokeRequest -Method POST -Path $parsePath
    Write-Ok "Import batch status: $($parse.importBatch.status); parser: $($parse.parseSummary.parserName)"
}

if ($ListImportSnapshots) {
    Assert-Required "ProjectId" $ProjectId
    Write-Step "Listing import review snapshots for project $ProjectId"
    $snapshots = Invoke-SmokeRequest -Method GET -Path "/api/projects/$(Encode-PathSegment $ProjectId)/import-review/snapshots"
    $snapshotItems = As-Array $snapshots
    $snapshotCount = $snapshotItems.Count
    Write-Ok "Import review snapshot count: $snapshotCount"
    if (-not $SnapshotId -and $snapshotCount -gt 0) {
        $SnapshotId = $snapshotItems[0].id
        Write-Ok "Using first snapshot for optional later steps: $SnapshotId"
    }
}

if ($ReadImportSnapshot -or $SnapshotId) {
    if ($ReadImportSnapshot) {
        Assert-Required "ProjectId" $ProjectId
        Assert-Required "SnapshotId" $SnapshotId
        Write-Step "Reading import snapshot $SnapshotId"
        $snapshot = Invoke-SmokeRequest -Method GET -Path "/api/projects/$(Encode-PathSegment $ProjectId)/import-review/snapshots/$(Encode-PathSegment $SnapshotId)"
        Write-Ok "Snapshot status: $($snapshot.snapshot.status); tasks: $((As-Array $snapshot.tasks).Count)"
    }
}

if ($CreateExportPreview) {
    Assert-WritesAllowed "Export preview creation"
    Assert-Required "ProjectId" $ProjectId
    Assert-Required "SnapshotId" $SnapshotId
    Assert-Required "ImportedTaskId" $ImportedTaskId

    if ([string]::IsNullOrWhiteSpace($SourceEntityId)) {
        $SourceEntityId = [Guid]::NewGuid().ToString()
        Write-Ok "No SourceEntityId provided; using synthetic source entity $SourceEntityId. The preview line may be ineligible without an approved source record."
    }

    Write-Step "Creating export preview for project $ProjectId"
    $previewBody = @{
        projectSnapshotId = $SnapshotId
        lines = @(
            @{
                importedTaskId = $ImportedTaskId
                sourceEntityType = $SourceEntityType
                sourceEntityId = $SourceEntityId
                fieldName = $ExportFieldName
                newValue = $ExportNewValue
                sourceActorUserId = $ActorUserId
                sourceTimestamp = (Get-Date).ToUniversalTime().ToString("o")
                reason = "Synthetic smoke preview. No Microsoft Project write-back."
                metadata = @{
                    source = "source-import-export-smoke"
                    synthetic = $true
                    projectWriteBack = $false
                }
            }
        )
        metadata = @{
            source = "source-import-export-smoke"
            synthetic = $true
            projectWriteBack = $false
        }
    }
    $preview = Invoke-SmokeRequest -Method POST -Path "/api/projects/$(Encode-PathSegment $ProjectId)/export-preview" -Body $previewBody
    $ExportBatchId = $preview.batch.id
    Write-Ok "Created export preview batch $ExportBatchId with $($preview.batch.eligibleLineCount) eligible line(s)"
}

if ($ReadExportPreview) {
    Assert-Required "ProjectId" $ProjectId
    Assert-Required "ExportBatchId" $ExportBatchId
    Write-Step "Reading export preview batch $ExportBatchId"
    $previewRead = Invoke-SmokeRequest -Method GET -Path "/api/projects/$(Encode-PathSegment $ProjectId)/export-preview/$(Encode-PathSegment $ExportBatchId)"
    Write-Ok "Export batch status: $($previewRead.batch.status); eligible lines: $($previewRead.batch.eligibleLineCount)"
}

if ($ApproveExportBatch) {
    Assert-WritesAllowed "Export batch approval"
    Assert-Required "ProjectId" $ProjectId
    Assert-Required "ExportBatchId" $ExportBatchId
    Write-Step "Approving export preview batch $ExportBatchId"
    $approveBody = @{
        reviewedByUserId = $ActorUserId
        reason = "Synthetic smoke approval. No Microsoft Project write-back."
        metadata = @{
            source = "source-import-export-smoke"
            synthetic = $true
            projectWriteBack = $false
        }
    }
    $approved = Invoke-SmokeRequest -Method POST -Path "/api/projects/$(Encode-PathSegment $ProjectId)/export-preview/$(Encode-PathSegment $ExportBatchId)/approve" -Body $approveBody
    Write-Ok "Export batch status: $($approved.batch.status)"
}

if ($GenerateExportArtifact) {
    Assert-WritesAllowed "Worker-backed export artifact generation"
    Assert-Required "ProjectId" $ProjectId
    Assert-Required "ExportBatchId" $ExportBatchId
    Write-Step "Requesting worker-backed export artifact generation for batch $ExportBatchId"
    $generateBody = @{
        generatedByUserId = $ActorUserId
        reason = "Synthetic smoke artifact generation. Manual Microsoft Project verification is separate."
        metadata = @{
            source = "source-import-export-smoke"
            synthetic = $true
            manualProjectVerification = $false
            projectWriteBack = $false
        }
    }
    $generated = Invoke-SmokeRequest -Method POST -Path "/api/projects/$(Encode-PathSegment $ProjectId)/export-preview/$(Encode-PathSegment $ExportBatchId)/generate-artifact" -Body $generateBody
    Write-Ok "Generated artifact URI: $($generated.exportFileUri)"
    Write-Ok "Generated artifact hash: $($generated.exportFileHash)"
}

Write-Step "Smoke run complete"
Write-Ok "No scheduler logic, automatic Project verification, or Project write-back was performed by this script."
