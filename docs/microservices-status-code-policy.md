# H-Smart Microservices Status Code Policy

## Purpose
This document standardizes HTTP status code usage across the H-Smart microservices system so that:

- frontend behavior is predictable
- gateway and downstream services speak the same error language
- automated tests can validate the contract consistently

## Rule of Thumb

- `2xx`: request succeeded
- `4xx`: client sent an invalid request or lacks valid authentication/authorization
- `5xx`: service or infrastructure failed to complete a valid request

## Standard Codes

| Code | Name | Owner | When to use |
| --- | --- | --- | --- |
| `200` | OK | Service | Standard success for `GET`, `PUT`, `POST` actions that do not create a new resource |
| `201` | Created | Service | New resource created successfully, e.g. `POST /api/v1/auth/register`, `POST /api/v1/products` |
| `204` | No Content | Service | Success with no response body, typically `DELETE` |
| `400` | Bad Request | Service | Validation error, malformed JSON, missing required field, invalid parameter format |
| `401` | Unauthorized | Service or Gateway | Missing JWT, invalid JWT, expired JWT, wrong login credentials |
| `403` | Forbidden | Service | Authenticated but not allowed to perform the action |
| `404` | Not Found | Service | Target resource does not exist |
| `409` | Conflict | Service | Duplicate or conflicting data, e.g. username/email already exists |
| `500` | Internal Server Error | Service | Unexpected unhandled exception inside the service |
| `502` | Bad Gateway | API Gateway | Gateway cannot connect to downstream service |
| `503` | Service Unavailable | Service or Gateway | Service temporarily unavailable, overloaded, or under maintenance |
| `504` | Gateway Timeout | API Gateway | Gateway waited too long for downstream response |

## Ownership by Layer

### Downstream services
Leaf services such as `user-service`, `product-service`, and `ai-service` should normally return:

- `200`
- `201`
- `204`
- `400`
- `401`
- `403`
- `404`
- `409`
- `500`

### API Gateway
`api-gateway` is responsible for surfacing infrastructure edge failures:

- `502`
- `504`

`503` can be returned either by the gateway or by a service, depending on where the overload/maintenance condition is detected.

## Current Mapping for `user-service`

| Endpoint | Success | Expected errors |
| --- | --- | --- |
| `POST /api/v1/auth/register` | `201` | `400`, `409`, `500` |
| `POST /api/v1/auth/login` | `200` | `400`, `401`, `500` |
| `GET /api/v1/users/profile` | `200` | `401`, `404`, `500` |
| `PUT /api/v1/users/profile` | `200` | `400`, `401`, `404`, `500` |
| `GET /health` | `200` | `500` |

## Response Contract

Controller responses should remain wrapped in:

```json
{
  "status": 200,
  "message": "Request processed successfully",
  "data": {}
}
```

For error responses:

```json
{
  "status": 400,
  "message": "Validation failed",
  "data": null
}
```

## Concrete H-Smart Examples

### `400 Bad Request`

- register payload missing `password`
- login body is malformed JSON
- profile update payload fails bean validation

### `401 Unauthorized`

- request to `/api/v1/users/profile` without bearer token
- JWT token expired
- wrong password on login

### `403 Forbidden`

- future admin-only endpoint accessed by a normal user

### `404 Not Found`

- authenticated user record no longer exists
- request for product id that does not exist

### `409 Conflict`

- registering with an existing username
- registering with an existing email

### `502/504`

- `api-gateway` cannot reach `user-service`
- `api-gateway` times out while waiting for `product-service`

## Testing Strategy

- Service-level automated tests should cover `2xx`, `400`, `401`, `403`, `404`, `409`, and `500` as far as the current service supports them.
- Gateway-level tests for `502`, `503`, and `504` should be added after `api-gateway` is implemented.
