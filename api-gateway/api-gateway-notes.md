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
- discover Spring Boot services through Eureka and route to them with Spring Cloud LoadBalancer
- attach the internal shared-secret header to trusted downstream service requests

## Runtime

- Framework: Spring Boot 3
- Stack: Reactive WebFlux
- Gateway: Spring Cloud Gateway
- Port: `8000`
- Spring Boot parent: `3.3.6`

## Routes

- `/api/v1/auth/**` -> `lb://user-service`
- `/api/v1/users/**` -> `lb://user-service`
- `/api/v1/products/**` -> `lb://product-service`
- `/api/v1/search/**` -> `lb://search-service`
- `/api/v1/orders/**` -> `lb://order-service`
- `/api/v1/reviews/**` -> `lb://review-service`
- `/api/v1/admin/**` -> `lb://admin-service`
- `/api/v1/predict/**` -> `ai-service`
- `/api/v1/assistant/**` -> `lb://interaction-service`
- `/api/v1/interactions/**` -> `lb://interaction-service`
- `/api/v1/interactions/ws/**` -> `lb:ws://interaction-service`

Spring Boot service routes are resolved through Eureka and Spring Cloud LoadBalancer.

Static downstream URLs were removed for:

- `user-service`
- `product-service`
- `interaction-service`
- `search-service`
- `order-service`
- `review-service`
- `admin-service`

The prediction route still uses `AI_SERVICE_URL` because `ai-service` is not currently a Spring Boot Eureka client.

Default prediction route value:

- `http://ai-service:8000`

## Service Discovery

The gateway registers itself with Eureka and fetches the service registry for load-balanced routes.

Eureka Dashboard:

- `http://localhost:8761`

Docker default zone:

- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/`

Expected gateway registry entry:

- service ID: `api-gateway`
- instance ID: `api-gateway:8000`

Expected downstream service IDs:

- `user-service`
- `product-service`
- `interaction-service`
- `search-service`
- `order-service`
- `review-service`
- `admin-service`

Circuit breaker filters remain attached to the gateway routes after switching to `lb://` URIs:

- `assistantCircuitBreaker` protects `lb://interaction-service`
- `searchCircuitBreaker` protects `lb://search-service`
- `predictCircuitBreaker` still protects the static `AI_SERVICE_URL` route

## Internal Service Authentication

The gateway is the trust boundary for Spring Boot services behind H-Smart.

Internal forwarding behavior:

- global filter: `InternalSecretForwardingFilter`
- header added to internal service routes: `X-Internal-Secret`
- secret source: `INTERNAL_SHARED_SECRET`
- the filter strips any incoming client-provided `X-Internal-Secret`
- the filter sets the configured secret only for `lb://` and `lb:ws://` routes
- `AI_SERVICE_URL` is not treated as a Spring Boot internal route by this filter

Secret handling:

- the secret is not hardcoded in `application.yml`
- Docker Compose reads it from the host environment or a gitignored `.env` file
- recommended local setup:

```bash
INTERNAL_SHARED_SECRET=<long-random-uuid-or-secret>
```

Valid downstream requests keep the existing trace context and any gateway-authenticated `X-User-Id`.

HTTP client timeout:

- `GATEWAY_RESPONSE_TIMEOUT` controls the downstream response timeout.
- Docker development default: `120s`
- The longer timeout keeps cloud AI-backed assistant requests from being cut off by the gateway while the provider is generating a response.

Redis:

- Docker container: `h-smart-redis`
- Image: `redis:7-alpine`
- Port: `6379`
- Gateway Redis host: `SPRING_DATA_REDIS_HOST`
- Gateway Redis port: `SPRING_DATA_REDIS_PORT`
- Internal shared secret: `INTERNAL_SHARED_SECRET`

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
  - `/api/v1/search/**`
  - `GET /api/v1/reviews/**`
  - `/health`
  - `/v3/api-docs/**`
  - `/swagger-ui/**`
  - `/swagger-ui.html`
- skips authentication for `OPTIONS` requests
- requires `Authorization: Bearer <token>` for protected routes
- supports `?token=<jwt>` for WebSocket handshake requests under `/api/v1/interactions/ws/**`
- validates JWT signature and expiration using the shared `JWT_SECRET`
- reads the JWT `role` claim
- writes `401 Unauthorized` JSON when the token is missing or invalid
- forwards `X-User-Id` and `X-User-Role` to downstream services after successful validation
- requires `role=ADMIN` for `/api/v1/admin/**`
- returns `403 Forbidden` with message `Admin role is required` when a non-admin calls admin routes

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

## Circuit Breakers

The gateway uses Spring Cloud CircuitBreaker with Reactor Resilience4j for sensitive downstream routes.

Protected route breakers:

- `/api/v1/assistant/**` -> `assistantCircuitBreaker`
- `/api/v1/search/**` -> `searchCircuitBreaker`
- `/api/v1/predict/**` -> `predictCircuitBreaker`

Shared breaker settings:

