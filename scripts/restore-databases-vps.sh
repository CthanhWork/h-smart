#!/bin/bash
# Auto restore databases from backup on VPS

set -e

echo "=== H-Smart Database Restore Script ==="
echo ""

# Check if backup directory exists
if [ ! -d ~/backups ]; then
    echo "Creating backups directory..."
    mkdir -p ~/backups
fi

# Check for database backups
echo "[1/3] Checking for database backups..."
if [ ! -f ~/backups/user-db.sql ]; then
    echo "Database backups not found in ~/backups/"
    echo "Syncing from local backup or VPS old..."

    # Try to find backup in deploy-backups
    if [ -d ~/backups/h-smart-backups ]; then
        echo "Found backups in deploy-backups, extracting..."
        # Will need to extract from tar.gz
    else
        echo "Please upload backup files to ~/backups/ first"
        exit 1
    fi
fi

cd ~/h-smart

echo ""
echo "[2/3] Restoring PostgreSQL databases..."

databases=("user" "product" "order" "review" "admin" "payment")

for db in "${databases[@]}"; do
    echo "  Restoring ${db}-db..."
    if [ -f ~/backups/${db}-db.sql ]; then
        docker compose -f docker-compose-gcp.yml exec -T ${db}-postgres-db psql -U hsmart < ~/backups/${db}-db.sql 2>&1 | grep -v "ERROR.*already exists" || true
        echo "  ✓ ${db}-db restored"
    else
        echo "  ✗ ${db}-db.sql not found"
    fi
done

echo ""
echo "[3/3] Restoring MongoDB..."
MONGO_PASS=$(grep MONGO_ROOT_PASSWORD ~/h-smart/.env | cut -d= -f2)

if [ -f ~/backups/interaction-mongo.archive.gz ]; then
    docker compose -f docker-compose-gcp.yml exec -T interaction-mongo-db mongorestore \
      --username=hsmart \
      --password=$MONGO_PASS \
      --authenticationDatabase=admin \
      --archive --gzip < ~/backups/interaction-mongo.archive.gz
    echo "  ✓ MongoDB restored"
else
    echo "  ✗ interaction-mongo.archive.gz not found"
fi

echo ""
echo "[4/4] Restoring upload volumes..."

if [ -f ~/backups/product-uploads.tar.gz ]; then
    echo "  Restoring product uploads..."
    docker compose -f docker-compose-gcp.yml exec -T product-service tar xzf - -C /app/uploads < ~/backups/product-uploads.tar.gz
    echo "  ✓ Product uploads restored"
else
    echo "  ✗ product-uploads.tar.gz not found"
fi

if [ -f ~/backups/order-uploads.tar.gz ]; then
    echo "  Restoring order uploads..."
    docker compose -f docker-compose-gcp.yml exec -T order-service tar xzf - -C /app/uploads < ~/backups/order-uploads.tar.gz
    echo "  ✓ Order uploads restored"
else
    echo "  ✗ order-uploads.tar.gz not found"
fi

echo ""
echo "=== Restore Complete! ==="
echo ""
echo "Verify:"
echo "  curl http://localhost:8000/api/v1/products"
echo "  curl http://localhost:8000/api/v1/users"
