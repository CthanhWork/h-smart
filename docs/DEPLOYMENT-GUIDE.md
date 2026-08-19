# H-Smart Live VPS Deploy Guide

This is the short runbook for daily deployments to the current live VPS.
It is meant for agents and humans who need to test and deploy quickly.

## 1. Current Live Target

- SSH user and host: `hoangchithanh23072003@100.110.169.59`
- Public IP: `100.110.169.59`
- Live repo: `/home/hoangchithanh23072003/h-smart`
- Live compose file: `docker-compose-gcp.yml`
- Public app port: `8000`

Preferred SSH command:

```bash
ssh -i ~/.ssh/id_rsa_hsmart_new hoangchithanh23072003@100.110.169.59
```

Windows PowerShell:

```powershell
ssh -i $env:USERPROFILE\.ssh\id_rsa_hsmart_new hoangchithanh23072003@100.110.169.59
```

Live `.env` must keep:

```env
PUBLIC_API_BASE_URL=https://hsmart.thatcherdev.id.vn
```

Notes:

- The old VPS (`103.145.63.51` / `100.66.247.41`) is no longer the active production target.
- The current live backend is on `100.110.169.59`.
- `ai-service` is not running inside the live VPS compose stack right now.

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
- `product-service` and `api-gateway` both consume `EXTERNAL_AI_SERVICE_URL`

## 3. Fastest Deploy

### Option A: code already committed

Use this when the target code is already pushed to the branch used on the VPS.

```bash
ssh -i ~/.ssh/id_rsa_hsmart_new hoangchithanh23072003@100.110.169.59
cd /home/hoangchithanh23072003/h-smart
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
scp -i ~/.ssh/id_rsa_hsmart_new $archive hoangchithanh23072003@100.110.169.59:/tmp/h-smart-deploy-source.tar.gz
```

Step 2 on the VPS:

```bash
ssh -i ~/.ssh/id_rsa_hsmart_new hoangchithanh23072003@100.110.169.59 '
  set -e
  mkdir -p /home/hoangchithanh23072003/deploy-backups
  ts=$(date +%Y%m%d-%H%M%S)
  tar -czf /home/hoangchithanh23072003/deploy-backups/h-smart-repo-$ts.tar.gz \
    -C /home/hoangchithanh23072003 h-smart
  stage=/tmp/h-smart-sync-$ts
  rm -rf "$stage"
  mkdir -p "$stage"
  tar -xzf /tmp/h-smart-deploy-source.tar.gz -C "$stage"
  rsync -a --delete --exclude .env --exclude .git \
    "$stage"/ /home/hoangchithanh23072003/h-smart/
  rm -rf "$stage"
  cd /home/hoangchithanh23072003/h-smart
  docker compose -f docker-compose-gcp.yml config --quiet
  docker compose -f docker-compose-gcp.yml up -d --build
  docker compose -f docker-compose-gcp.yml ps
  curl --fail http://127.0.0.1:8000/health
'
```

This flow preserves:

- `/home/hoangchithanh23072003/h-smart/.env`
- the VPS `.git` directory
- Docker volumes

Backups are stored under:

```bash
/home/hoangchithanh23072003/deploy-backups
```

## 4. Fast Verification

Run this after every deploy:

```bash
ssh -i ~/.ssh/id_rsa_hsmart_new hoangchithanh23072003@100.110.169.59 '
  cd /home/hoangchithanh23072003/h-smart
  docker compose -f docker-compose-gcp.yml ps
  curl --fail http://127.0.0.1:8000/health
  docker compose -f docker-compose-gcp.yml logs --tail=80 \
    api-gateway product-service order-service user-service \
    review-service admin-service interaction-service search-service payment-service
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

cd /home/hoangchithanh23072003/h-smart
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
cd /home/hoangchithanh23072003/h-smart
docker compose -f docker-compose-gcp.yml restart product-service
```

Rebuild one service:

```bash
cd /home/hoangchithanh23072003/h-smart
docker compose -f docker-compose-gcp.yml up -d --build product-service
```

Follow logs:

```bash
cd /home/hoangchithanh23072003/h-smart
docker compose -f docker-compose-gcp.yml logs -f --tail=200
```

Stop containers without deleting data:

```bash
cd /home/hoangchithanh23072003/h-smart
docker compose -f docker-compose-gcp.yml down
```

Do not use `down -v` on production unless data loss is acceptable.

## 7. Minimal Safety Rules

- keep `.env` on the VPS private and unchanged unless you are intentionally rotating config
- publish only port `8000`
- do not expose PostgreSQL, MongoDB, Redis, RabbitMQ, or Eureka publicly
- back up before syncing an uncommitted working tree to the VPS

## 8. AI Service Status

Current state as of 2026-07-09:

- `ai-service` now runs inside `docker-compose-gcp.yml` on the live VPS.
- Both `product-service` and `api-gateway` point to `http://ai-service:8000`.
- The service uses the model at `/app/models/household_yolo26n_best.pt`.
- Health check:
  - `GET http://ai-service:8000/health`
  - `GET http://127.0.0.1:8000/health` for the gateway

Useful checks:

```bash
docker compose -f docker-compose-gcp.yml ps ai-service
curl --fail http://ai-service:8000/health
curl --fail http://127.0.0.1:8000/health
```

If AI features break after a deploy:

- check `ai-service` logs first
- confirm the model file exists in `/home/hoangchithanh23072003/h-smart/ai-service/models/household_yolo26n_best.pt`
- confirm `product-service` and `api-gateway` were restarted after the stack update
- do not assume the old external AI endpoint is still usable
