# search-service Service Notes

## Overview

`search-service` owns product search for H-Smart.

Current scope:

- high-performance product search backed by Elasticsearch
- fuzzy product lookup for typo-tolerant queries
- Vietnamese accent-insensitive matching through Elasticsearch analysis
- RabbitMQ-driven product index synchronization
- public gateway access for product discovery
- Zipkin tracing and Logstash JSON logging

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8084`
- Search engine: Elasticsearch
- Elasticsearch URL in Docker: `http://h-smart-elasticsearch:9200`
- Messaging: RabbitMQ
- Discovery: Eureka Client
- API docs: `/swagger-ui.html`

## Elasticsearch Index

Index name:

- `products_index`

Indexed fields:

- `id`
- `title`
- `description`
- `price`
- `categoryName`
- `status`

Analyzer:

- `hsmart_text_analyzer`
- tokenizer: `standard`
- filters: `lowercase`, `asciifolding`

This allows queries such as `may giat` or `máy giat` to match indexed titles such as `Máy giặt`.

## RabbitMQ Product Sync

`search-service` consumes product lifecycle events from RabbitMQ and updates `products_index`.

Broker resources:

- topic exchange: `product.exchange`
- durable queue: `product.search.index.queue`
- routing keys:
  - `product.event.created`
  - `product.event.updated`

Consumed payload:

```json
{
  "id": 101,
  "title": "Máy giặt mini",
  "description": "Compact washing machine for dorm rooms",
  "price": 1200000,
  "categoryName": "Máy giặt",
  "status": "ACTIVE"
}
```

Listener behavior:

- `ProductSearchEventListener` listens to `product.search.index.queue`
- valid events are saved into Elasticsearch as `ProductDocument`
- invalid events are ignored with an English warning log
- listener retry is enabled with a simple 3-attempt policy

Operational note:

- The service indexes new create/update events.
- Existing product rows created before this service is enabled need a future backfill job or a product update/re-publish event to appear in `products_index`.

## HTTP API

Endpoint:

- `GET /api/v1/search/products?q=...`

Gateway route:

- public path: `http://localhost:8000/api/v1/search/products?q=may%20giat`
- downstream path: `http://localhost:8084/api/v1/search/products?q=may%20giat`

Response contract:

```json
{
  "status": 200,
  "message": "Products searched successfully",
  "data": {
    "content": [],
    "pageNo": 0,
    "pageSize": 20,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  }
}
```

Supported query parameters:

- `q`: fuzzy search text
- `page`: page number
- `size`: page size
- `sort`: pageable sort parameter

Example:

```bash
curl "http://localhost:8000/api/v1/search/products?q=may%20giat&page=0&size=10"
```

## Gateway Access

The gateway route `/api/v1/search/**` points to `lb://search-service`.

Authentication:

- `/api/v1/search/**` is public and does not require JWT
- protected product CRUD routes remain unchanged under `/api/v1/products/**`

Resilience:

- gateway circuit breaker: `searchCircuitBreaker`
- fallback message: `Search service is busy, please try again later.`
- fallback data: empty `PageResponseDTO` product page
- fallback logs include the active trace id when available

Gateway environment variable:

- static `SEARCH_SERVICE_URL` is no longer used for the Spring Boot search route
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/`
- `INTERNAL_SHARED_SECRET` is required for accepted HTTP requests

Service discovery:

- Eureka Dashboard: `http://localhost:8761`
- registry service ID: `search-service`
- registry instance ID: `search-service:8084`
- runtime registration logs are emitted in English by Spring Cloud Netflix

## Internal Service Authentication

`search-service` is public only through `api-gateway`; direct service-port requests are rejected unless they carry the trusted internal secret.

Runtime behavior:

- filter: `InternalSecurityFilter`
- required header: `X-Internal-Secret`
- configured secret source: `INTERNAL_SHARED_SECRET`
- invalid or missing secret returns `401 Unauthorized`
- rejection response follows the standard `ApiResponse` JSON shape
- rejected requests are logged in English with the source IP
- rejected logs are sent to Logstash/ELK with `traceId` and `spanId` when tracing is active

Secret handling:

- the secret value is not hardcoded in `application.yml`
- Docker Compose reads it from the host environment or a gitignored `.env` file
- service healthchecks send `X-Internal-Secret` from the container environment

## Observability

Dashboards:

- Eureka: `http://localhost:8761`
- Zipkin: `http://localhost:9411`
- Kibana: `http://localhost:5601`
- Elasticsearch API: `http://localhost:9200`

Tracing:

- Micrometer Tracing is enabled through the Brave bridge.
- Zipkin reporter sends spans to `http://zipkin:9411/api/v2/spans` inside Docker.
- Sampling is configured as `management.tracing.sampling.probability=1.0` for development.
- Gateway requests to `/api/v1/search/products` should show `api-gateway` and `search-service` in Zipkin.
- Gateway client spans should show `spring.cloud.gateway.route.uri=lb://search-service`.

Logging:

- `logback-spring.xml` sends JSON logs directly to Logstash over TCP.
- Docker destination: `logstash:5044`
- Kibana data view pattern: `hsmart-logs-*`
- Logs include `service`, `traceId`, and `spanId`.
- Elasticsearch query duration and hit count are logged in English.

## Manual Runtime Test

Start required services:

```bash
docker compose up -d discovery-server elasticsearch rabbitmq zipkin logstash kibana search-service api-gateway
```

Publish a sample product event through RabbitMQ management API:

```powershell
$body = '{"properties":{"content_type":"application/json"},"routing_key":"product.event.created","payload":"{\"id\":9001,\"title\":\"Máy giặt mini\",\"description\":\"Compact washer for dorm rooms\",\"price\":1200000,\"categoryName\":\"Máy giặt\",\"status\":\"ACTIVE\"}","payload_encoding":"string"}'
curl.exe -u hsmart:hsmart_password -H "content-type: application/json" -d $body http://localhost:15672/api/exchanges/%2F/product.exchange/publish
```

Search through the gateway:

```bash
curl "http://localhost:8000/api/v1/search/products?q=may%20giat&page=0&size=10"
```

Expected result:

- response status is `200`
- response follows `ApiResponse<PageResponseDTO<ProductSearchResponseDTO>>`
- result includes the indexed `Máy giặt mini` document
- Zipkin trace contains `api-gateway` and `search-service`
- Kibana logs under `hsmart-logs-*` include `search-service` query timing logs with the same trace id

## Verification

Verified locally:

- `search-service`: `mvn -q test` passed
- `product-service`: `mvn -q test` passed after producer event updates
- `api-gateway`: `mvn -q test` passed after public search whitelist update
- `docker compose config --quiet` passed
- Docker runtime smoke test passed:
  - `search-service`, `product-service`, and `api-gateway` rebuilt successfully
  - `h-smart-search-service` started healthy on port `8084`
  - RabbitMQ sample event routed to `product.search.index.queue`
  - listener indexed product `9001` into `products_index`
  - direct product-service update published `product.event.updated`
  - product update event indexed product `5` into `products_index`
  - producer/consumer Zipkin trace id: `69f7435a5517493e80c375eb3ed8f370`
  - producer/consumer Zipkin services: `product-service`, `rabbitmq`, `search-service`
  - gateway request `GET /api/v1/search/products?q=may%20giat&page=0&size=10` returned `200`
  - search response returned `1` indexed result
  - later gateway request `GET /api/v1/search/products?q=electrolux&page=0&size=10` returned `200`
  - Zipkin trace id: `69f7435d7aa085f7d936a52aebb87ace`
  - Zipkin services: `api-gateway`, `search-service`
  - Elasticsearch log index: `hsmart-logs-2026.05.03`
  - logs include gateway request/response and search-service query timing with the same `traceId`
- Eureka registration verification passed:
  - service ID: `SEARCH-SERVICE`
  - instance: `search-service:8084`
  - status: `UP`
- Load-balanced gateway route verification passed:
  - request: `GET http://localhost:8000/api/v1/search/products?q=electrolux&page=0&size=5`
  - response status: `200`
  - gateway route URI in Zipkin: `lb://search-service`
  - trace id: `69f7938a4ec99cfcd7530f5ed1fe5e9e`
  - Zipkin services: `api-gateway`, `search-service`
  - Elasticsearch index `hsmart-logs-2026.05.03` contains gateway and search-service logs for the same `traceId`
- Internal shared-secret verification passed:
  - direct request: `GET http://localhost:8084/health`
  - direct response status: `401`
  - direct response message: `Invalid internal service credentials`
  - rejected direct request log included source IP `172.20.0.1`
  - rejected direct request trace id: `69f79be22317ec68e8de9d4b7287ca95`
  - gateway request remained successful:
    - `GET http://localhost:8000/api/v1/search/products?q=electrolux&page=0&size=5`
    - response status: `200`
    - trace id: `69f79c13b96a42034093e1595430b594`
    - Zipkin services: `api-gateway`, `search-service`
    - gateway route URI: `lb://search-service`
