# H-Smart Migration to New VPS
# Migrates Backend + Frontend + Nginx setup to new GCP VPS

param(
    [string]$NewVpsIp = "136.110.63.123",
    [string]$NewVpsUser = "hoangchithanh23072003",
    [string]$NewVpsPassword = "Thanhabc123@@",
    [string]$BackupDir = "D:\H-smart\backups\2026-07-08_211818",
    [string]$FrontendDir = "D:\H-smart UI",
    [string]$BackendDir = "D:\H-smart"
)

$ErrorActionPreference = "Continue"

Write-Host "=== H-Smart Migration to New VPS ===" -ForegroundColor Cyan
Write-Host "Target VPS: $NewVpsUser@$NewVpsIp" -ForegroundColor Yellow
Write-Host ""

# Function to run SSH command with password
function Invoke-SshCommand {
    param(
        [string]$Command,
        [string]$Description
    )
    Write-Host "  $Description..." -NoNewline

    # Using sshpass alternative - will prompt for password first time
    $result = ssh "$NewVpsUser@$NewVpsIp" $Command 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Host " OK" -ForegroundColor Green
        return $result
    } else {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host "    Error: $result" -ForegroundColor Red
        return $null
    }
}

Write-Host "[1/8] Testing SSH connection..." -ForegroundColor Cyan
Invoke-SshCommand "echo 'Connection successful'" "Test connection"

Write-Host "`n[2/8] Installing dependencies on new VPS..." -ForegroundColor Cyan
Invoke-SshCommand "sudo apt-get update -qq" "Update package list"
Invoke-SshCommand "sudo apt-get install -y docker.io docker-compose git nginx certbot python3-certbot-nginx curl" "Install Docker, Nginx, Git, Certbot"
Invoke-SshCommand "sudo systemctl enable docker" "Enable Docker service"
Invoke-SshCommand "sudo systemctl start docker" "Start Docker service"
Invoke-SshCommand "sudo usermod -aG docker $NewVpsUser" "Add user to docker group"

Write-Host "`n[3/8] Setting up directory structure..." -ForegroundColor Cyan
Invoke-SshCommand "mkdir -p ~/h-smart ~/backups /tmp/h-smart-migration" "Create directories"
Invoke-SshCommand "sudo mkdir -p /var/www/h-smart-ui" "Create web root"
Invoke-SshCommand "sudo chown -R $NewVpsUser`:$NewVpsUser /var/www/h-smart-ui" "Set web root permissions"

Write-Host "`n[4/8] Uploading backend code and backups..." -ForegroundColor Cyan
Write-Host "  This may take a while (558 MB backup + code)..." -ForegroundColor Yellow

# Upload backup files
Write-Host "  Uploading database backups..." -NoNewline
scp -r "$BackupDir\*.sql" "$NewVpsUser@$NewVpsIp`:~/backups/" 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host " OK" -ForegroundColor Green } else { Write-Host " FAILED" -ForegroundColor Red }

Write-Host "  Uploading MongoDB backup..." -NoNewline
scp "$BackupDir\interaction-mongo.archive.gz" "$NewVpsUser@$NewVpsIp`:~/backups/" 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host " OK" -ForegroundColor Green } else { Write-Host " FAILED" -ForegroundColor Red }

Write-Host "  Uploading volume backups..." -NoNewline
scp "$BackupDir\*.tar.gz" "$NewVpsUser@$NewVpsIp`:~/backups/" 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host " OK" -ForegroundColor Green } else { Write-Host " FAILED" -ForegroundColor Red }

# Upload backend code (excluding node_modules, target, volumes)
Write-Host "  Uploading backend code..." -NoNewline
$excludePatterns = "--exclude=node_modules --exclude=target --exclude=.git --exclude=backups --exclude=volumes"
bash -c "rsync -avz $excludePatterns '$BackendDir/' '$NewVpsUser@$NewVpsIp`:~/h-smart/'" 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host " OK" -ForegroundColor Green } else { Write-Host " FAILED" -ForegroundColor Red }

Write-Host "`n[5/8] Building and uploading frontend..." -ForegroundColor Cyan
Push-Location $FrontendDir

# Build frontend with production config
Write-Host "  Building frontend..." -NoNewline
$env:VITE_API_BASE_URL = "/api/v1"
$env:VITE_USE_MOCK = "false"
npm run build 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host " OK" -ForegroundColor Green } else { Write-Host " FAILED" -ForegroundColor Red }

