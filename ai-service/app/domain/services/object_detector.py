from typing import Protocol

from app.domain.entities.detection import Detection


class ObjectDetector(Protocol):
    def predict(self, image_bytes: bytes) -> list[Detection]:
        ...
