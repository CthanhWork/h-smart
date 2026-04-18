from fastapi import APIRouter, Depends, File, HTTPException, UploadFile

from app.application.use_cases.predict_image import PredictImageUseCase
from app.presentation.api.schemas.predict import DetectionResponse, PredictResponse
from app.presentation.dependencies import get_predict_image_use_case

router = APIRouter(prefix="/api/v1", tags=["predict"])


@router.post("/predict", response_model=PredictResponse)
async def predict(
    file: UploadFile = File(...),
    use_case: PredictImageUseCase = Depends(get_predict_image_use_case),
) -> PredictResponse:
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Uploaded file must be an image")

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty file")

    try:
        detections = use_case.execute(image_bytes)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return PredictResponse(
        num_detections=len(detections),
        detections=[
            DetectionResponse(
                label=detection.label,
                class_id=detection.class_id,
                score=detection.score,
                bbox=detection.bbox,
            )
            for detection in detections
        ],
    )
