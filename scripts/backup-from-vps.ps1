# H-Smart VPS Backup Script
# Backs up databases, volumes, and configs from GCP VPS to local machine

param(
    [string]$VpsHost = "root@100.66.247.41",
    [string]$VpsPath = "/home/thanh678x/h-smart",
    [string]$LocalBackupDir = "D:\H-smart\backups\$(Get-Date -Format 'yyyy-MM-dd_HHmmss')"
)

$ErrorActionPreference = "Stop"

Write-Host "=== H-Smart VPS Backup ===" -ForegroundColor Cyan
Write-Host "VPS: $VpsHost" -ForegroundColor Yellow
Write-Host "Local backup directory: $LocalBackupDir" -ForegroundColor Yellow
Write-Host ""

# Create local backup directory
New-Item -ItemType Directory -Force -Path $LocalBackupDir | Out-Null
Write-Host "[OK] Created backup directory" -ForegroundColor Green

# 1. Backup PostgreSQL databases using pg_dumpall
Write-Host "`n[1/5] Backing up PostgreSQL databases..." -ForegroundColor Cyan

$databases = @(
    @{name="user"; container="user-postgres-db"},
    @{name="product"; container="product-postgres-db"},
    @{name="order"; container="order-postgres-db"},
    @{name="review"; container="review-postgres-db"},
    @{name="admin"; container="admin-postgres-db"},
    @{name="payment"; container="payment-postgres-db"}
)

foreach ($db in $databases) {
    Write-Host "  Backing up $($db.name)-service database..." -NoNewline

    $remoteCmd = "cd $VpsPath; docker compose -f docker-compose-gcp.yml exec -T $($db.container) pg_dumpall -U hsmart"
    $localFile = Join-Path $LocalBackupDir "$($db.name)-db.sql"

    ssh $VpsHost $remoteCmd | Out-File -FilePath $localFile -Encoding UTF8

    if (Test-Path $localFile) {
        $sizeKB = [math]::Round((Get-Item $localFile).Length / 1KB, 2)
        Write-Host " OK - $sizeKB KB" -ForegroundColor Green
    } else {
        Write-Host " FAILED" -ForegroundColor Red
    }
}

# 2. Backup MongoDB using mongodump with auth
Write-Host "`n[2/5] Backing up MongoDB..." -ForegroundColor Cyan
Write-Host "  Backing up interaction-service MongoDB..." -NoNewline

# Get MongoDB credentials from .env
$mongoUser = ssh $VpsHost "cd $VpsPath; grep MONGO_INITDB_ROOT_USERNAME .env | cut -d= -f2"
$mongoPass = ssh $VpsHost "cd $VpsPath; grep MONGO_INITDB_ROOT_PASSWORD .env | cut -d= -f2"

if ($mongoUser -and $mongoPass) {
    $mongoBackupCmd = "cd $VpsPath; docker compose -f docker-compose-gcp.yml exec -T interaction-mongo-db mongodump --username=$mongoUser --password=$mongoPass --authenticationDatabase=admin --archive --gzip --db=hsmart_interaction_db"
    $mongoFile = Join-Path $LocalBackupDir "interaction-mongo.archive.gz"

    # Save to file directly via redirection
    Invoke-Expression "ssh $VpsHost `"$mongoBackupCmd`" > `"$mongoFile`""

    if (Test-Path $mongoFile) {
        $sizeKB = [math]::Round((Get-Item $mongoFile).Length / 1KB, 2)
        Write-Host " OK - $sizeKB KB" -ForegroundColor Green
    } else {
        Write-Host " FAILED" -ForegroundColor Red
    }
} else {
    Write-Host " SKIPPED (no credentials)" -ForegroundColor Yellow
}

# 3. Backup Docker volumes (product uploads, order uploads)
Write-Host "`n[3/5] Backing up Docker volumes..." -ForegroundColor Cyan

$volumes = @(
    @{name="product-uploads"; path="/app/uploads"; service="product-service"},
    @{name="order-uploads"; path="/app/uploads"; service="order-service"}
)

