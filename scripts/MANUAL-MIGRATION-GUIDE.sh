# H-Smart Manual Migration Guide
# Run these commands step-by-step on the NEW VPS

# ============================================
# STEP 1: Install Docker, Docker Compose, Nginx, Git
# ============================================
sudo apt-get update
sudo apt-get install -y docker.io docker-compose git nginx certbot python3-certbot-nginx curl
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker $USER

# Logout and login again for docker group to take effect
# Or run: newgrp docker

# ============================================
# STEP 2: Create directory structure
# ============================================
mkdir -p ~/h-smart ~/backups
sudo mkdir -p /var/www/h-smart-ui
sudo chown -R $USER:$USER /var/www/h-smart-ui

# ============================================
# STEP 3: Clone backend code
# ============================================
cd ~
git clone https://github.com/YOUR_USERNAME/H-smart.git h-smart
# Or if repo is private, you'll need to setup Git credentials first

# Alternative: Upload from local machine
# From your LOCAL machine (Windows), run:
# scp -r D:\H-smart hoangchithanh23072003@136.110.63.123:~/
# (exclude backups, volumes, node_modules, target folders)

# ============================================
# STEP 4: Setup .env file
# ============================================
cd ~/h-smart
nano .env

# Copy content from D:\H-smart\backups\2026-07-08_211818\.env.backup
# Replace ***REDACTED*** with real secrets from old VPS
# IMPORTANT: Update these values:
#   - AI_PROVIDER_API_KEY (your Gemini/OpenAI key)
#   - INTERNAL_SHARED_SECRET (generate new: openssl rand -hex 32)
#   - Database passwords
#   - GHTK_API_TOKEN
#   - VNPAY tokens
#   - MongoDB root password

# ============================================
# STEP 5: Start Docker services
# ============================================
cd ~/h-smart
docker compose -f docker-compose-gcp.yml up -d --build

# Wait for services to start (2-3 minutes)
sleep 120

# Check services status
docker compose -f docker-compose-gcp.yml ps

# Check API Gateway health
curl http://localhost:8000/health

# ============================================
# STEP 6: Restore databases (run from LOCAL machine)
# ============================================

# From Windows PowerShell on LOCAL machine:

# Upload database backups
scp D:\H-smart\backups\2026-07-08_211818\*-db.sql hoangchithanh23072003@136.110.63.123:~/backups/
scp D:\H-smart\backups\2026-07-08_211818\interaction-mongo.archive.gz hoangchithanh23072003@136.110.63.123:~/backups/
scp D:\H-smart\backups\2026-07-08_211818\*-uploads.tar.gz hoangchithanh23072003@136.110.63.123:~/backups/

# Then on VPS, restore databases:

# ============================================
# STEP 7: Restore PostgreSQL databases (on VPS)
# ============================================
cd ~/h-smart

# Restore each database
docker compose -f docker-compose-gcp.yml exec -T user-postgres-db psql -U hsmart < ~/backups/user-db.sql
docker compose -f docker-compose-gcp.yml exec -T product-postgres-db psql -U hsmart < ~/backups/product-db.sql
docker compose -f docker-compose-gcp.yml exec -T order-postgres-db psql -U hsmart < ~/backups/order-db.sql
docker compose -f docker-compose-gcp.yml exec -T review-postgres-db psql -U hsmart < ~/backups/review-db.sql
docker compose -f docker-compose-gcp.yml exec -T admin-postgres-db psql -U hsmart < ~/backups/admin-db.sql
docker compose -f docker-compose-gcp.yml exec -T payment-postgres-db psql -U hsmart < ~/backups/payment-db.sql

# ============================================
# STEP 8: Restore MongoDB (on VPS)
# ============================================
# Get MongoDB password from .env
MONGO_PASS=$(grep MONGO_ROOT_PASSWORD ~/h-smart/.env | cut -d= -f2)

docker compose -f docker-compose-gcp.yml exec -T interaction-mongo-db mongorestore \
  --username=hsmart \
  --password=$MONGO_PASS \
  --authenticationDatabase=admin \
  --archive --gzip < ~/backups/interaction-mongo.archive.gz

# ============================================
# STEP 9: Restore upload files (on VPS)
# ============================================
cd ~/h-smart

# Product uploads
docker compose -f docker-compose-gcp.yml exec -T product-service tar xzf - -C /app/uploads < ~/backups/product-uploads.tar.gz

# Order uploads
docker compose -f docker-compose-gcp.yml exec -T order-service tar xzf - -C /app/uploads < ~/backups/order-uploads.tar.gz

# ============================================
# STEP 10: Deploy Frontend
# ============================================

# From LOCAL machine (Windows), build frontend:
cd "D:\H-smart UI"

# Make sure .env.local has production values:
# VITE_API_BASE_URL=/api/v1
# VITE_USE_MOCK=false

npm run build

# Upload frontend dist to VPS:
scp -r dist/* hoangchithanh23072003@136.110.63.123:/var/www/h-smart-ui/

# ============================================
# STEP 11: Configure Nginx (on VPS)
# ============================================
sudo nano /etc/nginx/sites-available/hsmart

# Paste this configuration:
# ---------------------------------------------
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
# ---------------------------------------------

# Enable site and restart nginx
sudo ln -sf /etc/nginx/sites-available/hsmart /etc/nginx/sites-enabled/hsmart
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl restart nginx

# ============================================
# STEP 12: Verify everything works
# ============================================

# Test backend
curl http://localhost:8000/health

# Test nginx proxy
curl http://localhost/api/v1/products

# Test frontend
curl http://localhost/

# ============================================
# STEP 13: Setup HTTPS with Let's Encrypt
# ============================================
sudo certbot --nginx -d hsmart.thatcherdev.id.vn

# ============================================
# STEP 14: Update DNS
# ============================================
# In your domain provider (Cloudflare/GCP DNS):
# Update A record for hsmart.thatcherdev.id.vn
# Point to: 136.110.63.123

# Wait 5-10 minutes for DNS propagation

# ============================================
# STEP 15: Final verification
# ============================================
# After DNS propagates:
curl https://hsmart.thatcherdev.id.vn
curl https://hsmart.thatcherdev.id.vn/api/v1/products

# ============================================
# STEP 16: Shutdown old VPS (ONLY after testing)
# ============================================
# Test everything on new VPS for 1-2 days first
# Then shutdown old VPS to stop charges
