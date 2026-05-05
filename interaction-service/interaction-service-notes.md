# interaction-service Service Notes

## Overview

`interaction-service` is responsible for real-time communication and notification delivery inside H-Smart.

Current scope:

- real-time buyer-seller chat over WebSocket
- chat history persistence in MongoDB
- notification creation and retrieval
- notification fan-out over WebSocket user queues
- asynchronous product sold notifications from RabbitMQ product events
- context-aware text assistant backed by Ollama and Qwen3
- product-aware assistant answers using live product data from `product-service`
- request identity handling through `X-User-Id` forwarded by `api-gateway`

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8083`
- Database: `hsmart_interaction_db`
- Database engine: MongoDB
- Messaging: RabbitMQ
- Assistant model: `qwen3:4b-instruct` through Ollama
- Product catalog source: `product-service`
- Resilience: Resilience4j circuit breaker
- Discovery: Eureka Client and Spring Cloud LoadBalancer
- WebSocket: STOMP over Spring WebSocket
- API docs: `/swagger-ui.html`

## Package Layout

Implemented packages:

- `domain.entities`
- `application.dto`
- `application.exceptions`
- `application.mapper`
- `service`
- `service.impl`
- `presentation.controllers`
- `presentation.websocket`
- `infrastructure.persistence`
- `infrastructure.context`
- `infrastructure.config`
- `infrastructure.exception`
- `infrastructure.messaging`
- `infrastructure.product`

## MongoDB Collections

### `chat_messages`

Fields:

- `id`
- `senderId`
- `receiverId`
- `productId`
- `content`
- `timestamp`

Assistant conversation messages are stored in this same collection:

- user message:
  - `senderId = <current user id>`
  - `receiverId = h-smart-assistant`
- assistant reply:
  - `senderId = h-smart-assistant`
  - `receiverId = <current user id>`
- `productId` is empty for assistant-only text chat

### `notifications`

Fields:

- `id`
- `userId`
- `type`
- `message`
- `productId`
- `read`
- `timestamp`

## Identity Handling

- REST requests read `X-User-Id` through `UserContextFilter`
- WebSocket handshake stores `X-User-Id` in session attributes
- STOMP `CONNECT` binds the authenticated user to a `Principal`
- runtime user queues use the forwarded gateway user id as the STOMP principal name

## Internal Service Authentication

`interaction-service` accepts HTTP requests only when the trusted internal secret header is present.

Runtime behavior:

- filter: `InternalSecurityFilter`
- required header: `X-Internal-Secret`
- configured secret source: `INTERNAL_SHARED_SECRET`
- invalid or missing secret returns `401 Unauthorized`
- rejection response follows the standard `ApiResponse` JSON shape
- rejected requests are logged in English with the source IP
- rejected logs are sent to Logstash/ELK with `traceId` and `spanId` when tracing is active

Secret handling:

- the secret is not hardcoded in `application.yml`
- Docker Compose requires it from the host environment or a gitignored `.env` file
- valid gateway requests keep `X-User-Id` for REST and WebSocket identity handling

Outbound internal calls:

- assistant RAG product lookup sends `X-Internal-Secret` to `product-service`
- `ProductServiceClient` keeps the existing `X-User-Id` header and adds the internal secret header

## HTTP Endpoints

- `GET /health`
- `POST /api/v1/assistant/chat`
- `GET /api/v1/interactions/messages`
- `POST /api/v1/interactions/notifications`
- `GET /api/v1/interactions/notifications`

## WebSocket Endpoint

- handshake endpoint: `/api/v1/interactions/ws`
- client send destination: `/app/chat.send`
- per-user chat queue: `/user/queue/messages`
- per-user notification queue: `/user/queue/notifications`

## RabbitMQ Product Event Consumer

`interaction-service` consumes product lifecycle events from RabbitMQ and creates notifications without a direct REST call from `product-service`.

Configured broker resources:

- topic exchange: `product.exchange`
- durable queue: `product.sold.notification.queue`
- routing key: `product.event.sold`

Consumed event:

- event DTO: `ProductSoldEvent`
- payload fields:
  - `productId`
  - `sellerId`
  - `title`

Listener behavior:

- `ProductSoldEventListener` listens to `product.sold.notification.queue`
- valid events create a MongoDB notification for `sellerId`
- notification type is `PRODUCT_SOLD`
- notification message format is `Congratulations! Your product [Title] has been marked as SOLD.`
- invalid events are ignored with an English warning log
- listener retry is enabled with a simple 3-attempt policy for processing failures

## Assistant Chat

`interaction-service` exposes a context-aware text assistant endpoint:

- endpoint: `POST /api/v1/assistant/chat`
- response wrapper: `ApiResponse<String>`
- request identity: `X-User-Id`, normally forwarded by `api-gateway`
- Ollama base URL: `http://host.docker.internal:11434`
- model: `qwen3:4b-instruct`
- default history limit: `10` recent assistant conversation messages

Default system message:

