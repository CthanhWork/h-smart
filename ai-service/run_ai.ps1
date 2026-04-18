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

    throw "Khong tim thay Python tren may. Hay cai Python 3 truoc khi chay script."
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModelsDir = Join-Path $ProjectRoot "models"
$VenvDir = Join-Path $ProjectRoot ".venv"
$VenvPython = Join-Path $VenvDir "Scripts\python.exe"
$RequirementsFile = Join-Path $ProjectRoot "requirements.txt"

Write-Step "Kiem tra artifact model"
$RequiredFiles = @(
    (Join-Path $ModelsDir "model.pth"),
    (Join-Path $ModelsDir "config_infer.yaml"),
    (Join-Path $ModelsDir "classes.json")
)

$MissingFiles = @()
foreach ($File in $RequiredFiles) {
    if (-not (Test-Path $File)) {
        $MissingFiles += $File
    }
}

if ($MissingFiles.Count -gt 0) {
    Write-Host "Thieu file trong models/:" -ForegroundColor Red
    $MissingFiles | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    throw "Dung lai vi artifact model chua day du."
}

Write-Step "Tao virtual environment neu chua co"
if (-not (Test-Path $VenvPython)) {
    $BasePython = Get-BasePython
    if ($BasePython.Count -eq 2) {
        Invoke-Checked -Command { & $BasePython[0] $BasePython[1] -m venv $VenvDir } -ErrorMessage "Khong tao duoc virtual environment."
    } else {
        Invoke-Checked -Command { & $BasePython[0] -m venv $VenvDir } -ErrorMessage "Khong tao duoc virtual environment."
    }
}

Write-Step "Nang cap pip, setuptools, wheel"
Invoke-Checked -Command { & $VenvPython -m pip install --upgrade pip setuptools wheel } -ErrorMessage "Khong nang cap duoc pip/setuptools/wheel."

Write-Step "Cai dependencies tu requirements.txt"
Invoke-Checked -Command { & $VenvPython -m pip install -r $RequirementsFile } -ErrorMessage "Khong cai duoc dependencies tu requirements.txt."

Write-Step "Cai detectron2"
Invoke-Checked -Command { & $VenvPython -m pip install --no-build-isolation --no-deps "git+https://github.com/facebookresearch/detectron2.git" } -ErrorMessage "Khong cai duoc detectron2."

Write-Step "Khoi chay ai-service"
Push-Location $ProjectRoot
try {
    & $VenvPython -m uvicorn app.main:app --host 0.0.0.0 --port 8000
} finally {
    Pop-Location
}
