# H-Smart Live VPS Deploy Guide

This file is the short runbook for daily deployments to the current live VPS.
It intentionally excludes first-time server provisioning details.

## 1. Current Live Target

- SSH: `root@100.66.247.41`
- Tailscale hostname: `instance-20260601-031713.tail0e1958.ts.net`
- Public IP: `34.126.116.93`
- Live repo: `/home/thanh678x/h-smart`
- Compose file: `docker-compose-gcp.yml`
- Public app port: `8000`

Use the Tailscale IP by default:

```bash
ssh root@100.66.247.41
```

If Tailscale asks for browser approval, complete it and retry SSH.

## 2. What Runs On The VPS

- `api-gateway`
- `discovery-server`
- Spring Boot services
- PostgreSQL databases
- MongoDB
- Redis
- RabbitMQ
- Elasticsearch

Important:

- only port `8000` is published to the host
- `ai-service` is not part of `docker-compose-gcp.yml`
- internal databases and service ports must stay private

## 3. Fastest Deploy

### Option A: code already committed

Use this when the target code is already pushed to the branch used on the VPS.

```bash
ssh root@100.66.247.41
cd /home/thanh678x/h-smart
git pull --ff-only
docker compose -f docker-compose-gcp.yml config --quiet
docker compose -f docker-compose-gcp.yml up -d --build
docker compose -f docker-compose-gcp.yml ps
curl --fail http://127.0.0.1:8000/health
```

### Option B: local working tree not committed yet

Use this when the code exists only on the local machine and must be synced safely to the live VPS.

Step 1 on Windows from `D:\H-smart`:

```powershell
$ts = Get-Date -Format 'yyyyMMdd-HHmmss'
$list = "D:\H-smart\tmp\deploy-file-list-$ts.txt"
$archive = "D:\H-smart\tmp\h-smart-deploy-source-$ts.tar.gz"

git ls-files --cached --others --exclude-standard |
  Where-Object {
    $_ -and
    $_ -notmatch '/$' -and
    $_ -notmatch '^\.claude/' -and
    $_ -notmatch '^tmp/' -and
    $_ -notmatch '\.tar\.gz$'
  } | Set-Content -Encoding Ascii $list

tar -czf $archive -T $list
scp $archive root@100.66.247.41:/tmp/h-smart-deploy-source.tar.gz
```

Step 2 on the VPS:

```bash
ssh root@100.66.247.41 '
  set -e
  mkdir -p /home/thanh678x/deploy-backups
  ts=$(date +%Y%m%d-%H%M%S)
  tar -czf /home/thanh678x/deploy-backups/h-smart-repo-$ts.tar.gz \
    -C /home/thanh678x h-smart
  stage=/tmp/h-smart-sync-$ts
  rm -rf "$stage"
  mkdir -p "$stage"
  tar -xzf /tmp/h-smart-deploy-source.tar.gz -C "$stage"
  rsync -a --delete --exclude .env --exclude .git \
    "$stage"/ /home/thanh678x/h-smart/
  rm -rf "$stage"
  cd /home/thanh678x/h-smart
  docker compose -f docker-compose-gcp.yml config --quiet
  docker compose -f docker-compose-gcp.yml up -d --build
  docker compose -f docker-compose-gcp.yml ps
  curl --fail http://127.0.0.1:8000/health
'
```

This flow preserves:

- `/home/thanh678x/h-smart/.env`
- the VPS `.git` directory
- Docker volumes

Backups are stored under:

```bash
/home/thanh678x/deploy-backups
```

## 4. Fast Verification

Run this after every deploy:

```bash
ssh root@100.66.247.41 '
  cd /home/thanh678x/h-smart
  docker compose -f docker-compose-gcp.yml ps
  curl --fail http://127.0.0.1:8000/health
  docker compose -f docker-compose-gcp.yml logs --tail=80 \
    api-gateway product-service order-service user-service \
    review-service admin-service interaction-service search-service
'
```

The deploy is good when:

- `api-gateway` is `healthy`
- the main Spring Boot services are `healthy`
- `curl http://127.0.0.1:8000/health` returns `200`
- no service is stuck in a restart loop

## 5. Known Repair: review-service `updated_at`

If `review-service` logs an error like:

- `add column updated_at timestamp(6) not null`
- `column "updated_at" of relation "reviews" contains null values`

run this one-time repair:

```bash
docker exec h-smart-review-postgres-db \
  psql -U hsmart -d hsmart_review_db -v ON_ERROR_STOP=1 \
  -c 'ALTER TABLE reviews ADD COLUMN IF NOT EXISTS updated_at timestamp(6) without time zone;' \
  -c 'UPDATE reviews SET updated_at = created_at WHERE updated_at IS NULL;' \
  -c 'ALTER TABLE reviews ALTER COLUMN updated_at SET NOT NULL;'

cd /home/thanh678x/h-smart
docker compose -f docker-compose-gcp.yml restart review-service
docker compose -f docker-compose-gcp.yml ps review-service
```

Confirm the backfill:

```bash
docker exec h-smart-review-postgres-db \
  psql -U hsmart -d hsmart_review_db -t -A \
  -c 'select count(*) from reviews where updated_at is null;'
```

The final query must return `0`.

## 6. Useful Operations

Restart one service:

```bash
cd /home/thanh678x/h-smart
docker compose -f docker-compose-gcp.yml restart product-service
```

Rebuild one service:

```bash
cd /home/thanh678x/h-smart
docker compose -f docker-compose-gcp.yml up -d --build product-service
```

Follow logs:

```bash
cd /home/thanh678x/h-smart
docker compose -f docker-compose-gcp.yml logs -f --tail=200
```

Stop containers without deleting data:

```bash
cd /home/thanh678x/h-smart
docker compose -f docker-compose-gcp.yml down
```

Do not use `down -v` on production unless data loss is acceptable.

## 7. Minimal Safety Rules

- keep `.env` on the VPS private and unchanged unless you are intentionally rotating config
- publish only port `8000`
- do not expose PostgreSQL, MongoDB, Redis, RabbitMQ, or Eureka publicly
- back up before syncing an uncommitted working tree to the VPS
