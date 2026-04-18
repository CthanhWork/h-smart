# H-Smart

H-Smart is an AI-powered marketplace for second-hand household appliances. The platform is built as a microservices system so that authentication, product listing, real-time interaction, and AI inference can evolve independently while remaining connected through a single API Gateway.

## Architecture Overview

The backend follows a microservices architecture with a clear service boundary per business domain:

- `api-gateway`
  - Single public entry point
  - JWT validation
  - Request routing
  - Global CORS
  - Gateway-level error normalization
- `user-service`
  - User registration and login
  - JWT issuance
  - User profile management
- `product-service`
  - Category management
  - Product listing CRUD
  - Media storage
  - AI integration for product analysis
- `interaction-service`
  - Real-time chat
  - Notification delivery
  - Chat history persistence
- `ai-service`
  - Detectron2 inference service
  - Appliance object detection
  - Structured AI metadata extraction

## Running Services

The current local runtime layout is:

| Service | Port | Purpose |
| --- | --- | --- |
| `api-gateway` | `8000` | Public gateway, JWT validation, routing |
| `user-service` | `8081` | Identity and profile |
| `product-service` | `8082` | Catalog, product posts, media, AI integration |
| `interaction-service` | `8083` | WebSocket chat, notifications, MongoDB-backed interaction flows |
| `ai-service` | `8002` | Detectron2 inference runtime exposed from container port `8000` |

Supporting databases:

- PostgreSQL for `user-service`
- PostgreSQL for `product-service`
- MongoDB for `interaction-service`

## Core Features

### Smart Naming

`product-service` integrates with `ai-service` to analyze uploaded appliance images. If the seller leaves the product title empty, the service automatically chooses the highest-confidence AI label and converts it into a human-friendly product name.

### JWT Gateway Validation

`api-gateway` is the only public backend entry point. It validates JWT tokens before forwarding protected requests and injects `X-User-Id` into downstream requests. This keeps service-level identity handling consistent without duplicating token parsing logic across every service.

### WebSocket Chat

`interaction-service` provides STOMP-over-WebSocket messaging for buyer-seller communication. Chat messages are persisted in MongoDB and delivered in real time through user-specific queues. Every incoming chat message can also generate an automatic notification for the receiver.

### AI Metadata Extraction

`ai-service` returns structured inference results such as object labels, confidence scores, and bounding boxes. `product-service` stores this metadata so the frontend can later visualize or reuse the detection results.

## Tech Stack

### Backend

- Java 17
- Spring Boot 3
- Spring Cloud Gateway
- Spring Security
- Spring Data JPA
- Spring Data MongoDB
- Spring WebSocket with STOMP
- MapStruct
- Lombok

### AI

- Python 3.10
- FastAPI
- PyTorch
- Detectron2

### Databases and Infrastructure

- PostgreSQL
- MongoDB
- Docker
- Docker Compose

## Request Flow

The standard request path is:

1. Frontend sends a request to `api-gateway`
2. `api-gateway` validates the JWT
3. `api-gateway` forwards the request to the target service
4. Downstream services use `X-User-Id` for identity-aware business logic
5. If needed, `product-service` calls `ai-service`
6. `interaction-service` handles real-time chat and notification fan-out

## Service Responsibilities

### `api-gateway`

- Validates JWT tokens
- Forwards `X-User-Id`
- Routes HTTP requests
- Routes WebSocket upgrade traffic for interaction flows
- Returns standardized gateway errors such as `502`, `503`, and `504`

### `user-service`

- Registers users
- Authenticates credentials
- Issues JWT tokens
- Exposes profile read and update endpoints

### `product-service`

- Manages categories
- Creates, updates, lists, and soft-deletes products
- Stores media files
- Generates absolute media URLs through the gateway
- Calls `ai-service` for image analysis

### `interaction-service`

- Accepts authenticated REST requests for notifications and message history
- Accepts authenticated WebSocket connections through the gateway
- Stores chat messages in MongoDB
- Pushes messages to `/user/queue/messages`
- Pushes notifications to `/user/queue/notifications`

### `ai-service`

- Loads the trained Detectron2 model
- Runs inference on uploaded images
- Returns detection objects with labels, scores, and bounding boxes

## Local Development

To start the full system locally:

```bash
docker compose up -d --build
```

Useful checks:

```bash
docker compose ps -a
curl http://localhost:8000/health
```

## API and Runtime Notes

- `api-gateway` is the recommended access point for the frontend
- Public media is exposed through the gateway
- Protected REST endpoints require JWT
- WebSocket interaction traffic can authenticate through:
  - `ws://localhost:8000/api/v1/interactions/ws?token=<jwt>`

## Project Direction

H-Smart is designed to support:

- reliable authentication and ownership validation
- AI-assisted product posting
- real-time buyer-seller communication
- clean service boundaries for future scaling

This repository reflects the current backend and AI integration state of the project and is structured to support incremental delivery without collapsing service responsibilities into a monolith.
