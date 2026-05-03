# product-service Service Notes

## Overview

`product-service` is responsible for the product domain inside H-Smart.

Current business scope:

- household appliance categories
- product listing creation
- product listing update
- product listing soft delete
- product detail and paginated listing queries
- physical image storage under `uploads/`
- AI metadata persistence returned from `ai-service`
- ownership validation based on `X-User-Id` forwarded by `api-gateway`
- asynchronous product lifecycle events through RabbitMQ

This service does not validate JWT tokens directly. Authentication is handled by `api-gateway`. `product-service` trusts the forwarded identity header after the gateway has validated the token.

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8082`
- Database: `hsmart_product_db`
- Database engine: PostgreSQL
- Messaging: RabbitMQ
- ORM: Spring Data JPA + Hibernate 6
- API docs: `/swagger-ui.html`

## Architecture Rules Applied

The service follows the project rules defined in `agent.md`.

Implemented package structure:

- `domain.entities`
- `application.dto`
- `application.mapper`
- `application.exceptions`
- `service`
- `service.impl`
- `presentation.controllers`
- `infrastructure.persistence`
- `infrastructure.context`
- `infrastructure.config`
- `infrastructure.exception`

Layer responsibilities currently enforced:

- controllers only expose endpoints and wrap responses in `ApiResponse<T>`
- service implementations contain business rules and orchestration
- repositories stay inside `infrastructure.persistence`
- user identity is read from request context, not from JWT parsing inside this service

## Database Ownership

This microservice owns one dedicated database:

- database name: `hsmart_product_db`

There is no direct database dependency on `user-service`, `api-gateway`, or `ai-service`.

## Domain Model

### Category

Fields:

- `id`
- `name`

Business rules:

- category names must be unique
- duplicate creation returns `409 Conflict`

### Product

Fields:

- `id`
- `title`
- `description`
- `price`
- `status`
- `sellerId`
- `imageUrl`
- `aiMetadata`
- `category`
- `isDeleted`
- `createdAt`
- `updatedAt`

Status values:

- `ACTIVE`
- `SOLD`
- `HIDDEN`

Important rules:

- `sellerId` is taken from `X-User-Id`
- `aiMetadata` is stored as JSONB-compatible content
- `isDeleted = true` means the product is soft deleted
- `updatedAt` is refreshed when the record is modified
- all read APIs return only products where `isDeleted = false`

## Request Context and Ownership

Identity flow:

1. frontend sends JWT to `api-gateway`
2. `api-gateway` validates the JWT
3. `api-gateway` forwards `X-User-Id`
4. `UserContextFilter` stores `X-User-Id` in `UserContextHolder`
5. service logic reads the current user from `UserContextHolder`

Ownership validation is enforced for modifying operations.

Protected business rule:

- only the seller who created the product can update or delete it

Validation behavior:

- if `X-User-Id` is missing for a protected request, the service returns `401 Unauthorized`
- if `X-User-Id` does not match `sellerId`, the service returns `403 Forbidden`
- the ownership error message is:
  - `You do not have permission to modify this product`

## AI Integration

`VisionServiceImpl` integrates with `ai-service` through HTTP.

Downstream endpoint:

- `POST {AI_SERVICE_BASE_URL}/api/v1/predict`

Behavior:

- sends the uploaded image as multipart form data
- receives `numDetections` and `detections`
- serializes detections into JSON
- stores the JSON in `Product.aiMetadata`

Failure handling:

- AI connection failure returns `503 Service Unavailable`
- unreadable upload returns `400 Bad Request`

## RabbitMQ Product Events

`product-service` publishes product lifecycle events through RabbitMQ instead of calling `interaction-service` over REST.

Configured exchange:

- type: topic
- name: `product.exchange`

Published event:

- event DTO: `ProductSoldEvent`
- routing key: `product.event.sold`
- payload fields:
  - `productId`
  - `sellerId`
  - `title`

Publish behavior:

- `PUT /api/v1/products/{id}` checks the previous product status before applying updates
- an event is published only when the status transitions from a non-`SOLD` value to `SOLD`
- the event is registered after the database transaction commits
- RabbitMQ publishing uses JSON conversion and a simple retry policy
- if RabbitMQ is unavailable after retry attempts, the failure is logged in English and the product update remains committed

## Smart Naming

If `title` is empty during product creation:

1. the service calls `ai-service`
2. the service selects the detection with the highest score
3. the label is translated to a display title when known
4. if no detection is available, fallback title is used

Examples already mapped:

- `chair` -> `Ghe`
- `microwave_oven` -> `Lo vi song`
- `refrigerator` -> `Tu lanh`

Note:

- translation values are business labels for end users
- runtime API messages and logs remain in English

## Media Storage

Physical storage:

- directory: `uploads/`

Container path:

- `/app/uploads`

Public media endpoint:

- `GET /api/v1/products/media/{filename}`

Returned image URL format:

- `http://localhost:8000/api/v1/products/media/{filename}`

