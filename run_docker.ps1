$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-ComposeCommand {
    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCmd) {
        try {
            docker compose version | Out-Null
            return @("docker", "compose")
        } catch {
        }
    }

    $dockerComposeCmd = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($dockerComposeCmd) {
        return @("docker-compose")
    }

    throw "Docker Compose was not found. Install Docker Desktop or docker-compose before running this script."
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
$ModelsDir = Join-Path $ProjectRoot "ai-service\\models"

$RequiredFiles = @(
    (Join-Path $ModelsDir "household_yolo26n_best.pt")
)

Write-Step "Checking Docker Compose"
$ComposeCommand = Get-ComposeCommand

Write-Step "Checking model artifacts"
$MissingFiles = @()
foreach ($File in $RequiredFiles) {
    if (-not (Test-Path $File)) {
        $MissingFiles += $File
    }
}

if ($MissingFiles.Count -gt 0) {
    Write-Host "Missing required files in ai-service/models:" -ForegroundColor Red
    $MissingFiles | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    throw "Model artifacts are incomplete."
}

Write-Step "Building Docker images"
if ($ComposeCommand.Count -eq 2) {
    & $ComposeCommand[0] $ComposeCommand[1] -f $ComposeFile build
} else {
    & $ComposeCommand[0] -f $ComposeFile build
}
if ($LASTEXITCODE -ne 0) {
    throw "Docker image build failed."
}

Write-Step "Starting Docker stack"
if ($ComposeCommand.Count -eq 2) {
    & $ComposeCommand[0] $ComposeCommand[1] -f $ComposeFile up
} else {
    & $ComposeCommand[0] -f $ComposeFile up
}
