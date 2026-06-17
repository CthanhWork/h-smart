param(
    [string]$Distribution = "Ubuntu",
    [string]$ContainerName = "h-smart-ai-service",
    [int]$HealthTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

$keepAliveCommand = @"
docker update --restart unless-stopped $ContainerName >/dev/null 2>&1
docker start $ContainerName >/dev/null 2>&1
while true; do sleep 300; done
"@

Start-Process `
    -FilePath "wsl.exe" `
    -ArgumentList @("-d", $Distribution, "--", "bash", "-lc", $keepAliveCommand) `
    -WindowStyle Hidden

$deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
do {
    Start-Sleep -Seconds 5
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8002/health" -TimeoutSec 5
        if ($health.status -eq "healthy" -and $health.model_loaded) {
            Write-Host "AI service is healthy on http://localhost:8002"
            exit 0
        }
    } catch {
        # The Detectron2 model can take several seconds to load on CPU.
    }
} while ((Get-Date) -lt $deadline)

throw "AI service did not become healthy within $HealthTimeoutSeconds seconds."
