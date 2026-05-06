# admin-service Notes

## Overview

`admin-service` is the H-Smart marketplace administration service, also documented in the project log as the "Mắt thần quản trị" service.

Current scope:

- automatic product moderation from RabbitMQ product creation events
- admin notifications for products that require manual review
- dashboard overview analytics for marketplace operations
- admin-only access through `api-gateway`
- Eureka registration, Zipkin tracing, and Logstash JSON logging

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8087`
- Database: `hsmart_admin_db`
- Database engine: PostgreSQL
- Messaging: RabbitMQ
- Discovery: Eureka Client
- HTTP client: load-balanced Spring `RestClient`
- JVM memory: `-Xms256m -Xmx384m`
- Docker memory limit: `400m`

## Database

Owned database:

- `hsmart_admin_db`

Main table:

- `admin_notifications`

Notification fields:

- `id`
- `productId`
- `title`
- `type`
- `message`
- `createdAt`

## Auto-Moderation

`admin-service` listens to product creation events:

- exchange: `product.exchange`
- queue: `admin.product.moderation.queue`
- routing key: `product.event.created`

Consumed payload:

- `id`
- `title`
- `categoryName`
- `sellerId`
- `status`
- `aiMetadata`

Decision rules:

- if the highest-confidence AI label matches the selected category and confidence is at least `0.6`, the product is set to `APPROVED`
- if AI confidence is below `0.6`, no label is detected, the selected category is missing, or the label does not match, the product is set to `PENDING_REVIEW`
- pending review decisions create an `admin_notifications` row
- auto-rejected cases are logged in English with the product id and reason

Product status updates are sent to `product-service` through load-balanced `RestClient`:

- `PUT /api/v1/products/internal/{id}/moderation-status`
- header: `X-Internal-Secret`

## Analytics API

Endpoint:

- `GET /api/v1/admin/stats/overview`

Gateway access:

- JWT is required
- JWT claim `role` must be `ADMIN`
- gateway forwards `X-User-Id`, `X-User-Role`, and `X-Internal-Secret`
- `admin-service` validates `X-User-Role=ADMIN` again before serving `/api/v1/admin/**`

Response format:

```json
{
  "status": 200,
  "message": "Admin overview stats fetched successfully",
  "data": {
    "totalUsers": 10,
    "totalSellingProducts": 25,
    "totalCompletedRevenue": 1500000.00
  }
}
```

Downstream stats sources:

- `user-service`: `GET /api/v1/users/internal/stats`
- `product-service`: `GET /api/v1/products/internal/stats`
- `order-service`: `GET /api/v1/orders/internal/stats`

All downstream calls use:

- load-balanced `RestClient`
- `X-Internal-Secret`
- Zipkin trace propagation

## Docker Compose

Services:

- `h-smart-admin-postgres-db`
- `h-smart-admin-service`

Important environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `PRODUCT_SERVICE_BASE_URL`
- `USER_SERVICE_BASE_URL`
- `ORDER_SERVICE_BASE_URL`
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`
- `INTERNAL_SHARED_SECRET`
- `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT`
- `LOGSTASH_HOST`
- `LOGSTASH_PORT`

Dashboards:

- Eureka: `http://localhost:8761`
- Zipkin: `http://localhost:9411`
- Kibana: `http://localhost:5601`

## Test with curl

Admin stats through gateway:

```bash
curl http://localhost:8000/api/v1/admin/stats/overview \
  -H "Authorization: Bearer <admin-jwt>"
```

Expected non-admin response:

```json
{
  "status": 403,
  "message": "Admin role is required",
  "data": null
}
```

Direct service health check:

```bash
curl http://localhost:8087/health \
  -H "X-Internal-Secret: <internal-shared-secret>"
```

## Observability

- logs are in English
- `logback-spring.xml` sends JSON logs to Logstash
- logs include `traceId` and `spanId`
- Micrometer Tracing sends spans to Zipkin
- dashboard requests should show `api-gateway`, `admin-service`, and the downstream stats services in one trace

## Current Verification

- `mvn test` passed for `admin-service`
- targeted tests passed for modified `api-gateway`, `product-service`, `order-service`, `user-service`, `interaction-service`, and `search-service`
- `docker compose config --quiet` passed with `INTERNAL_SHARED_SECRET` provided from the shell
