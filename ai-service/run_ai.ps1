$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Checked {
    param(
        [scriptblock]$Command,
        [string]$ErrorMessage
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw $ErrorMessage
    }
}

function Get-BasePython {
    $pyLauncher = Get-Command py -ErrorAction SilentlyContinue
    if ($pyLauncher) {
        return @("py", "-3")
    }

    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pythonCmd) {
        return @("python")
    }

    throw "Python was not found. Install Python 3 before running this script."
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModelsDir = Join-Path $ProjectRoot "models"
$VenvDir = Join-Path $ProjectRoot ".venv"
$VenvPython = Join-Path $VenvDir "Scripts\python.exe"
$RequirementsFile = Join-Path $ProjectRoot "requirements.txt"

Write-Step "Checking model artifacts"
$RequiredFiles = @(
    (Join-Path $ModelsDir "model.pth")
)

$MissingFiles = @()
foreach ($File in $RequiredFiles) {
    if (-not (Test-Path $File)) {
        $MissingFiles += $File
    }
}

if ($MissingFiles.Count -gt 0) {
    Write-Host "Missing required files in models/:" -ForegroundColor Red
    $MissingFiles | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    throw "Model artifacts are incomplete."
}

Write-Step "Creating virtual environment when needed"
if (-not (Test-Path $VenvPython)) {
    $BasePython = Get-BasePython
    if ($BasePython.Count -eq 2) {
        Invoke-Checked -Command { & $BasePython[0] $BasePython[1] -m venv $VenvDir } -ErrorMessage "Unable to create virtual environment."
    } else {
        Invoke-Checked -Command { & $BasePython[0] -m venv $VenvDir } -ErrorMessage "Unable to create virtual environment."
    }
}

Write-Step "Upgrading pip, setuptools, and wheel"
Invoke-Checked -Command { & $VenvPython -m pip install --upgrade pip setuptools wheel } -ErrorMessage "Unable to upgrade pip, setuptools, and wheel."

Write-Step "Installing dependencies from requirements.txt"
Invoke-Checked -Command { & $VenvPython -m pip install --no-build-isolation -r $RequirementsFile } -ErrorMessage "Unable to install dependencies from requirements.txt."

Write-Step "Starting ai-service"
Push-Location $ProjectRoot
try {
    & $VenvPython -m uvicorn app.main:app --host 0.0.0.0 --port 8000
} finally {
    Pop-Location
}
