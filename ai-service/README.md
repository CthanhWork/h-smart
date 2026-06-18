# AI Service

FastAPI service for YOLO household object detection.

## Models

Place the trained YOLO checkpoint in `models/`:

- `household_yolo26n_best.pt`

The service uses the 20-class Open Images household model trained with Ultralytics YOLO.

## Local run

```powershell
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## Health check

- `GET /health`

## Predict

- `POST /api/v1/predict`
- form-data key: `file`