This depends on:

- `APP_PUBLIC_BASE_URL=http://localhost:8000`
- gateway route for `/api/v1/products/**`
- gateway whitelist for `/api/v1/products/media/**`

## DTO Inventory

Request DTOs:

- `CategoryRequestDTO`
- `ProductRequestDTO`

Response DTOs:

- `ApiResponse<T>`
- `CategoryResponseDTO`
- `DetectionDTO`
- `PageResponseDTO<T>`
- `PredictResponseDTO`
- `ProductResponseDTO`

## Persistence Layer

Repositories:

- `CategoryRepository`
- `ProductRepository`

Repository behavior currently in use:

- duplicate category detection by name
- paginated product reads
- active product lookup using `isDeleted = false`
- list queries that exclude soft deleted products

## Service Layer

### `CategoryService`

Responsibilities:

- create category
- list categories
- enforce unique category names

### `ProductService`

Responsibilities:

- create product with image upload
- call AI detection
- apply smart naming when title is blank
- list products with pagination
- fetch product detail
- update owned product
- soft delete owned product
- publish `ProductSoldEvent` when an owned product is marked as `SOLD`

### `VisionService`

Responsibilities:

- call `ai-service`
- map prediction response
- surface AI availability failures consistently

## Controller Layer

### `CategoryController`

Endpoints:

- `POST /api/v1/products/categories`
- `GET /api/v1/products/categories`

### `ProductController`

Endpoints:

- `POST /api/v1/products`
- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `PUT /api/v1/products/{id}`
- `DELETE /api/v1/products/{id}`
- `GET /api/v1/products/media/{filename}`

Current behavior:

- create consumes multipart form data
- update currently allows editing:
  - `title`
  - `description`
  - `price`
  - `status`
- delete performs soft delete, not physical row deletion
- list endpoint supports pageable and basic search queries
- default list sort remains newest first by `id DESC`

Supported `GET /api/v1/products` query parameters:

- `keyword`: searches product title, description, and category name
- `status`: filters by product status such as `ACTIVE` or `SOLD`
- `categoryId`: filters by category id
- `page`, `size`, `sort`: standard Spring pageable parameters

The assistant RAG flow in `interaction-service` uses this endpoint with `keyword`, `status=ACTIVE`, `page=0`, `size=30`, and `sort=id,desc`.

## CRUD Behavior Summary

### Create

- requires image file
- requires authenticated user propagated by gateway
- stores uploaded image
- calls `ai-service`
- persists AI metadata
- generates fallback title when necessary

### Read

- returns only records where `isDeleted = false`
- supports paginated list
- supports single product detail lookup
- returns absolute image URL

### Update

- loads only non-deleted product
- validates ownership
- updates mutable fields
- refreshes `updatedAt`
- publishes `product.event.sold` after commit when status changes to `SOLD`

### Delete

