# api-gateway Service Notes

## Scope

`api-gateway` is the single entry point for the H-Smart microservices system.

Current responsibilities:

- route frontend traffic to downstream services
- apply global CORS
- standardize gateway error responses
- log incoming requests and outgoing responses
- validate JWT before forwarding protected requests
- forward authenticated user identity to downstream services
- rate-limit AI assistant requests per user through Redis

## Runtime

- Framework: Spring Boot 3
- Stack: Reactive WebFlux
- Gateway: Spring Cloud Gateway
- Port: `8000`
- Spring Boot parent: `3.3.6`

## Routes

- `/api/v1/auth/**` -> `user-service`
- `/api/v1/users/**` -> `user-service`
- `/api/v1/products/**` -> `product-service`
- `/api/v1/assistant/**` -> `interaction-service`
- `/api/v1/interactions/**` -> `interaction-service`
- `/api/v1/interactions/ws/**` -> `interaction-service` over WebSocket

Downstream base URL is configured via:

- `USER_SERVICE_URL`
- `PRODUCT_SERVICE_URL`
- `INTERACTION_SERVICE_URL`
- `INTERACTION_SERVICE_WS_URL`

Default value:

- `http://user-service:8081`
- `http://product-service:8082`
- `http://interaction-service:8083`
- `ws://interaction-service:8083`

HTTP client timeout:

- `GATEWAY_RESPONSE_TIMEOUT` controls the downstream response timeout.
- Docker development default: `120s`
- The longer timeout keeps LLM-backed assistant requests from being cut off by the gateway while Ollama is generating a response.

Redis:

- Docker container: `h-smart-redis`
- Image: `redis:7-alpine`
- Port: `6379`
- Gateway Redis host: `SPRING_DATA_REDIS_HOST`
- Gateway Redis port: `SPRING_DATA_REDIS_PORT`

## Error policy

Gateway-specific infrastructure failures are translated to `ApiResponse<T>` JSON in English:

- `502 Bad Gateway`
- `503 Service Unavailable`
- `504 Gateway Timeout`

Current implementation details:

- `ConnectException`, `UnknownHostException`, and `NoRouteToHostException` -> `502`
- `TimeoutException` and `SocketTimeoutException` -> `504`
- `ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)` -> `503`

All custom gateway messages are returned in English.

Authentication failures are returned directly by the gateway as:

```json
{
  "status": 401,
  "message": "Invalid or missing security token",
  "data": null
}
```

AI assistant rate-limit failures are returned directly by the gateway as:

```json
{
  "status": 429,
  "message": "Too many AI requests. Please wait a moment before trying again.",
  "data": null
}
```

## Authentication filter

The gateway contains a custom `AuthenticationFilter` implemented as `AbstractGatewayFilterFactory`.

Current behavior:

- skips JWT validation for:
  - `/api/v1/auth/**`
  - `/api/v1/products/media/**`
  - `/health`
  - `/v3/api-docs/**`
  - `/swagger-ui/**`
  - `/swagger-ui.html`
- skips authentication for `OPTIONS` requests
- requires `Authorization: Bearer <token>` for protected routes
- supports `?token=<jwt>` for WebSocket handshake requests under `/api/v1/interactions/ws/**`
- validates JWT signature and expiration using the shared `JWT_SECRET`
- writes `401 Unauthorized` JSON when the token is missing or invalid
- forwards `X-User-Id` to downstream services after successful validation

Current header-forwarding note:

- `X-User-Id` currently carries the JWT subject value.
- In the current token contract, the subject is the `username` issued by `user-service`.
- If `user-service` later emits a numeric user id in the JWT subject, the same header can carry that value without changing the gateway contract.

## AI Assistant Rate Limiting

The `/api/v1/assistant/**` route is protected with Spring Cloud Gateway `RequestRateLimiter`.

Implementation details:

- rate limiter bean: `assistantRedisRateLimiter`
- key resolver bean: `userOrIpKeyResolver`
- primary key: `X-User-Id`
- fallback key: client IP address
- Redis key prefix: `hsmart:gateway:rate-limit:assistant`
- response status when blocked: `429 Too Many Requests`

Configured limits:

- `ASSISTANT_RATE_LIMIT_REPLENISH_RATE=5`
- `ASSISTANT_RATE_LIMIT_BURST_CAPACITY=10`
- `ASSISTANT_RATE_LIMIT_REFILL_PERIOD_SECONDS=60`

Behavior:

- each user receives up to `10` immediate assistant requests as burst capacity
- tokens replenish at `5` requests per `60` seconds
- blocked requests do not reach `interaction-service`
- blocked requests are logged in English with the request trace id
- blocked requests still appear in Zipkin as gateway-handled requests

