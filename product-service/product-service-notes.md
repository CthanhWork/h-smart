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
- Smart Naming image analysis endpoint for upload previews
- Smart Pricing suggestions backed by Redis average sold price cache
- seller district and province enrichment for product detail/listing responses
- ownership validation based on `X-User-Id` forwarded by `api-gateway`
- asynchronous product lifecycle events through RabbitMQ
- asynchronous order completion consumption through RabbitMQ

This service does not validate JWT tokens directly. Authentication is handled by `api-gateway`. `product-service` trusts the forwarded identity header after the gateway has validated the token.

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8082`
- Database: `hsmart_product_db`
- Database engine: PostgreSQL
- Messaging: RabbitMQ
- Cache: Redis
- Discovery: Eureka Client
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

`product-service` may call `user-service` through its internal address endpoint to enrich product responses with seller district and province. This supports local marketplace UX without exposing the seller's full street address. If the lookup fails, the product response is still returned and the location fields remain empty.

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
- `APPROVED`
- `PENDING_REVIEW`
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
- AI timeout failure returns `504 Gateway Timeout`
- unreadable upload returns `400 Bad Request`

## Smart Naming and Smart Pricing

`product-service` exposes a pre-listing image analysis API for the frontend upload flow.

Endpoint:

- `POST /api/v1/products/analyze-image`
- content type: `multipart/form-data`
- request field: `file`
- requires JWT through `api-gateway`

Behavior:

1. sends the image to `ai-service` through `POST /api/v1/predict`
2. reads the English AI label, confidence, and `translated_label`
3. reads the average sold price from Redis using key `price:avg:{label}`
4. returns a suggested display name, suggested price, and AI metadata

Response data shape:

```json
{
  "suggestedName": "Ghe",
  "suggestedPrice": 250000.00,
  "aiMetadata": {
    "label": "chair",
    "confidence": 0.93,
    "translated_label": "Ghe",
    "num_detections": 1,
    "detections": []
  }
}
```

Cache behavior:

- Redis key format: `price:avg:{label}`
- Redis value format: decimal string with two fractional digits
- missing Redis values return `suggestedPrice: null`
- Redis lookup failures are logged in English and treated as cache misses

Average price refresh:

- component: `AveragePriceCacheRefreshJob`
- schedule: `${PRICING_AVERAGE_REFRESH_CRON:0 0 * * * *}`
- default cadence: hourly
- source table: `products`
- source status: `SOLD`
- grouping field: `ai_metadata[].label`
- storage: Redis through `RedisTemplate<String, String>`
- purpose: reduce database reads during the upload preview flow

## RabbitMQ Product Events

`product-service` publishes product lifecycle events through RabbitMQ instead of calling `interaction-service` over REST.

Configured exchange:

- type: topic
- name: `product.exchange`

Order completion input:

- exchange: `order.exchange`
- queue: `order.product.update.queue`
- routing key: `order.event.completed`
- payload fields:
  - `productId`

Published event:

- event DTO: `ProductSoldEvent`
- routing key: `product.event.sold`
- payload fields:
  - `productId`
  - `sellerId`
  - `title`

Search synchronization events:

- event DTO: `ProductSearchEvent`
- routing keys:
  - `product.event.created`
  - `product.event.updated`
- payload fields:
  - `id`
  - `title`
  - `description`
  - `price`
  - `categoryName`
  - `status`
  - `sellerId`
  - `aiMetadata`

Publish behavior:

- `POST /api/v1/products` publishes `product.event.created` after the database transaction commits
- `PUT /api/v1/products/{id}` publishes `product.event.updated` after the database transaction commits
- `PUT /api/v1/products/{id}` checks the previous product status before applying updates
- an event is published only when the status transitions from a non-`SOLD` value to `SOLD`
- the event is registered after the database transaction commits
- RabbitMQ publishing uses JSON conversion and a simple retry policy
- RabbitMQ listener processing uses a simple retry policy
- if RabbitMQ is unavailable after retry attempts, the failure is logged in English and the product update remains committed
- when `order.event.completed` is consumed, the product is marked as `SOLD`
- duplicate order completion events for products that are already `SOLD` are skipped
- order-driven product status changes publish `product.event.updated` for search sync and `product.event.sold` for seller notification

## Admin Auto-Moderation Support

`product-service` publishes enough product data for `admin-service` to moderate new listings asynchronously.

Moderation event source:

- exchange: `product.exchange`
- routing key: `product.event.created`
- payload: `ProductSearchEvent`

Additional payload fields for moderation:

- `sellerId`
- `aiMetadata`

Internal moderation endpoint:

- `PUT /api/v1/products/internal/{id}/moderation-status`

Expected request body:

```json
{
  "status": "APPROVED"
}
```

Behavior:

- `admin-service` calls this endpoint with `X-Internal-Secret`
- status can be moved to `APPROVED` or `PENDING_REVIEW`
- moderation status updates publish `product.event.updated` for `search-service`
- public seller create/update requests cannot assign `APPROVED` or `PENDING_REVIEW`; those statuses are reserved for `admin-service`

Internal stats endpoint:

- `GET /api/v1/products/internal/stats`

Returned data:

- `totalSellingProducts`: count of non-deleted products with status `APPROVED`

## Product Create Smart Naming

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
- `ImageAnalysisResponseDTO`
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
- publish product create/update events for search indexing
- publish `ProductSoldEvent` when an owned product is marked as `SOLD`
- consume `OrderCompletedEvent` and mark the related product as `SOLD`

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
- `POST /api/v1/products/analyze-image`
- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `PUT /api/v1/products/{id}`
- `DELETE /api/v1/products/{id}`
- `GET /api/v1/products/media/{filename}`

Current behavior:

- create consumes multipart form data
- analyze-image consumes multipart form data and returns upload-time suggestions
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
- `status`: filters by product status such as `APPROVED` or `SOLD`
- `categoryId`: filters by category id
- `page`, `size`, `sort`: standard Spring pageable parameters

The assistant RAG flow in `interaction-service` uses this endpoint with `keyword`, `status=APPROVED`, `page=0`, `size=30`, and `sort=id,desc`.

## CRUD Behavior Summary

### Create

- requires image file
- requires authenticated user propagated by gateway
- stores uploaded image
- calls `ai-service`
- persists AI metadata
- generates fallback title when necessary
- publishes `product.event.created` after commit for search indexing

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
- publishes `product.event.updated` after commit for search indexing
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
- `504 Gateway Timeout`

Examples:

- missing image file -> `400`
- missing `X-User-Id` -> `401`
- invalid ownership -> `403`
- missing category or missing active product -> `404`
- duplicate category -> `409`
- AI service unavailable -> `503`
- AI service timeout -> `504`

## Gateway Integration

Gateway route:

- `/api/v1/products/**` -> `lb://product-service`

Internal service authentication:

- `product-service` requires `X-Internal-Secret` on every HTTP request
- the configured value is read from `INTERNAL_SHARED_SECRET`
- if the header is missing or invalid, `InternalSecurityFilter` returns `401 Unauthorized`
- rejected requests are logged in English with the source IP
- rejected logs are sent to Logstash/ELK with `traceId` and `spanId` when tracing is active
- valid gateway requests keep `X-User-Id` for ownership checks
- valid assistant RAG requests from `interaction-service` also include `X-Internal-Secret`

Secret handling:

- the secret is not hardcoded in `application.yml`
- Docker Compose requires the secret from the host environment or a gitignored `.env` file

Public route through gateway:

- `/api/v1/products/media/**`

Protected routes through gateway:

- product image analysis
- category creation
- category listing
- product creation
- product listing
- product detail
- product update
- product delete

Search integration:

- product create/update events are consumed by `search-service`
- `search-service` indexes product snapshots into Elasticsearch index `products_index`
- public search is exposed through gateway route `/api/v1/search/**`

Order integration:

- `order-service` publishes `order.event.completed` after an order becomes `COMPLETED`
- `product-service` consumes the event and updates the product status to `SOLD`
- the resulting `product.event.sold` is consumed by `interaction-service` to create the seller notification

Service discovery:

- `product-service` registers with Eureka through `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`
- Docker default zone: `http://discovery-server:8761/eureka/`
- Eureka Dashboard: `http://localhost:8761`
- registry service ID: `product-service`
- registry instance ID: `product-service:8082`

Observed behavior:

- gateway validates JWT
- gateway forwards `X-User-Id`
- product ownership checks rely on forwarded identity
- missing or invalid token is rejected at gateway before reaching this service

## Observability

Runtime observability is configured for the Docker development stack.

Dashboards:

- Eureka: `http://localhost:8761`
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
- Verified Eureka registration:
  - service ID: `PRODUCT-SERVICE`
  - instance: `product-service:8082`
  - status: `UP`

## Verification Status

Verified locally with Maven tests:

- `mvn -q test` passed
- `docker compose config --quiet` passed with required secrets supplied from the shell

Verified business behavior through tests and implementation review:

- create product flow
- get list and detail from approved products only
- ownership denial on update
- soft delete for owned product
- product sold event publication when status transitions to `SOLD`
- product update search event publication through `product.event.updated`
- image analysis suggestion flow
- Redis average price cache refresh flow
- Docker build passed after product search event producer updates
- Docker runtime producer smoke test passed:
  - `PUT /api/v1/products/5` with `X-User-Id: seller-bk-1` returned `200`
  - product-service published `product.event.updated`
  - search-service consumed the event and indexed product `5`
  - Zipkin trace id: `69f7435a5517493e80c375eb3ed8f370`
  - trace services: `product-service`, `rabbitmq`, `search-service`
- Docker runtime order event smoke test passed:
  - `order-service` completed order `1` for product `5`
  - product-service consumed `order.event.completed` from `order.product.update.queue`
  - product `5` changed to `SOLD`
  - product-service published `product.event.updated` and `product.event.sold`
  - trace id: `69f8290adb06113ee6510f5a84d57c5e`
  - logs show the same trace id across `order-service` and `product-service`

Observed log example:

- `Soft deleted product 11 for seller seller-1`
- `Updated product 12 for seller seller-1`

## Environment Variables

- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `AI_SERVICE_BASE_URL`
- `AI_SERVICE_CONNECT_TIMEOUT_MS`
- `AI_SERVICE_READ_TIMEOUT_MS`
- `APP_PUBLIC_BASE_URL`
- `STORAGE_UPLOAD_DIR`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `PRICING_AVERAGE_REFRESH_CRON`
- `INTERNAL_SHARED_SECRET`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`
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
- Smart Naming and Smart Pricing upload analysis
- Redis average sold price cache
- gateway-compatible absolute media URLs

## Wishlist Support

`product-service` supports authenticated saved-product toggles and paginated wishlist reads.

Database additions:

- `products.like_count`: cached number of users who saved the product
- `product_likes.id`
- `product_likes.user_id`
- `product_likes.product_id`
- `product_likes.created_at`
- unique constraint: `(user_id, product_id)`

Endpoints:

- `POST /api/v1/products/{id}/like`
- `GET /api/v1/products/wishlist`

Toggle behavior:

- requires authenticated `X-User-Id` context forwarded by `api-gateway`
- creates a `ProductLike` record when the product is not saved
- increments `Product.likeCount` when a record is created
- returns `Product saved to wishlist` after saving
- deletes the existing `ProductLike` record when the product is already saved
- decrements `Product.likeCount` without allowing negative values
- returns `Product removed from wishlist` after removal
- soft-deleted products cannot be toggled because product lookup uses `isDeleted = false`

Wishlist query behavior:

- returns `ApiResponse<PageResponseDTO<ProductResponseDTO>>`
- supports standard `page`, `size`, and `sort` query parameters
- defaults to `createdAt DESC` so recently saved products appear first
- joins `ProductLike` and `Product`
- excludes products where `isDeleted = true`
- excludes products with status `SOLD`
- includes `likeCount` in `ProductResponseDTO`

Runtime message policy:

- wishlist API messages are in English
- wishlist logs are in English

## Moderated Product Creation and Hybrid AI Fail-Safe

Product creation no longer fails when the externally hosted `ai-service` is unavailable or times out.

Product status behavior:

- new products are always created as `PENDING_REVIEW`
- product creation responses use `Product submitted successfully and is pending moderation review.`
- sellers cannot set product status through create or update requests
- moderation statuses are assigned only by admin-service
- `SOLD` is assigned only by the order completed workflow

AI fallback behavior:

- catches AI connection and timeout failures during image analysis
- stores empty AI metadata instead of rejecting the listing
- uses `Uncategorized Product` when the submitted title is blank
- forces the new product status to `PENDING_REVIEW`
- saves the product normally and continues publishing `product.event.created` after the database commit
- writes an English warning log containing the saved product ID and AI failure details

The regular AI-assisted flow still stores AI metadata when `ai-service` is available, but admin-service remains responsible for approving the product. Docker Compose also no longer waits for the AI container before starting `product-service`.

## AI Category Bootstrap

`AiCategoryInitializer` keeps the product category table aligned with the 53 English labels used by the Detectron2 model.

- runs during application startup
- inserts only missing category names
- preserves existing category records and identifiers
- uses the exact English AI labels required by moderation label matching
- allows a fresh deployment to use Smart Upload without manually creating categories first

## Listing Suggestion Preparation

`product-service` exposes a single image-driven listing suggestion endpoint for the seller UI.

Endpoint:

- `POST /api/v1/products/prepare-listing`

Behavior:

- accepts a multipart image field named `file`
- runs the existing image analysis flow to resolve a suggested product title
- calls `interaction-service` internally to generate a Vietnamese product description
- sends `X-User-Id` and `X-Internal-Secret` to the downstream assistant endpoint
- returns only seller-facing draft fields:
  - `suggestedTitle`
  - `suggestedDescription`
- does not expose AI labels, confidence scores, detection metadata, or suggested pricing to the frontend
- returns an empty description if assistant generation is unavailable, while preserving the title suggestion when available

Configuration:

- `INTERACTION_SERVICE_BASE_URL` controls the internal assistant service URL
- Docker Compose sets it to `http://interaction-service:8083`

## Multi-Image Product Posting

Product creation now supports multiple uploaded images while limiting Computer Vision analysis to exactly one selected image.

Behavior:

- `POST /api/v1/products` accepts:
  - repeated multipart image fields named `files`
  - optional legacy single image field named `file`
  - `analysisImageIndex` to choose which uploaded image is sent to `ai-service`
- all uploaded images are stored and returned in `imageUrls`
- `imageUrl` remains the primary cover image for backward compatibility
- only the image at `analysisImageIndex` is analyzed for AI metadata and auto-filled naming support
- if `imageUrls` metadata is missing or invalid for older rows, the API falls back to the primary `imageUrl`
