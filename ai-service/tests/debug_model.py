import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.infrastructure.config.settings import get_settings
from app.infrastructure.detectors.detectron2_detector import Detectron2Detector


def main() -> None:
    settings = get_settings()

    detector = Detectron2Detector(
        config_path=settings.config_path,
        model_path=settings.model_path,
        classes_path=settings.classes_path,
        score_threshold=settings.score_threshold,
        device=settings.device,
    )

    print("Model loaded successfully")
    print(f"Model path: {settings.model_path}")
    print(f"Config path: {settings.config_path}")
    print(f"Classes path: {settings.classes_path}")
    print(f"Num classes: {len(detector.class_names)}")
    print(f"Device: {detector.cfg.MODEL.DEVICE}")
    print(f"Score threshold: {detector.cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST}")


if __name__ == "__main__":
    main()