Route filter configuration:

```yaml
filters:
  - name: RequestRateLimiter
    args:
      rate-limiter: "#{@assistantRedisRateLimiter}"
      key-resolver: "#{@userOrIpKeyResolver}"
      status-code: TOO_MANY_REQUESTS
      assistant-redis-rate-limiter.replenishRate: 5
      assistant-redis-rate-limiter.burstCapacity: 10
      assistant-redis-rate-limiter.refillPeriodSeconds: 60
```

## Logging

The gateway logs:

- incoming request method + path
- outgoing response status code

All custom log messages are in English.

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
- A request entering `api-gateway` should create the root gateway span and propagate trace context to downstream services.

Centralized logging:

- `logback-spring.xml` sends JSON logs directly to Logstash over TCP.
- Docker destination: `logstash:5044`
- Kibana data view pattern: `hsmart-logs-*`
- Timestamp field: `@timestamp`
- Logs include `service`, `traceId`, and `spanId` fields for correlation with Zipkin traces.
- AI assistant rate-limit blocks log `Rate limited AI request...` with the same trace id.

Current runtime verification status:

- Maven tests pass with tracing dependencies loaded.
- Docker Compose configuration is valid.
- Live Zipkin request-flow verification passed after Docker Desktop was started.
- Verified request:
  - `POST http://localhost:8000/api/v1/auth/register`
- Verified Zipkin trace:
  - trace id: `69eef967827daba552e6c55045ec9834`
  - services: `api-gateway`, `user-service`
  - span count: `7`
- Verified Elasticsearch log correlation:
  - index: `hsmart-logs-2026.04.27`
  - gateway request and response logs contain the same `traceId` and `spanId`
- Verified assistant request through gateway after increasing the development response timeout for LLM calls:
  - request: `POST http://localhost:8000/api/v1/assistant/chat`
  - response status: `200`
  - trace id: `69f5b049592f2629b63ed0ca200d7698`
  - Zipkin services: `api-gateway`, `interaction-service`
  - Elasticsearch index: `hsmart-logs-2026.05.02`
  - gateway request and response logs contain the same `traceId` and `spanId`
- AI assistant rate-limit implementation is covered by unit tests for:
  - `X-User-Id` and IP key resolution
  - standard `ApiResponse` body for `429 Too Many Requests`
- Verified assistant rate-limit block through gateway:
  - request: `POST http://localhost:8000/api/v1/assistant/chat`
  - response status: `429`
  - response message: `Too many AI requests. Please wait a moment before trying again.`
  - trace id: `69f731e84c5441c6060547309057ef97`
  - Zipkin service: `api-gateway`
  - Elasticsearch index: `hsmart-logs-2026.05.03`
  - logs include incoming request, `Rate limited AI request...`, and outgoing `429` response with the same `traceId`

## CORS

Global CORS is enabled through `CorsWebFilter`.

Allowed values:

- origins: `*`
- methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`
- headers: `*`

## Verification

Verified:

- Maven tests pass: `mvn -q test`
- Maven package pass: `mvn -q -DskipTests package`
- Docker build pass
- Gateway health endpoint pass on `http://localhost:8000/health`
- Proxy route pass:
  - `GET /api/v1/users/profile` through gateway returns downstream `401 Unauthorized`
  - `POST /api/v1/auth/register` through gateway returns downstream `201 Created`
  - `/api/v1/products/**` is routed to `product-service`
- `/api/v1/assistant/**` is routed to `interaction-service` and live assistant chat returns `200`
- `/api/v1/assistant/**` returns gateway-level `429` when Redis rate limit is exceeded
  - `/api/v1/interactions/**` is routed to `interaction-service`
  - `/api/v1/interactions/ws/**` is routed to `interaction-service` as WebSocket traffic
- JWT filter pass:
  - protected route without token returns `401` with message `Invalid or missing security token`
  - protected route with valid token returns downstream `200`
  - whitelist route `/api/v1/auth/register` remains accessible without token
  - whitelist route `/api/v1/products/media/**` remains accessible without token
- `502 Bad Gateway` runtime verification pass by temporarily stopping `user-service`
- `504 Gateway Timeout` mapping is covered by unit test in `GatewayExceptionMapperTest`
- authenticated header forwarding is covered by unit test in `AuthenticationFilterTest`
- assistant rate-limit key resolution is covered by unit test in `GatewayRateLimitConfigTest`
- assistant `429` response formatting is covered by unit test in `RateLimitResponseFilterTest`

## Operational note

To free host port `8000` for `api-gateway`, the host debug port of `ai-service` was moved from `8000` to `8002` in `docker-compose.yml`.
