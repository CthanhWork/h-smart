# user-service Service Notes

## 1. Muc tieu service

`user-service` la microservice phu trach hai nghiep vu:

- `Identity / Auth`
- `Profile`

Service nay duoc tach rieng khoi `backend-service` de dam bao:

- moi service co database rieng
- auth/profile co vong doi rieng
- de mo rong sau nay sang OTP, trust score, refresh token, role management

## 2. Cong nghe

- Spring Boot 3
- Java 17
- Spring Security
- Spring Data JPA
- PostgreSQL
- MapStruct
- Swagger OpenAPI
- Docker

## 3. Database

Database rieng:

- `hsmart_user_db`

Cau hinh hien tai:

- `spring.jpa.hibernate.ddl-auto=update`

Service nay khong truy cap database cua service khac.

## 4. Cau truc package

Package layout da duoc tao theo clean architecture:

```text
com.hsmart.backend
|-- domain.entities
|-- application.dto
|-- application.mapper
|-- service
|-- service.impl
|-- presentation.controllers
|-- infrastructure.persistence
|-- infrastructure.config
`-- infrastructure.exception
```

## 5. Domain model

### Entity `User`

File:

- `src/main/java/com/hsmart/backend/domain/entities/User.java`

Truong da co:

- `id`
- `username`
- `password`
- `email`
- `role`
- `fullName`
- `phoneNumber`
- `address`
- `avatarUrl`

### Enum `Role`

File:

- `src/main/java/com/hsmart/backend/domain/entities/Role.java`

Gia tri hien tai:

- `ADMIN`
- `USER`

### Ghi chu thiet ke

Profile fields hien dang duoc gop thang vao `User` de giu service gon trong giai doan dau. Chua tach `UserProfile` thanh entity rieng.

## 6. DTO da duoc tao

Trong `application.dto` da co:

- `ApiResponse<T>`
- `RegisterRequestDTO`
- `LoginRequestDTO`
- `AuthResponseDTO`
- `UserProfileResponseDTO`
- `UpdateProfileRequestDTO`

Muc dich:

- request/response ro rang
- khong expose entity truc tiep ra controller
- giu contract on dinh cho frontend

## 7. Mapper

Da dung MapStruct theo dung quy tac trong `agent.md`.

Mapper hien co:

- `src/main/java/com/hsmart/backend/application/mapper/UserMapper.java`

Mapper dang dam nhan:

- `User -> UserProfileResponseDTO`
- `UpdateProfileRequestDTO -> update vao User`
- `User + JWT -> AuthResponseDTO`

## 8. Repository

Repository hien co:

- `src/main/java/com/hsmart/backend/infrastructure/persistence/UserRepository.java`

Ham chinh:

- `findByUsername(...)`
- `findByEmail(...)`
- `findByUsernameOrEmail(...)`
- `existsByUsername(...)`
- `existsByEmail(...)`

## 9. Service layer

### `AuthService`

Interface:

- `src/main/java/com/hsmart/backend/service/AuthService.java`

Implementation:

- `src/main/java/com/hsmart/backend/service/impl/AuthServiceImpl.java`

Chuc nang:

- register user moi
- hash password bang BCrypt
- validate trung `username` / `email`
- login bang `username` hoac `email`
- sinh JWT access token

### `UserService`

Interface:

- `src/main/java/com/hsmart/backend/service/UserService.java`

Implementation:

- `src/main/java/com/hsmart/backend/service/impl/UserServiceImpl.java`

Chuc nang:

- lay profile cua user dang nhap
- cap nhat profile

## 10. Security & JWT

### Files lien quan

- `infrastructure/config/SecurityConfig.java`
- `infrastructure/config/JwtService.java`
- `infrastructure/config/JwtProperties.java`
- `infrastructure/config/JwtAuthenticationFilter.java`
- `infrastructure/config/CustomUserDetailsService.java`
- `infrastructure/config/JwtAuthenticationEntryPoint.java`
- `infrastructure/config/JwtAccessDeniedHandler.java`

### Hanh vi hien tai

- Password duoc hash bang `BCryptPasswordEncoder`
- JWT duoc sinh khi:
  - register thanh cong
  - login thanh cong
- JWT duoc parse tu header:
  - `Authorization: Bearer <token>`
- Principal hien tai la `username`

### White list hien tai

- `/api/v1/auth/**`
- `/swagger-ui.html`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/health`
- `/error`

### Bao ve hien tai

- Tat ca endpoint ngoai whitelist deu can JWT
- Unauthorized tra `ApiResponse<Void>`
- Forbidden tra `ApiResponse<Void>`

## 11. Swagger / OpenAPI

Da tich hop:

- `springdoc-openapi-starter-webmvc-ui`

Swagger UI:

- `/swagger-ui.html`

Da cau hinh Bearer auth de test JWT truc tiep trong Swagger.

File lien quan:

- `src/main/java/com/hsmart/backend/infrastructure/config/OpenApiConfig.java`

## 12. Controller layer

### `AuthController`

File:

- `src/main/java/com/hsmart/backend/presentation/controllers/AuthController.java`

Endpoint:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

### `UserController`

File:

- `src/main/java/com/hsmart/backend/presentation/controllers/UserController.java`

Endpoint:

- `GET /api/v1/users/profile`
- `PUT /api/v1/users/profile`

### `HealthController`

File:

- `src/main/java/com/hsmart/backend/presentation/controllers/HealthController.java`

Endpoint:

- `GET /health`

### Response format

Tat ca controller deu tra theo chuan:

```json
{
  "status": 200,
  "message": "message",
  "data": {}
}
```

Khong co controller nao goi repository truc tiep.

## 13. Exception handling

Global exception handler:

- `src/main/java/com/hsmart/backend/infrastructure/exception/GlobalExceptionHandler.java`

Custom exceptions da co:

- `DuplicateResourceException`
- `InvalidCredentialsException`
- `ResourceNotFoundException`

Muc dich:

- tra ve contract loi thong nhat
- khong de stacktrace leak ra API

Da bo sung handler cho:

- `HttpMessageNotReadableException`

De dam bao body JSON loi duoc tra ve:

- `400 Bad Request`

thay vi roi vao `500 Internal Server Error`.

## 14. Endpoint contract tom tat

### `POST /api/v1/auth/register`

Input:

- `username`
- `email`
- `password`
- `fullName`
- `phoneNumber`
- `address`
- `avatarUrl`

Output:

- JWT token
- thong tin user da tao

### `POST /api/v1/auth/login`

Input:

- `usernameOrEmail`
- `password`

Output:

- JWT token
- thong tin user

### `GET /api/v1/users/profile`

Auth:

- can Bearer token

Output:

- thong tin profile cua user dang dang nhap

### `PUT /api/v1/users/profile`

Auth:

- can Bearer token

Input:

- `fullName`
- `phoneNumber`
- `address`
- `avatarUrl`

Output:

- profile sau khi cap nhat

## 15. Cau hinh moi truong

File:

- `.env.example`
- `src/main/resources/application.yml`

Bien hien tai:

- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION_MS`

Mac dinh:

- service port: `8081`
- database host trong Docker: `user-postgres-db`

## 16. Docker / Compose

Da them vao `docker-compose.yml`:

- `user-postgres-db`
- `user-service`

Port mapping:

- `user-postgres-db`: host `5433` -> container `5432`
- `user-service`: host `8081` -> container `8081`

Healthcheck:

- `user-postgres-db` dung `pg_isready`
- `user-service` dung `curl http://localhost:8081/health`

## 17. Trang thai verify

Da verify:

- tao cau truc service day du
- Maven build local pass:
  - `mvn -q -DskipTests package`
- `docker compose config` pass
- Docker image build pass
- `user-postgres-db` va `user-service` start thanh cong
- `GET /health` pass
- `POST /api/v1/auth/register` pass
- `POST /api/v1/auth/login` pass
- `GET /api/v1/users/profile` pass voi JWT
- `PUT /api/v1/users/profile` pass voi JWT
- `mvn -q test` pass

Status code da duoc verify tu dong:

- `201 Created` cho register thanh cong
- `200 OK` cho login/profile/update thanh cong
- `400 Bad Request` cho validation va malformed JSON
- `401 Unauthorized` cho login sai thong tin va request profile thieu JWT
- `403 Forbidden` da duoc verify qua security handler test
- `404 Not Found` cho profile user khong ton tai
- `409 Conflict` cho duplicate username/email
- `500 Internal Server Error` cho loi unexpected trong service

Tai lieu bo sung:

- `../docs/microservices-status-code-policy.md`
- `../docs/user-service-status-testcases.md`

Test source da them:

- `src/test/java/com/hsmart/backend/presentation/controllers/UserServiceStatusCodeTest.java`
- `src/test/java/com/hsmart/backend/infrastructure/config/SecurityHandlersTest.java`
- `src/test/resources/application.yml`

Luu y test:

- Login can duoc test sau khi register commit xong.
- Khi test bang PowerShell, `Invoke-RestMethod` dang tin cay hon `curl.exe` cho JSON body.

## 18. Gioi han hien tai

Nhung phan chua co:

- refresh token
- forgot password
- OTP
- email verification
- trust score
- audit log
- role management nang cao
- avatar upload file thuc te

Hien tai `avatarUrl` chi la string URL, chua co upload media rieng cho avatar.

## 19. Huong mo rong tiep theo

Buoc tiep theo hop ly:

1. Chay Docker va verify runtime:
   - register
   - login
   - profile
   - swagger authorize

2. Them route admin:
   - list users
   - change role
   - deactivate user

3. Them refresh token flow.

4. Tach `trust / verification` thanh sub-module nghiep vu ro hon neu can.

## 20. Status code policy

`user-service` hien dang follow status code convention sau:

- `200` cho `GET/PUT` thanh cong
- `201` cho `POST /api/v1/auth/register`
- `400` cho validation loi hoac malformed JSON
- `401` cho thieu JWT hoac sai credentials
- `403` de danh cho access denied
- `404` cho resource khong ton tai
- `409` cho xung dot du lieu dang ky
- `500` cho loi unexpected

Luu y:

- `502`, `503`, `504` la nhom loi can test o tang `api-gateway` hoac integration level, khong phai la leaf-service responsibility chinh cua `user-service`.

## 21. Ket luan

`user-service` da duoc khoi tao dung huong:

- dung clean architecture
- co database rieng
- co auth JWT
- co profile API
- co Swagger bearer auth
- co status code policy ro rang va bo test tu dong
- co package structure nhat quan voi he thong hien tai

Tai thoi diem nay, service da san sang cho viec standardize contract loi va tich hop tiep vao `api-gateway`.

## 22. Observability

Runtime observability is configured for the Docker development stack.

Dashboards:

- Zipkin: `http://localhost:9411`
- Kibana: `http://localhost:5601`
- Elasticsearch API: `http://localhost:9200`

Tracing:

- Micrometer Tracing is enabled through the Brave bridge.
- Zipkin reporter sends spans to `http://zipkin:9411/api/v2/spans` inside Docker.
- Sampling is configured as `management.tracing.sampling.probability=1.0` for development.
- Gateway-propagated trace context should connect user-service spans to the original gateway request trace.

Centralized logging:

- `logback-spring.xml` sends JSON logs directly to Logstash over TCP.
- Docker destination: `logstash:5044`
- Kibana data view pattern: `hsmart-logs-*`
- Timestamp field: `@timestamp`
- Logs include `service`, `traceId`, and `spanId` fields for correlation with Zipkin traces.

Current runtime verification status:

- Maven tests pass with tracing dependencies present.
- Docker Compose configuration is valid.
- Live Zipkin request-flow verification passed after Docker Desktop was started.
- Verified request:
  - `POST http://localhost:8000/api/v1/auth/register`
- Verified Zipkin trace:
  - trace id: `69eef967827daba552e6c55045ec9834`
  - services: `api-gateway`, `user-service`
  - span count: `7`
- Verified centralized logging:
  - Elasticsearch index `hsmart-logs-2026.04.27` received service logs through Logstash