# Upload frontend build
Write-Host "  Uploading frontend dist..." -NoNewline
scp -r dist/* "$NewVpsUser@$NewVpsIp`:/var/www/h-smart-ui/" 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host " OK" -ForegroundColor Green } else { Write-Host " FAILED" -ForegroundColor Red }

Pop-Location

Write-Host "`n[6/8] Restoring databases on new VPS..." -ForegroundColor Cyan

$restoreScript = @'
cd ~/h-smart
# Wait for databases to be ready
echo "Waiting for databases to start..."
sleep 30

# Restore PostgreSQL databases
for db in user product order review admin payment; do
    echo "Restoring ${db}-db..."
    docker compose -f docker-compose-gcp.yml exec -T ${db}-postgres-db psql -U hsmart < ~/backups/${db}-db.sql
done

# Restore MongoDB
echo "Restoring MongoDB..."
docker compose -f docker-compose-gcp.yml exec -T interaction-mongo-db mongorestore --username=hsmart --password=$MONGO_ROOT_PASSWORD --authenticationDatabase=admin --archive --gzip < ~/backups/interaction-mongo.archive.gz

# Restore volumes
echo "Restoring product uploads..."
docker compose -f docker-compose-gcp.yml exec -T product-service tar xzf - -C /app/uploads < ~/backups/product-uploads.tar.gz

echo "Restoring order uploads..."
docker compose -f docker-compose-gcp.yml exec -T order-service tar xzf - -C /app/uploads < ~/backups/order-uploads.tar.gz

echo "Database restore complete!"
'@

$restoreScript | Out-File -FilePath "$env:TEMP\restore-script.sh" -Encoding UTF8
scp "$env:TEMP\restore-script.sh" "$NewVpsUser@$NewVpsIp`:~/restore-databases.sh" 2>&1 | Out-Null
Invoke-SshCommand "chmod +x ~/restore-databases.sh" "Make restore script executable"

Write-Host "`n[7/8] Configuring Nginx reverse proxy..." -ForegroundColor Cyan

$nginxConfig = @'
server {
    listen 80;
    server_name hsmart.thatcherdev.id.vn;
    client_max_body_size 25m;

    # Frontend static files
    root /var/www/h-smart-ui;
    index index.html;

    # SPA routing - serve index.html for all routes
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API reverse proxy to backend gateway
    location /api/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
}
'@

$nginxConfig | Out-File -FilePath "$env:TEMP\hsmart-nginx.conf" -Encoding UTF8
scp "$env:TEMP\hsmart-nginx.conf" "$NewVpsUser@$NewVpsIp`:/tmp/hsmart-nginx.conf" 2>&1 | Out-Null
Invoke-SshCommand "sudo mv /tmp/hsmart-nginx.conf /etc/nginx/sites-available/hsmart" "Install Nginx config"
Invoke-SshCommand "sudo ln -sf /etc/nginx/sites-available/hsmart /etc/nginx/sites-enabled/hsmart" "Enable Nginx site"
Invoke-SshCommand "sudo rm -f /etc/nginx/sites-enabled/default" "Remove default Nginx site"
Invoke-SshCommand "sudo nginx -t" "Test Nginx configuration"
Invoke-SshCommand "sudo systemctl restart nginx" "Restart Nginx"

Write-Host "`n[8/8] Final instructions..." -ForegroundColor Cyan
Write-Host ""
Write-Host "=== Migration Setup Complete! ===" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps (run manually on new VPS):" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. SSH to new VPS:" -ForegroundColor White
Write-Host "   ssh $NewVpsUser@$NewVpsIp" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Setup .env file:" -ForegroundColor White
Write-Host "   cd ~/h-smart" -ForegroundColor Gray
Write-Host "   nano .env  # Copy from backup .env.backup and add real secrets" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Start Docker services:" -ForegroundColor White
Write-Host "   docker compose -f docker-compose-gcp.yml up -d --build" -ForegroundColor Gray
Write-Host ""
Write-Host "4. Run database restore:" -ForegroundColor White
Write-Host "   ./restore-databases.sh" -ForegroundColor Gray
Write-Host ""
Write-Host "5. Verify health:" -ForegroundColor White
Write-Host "   curl http://localhost:8000/health" -ForegroundColor Gray
Write-Host "   curl http://localhost/api/v1/products" -ForegroundColor Gray
Write-Host ""
Write-Host "6. Setup HTTPS with Let's Encrypt:" -ForegroundColor White
Write-Host "   sudo certbot --nginx -d hsmart.thatcherdev.id.vn" -ForegroundColor Gray
Write-Host ""
Write-Host "7. Update DNS:" -ForegroundColor White
Write-Host "   Point hsmart.thatcherdev.id.vn A record to: $NewVpsIp" -ForegroundColor Gray
Write-Host ""
Write-Host "Password for new VPS: $NewVpsPassword" -ForegroundColor Yellow