- loads only non-deleted product
- validates ownership
- sets `isDeleted = true`
- does not physically remove the database row

## Response Contract

Every controller returns `ApiResponse<T>`.

Example:

```json
{
  "status": 200,
  "message": "Products fetched successfully",
  "data": {}
}
```

Runtime message policy:

- API messages are in English
- logs are in English
- ownership denial is in English
- exception handler output is in English

## Exception Handling

`ApiExceptionHandler` currently standardizes:

- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `404 Not Found`
- `409 Conflict`
- `500 Internal Server Error`
- `503 Service Unavailable`

Examples:

- missing image file -> `400`
- missing `X-User-Id` -> `401`
- invalid ownership -> `403`
- missing category or missing active product -> `404`
- duplicate category -> `409`
- AI service unavailable -> `503`

## Gateway Integration

Gateway route:

- `/api/v1/products/**` -> `product-service`

Public route through gateway:

- `/api/v1/products/media/**`

Protected routes through gateway:

- category creation
- category listing
- product creation
- product listing
- product detail
- product update
- product delete

Observed behavior:

- gateway validates JWT
- gateway forwards `X-User-Id`
- product ownership checks rely on forwarded identity
- missing or invalid token is rejected at gateway before reaching this service

## Observability

Runtime observability is configured for the Docker development stack.

Dashboards:

- Zipkin: `http://localhost:9411`
- Kibana: `http://localhost:5601`
- Elasticsearch API: `http://localhost:9200`

Tracing:

- Micrometer Tracing is enabled through the Brave bridge.
- Zipkin reporter sends spans to `http://zipkin:9411/api/v2/spans` inside Docker.
- Sampling is configured as `management.tracing.sampling.probability=1.0` for development.
- Gateway-propagated trace context should connect product-service spans to the original gateway request trace.

Centralized logging:

- `logback-spring.xml` sends JSON logs directly to Logstash over TCP.
- Docker destination: `logstash:5044`
- Kibana data view pattern: `hsmart-logs-*`
- Timestamp field: `@timestamp`
- Logs include `service`, `traceId`, and `spanId` fields for correlation with Zipkin traces.

Current runtime verification status:

- Maven tests pass with tracing dependencies present.
- Docker Compose configuration is valid.
- Live Zipkin request-flow verification through `api-gateway` passed after Docker Desktop was started.
- Verified request path used for the runtime trace:
  - `POST http://localhost:8000/api/v1/auth/register`
- Verified Zipkin trace:
  - trace id: `69eef967827daba552e6c55045ec9834`
  - services: `api-gateway`, `user-service`
  - span count: `7`
- Product-service tracing and Logstash logging are configured with the same Micrometer and JSON logging stack for product routes.

## Verification Status

Verified locally with Maven tests:

- `mvn -q test` passed

Verified business behavior through tests and implementation review:

- create product flow
- get list and detail from active products only
- ownership denial on update
- soft delete for owned product
- product sold event publication when status transitions to `SOLD`

Observed log example:

- `Soft deleted product 11 for seller seller-1`
- `Updated product 12 for seller seller-1`

## Environment Variables

- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `AI_SERVICE_BASE_URL`
- `APP_PUBLIC_BASE_URL`
- `STORAGE_UPLOAD_DIR`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`
- `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT`
- `LOGSTASH_HOST`
- `LOGSTASH_PORT`

## Current Limitations

- update flow currently does not replace uploaded image
- soft delete does not remove the physical image file
- ownership is based on forwarded gateway subject, which currently maps to the gateway-provided user identity value
- no end-to-end integration test with live `ai-service` inside the test suite
- RabbitMQ publishing retries are simple in-process retries, not a durable outbox

## Current State Summary

`product-service` now supports:

- catalog creation and listing
- product create
- product read
- product update
- product soft delete
- ownership validation using `X-User-Id`
- RabbitMQ product sold event publishing
- English runtime messages and logs
- AI metadata persistence
- gateway-compatible absolute media URLs
