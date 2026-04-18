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
        "device": getattr(getattr(detector, "cfg", None), "MODEL", None).DEVICE if detector is not None else settings.device,
        "model_path": str(settings.model_path),
        "config_path": str(settings.config_path),
        "classes_path": str(settings.classes_path),
    }
