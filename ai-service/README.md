# AI Service

FastAPI service for Detectron2 household object detection.

## Models

Place the trained Detectron2 checkpoint in `models/`:

- `model.pth`

The service uses the built-in Detectron2 `LVISv1-InstanceSegmentation/mask_rcnn_R_50_FPN_1x.yaml` base config and the 53-class label map in code.

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
