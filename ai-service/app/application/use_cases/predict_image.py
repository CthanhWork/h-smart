from app.domain.entities.detection import Detection
from app.domain.services.object_detector import ObjectDetector


class PredictImageUseCase:
    def __init__(self, detector: ObjectDetector) -> None:
        self._detector = detector

    def execute(self, image_bytes: bytes) -> list[Detection]:
        return self._detector.predict(image_bytes)
