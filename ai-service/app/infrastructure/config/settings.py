import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Optional


@dataclass(frozen=True)
class Settings:
    root_dir: Path
    model_dir: Path
    model_path: Path
    score_threshold: float
    device: Optional[str]
    demo_mode: bool


@lru_cache
def get_settings() -> Settings:
    root_dir = Path(__file__).resolve().parents[3]
    model_dir = Path(os.getenv("AI_MODEL_DIR", root_dir / "models"))

    return Settings(
        root_dir=root_dir,
        model_dir=model_dir,
        model_path=Path(os.getenv("AI_MODEL_PATH", model_dir / "model.pth")),
        score_threshold=float(os.getenv("AI_SCORE_THRESHOLD", "0.4")),
        device=os.getenv("AI_DEVICE") or "cpu",
        demo_mode=os.getenv("AI_DEMO_MODE", "false").lower() in {"1", "true", "yes", "on"},
    )
