import json
from pathlib import Path

import cv2
import numpy as np
import torch
from detectron2.config import get_cfg
from detectron2.engine import DefaultPredictor

from app.domain.entities.detection import Detection


class Detectron2Detector:
    def __init__(
        self,
        config_path: str | Path,
        model_path: str | Path,
        classes_path: str | Path,
        score_threshold: float = 0.4,
        device: str | None = None,
    ) -> None:
        self.config_path = Path(config_path)
        self.model_path = Path(model_path)
        self.classes_path = Path(classes_path)

        if not self.config_path.exists():
            raise FileNotFoundError(f"Missing config file: {self.config_path}")
        if not self.model_path.exists():
            raise FileNotFoundError(f"Missing model file: {self.model_path}")
        if not self.classes_path.exists():
            raise FileNotFoundError(f"Missing classes file: {self.classes_path}")

        classes_payload = json.loads(self.classes_path.read_text(encoding="utf-8"))
        if isinstance(classes_payload, list):
            self.class_names = classes_payload
            classes_score_threshold = score_threshold
        else:
            self.class_names = classes_payload["thing_classes"]
            classes_score_threshold = float(classes_payload.get("score_thresh_test", score_threshold))

        self.cfg = get_cfg()
        self.cfg.merge_from_file(str(self.config_path))
        self.cfg.MODEL.WEIGHTS = str(self.model_path)
        self.cfg.MODEL.MASK_ON = False
        self.cfg.MODEL.KEYPOINT_ON = False
        self.cfg.MODEL.ROI_HEADS.NUM_CLASSES = len(self.class_names)
        self.cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = classes_score_threshold
        self.cfg.MODEL.DEVICE = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self.cfg.DATASETS.TRAIN = ()
        self.cfg.DATASETS.TEST = ()
        self.cfg.DATALOADER.NUM_WORKERS = 0

        self.predictor = DefaultPredictor(self.cfg)

    def predict(self, image_bytes: bytes) -> list[Detection]:
        image = self._decode_image(image_bytes)
        outputs = self.predictor(image)
        instances = outputs["instances"].to("cpu")

        boxes = instances.pred_boxes.tensor.tolist() if instances.has("pred_boxes") else []
        scores = instances.scores.tolist() if instances.has("scores") else []
        class_ids = instances.pred_classes.tolist() if instances.has("pred_classes") else []

        detections: list[Detection] = []
        for box, score, class_id in zip(boxes, scores, class_ids):
            detections.append(
                Detection(
                    label=self.class_names[class_id],
                    class_id=int(class_id),
                    score=round(float(score), 4),
                    bbox=[round(float(value), 2) for value in box],
                )
            )
        return detections

    @staticmethod
    def _decode_image(image_bytes: bytes) -> np.ndarray:
        np_buffer = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(np_buffer, cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Invalid image bytes")
        return image
