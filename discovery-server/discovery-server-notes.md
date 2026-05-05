# discovery-server Service Notes

## Overview

`discovery-server` provides Eureka service discovery for the H-Smart Spring Boot services.

Current scope:

- central service registry
- Eureka dashboard for local development
- registration target for `api-gateway`, `user-service`, `product-service`, `interaction-service`, and `search-service`

## Runtime and Stack

- Framework: Spring Boot 3
- Java: 17
- Port: `8761`
- Discovery: Spring Cloud Netflix Eureka Server
- Container: `h-smart-discovery-server`

## Dashboard

Eureka Dashboard:

- `http://localhost:8761`

Expected registered service IDs:

- `api-gateway`
- `user-service`
- `product-service`
- `interaction-service`
- `search-service`

## Configuration

The server does not register itself:

- `eureka.client.register-with-eureka=false`
- `eureka.client.fetch-registry=false`

Docker default zone:

- `http://discovery-server:8761/eureka/`

Docker container:

- service name: `discovery-server`
- container name: `h-smart-discovery-server`
- host port: `8761`

Client configuration used by Spring Boot services:

- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/`

## Observability

- Eureka: `http://localhost:8761`
- Zipkin: `http://localhost:9411`
- Kibana: `http://localhost:5601`
- Logstash destination in Docker: `logstash:5044`
- Logs include `service`, `traceId`, and `spanId`.
- Eureka registration and renewal logs are emitted in English by Spring Cloud Netflix components.

## Verification

Use:

```bash
docker compose up -d discovery-server
curl http://localhost:8761
```

Runtime verification passed:

- `h-smart-discovery-server` started healthy on port `8761`
- Eureka registry returned these services as `UP`:
  - `API-GATEWAY`
  - `USER-SERVICE`
  - `PRODUCT-SERVICE`
  - `INTERACTION-SERVICE`
  - `SEARCH-SERVICE`
- gateway search request through Eureka LoadBalancer returned `200`
- Zipkin trace id `69f7938a4ec99cfcd7530f5ed1fe5e9e` contained:
  - `api-gateway`
  - `search-service`
  - gateway route URI tag: `lb://search-service`
