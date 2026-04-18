import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    root_dir: Path
    model_dir: Path
    model_path: Path
    config_path: Path
    classes_path: Path
    score_threshold: float
    device: str | None


@lru_cache
def get_settings() -> Settings:
    root_dir = Path(__file__).resolve().parents[3]
    model_dir = Path(os.getenv("AI_MODEL_DIR", root_dir / "models"))

    return Settings(
        root_dir=root_dir,
        model_dir=model_dir,
        model_path=Path(os.getenv("AI_MODEL_PATH", model_dir / "model.pth")),
        config_path=Path(os.getenv("AI_CONFIG_PATH", model_dir / "config_infer.yaml")),
        classes_path=Path(os.getenv("AI_CLASSES_PATH", model_dir / "classes.json")),
        score_threshold=float(os.getenv("AI_SCORE_THRESHOLD", "0.4")),
        device=os.getenv("AI_DEVICE") or None,
    )