foreach ($vol in $volumes) {
    Write-Host "  Backing up $($vol.name)..." -NoNewline

    $tarCmd = "cd $VpsPath; docker compose -f docker-compose-gcp.yml exec -T $($vol.service) tar czf - -C $($vol.path) ."
    $localFile = Join-Path $LocalBackupDir "$($vol.name).tar.gz"

    # Save to file directly
    Invoke-Expression "ssh $VpsHost `"$tarCmd`" > `"$localFile`""

    if (Test-Path $localFile) {
        $sizeKB = [math]::Round((Get-Item $localFile).Length / 1KB, 2)
        Write-Host " OK - $sizeKB KB" -ForegroundColor Green
    } else {
        Write-Host " FAILED" -ForegroundColor Red
    }
}

# 4. Backup .env file (sanitized - secrets masked)
Write-Host "`n[4/5] Backing up environment config..." -ForegroundColor Cyan
Write-Host "  Backing up .env file (sanitized)..." -NoNewline

$envCmd = "cd $VpsPath; cat .env"
$envContent = ssh $VpsHost $envCmd

if ($envContent) {
    # Sanitize secrets
    $sanitized = $envContent -replace '(API_KEY|SECRET|PASSWORD|TOKEN)=.+', '$1=***REDACTED***'
    $envFile = Join-Path $LocalBackupDir ".env.backup"
    $sanitized | Out-File -FilePath $envFile -Encoding UTF8
    Write-Host " OK" -ForegroundColor Green
} else {
    Write-Host " FAILED" -ForegroundColor Red
}

# 5. Backup Elasticsearch indices
Write-Host "`n[5/5] Backing up Elasticsearch indices..." -ForegroundColor Cyan
Write-Host "  Backing up products_index..." -NoNewline

$esCmd = "cd $VpsPath; docker compose -f docker-compose-gcp.yml exec -T elasticsearch curl -s -X GET 'http://localhost:9200/products_index/_search?size=10000' -H 'Content-Type: application/json'"
$esFile = Join-Path $LocalBackupDir "products_index.json"

ssh $VpsHost $esCmd | Out-File -FilePath $esFile -Encoding UTF8

if (Test-Path $esFile) {
    Write-Host " OK" -ForegroundColor Green
} else {
    Write-Host " FAILED" -ForegroundColor Red
}

Write-Host "  Backing up hsmart-policy-index..." -NoNewline

$policyCmd = "cd $VpsPath; docker compose -f docker-compose-gcp.yml exec -T elasticsearch curl -s -X GET 'http://localhost:9200/hsmart-policy-index/_search?size=10000' -H 'Content-Type: application/json'"
$policyFile = Join-Path $LocalBackupDir "hsmart-policy-index.json"

ssh $VpsHost $policyCmd | Out-File -FilePath $policyFile -Encoding UTF8

if (Test-Path $policyFile) {
    Write-Host " OK" -ForegroundColor Green
} else {
    Write-Host " FAILED" -ForegroundColor Red
}

# Summary
Write-Host "`n=== Backup Complete ===" -ForegroundColor Green
Write-Host "Backup location: $LocalBackupDir" -ForegroundColor Yellow
Write-Host ""
Write-Host "Backed up:" -ForegroundColor Cyan
Write-Host "  - 6 PostgreSQL databases" -ForegroundColor White
Write-Host "  - 1 MongoDB database" -ForegroundColor White
Write-Host "  - 2 Docker volumes (uploads)" -ForegroundColor White
Write-Host "  - Environment config" -ForegroundColor White
Write-Host "  - 2 Elasticsearch indices" -ForegroundColor White
Write-Host ""

# List files
Write-Host "Files:" -ForegroundColor Cyan
Get-ChildItem $LocalBackupDir | ForEach-Object {
    $sizeKB = [math]::Round($_.Length / 1KB, 2)
    Write-Host "  - $($_.Name) - $sizeKB KB" -ForegroundColor White
}

Write-Host "`nNext steps:" -ForegroundColor Yellow
Write-Host "  1. Review the backup files in $LocalBackupDir"
Write-Host "  2. Keep the backup safe before migrating to new VPS"
Write-Host "  3. When ready, use restore-to-new-vps.ps1 to deploy to new VPS"
