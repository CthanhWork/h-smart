from dataclasses import dataclass


@dataclass(frozen=True)
class Detection:
    label: str
    class_id: int
    score: float
    bbox: list[float]
