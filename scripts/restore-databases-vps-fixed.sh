#!/bin/bash
# Auto restore databases from backup on VPS (Fixed version)

set -e

echo "=== H-Smart Database Restore Script ==="
echo ""

cd ~/h-smart

echo "[1/4] Creating PostgreSQL databases if not exist..."

databases=("user" "product" "order" "review" "admin" "payment")

for db in "${databases[@]}"; do
    echo "  Creating ${db}-db..."
    docker compose -f docker-compose-gcp.yml exec -T ${db}-postgres-db psql -U hsmart -d postgres -c "CREATE DATABASE hsmart;" 2>&1 | grep -v "ERROR.*already exists" || true
    echo "  ✓ Database ready"
done

echo ""
echo "[2/4] Restoring PostgreSQL databases..."

for db in "${databases[@]}"; do
    echo "  Restoring ${db}-db..."
    if [ -f ~/backups/${db}-db.sql ]; then
        docker compose -f docker-compose-gcp.yml exec -T ${db}-postgres-db psql -U hsmart -d hsmart < ~/backups/${db}-db.sql 2>&1 | grep -v "ERROR.*already exists" || true
        echo "  ✓ ${db}-db restored"
    else
        echo "  ✗ ${db}-db.sql not found"
    fi
done

echo ""
echo "[3/4] Restoring MongoDB..."
MONGO_PASS=$(grep MONGO_ROOT_PASSWORD ~/h-smart/.env | cut -d= -f2)

if [ -f ~/backups/interaction-mongo.archive.gz ]; then
    docker compose -f docker-compose-gcp.yml exec -T interaction-mongo-db mongorestore \
      --username=hsmart \
      --password=$MONGO_PASS \
      --authenticationDatabase=admin \
      --drop \
      --archive --gzip < ~/backups/interaction-mongo.archive.gz
    echo "  ✓ MongoDB restored"
else
    echo "  ✗ interaction-mongo.archive.gz not found"
fi

echo ""
echo "[4/4] Restoring upload volumes..."

# Extract uploads to temp directory then copy into container
if [ -f ~/backups/product-uploads.tar.gz ]; then
    echo "  Restoring product uploads..."
    mkdir -p ~/backups/temp-product-uploads
    tar xzf ~/backups/product-uploads.tar.gz -C ~/backups/temp-product-uploads
    docker cp ~/backups/temp-product-uploads/. $(docker compose -f docker-compose-gcp.yml ps -q product-service):/app/uploads/
    rm -rf ~/backups/temp-product-uploads
    echo "  ✓ Product uploads restored"
else
    echo "  ✗ product-uploads.tar.gz not found"
fi

if [ -f ~/backups/order-uploads.tar.gz ]; then
    echo "  Restoring order uploads..."
    mkdir -p ~/backups/temp-order-uploads
    tar xzf ~/backups/order-uploads.tar.gz -C ~/backups/temp-order-uploads
    docker cp ~/backups/temp-order-uploads/. $(docker compose -f docker-compose-gcp.yml ps -q order-service):/app/uploads/
    rm -rf ~/backups/temp-order-uploads
    echo "  ✓ Order uploads restored"
else
    echo "  ✗ order-uploads.tar.gz not found"
fi

echo ""
echo "=== Restore Complete! ==="
echo ""
echo "Verify:"
echo "  curl http://localhost:8000/api/v1/products | jq '.data | length'"
echo "  curl http://localhost:8000/api/v1/users | jq '.data | length'"
