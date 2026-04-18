from fastapi import HTTPException, Request

from app.application.use_cases.predict_image import PredictImageUseCase


def get_predict_image_use_case(request: Request) -> PredictImageUseCase:
    use_case = getattr(request.app.state, "predict_image_use_case", None)
    if use_case is None:
        raise HTTPException(status_code=503, detail="Model is not initialized")
    return use_case