```text
You are H-Smart Assistant, a friendly and witty expert in second-hand household appliances. You help users at Ho Chi Minh City University of Technology (HCMUT) marketplace. Always answer in Vietnamese unless requested otherwise.
```

Behavior:

- reads the current user id from `X-User-Id`
- loads the most recent assistant conversation messages for that user from MongoDB
- detects product category keywords such as `máy giặt`, `tủ lạnh`, `ghế`, `bàn`, `sofa`, `điều hòa`, `quạt`, `nồi cơm`, `bếp`, `tivi`, `lò vi sóng`, `giường`, and `kệ`
- when product keywords are detected, calls `product-service` for live product data before calling Ollama
- appends live H-Smart product data to the system prompt when matching products are available
- sends the system prompt, recent history, and latest user message to Ollama `/api/chat`
- uses `stream=false`
- stores the user question and assistant answer in `chat_messages`
- logs processing start, loaded history count, product keyword detection, product retrieval count, stored conversation turn, total processing time, and AI response time in English
- Micrometer Tracing attaches the request trace to logs for Zipkin and ELK correlation

Product-aware RAG behavior:

- `ProductKeywordExtractor` scans the user message for known product category keywords.
- `ProductServiceClient` calls `GET /api/v1/products` on `product-service` through a load-balanced `RestClient`.
- Default product-service service ID URL: `http://product-service`
- `product-service` is resolved from Eureka by Spring Cloud LoadBalancer.
- Product catalog lookup is wrapped by `productCatalogCircuitBreaker`.
- Request parameters used by the client:
  - `keyword=<detected product keyword>`
  - `status=ACTIVE`
  - `page=0`
  - `size=30`
  - `sort=id,desc`
- Matching product fields included in the prompt:
  - title
  - price
  - short description
  - seller id
  - category name
- Prompt addition format:

```text
Dưới đây là dữ liệu thực tế từ kho hàng H-Smart: [Dữ liệu sản phẩm]. Hãy sử dụng thông tin này để trả lời người dùng một cách chính xác nhất.
```

If `product-service` is unavailable:

- Resilience4j opens or rejects calls through `productCatalogCircuitBreaker` according to the configured failure and slow-call thresholds.
- the assistant still calls Ollama and answers using general knowledge
- logs an English warning that the product catalog circuit breaker fallback was triggered
- the user-facing answer is prefixed with:

```text
Hiện tại tôi không thể truy cập dữ liệu thời gian thực, đây là thông tin tham khảo...
```

Environment variables:

- `ASSISTANT_OLLAMA_BASE_URL`
- `ASSISTANT_OLLAMA_MODEL`
- `ASSISTANT_HISTORY_LIMIT`
- `ASSISTANT_ID`
- `ASSISTANT_SYSTEM_PROMPT`
- `PRODUCT_SERVICE_BASE_URL`
- `PRODUCT_SERVICE_PAGE_SIZE`
- `PRODUCT_SERVICE_CONNECT_TIMEOUT_MS`
- `PRODUCT_SERVICE_READ_TIMEOUT_MS`
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`
- `INTERNAL_SHARED_SECRET`

Resilience4j configuration:

- circuit breaker name: `productCatalogCircuitBreaker`
- `failure-rate-threshold=50`
- `slow-call-duration-threshold=10s`
- `slow-call-rate-threshold=50`
- `sliding-window-size=10`
- `minimum-number-of-calls=5`
- `wait-duration-in-open-state=30s`
- `permitted-number-of-calls-in-half-open-state=3`
- automatic transition from `OPEN` to `HALF_OPEN` is enabled

Service discovery:

- `interaction-service` registers itself with Eureka.
- Eureka Dashboard: `http://localhost:8761`
- Docker default zone: `http://discovery-server:8761/eureka/`
- registry service ID: `interaction-service`
- registry instance ID: `interaction-service:8083`
- gateway routes `/api/v1/assistant/**` and `/api/v1/interactions/**` to `lb://interaction-service`
- gateway routes `/api/v1/interactions/ws/**` to `lb:ws://interaction-service`

Local prerequisites:

```powershell
ollama pull qwen3:4b-instruct
ollama serve
```

Direct service test through port `8083`:

```bash
curl -X POST "http://localhost:8083/api/v1/assistant/chat" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -d "{\"message\":\"Minh nen kiem tra gi khi mua tu lanh cu cho phong tro?\"}"
```

Gateway test through port `8000`:

```bash
curl -X POST "http://localhost:8000/api/v1/assistant/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d "{\"message\":\"Nho lai cau hoi truoc va goi y them cho minh nhe.\"}"
```

Product-aware RAG test through gateway:

```bash
curl -X POST "http://localhost:8000/api/v1/assistant/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d "{\"message\":\"H-Smart hien co may giat nao phu hop phong tro khong?\"}"
```

Expected response shape:

```json
{
  "status": 200,
  "message": "Assistant response generated successfully",
  "data": "..."
}
```

Context verification:

