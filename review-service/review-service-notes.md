# review-service Service Notes

## Overview

`review-service` manages post-purchase reviews and publishes seller trust-score events.

Current scope:

- create reviews for completed orders
- expose public seller review reads
- verify completed orders through `order-service`
- publish review-created events through RabbitMQ
- register with Eureka as `review-service`
- send traces to Zipkin and JSON logs to Logstash
- accept HTTP requests only from trusted internal callers through `X-Internal-Secret`

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8086`
- Database: `hsmart_review_db`
- Database engine: PostgreSQL
- Messaging: RabbitMQ
- Discovery: Eureka Client
- HTTP client: load-balanced Spring `RestClient`
- JVM memory: `-Xms256m -Xmx384m`
- Docker memory limit: `400m`

## Domain Model

### `reviews`

Fields:

- `id`
- `orderId`
- `buyerId`
- `sellerId`
- `rating`
- `comment`
- `createdAt`

Rules:

- `rating` must be between `1` and `5`
- one order can be reviewed only once
- only the buyer of a completed order can create the review

## HTTP Endpoints

- `GET /health`
- `POST /api/v1/reviews`
- `GET /api/v1/reviews/sellers/{sellerId}`

Gateway behavior:

- `GET /api/v1/reviews/**` is public
- `POST /api/v1/reviews` requires JWT authentication

## Create Review Flow

```bash
curl -X POST http://localhost:8000/api/v1/reviews \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d "{\"orderId\": 1, \"rating\": 5, \"comment\": \"Smooth transaction.\"}"
```

Behavior:

- `api-gateway` validates the JWT
- gateway forwards `X-User-Id` and `X-Internal-Secret`
- `review-service` reads `buyerId` from `X-User-Id`
- `review-service` calls `order-service` through load-balanced `RestClient`
- the order must have status `COMPLETED`
- the order `buyerId` must match the current user
- review is saved to PostgreSQL
- after commit, `review.event.created` is published

## Public Seller Reviews

```bash
curl http://localhost:8000/api/v1/reviews/sellers/<sellerId>
```

Behavior:

- no JWT is required
- gateway still attaches `X-Internal-Secret` before forwarding to `review-service`
- reviews are returned newest first

## RabbitMQ Event

Published event:

- exchange: `review.exchange`
- routing key: `review.event.created`
- payload:
  - `sellerId`
  - `rating`

`user-service` consumes this event from:

- queue: `review.trust.update.queue`

## Trust Score

`review-service` does not calculate seller trust directly. It publishes immutable review facts and lets `user-service` update the seller profile.

Current `user-service` trust calculation:

- `trustScore = average(all received review ratings)`
- `reviewCount` increments with every processed review event

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

## Docker Compose

Services:

- `h-smart-review-postgres-db`
- `h-smart-review-service`

Important environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `ORDER_SERVICE_BASE_URL`
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`
- `INTERNAL_SHARED_SECRET`
- `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT`
- `LOGSTASH_HOST`
- `LOGSTASH_PORT`

## Current Verification

- `mvn test` passed for `review-service`
- `mvn test` passed for updated `order-service`
- `mvn test` passed for updated `user-service`
- `mvn test` passed for updated `api-gateway`
- `docker compose config --quiet` passed with `INTERNAL_SHARED_SECRET` provided from the shell
- Docker build passed for `review-service`, `order-service`, `user-service`, and `api-gateway`
- Docker runtime smoke test passed:
  - `GET /api/v1/reviews/sellers/productuser1775766067` returned `200` without JWT
  - unauthenticated `POST /api/v1/reviews` returned `401`
  - a JWT-authenticated buyer created order `2`
  - order `2` was completed
  - review `1` was created for order `2`
  - `review-service` published `review.event.created`
  - `user-service` consumed the event from `review.trust.update.queue`
  - seller `productuser1775766067` trust score became `5.00`
  - seller review count became `1`
  - observed trace id across review-service and user-service logs: `69f8349c6a811584e53be9a74ec0ab97`
  - Zipkin trace `69f8349c6a811584e53be9a74ec0ab97` contains `api-gateway`, `review-service`, and `user-service`
