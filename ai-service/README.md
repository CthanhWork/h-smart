# AI Service

FastAPI service de phuc vu inference Detectron2.

## Thu muc models

Can copy cac file sau vao `models/`:

- `model.pth`
- `config_infer.yaml`
- `classes.json`

Co the copy truc tiep tu bundle export cua Colab.

## Chay local

```powershell
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
pip install --no-build-isolation --no-deps "git+https://github.com/facebookresearch/detectron2.git"
uvicorn app.main:app --reload
```

## Health check

- `GET /health`

## Predict

- `POST /api/v1/predict`
- form-data key: `file`
