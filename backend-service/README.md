# Backend Service

Backend service nay da duoc chuyen sang Spring Boot 3, Maven va Java 17 de lam cau noi nghiep vu toi `ai-service`.

## Clean architecture map

```text
src/main/java/com/hsmart/backend/
|-- domain/entities
|-- application/dto
|-- infrastructure/ai_client
|-- infrastructure/config
`-- presentation/controllers
```

## Thanh phan chinh

- `domain/entities/Product.java`: entity san pham.
- `domain/entities/Detection.java`: entity detection cua AI.
- `application/dto/DetectionDTO.java`: DTO khop JSON contract cua AI service.
- `application/dto/PredictResponseDTO.java`: DTO response cua AI service.
- `infrastructure/ai_client/VisionClient.java`: adapter goi `POST /api/v1/predict`.
- `presentation/controllers/HealthController.java`: endpoint kiem tra backend.

## API contract backend dang bam theo

```json
{
  "num_detections": 1,
  "detections": [
    {
      "label": "chair",
      "class_id": 7,
      "score": 0.9132,
      "bbox": [120.4, 55.2, 380.8, 420.1]
    }
  ]
}
```

## Chay local

```powershell
mvn spring-boot:run
```

## Docker

Backend duoc build qua `docker-compose.yml` tai thu muc goc monorepo.
