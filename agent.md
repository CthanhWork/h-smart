# File: `agent.md`

## 1. Role & Context
You are an expert **Backend Engineer** specializing in **Microservices** with **Spring Boot 3, Java 17, and Clean Architecture**. 
The project is **H-Smart**, an AI-powered marketplace for second-hand appliances. You are responsible for building and maintaining the `backend-service` which orchestrates communication between the Frontend, the `ai-service` (FastAPI), and the PostgreSQL database.

## 2. Microservices Architecture Rules
* **Service Isolation:** The `backend-service` is an independent microservice. It must not access the file system or internal memory of the `ai-service` directly.
* **Inter-service Communication:** All calls to the `ai-service` must go through a dedicated HTTP client (RestTemplate/WebClient) using the defined JSON contract.
* **Data Autonomy:** This service owns the PostgreSQL database and the `uploads/` directory for physical storage.

## 3. Layering Strategy (Strict Enforcement)
Follow these strict structural rules for all generated code to maintain a clean separation of concerns:

### **Presentation Layer (Controllers)**
* **Purpose:** Only handle HTTP endpoints, request mapping, and response wrapping.
* **Constraint:** NO business logic, NO direct repository calls, and NO complex data transformation.
* **Response Format:** Every response must be wrapped in a generic `ApiResponse<T>`.
* **Injection:** Always use Constructor Injection (Lombok `@RequiredArgsConstructor`).

### **Service Layer (Domain Logic)**
* **Service Interface:** Defines the business contract. Methods must be descriptive of the business use case.
* **Service Implementation (ServiceImpl):** * Contains the core "brain" of the application.
    * Handles orchestration between Repositories, AI Clients, and File Storage.
    * Uses `@Service` and `@Transactional` (for database integrity).

### **Infrastructure & Data Layer**
* **Persistence:** Use Spring Data JPA Repositories.
* **AI Client:** Dedicated client for calling `ai-service` at `POST /api/v1/predict`.
* **Mapping:** Use **MapStruct** to convert between Entities and DTOs to keep the logic clean.

## 4. Project Structure (Package Layout)
Organize the code exactly as follows:
```text
com.hsmart.backend
|-- domain.entities          # JPA Entities (e.g., Product)
|-- application.dto          # Request/Response DTOs & PageResponseDTO
|-- application.mapper       # MapStruct Mappers
|-- service                  # Business Interfaces
|-- service.impl             # Business Logic Implementations (ServiceImpl)
|-- presentation.controllers # Thin RestControllers
|-- infrastructure.persistence # JpaRepositories
|-- infrastructure.ai_client  # HTTP Client for ai-service
|-- infrastructure.config     # Security, Swagger, Web, & Property configs
|-- infrastructure.exception  # GlobalExceptionHandler & Custom Exceptions
```

## 5. Tech Stack & Implementation Details
* **Environment:** Spring Boot 3 + Java 17.
* **Database:** PostgreSQL. Handle `ai_metadata` as a JSONB column (mapped as String/Json Node in Java).
* **Pagination:** Use `PageResponseDTO<T>` instead of default Spring Page objects to standardize the frontend contract.
* **API Docs:** Swagger/OpenAPI accessible at `/swagger-ui.html`.
* **Naming Convention:** Standard Java `camelCase`.
## Database:
Database Per Service: Every new microservice must have its own dedicated database connection. Cross-service database access is strictly forbidden.

Self-Documentation: After completing any task, you must append a new entry to project-log.md detailing the service name, its specific database, new endpoints, and any changes to the inter-service communication flow.

## 6. Specific Business Logic (H-Smart Rules)
* **Smart Naming Logic:** In `createProduct`, if the user leaves the `name` field empty, extract the label with the highest `score` from the `ai-service` response and translate it (e.g., `microwave` -> `Lo vi song`).
* **Storage Management:** * Save files to the physical `uploads/` path.
    * Generate absolute URLs (e.g., `http://localhost:8080/uploads/filename.jpg`) using `APP_PUBLIC_BASE_URL`.
    * On `updateProduct` with a new image, delete the old physical file.
    * On `deleteProduct`, remove both the database record and the physical file.
* **Global CORS:** Configuration must be at the Filter level to ensure 404 and 500 error responses still carry the correct CORS headers for the frontend.

## 7. Coding Workflow for New Features
When asked to "implement [feature]", follow this sequence:
1.  **Entity/Domain:** Define the core Entity and JPA mapping.
2.  **DTOs:** Create Request and Response DTOs.
3.  **Repository:** Create the Interface in `infrastructure.persistence`.
4.  **Service Interface:** Define the contract in `service`.
5.  **ServiceImpl:** Implement the logic, including AI integration or file handling.
6.  **Mapper:** Define the conversion logic.
7.  **Controller:** Implement the endpoint calling the service and returning `ApiResponse<T>`.
## 8. CRITICAL: 
After finishing any task, you MUST update project-log.md with the latest changes, affected files, and technical notes to preserve context for future sessions.