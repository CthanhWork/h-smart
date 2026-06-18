from fastapi import APIRouter, Request

from app.infrastructure.config.settings import get_settings

router = APIRouter(tags=["health"])


@router.get("/health")
def health_check(request: Request) -> dict:
    settings = get_settings()
    detector = getattr(request.app.state, "detector", None)
    return {
        "status": "healthy",
        "service": "ai-service",
        "model_loaded": detector is not None,
        "device": getattr(detector, "device", settings.device),
        "model_path": str(settings.model_path),
        "architecture": "Ultralytics YOLO household detector",
        "score_threshold": settings.score_threshold,
        "demo_mode": settings.demo_mode,
    }
