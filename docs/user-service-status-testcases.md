# User-Service Status Code Testcases

## Scope
These testcases validate that `user-service` follows the H-Smart microservices status code policy for authentication and profile endpoints.

## Automated Cases

| ID | Endpoint | Scenario | Expected status |
| --- | --- | --- | --- |
| `US-AUTH-001` | `POST /api/v1/auth/register` | Valid registration payload | `201` |
| `US-AUTH-002` | `POST /api/v1/auth/register` | Missing required field | `400` |
| `US-AUTH-003` | `POST /api/v1/auth/register` | Username or email already exists | `409` |
| `US-AUTH-004` | `POST /api/v1/auth/login` | Valid login payload | `200` |
| `US-AUTH-005` | `POST /api/v1/auth/login` | Wrong credentials | `401` |
| `US-AUTH-006` | `POST /api/v1/auth/login` | Malformed JSON body | `400` |
| `US-AUTH-007` | `POST /api/v1/auth/login` | Unexpected server exception | `500` |
| `US-USER-001` | `GET /api/v1/users/profile` | Missing JWT | `401` |
| `US-USER-002` | `GET /api/v1/users/profile` | Authenticated user exists | `200` |
| `US-USER-003` | `GET /api/v1/users/profile` | Authenticated principal not found in DB | `404` |
| `US-USER-004` | `PUT /api/v1/users/profile` | Valid authenticated profile update | `200` |
| `US-SEC-001` | security handler | Access denied handler invoked | `403` |

## Manual / Future Cases

| ID | Scope | Scenario | Expected status |
| --- | --- | --- | --- |
| `GW-001` | `api-gateway` | Gateway cannot connect to `user-service` | `502` |
| `GW-002` | `api-gateway` | Gateway times out waiting for `user-service` | `504` |
| `GW-003` | `api-gateway` or service | Service maintenance or overload | `503` |

## Notes

- `403` is validated at the handler level because `user-service` does not currently expose a role-restricted endpoint.
- `502`, `503`, and `504` are not leaf-service responsibilities and should be tested after `api-gateway` is introduced.
