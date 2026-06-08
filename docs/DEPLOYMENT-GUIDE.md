# H-Smart GCP Deployment Guide

## 1. Deployment Architecture

The first GCP deployment uses a hybrid architecture:

- GCP VPS:
  - API Gateway
  - Eureka discovery server
  - Spring Boot microservices
  - PostgreSQL databases
  - MongoDB
  - Redis
  - RabbitMQ
  - Elasticsearch
- Local laptop:
  - FastAPI `ai-service`
  - Tailscale provides the private network path from GCP to the laptop

The GCP Compose file does not include:

- `ai-service`
- Logstash
- Kibana
- Zipkin
- legacy `backend-service`

Only TCP port `8000` is published by Docker. All databases, brokers, Eureka, and microservices remain reachable only through the internal Docker bridge network.

## 2. Search Infrastructure

Elasticsearch runs as a private, single-node search datastore for:

- product indexing and search through `search-service`
- POLICY document retrieval through `interaction-service`

The container has a `2 GiB` memory limit and a `1 GiB` JVM heap. Port `9200` is not published to the VPS host.

## 3. Prepare the GCP VPS

Recommended operating system:

- Ubuntu 24.04 LTS or another Ubuntu version supported by Docker Engine

Recommended GCP firewall ingress rules:

- TCP `22` from the administrator's trusted IP only
- TCP `8000` from the required client networks

Do not create public GCP firewall rules for:

- PostgreSQL ports
- MongoDB `27017`
- Redis `6379`
- RabbitMQ `5672` or `15672`
- Eureka `8761`
- service ports `8081` through `8087`

Update the VPS:

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y ca-certificates curl git
```

## 4. Install Docker Engine and Docker Compose

Remove conflicting packages if this is a new server:

```bash
for pkg in docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc; do
  sudo apt-get remove -y "$pkg"
done
```

Add Docker's official apt repository:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin
```

Enable Docker:

```bash
sudo systemctl enable --now docker
sudo docker run --rm hello-world
sudo docker compose version
```

Optional non-root Docker access:

```bash
sudo usermod -aG docker "$USER"
newgrp docker
docker version
```

Docker group membership grants root-level privileges. Only add trusted VPS users.

Official references:

- https://docs.docker.com/engine/install/ubuntu/
- https://docs.docker.com/engine/install/linux-postinstall/

## 5. Connect the VPS and Laptop with Tailscale

Install Tailscale on the GCP VPS:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
tailscale ip -4
```

Install Tailscale on the laptop and sign in to the same tailnet.

The laptop must:

- run `ai-service`
- publish host port `8002`
- allow inbound TCP `8002` on the Tailscale interface
- remain awake while GCP needs AI inference

Find the laptop Tailscale IPv4 address:

```bash
tailscale ip -4
```

Test from the GCP VPS:

```bash
curl --fail http://<IP-Tailscale-Laptop>:8002/health
```

Test from a temporary Docker container:

```bash
docker run --rm curlimages/curl:8.12.1 \
  --fail http://<IP-Tailscale-Laptop>:8002/health
```

The container-level check is important because `product-service` and `api-gateway` call the Tailscale address from inside Docker.

Official Tailscale reference:

- https://tailscale.com/docs/install/linux

## 6. Upload the Repository

Example:

```bash
sudo mkdir -p /opt/h-smart
sudo chown "$USER":"$USER" /opt/h-smart
git clone <H-SMART-GIT-URL> /opt/h-smart
cd /opt/h-smart
```

For later deployments:

```bash
cd /opt/h-smart
git pull --ff-only
```

## 7. Create the VPS Environment File

Create `/opt/h-smart/.env`:

```bash
cd /opt/h-smart
touch .env
chmod 600 .env
nano .env
```

Use URL-safe passwords for MongoDB because its credentials are embedded in a connection URI. Avoid `@`, `:`, `/`, `?`, and `#` unless the value is URL-encoded.

Example:

```dotenv
EXTERNAL_AI_SERVICE_URL=http://<IP-Tailscale-Laptop>:8002
PUBLIC_API_BASE_URL=http://<GCP-EXTERNAL-IP>:8000

INTERNAL_SHARED_SECRET=<GENERATE_A_LONG_RANDOM_SECRET>
JWT_SECRET=<GENERATE_A_BASE64_SECRET>
JWT_EXPIRATION_MS=86400000

POSTGRES_USER=hsmart
USER_DB_PASSWORD=<USER_DATABASE_PASSWORD>
PRODUCT_DB_PASSWORD=<PRODUCT_DATABASE_PASSWORD>
ORDER_DB_PASSWORD=<ORDER_DATABASE_PASSWORD>
REVIEW_DB_PASSWORD=<REVIEW_DATABASE_PASSWORD>
ADMIN_DB_PASSWORD=<ADMIN_DATABASE_PASSWORD>

MONGO_ROOT_USERNAME=hsmart
MONGO_ROOT_PASSWORD=<URL_SAFE_MONGO_PASSWORD>

RABBITMQ_USER=hsmart
RABBITMQ_PASSWORD=<RABBITMQ_PASSWORD>
REDIS_PASSWORD=<REDIS_PASSWORD>

AI_PROVIDER_URL=https://generativelanguage.googleapis.com/v1beta/openai
AI_PROVIDER_API_KEY=<AI_PROVIDER_API_KEY>
AI_PROVIDER_MODEL=<AI_PROVIDER_MODEL>

MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<SMTP_ACCOUNT_EMAIL>
MAIL_PASSWORD=<SMTP_APP_PASSWORD>
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
MAIL_FROM=<SMTP_ACCOUNT_EMAIL>
FRONTEND_BASE_URL=https://<FRONTEND_DOMAIN>
EMAIL_VERIFICATION_TOKEN_MINUTES=1440
PASSWORD_RESET_TOKEN_MINUTES=30

GHTK_API_URL=https://services.giaohangtietkiem.vn
GHTK_API_TOKEN=
GHTK_CLIENT_SOURCE=
GHTK_WEBHOOK_HASH=
```

