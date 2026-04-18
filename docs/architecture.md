# Kien truc tong quan

## 1. Dich vu

- `ai-service`
  - Nhan anh.
  - Chay inference Detectron2.
  - Tra ve JSON contract co `label`, `class_id`, `score`, `bbox`.

- `backend-service`
  - Nhan request tu frontend.
  - Upload va luu anh.
  - Goi `ai-service`.
  - Ghi `ai_metadata` vao database.
  - Tra du lieu tong hop ve frontend.

## 2. Data flow

1. Frontend gui anh len `backend-service`.
2. `backend-service` goi `ai-service`.
3. `ai-service` tra ve danh sach detection.
4. `backend-service` luu metadata va xu ly nghiep vu tiep.
5. Frontend hien thi box, label, confidence.

## 3. API contract de backend bam theo

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

## 4. Ghi chu trien khai

- `ai-service` duoc uu tien hoan thien truoc.
- `backend-service` giu cau truc clean architecture, chua khoa chat vao Node hay Spring o giai doan nay.
