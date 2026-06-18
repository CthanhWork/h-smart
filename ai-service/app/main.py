from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.application.use_cases.predict_image import PredictImageUseCase
from app.infrastructure.config.settings import get_settings
from app.infrastructure.detectors.yolo_detector import DemoDetector, YoloDetector
from app.presentation.api.routes.health import router as health_router
from app.presentation.api.routes.predict import router as predict_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    detector = DemoDetector() if settings.demo_mode else YoloDetector(
        model_path=settings.model_path,
        score_threshold=settings.score_threshold,
        device=settings.device,
    )
    app.state.detector = detector
    app.state.predict_image_use_case = PredictImageUseCase(detector=detector)
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title="H-Smart AI Service",
        version="0.1.0",
        description="YOLO inference service for household object detection.",
        lifespan=lifespan,
    )
    app.include_router(health_router)
    app.include_router(predict_router)
    return app


app = create_app()
