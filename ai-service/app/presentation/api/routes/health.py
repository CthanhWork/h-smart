from fastapi import APIRouter, Request

from app.infrastructure.config.settings import get_settings

router = APIRouter(tags=["health"])


@router.get("/health")
def health_check(request: Request) -> dict:
    settings = get_settings()
    detector = getattr(request.app.state, "detector", None)
    model_cfg = getattr(getattr(detector, "cfg", None), "MODEL", None)
    return {
        "status": "healthy",
        "service": "ai-service",
        "model_loaded": detector is not None,
        "device": model_cfg.DEVICE if model_cfg is not None else settings.device,
        "model_path": str(settings.model_path),
        "architecture": "Detectron2 Mask R-CNN R-50-FPN",
        "score_threshold": settings.score_threshold,
        "demo_mode": settings.demo_mode,
    }
