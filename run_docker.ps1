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

    throw "Khong tim thay Docker Compose. Hay cai Docker Desktop hoac docker-compose truoc."
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
$ModelsDir = Join-Path $ProjectRoot "ai-service\\models"

$RequiredFiles = @(
    (Join-Path $ModelsDir "model.pth"),
    (Join-Path $ModelsDir "config_infer.yaml"),
    (Join-Path $ModelsDir "classes.json")
)

Write-Step "Kiem tra Docker Compose"
$ComposeCommand = Get-ComposeCommand

Write-Step "Kiem tra artifact model"
$MissingFiles = @()
foreach ($File in $RequiredFiles) {
    if (-not (Test-Path $File)) {
        $MissingFiles += $File
    }
}

if ($MissingFiles.Count -gt 0) {
    Write-Host "Thieu file trong ai-service/models:" -ForegroundColor Red
    $MissingFiles | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    throw "Dung lai vi artifact model chua day du."
}

Write-Step "Build image ai-service"
if ($ComposeCommand.Count -eq 2) {
    & $ComposeCommand[0] $ComposeCommand[1] -f $ComposeFile build
} else {
    & $ComposeCommand[0] -f $ComposeFile build
}
if ($LASTEXITCODE -ne 0) {
    throw "Build Docker image that bai."
}

Write-Step "Khoi chay stack Docker"
if ($ComposeCommand.Count -eq 2) {
    & $ComposeCommand[0] $ComposeCommand[1] -f $ComposeFile up
} else {
    & $ComposeCommand[0] -f $ComposeFile up
}