- `failure-rate-threshold=50`
- `slow-call-duration-threshold=10s`
- `slow-call-rate-threshold=50`
- `sliding-window-size=10`
- `minimum-number-of-calls=5`
- `wait-duration-in-open-state=30s`
- `permitted-number-of-calls-in-half-open-state=3`
- automatic transition from `OPEN` to `HALF_OPEN` is enabled

Fallback responses:

- search fallback returns an empty product page with message `Search service is busy, please try again later.`
- assistant fallback returns `Assistant is currently resting, will be back soon!`
- prediction fallback returns `Prediction service is busy, please try again later.`

Observability behavior:

- fallback handlers tag the active Zipkin span with `resilience4j.circuit_breaker.name`
- fallback handlers tag the active Zipkin span with `resilience4j.fallback=true`
- `CircuitBreakerEventLogger` logs state transitions such as `CLOSED -> OPEN` and `OPEN -> HALF_OPEN`
- logs are sent to Logstash and include `traceId` and `spanId`
- circuit breaker health and metrics are exposed through actuator health/metrics endpoints

## Logging

The gateway logs:

- incoming request method + path
- outgoing response status code

All custom log messages are in English.

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
- Circuit breaker fallback implementation is covered by unit tests for:
  - search fallback empty product page
  - assistant fallback message
- Verified assistant rate-limit block through gateway:
  - request: `POST http://localhost:8000/api/v1/assistant/chat`
  - response status: `429`
  - response message: `Too many AI requests. Please wait a moment before trying again.`
  - trace id: `69f731e84c5441c6060547309057ef97`
  - Zipkin service: `api-gateway`
  - Elasticsearch index: `hsmart-logs-2026.05.03`
  - logs include incoming request, `Rate limited AI request...`, and outgoing `429` response with the same `traceId`
- Verified public search request through gateway:
  - request: `GET http://localhost:8000/api/v1/search/products?q=electrolux&page=0&size=10`
  - response status: `200`
  - trace id: `69f7435d7aa085f7d936a52aebb87ace`
  - Zipkin services: `api-gateway`, `search-service`
  - Elasticsearch index: `hsmart-logs-2026.05.03`
  - logs include gateway request, search-service query timing, and gateway response with the same `traceId`
- Verified Eureka load-balanced search request through gateway:
  - request: `GET http://localhost:8000/api/v1/search/products?q=electrolux&page=0&size=5`
  - response status: `200`
  - gateway route URI in Zipkin: `lb://search-service`
  - trace id: `69f7938a4ec99cfcd7530f5ed1fe5e9e`
  - Zipkin services: `api-gateway`, `search-service`
  - gateway and search-service logs contain the same `traceId`
  - Elasticsearch index `hsmart-logs-2026.05.03` contains gateway request, search-service query timing, and gateway response logs for the same `traceId`
- Verified internal shared-secret forwarding:
  - direct request to `http://localhost:8084/health` without `X-Internal-Secret` returned `401`
  - gateway request to `GET /api/v1/search/products` returned `200`
  - trace id: `69f79c13b96a42034093e1595430b594`
  - Zipkin services: `api-gateway`, `search-service`
  - gateway route URI in Zipkin: `lb://search-service`
  - valid request logs kept the same `traceId` across gateway and search-service
  - rejected direct request logs were stored in Elasticsearch with source IP and trace id `69f79be22317ec68e8de9d4b7287ca95`

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
- `/api/v1/search/**` is routed to `search-service` and remains publicly accessible without JWT
- `/api/v1/search/**` is routed through Eureka LoadBalancer as `lb://search-service`
- `/api/v1/orders/**` is routed through Eureka LoadBalancer as `lb://order-service` and requires JWT authentication
- unauthenticated `POST /api/v1/orders` returns gateway-level `401 Unauthorized`
- `/api/v1/reviews/**` is routed through Eureka LoadBalancer as `lb://review-service`
- `GET /api/v1/reviews/**` is public
- `POST /api/v1/reviews` requires JWT authentication
- Eureka registry contains `API-GATEWAY`, `USER-SERVICE`, `PRODUCT-SERVICE`, `INTERACTION-SERVICE`, and `SEARCH-SERVICE` as `UP`
- internal routes receive `X-Internal-Secret` from the gateway
- `/api/v1/predict/**` is routed to `ai-service` with circuit breaker fallback
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
## Category Administration Routes

Product category access is split by HTTP method:

- `GET /api/v1/products/categories`
  - public
  - forwarded to `product-service`
- `POST /api/v1/products/categories`
  - requires JWT
  - requires JWT claim `role=ADMIN`
  - forwarded to `product-service`

Gateway routes:

- `product-category-read-route`
- `product-category-create-route`

The generic `/api/v1/products/**` route remains available for the rest of the product domain.
## Public Product Reads

The marketplace can read product data without a JWT through:

- `GET /api/v1/products`
- `GET /api/v1/products/{numericId}`

Authentication remains required for product creation, updates, deletion, wishlist operations, image analysis, and internal product endpoints. The whitelist accepts only a numeric single-segment product identifier to avoid exposing protected subpaths.
