# order-service Service Notes

## Overview

`order-service` manages H-Smart purchase workflows between buyers and sellers.

Current scope:

- create pending orders for active products
- complete pending orders
- publish order completion events through RabbitMQ
- register with Eureka as `order-service`
- send traces to Zipkin and JSON logs to Logstash
- accept requests only from trusted internal callers through `X-Internal-Secret`

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8085`
- Database: `hsmart_order_db`
- Database engine: PostgreSQL
- Messaging: RabbitMQ
- Discovery: Eureka Client
- HTTP client: load-balanced Spring `RestClient`
- JVM memory: `-Xms256m -Xmx384m`
- Docker memory limit: `400m`

## Domain Model

### `orders`

Fields:

- `id`
- `buyerId`
- `sellerId`
- `productId`
- `amount`
- `status`
- `createdAt`
- `updatedAt`

Status values:

- `PENDING`
- `COMPLETED`
- `CANCELLED`

## HTTP Endpoints

- `GET /health`
- `POST /api/v1/orders`
- `POST /api/v1/orders/{id}/complete`
- `GET /api/v1/orders/{id}`

All order endpoints are protected at `api-gateway` and require JWT authentication.

## Create Order Flow

Request:

```bash
curl -X POST http://localhost:8000/api/v1/orders \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d "{\"productId\": 1}"
```

Behavior:

- `api-gateway` validates the JWT
- gateway forwards `X-User-Id` and `X-Internal-Secret`
- `order-service` reads `buyerId` from `X-User-Id`
- `order-service` calls `product-service` through load-balanced `RestClient`
- product must exist and have status `ACTIVE`
- order amount is copied from the current product price
- order is saved with status `PENDING`

## Complete Order Flow

Request:

```bash
curl -X POST http://localhost:8000/api/v1/orders/1/complete \
  -H "Authorization: Bearer <jwt>"
```

Behavior:

- only the buyer who created the order can complete it
- only `PENDING` orders can become `COMPLETED`
- after the transaction commits, `order-service` publishes:
  - exchange: `order.exchange`
  - routing key: `order.event.completed`
  - payload: `productId`

## Product Status Synchronization

`product-service` consumes `order.event.completed` from queue `order.product.update.queue`.

When the event is processed:

- product status changes to `SOLD`
- `product.event.updated` is published for `search-service`
- `product.event.sold` is published for `interaction-service` notifications

## Review Verification

`review-service` calls `GET /api/v1/orders/{id}` through load-balanced `RestClient` to verify review eligibility.

Review eligibility requires:

- order status is `COMPLETED`
- order `buyerId` matches the current reviewer

## Observability

Dashboards:

- Eureka: `http://localhost:8761`
- Zipkin: `http://localhost:9411`
- Kibana: `http://localhost:5601`

Logging and tracing:

- logs are in English
- `logback-spring.xml` sends JSON logs to Logstash at `LOGSTASH_HOST:LOGSTASH_PORT`
- log records include `traceId` and `spanId`
- Micrometer Tracing sends spans to Zipkin
- `management.tracing.sampling.probability=1.0` is used for development
- the order create flow should show gateway, order-service, and product-service spans in one Zipkin trace

## Docker Compose

Services:

- `h-smart-order-postgres-db`
- `h-smart-order-service`

Important environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `PRODUCT_SERVICE_BASE_URL`
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`
- `INTERNAL_SHARED_SECRET`
- `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT`
- `LOGSTASH_HOST`
- `LOGSTASH_PORT`

## Current Verification

- `mvn test` passed for `order-service`
- `mvn test` passed for updated `product-service`
- `mvn test` passed for updated `api-gateway`
- `docker compose config --quiet` passed with `INTERNAL_SHARED_SECRET` provided from the shell
- Docker build passed for `order-service`, `product-service`, and `api-gateway`
- Docker runtime smoke test passed:
  - direct internal `POST /api/v1/orders` with `X-Internal-Secret` created order `1` for product `5`
  - direct internal `POST /api/v1/orders/1/complete` completed the order
  - `order-service` published `order.event.completed`
  - `product-service` consumed the event from `order.product.update.queue`
  - product `5` changed to `SOLD`
  - `product-service` published `product.event.updated` and `product.event.sold`
  - RabbitMQ binding exists from `order.exchange` to `order.product.update.queue` with routing key `order.event.completed`
  - trace id observed across order-service and product-service logs: `69f8290adb06113ee6510f5a84d57c5e`
- Gateway and discovery verification passed:
  - `GET http://localhost:8000/health` returned `200`
  - unauthenticated `POST http://localhost:8000/api/v1/orders` returned `401`
  - Eureka registered `ORDER-SERVICE` as `UP` with instance id `order-service:8085`