- send two or more messages with the same `X-User-Id` or JWT user
- verify the assistant can refer to the earlier appliance discussion
- ask about a product category such as `may giat` or `tu lanh`
- verify the assistant response uses live product data from `product-service` when available
- inspect MongoDB `chat_messages` for alternating user and `h-smart-assistant` records
- inspect Zipkin at `http://localhost:9411` for the chat request trace
- for product-aware requests, Zipkin should show `api-gateway`, `interaction-service`, and `product-service` in the same trace
- inspect Kibana with `hsmart-logs-*` and filter by the request `traceId`

## Behavior

### Chat

- stores each incoming message in MongoDB
- publishes the message to sender and receiver queues
- generates a notification for the receiver automatically

### Notifications

- REST endpoint can create a notification document
- notifications are fetched for the current gateway-authenticated user
- created notifications are pushed to the receiver's WebSocket queue
- product sold events automatically create seller notifications and publish them to the seller's WebSocket queue

### Assistant

- stores assistant chat messages in MongoDB
- keeps per-user assistant context by reading the latest `5` to `10` assistant conversation messages
- retrieves live product context from `product-service` when a product category keyword is detected
- calls Ollama with the configured Qwen3 model
- answers in Vietnamese by default through the system prompt

## Error Contract

REST responses follow the standard `ApiResponse<T>` contract.

Handled status codes:

- `200 OK`
- `201 Created`
- `400 Bad Request`
- `401 Unauthorized`
- `500 Internal Server Error`

Runtime messages and logs are in English.

Current `400` sources include:

- bean validation failures on request bodies
- malformed JSON
- missing request parameters such as `participantId`
- invalid interaction requests such as identical sender and receiver

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
- Gateway-propagated trace context should connect interaction-service spans to the original gateway request trace.

Centralized logging:

- `logback-spring.xml` sends JSON logs directly to Logstash over TCP.
- Docker destination: `logstash:5044`
- Kibana data view pattern: `hsmart-logs-*`
- Timestamp field: `@timestamp`
- Logs include `service`, `traceId`, and `spanId` fields for correlation with Zipkin traces.
- Resilience4j logs include circuit breaker state transitions such as `CLOSED`, `OPEN`, and `HALF_OPEN`.
- Product catalog fallback logs include the active request `traceId`.
- Assistant RAG spans are tagged with `resilience4j.circuit_breaker.name` and `resilience4j.circuit_breaker.state` when a trace span is available.

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
- Interaction-service tracing and Logstash logging are configured with the same Micrometer and JSON logging stack for interaction routes.
- Assistant service unit test verifies context ordering, prompt construction, and MongoDB persistence for the user question and assistant answer.
- Live assistant request-flow verification through `api-gateway` passed:
  - request: `POST http://localhost:8000/api/v1/assistant/chat`
  - response status: `200`
  - trace id: `69f5b049592f2629b63ed0ca200d7698`
  - Zipkin services: `api-gateway`, `interaction-service`
  - Elasticsearch index: `hsmart-logs-2026.05.02`
  - logs include assistant history load, MongoDB persistence, total processing time, and AI response time
- Context-aware assistant verification passed with user `assistant-smoke-user`; the follow-up request remembered that the user was looking for a used washing machine.
- Live product-aware RAG verification through `api-gateway` passed:
  - request: `POST http://localhost:8000/api/v1/assistant/chat`
  - user: `rag-test-user`
  - prompt: `H-Smart hien co may giat nao phu hop phong tro khong?`
  - response status: `200`
  - assistant answer used live product data from `product-service`
  - trace id: `69f73b39e07f41301563259afb154a95`
  - Zipkin services: `api-gateway`, `interaction-service`, `product-service`
  - product catalog lookup returned `1` item in `63 ms`
- Product-service unavailable fallback verification passed:
  - product-service was temporarily stopped
  - assistant still returned `200`
  - response was prefixed with `Hiện tại tôi không thể truy cập dữ liệu thời gian thực, đây là thông tin tham khảo...`
- Verified Eureka registration:
  - service ID: `INTERACTION-SERVICE`
  - instance: `interaction-service:8083`
  - status: `UP`

## Current Verification

- unit test added for `ChatServiceImpl`
- unit test added for `ProductSoldEventListener`
- unit test added for `AssistantServiceImpl`
- unit test added for `ProductContextServiceImpl`
- assistant unit test covers product context prompt injection
- assistant unit test covers realtime product-data fallback prefix
- product context unit test covers lookup through a closed circuit breaker
- product context unit test covers fallback when `productCatalogCircuitBreaker` is `OPEN`
- product context unit test covers fallback when product-service lookup fails
- RabbitMQ queue binding configuration validated with `docker compose config`
- Docker runtime verified with:
  - successful MongoDB connection
  - successful REST notification create and fetch through `api-gateway`
  - successful STOMP WebSocket handshake through `api-gateway`
  - successful chat delivery on `/user/queue/messages`
  - successful automatic notification delivery on `/user/queue/notifications`
  - successful assistant chat through `api-gateway`
  - successful assistant context recall from MongoDB-backed conversation history
  - successful assistant RAG lookup through `product-service`
