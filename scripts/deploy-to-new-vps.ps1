# Deploy H-Smart to new VPS
# Run from D:\H-smart directory

$ErrorActionPreference = "Stop"

Write-Host "=== H-Smart Deploy to New VPS ===" -ForegroundColor Cyan

# 1. Create timestamped archive
$ts = Get-Date -Format 'yyyyMMdd-HHmmss'
$list = "D:\H-smart\tmp\deploy-file-list-$ts.txt"
$archive = "D:\H-smart\tmp\h-smart-deploy-source-$ts.tar.gz"

Write-Host "`n[1/5] Creating file list..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "D:\H-smart\tmp" | Out-Null

git ls-files --cached --others --exclude-standard |
  Where-Object {
    $_ -and
    $_ -notmatch '/$' -and
    $_ -notmatch '^\.claude/' -and
    $_ -notmatch '^tmp/' -and
    $_ -notmatch '^backups/' -and
    $_ -notmatch '\.tar\.gz$'
  } | Set-Content -Encoding Ascii $list

Write-Host "[2/5] Creating archive..." -ForegroundColor Yellow
tar -czf $archive -T $list

Write-Host "[3/5] Uploading to VPS..." -ForegroundColor Yellow
scp -i ~/.ssh/id_rsa_hsmart_new $archive hoangchithanh23072003@100.110.169.59:/tmp/h-smart-deploy-source.tar.gz

Write-Host "[4/5] Deploying on VPS..." -ForegroundColor Yellow
ssh -i ~/.ssh/id_rsa_hsmart_new hoangchithanh23072003@100.110.169.59 @'
  set -e

  # Backup
  mkdir -p /home/hoangchithanh23072003/deploy-backups
  ts=$(date +%Y%m%d-%H%M%S)
  tar -czf /home/hoangchithanh23072003/deploy-backups/h-smart-repo-$ts.tar.gz \
    -C /home/hoangchithanh23072003 h-smart

  # Extract to staging
  stage=/tmp/h-smart-sync-$ts
  rm -rf "$stage"
  mkdir -p "$stage"
  tar -xzf /tmp/h-smart-deploy-source.tar.gz -C "$stage"

  # Sync to live (preserve .env and .git)
  rsync -a --delete --exclude .env --exclude .git \
    "$stage"/ /home/hoangchithanh23072003/h-smart/
  rm -rf "$stage"

  # Update .env with PUBLIC_API_BASE_URL if not exists
  cd /home/hoangchithanh23072003/h-smart
  if ! grep -q "PUBLIC_API_BASE_URL" .env 2>/dev/null; then
    echo "" >> .env
    echo "# Public base URL (added $ts)" >> .env
    echo "PUBLIC_API_BASE_URL=https://hsmart.thatcherdev.id.vn" >> .env
    echo "âœ“ Added PUBLIC_API_BASE_URL to .env"
  else
    echo "âœ“ PUBLIC_API_BASE_URL already exists in .env"
  fi

  # Deploy
  docker compose -f docker-compose-gcp.yml config --quiet
  docker compose -f docker-compose-gcp.yml up -d --build
'@

Write-Host "[5/5] Verifying deployment..." -ForegroundColor Yellow
ssh -i ~/.ssh/id_rsa_hsmart_new hoangchithanh23072003@100.110.169.59 @'
  cd /home/hoangchithanh23072003/h-smart
  echo ""
  echo "=== Container Status ==="
  docker compose -f docker-compose-gcp.yml ps --format "table {{.Name}}\t{{.Status}}"

  echo ""
  echo "=== Gateway Health ==="
  sleep 5
  curl --fail http://127.0.0.1:8000/health || echo "FAILED"

  echo ""
  echo "=== Test Product API (check URLs) ==="
  curl -s http://127.0.0.1:8000/api/v1/products?page=0\&size=1 | grep -o '"imageUrl":"[^"]*"' | head -1
'@

Write-Host "`n=== Deploy Complete ===" -ForegroundColor Green
Write-Host "Backup saved on VPS: /home/hoangchithanh23072003/deploy-backups/" -ForegroundColor Gray
