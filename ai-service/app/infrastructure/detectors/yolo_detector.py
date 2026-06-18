from pathlib import Path
from typing import Final, Optional, Union

import cv2
import numpy as np

from app.domain.entities.detection import Detection


CLASS_NAMES: Final[list[str]] = [
    "bed",
    "cabinet",
    "chair",
    "table",
    "desk",
    "sofa",
    "blender",
    "dishwasher",
    "fan",
    "kettle",
    "lamp",
    "microwave",
    "mirror",
    "oven_stove",
    "refrigerator",
    "sink",
    "faucet",
    "television",
    "toaster",
    "washing_machine",
]

LABEL_TRANSLATIONS: Final[dict[str, str]] = {
    "bed": "Giường",
    "cabinet": "Tủ",
    "chair": "Ghế",
    "table": "Bàn",
    "desk": "Bàn làm việc",
    "sofa": "Sofa",
    "blender": "Máy xay sinh tố",
    "dishwasher": "Máy rửa chén",
    "fan": "Quạt",
    "kettle": "Ấm đun nước",
    "lamp": "Đèn",
    "microwave": "Lò vi sóng",
    "mirror": "Gương",
    "oven_stove": "Lò nướng/Bếp",
    "refrigerator": "Tủ lạnh",
    "sink": "Bồn rửa",
    "faucet": "Vòi nước",
    "television": "Tivi",
    "toaster": "Máy nướng bánh mì",
    "washing_machine": "Máy giặt",
}


class YoloDetector:
    def __init__(
        self,
        model_path: Union[str, Path],
        score_threshold: float = 0.4,
        device: Optional[str] = None,
    ) -> None:
        self.model_path = Path(model_path)

        if not self.model_path.exists():
            raise FileNotFoundError(f"Missing model file: {self.model_path}")

        self.score_threshold = score_threshold
        self.device = device or "cpu"
        from ultralytics import YOLO

        self.model = YOLO(str(self.model_path))
        self.class_names = self._resolve_class_names()

    def predict(self, image_bytes: bytes) -> list[Detection]:
        image = self._decode_image(image_bytes)
        results = self.model.predict(
            source=image,
            conf=self.score_threshold,
            device=self.device,
            verbose=False,
        )

        if not results:
            return []

        boxes = results[0].boxes
        if boxes is None or len(boxes) == 0:
            return []

        detections: list[Detection] = []
        xyxy_values = boxes.xyxy.cpu().tolist()
        scores = boxes.conf.cpu().tolist()
        class_ids = boxes.cls.cpu().tolist()

        for bbox, score, raw_class_id in zip(xyxy_values, scores, class_ids):
            class_id = int(raw_class_id)
            if class_id < 0 or class_id >= len(self.class_names):
                continue

            label = self.class_names[class_id]
            detections.append(
                Detection(
                    label=label,
                    class_id=class_id,
                    score=round(float(score), 4),
                    bbox=[round(float(value), 2) for value in bbox],
                    translated_label=LABEL_TRANSLATIONS.get(label, label),
                )
            )

        return sorted(detections, key=lambda detection: detection.score, reverse=True)

    def _resolve_class_names(self) -> list[str]:
        names = getattr(self.model, "names", None)
        if isinstance(names, dict):
            ordered_names = [str(names[index]) for index in sorted(names)]
            if ordered_names:
                return ordered_names

        if isinstance(names, list) and names:
            return [str(name) for name in names]

        return CLASS_NAMES

    @staticmethod
    def _decode_image(image_bytes: bytes) -> np.ndarray:
        np_buffer = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(np_buffer, cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Invalid image bytes")
        return image


class DemoDetector:
    def __init__(self) -> None:
        self.class_names = CLASS_NAMES

    def predict(self, image_bytes: bytes) -> list[Detection]:
        if not image_bytes:
            raise ValueError("Invalid image bytes")

        demo_labels = [
            "chair",
            "sofa",
            "washing_machine",
            "television",
            "table",
            "lamp",
        ]
        label = demo_labels[sum(image_bytes[:4096]) % len(demo_labels)]
        return [
            Detection(
                label=label,
                class_id=self.class_names.index(label),
                score=0.91,
                bbox=[],
                translated_label=LABEL_TRANSLATIONS.get(label, label),
            )
        ]