Generate secrets:

```bash
openssl rand -base64 48
openssl rand -hex 32
```

Do not commit `.env`.

For Gmail SMTP, enable two-step verification and create a Google App Password. Do not use the normal Google account password. `FRONTEND_BASE_URL` must point to the deployed frontend that handles `/verify-email` and `/reset-password`.

The GCP Compose file requires SMTP credentials because newly registered accounts cannot log in until their email address is verified.

## 8. Validate the Deployment Configuration

Validate interpolation and YAML:

```bash
cd /opt/h-smart
docker compose -f docker-compose-gcp.yml config --quiet
```

Review the service list:

```bash
docker compose -f docker-compose-gcp.yml config --services
```

Confirm that only port `8000` is published:

```bash
docker compose -f docker-compose-gcp.yml config \
  | grep -A 5 -B 2 "published:"
```

## 9. Build and Start H-Smart

Configure the Linux virtual memory requirement used by Elasticsearch:

```bash
echo 'vm.max_map_count=262144' \
  | sudo tee /etc/sysctl.d/99-elasticsearch.conf
sudo /sbin/sysctl --system
sudo /sbin/sysctl vm.max_map_count
```

The final command must report at least `262144`.

Build images:

```bash
docker compose -f docker-compose-gcp.yml build
```

Start the deployment:

```bash
docker compose -f docker-compose-gcp.yml up -d
```

The equivalent one-command deployment is:

```bash
docker compose -f docker-compose-gcp.yml up -d --build
```

## 10. Verify the Deployment

Check container state:

```bash
docker compose -f docker-compose-gcp.yml ps
```

Check API Gateway:

```bash
curl --fail http://localhost:8000/health
curl --fail http://<GCP-EXTERNAL-IP>:8000/health
```

Inspect logs:

```bash
docker compose -f docker-compose-gcp.yml logs --tail=200 api-gateway
docker compose -f docker-compose-gcp.yml logs --tail=200 product-service
docker compose -f docker-compose-gcp.yml logs --tail=200 admin-service
docker compose -f docker-compose-gcp.yml logs --tail=200 elasticsearch search-service
```

Verify the AI connection from inside `product-service`:

```bash
docker compose -f docker-compose-gcp.yml exec product-service \
  sh -c 'curl --fail "$AI_SERVICE_BASE_URL/health"'
```

Verify that internal services are not published:

```bash
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

Only `h-smart-api-gateway` should show a host mapping.

Verify Elasticsearch from its private Docker network:

```bash
docker compose -f docker-compose-gcp.yml exec elasticsearch \
  curl --fail http://localhost:9200/_cluster/health
```

Elasticsearch is intentionally not published on the VPS host.

## 11. Operations

Restart one service:

```bash
docker compose -f docker-compose-gcp.yml restart product-service
```

Rebuild one service:

```bash
docker compose -f docker-compose-gcp.yml up -d --build product-service
```

Follow logs:

```bash
docker compose -f docker-compose-gcp.yml logs -f --tail=200
```

Stop containers without deleting data:

```bash
docker compose -f docker-compose-gcp.yml down
```

Never use `down -v` on production unless all persistent database and upload data may be deleted.

## 12. Backup Priorities

Back up these named volumes:

- `h-smart-gcp_user-postgres-data`
- `h-smart-gcp_product-postgres-data`
- `h-smart-gcp_order-postgres-data`
- `h-smart-gcp_review-postgres-data`
- `h-smart-gcp_admin-postgres-data`
- `h-smart-gcp_interaction-mongo-data`
- `h-smart-gcp_rabbitmq-data`
- `h-smart-gcp_redis-data`
- `h-smart-gcp_elasticsearch-data`
- `h-smart-gcp_product-uploads`

Database-native backups with `pg_dump` and `mongodump` are preferred over copying live volume files.

## 13. Security Checklist

- Restrict SSH to trusted source IPs.
- Publish only API Gateway port `8000`.
- Keep `.env` permissions at `600`.
- Use unique passwords for each database.
- Rotate any API key previously shared in chat or logs.
- Restrict laptop AI access with Tailscale ACLs.
- Do not expose the RabbitMQ management UI publicly.
- Do not expose Eureka publicly.
- Keep Docker Engine and Ubuntu security updates current.
- Put HTTPS in front of port `8000` before production user traffic.
