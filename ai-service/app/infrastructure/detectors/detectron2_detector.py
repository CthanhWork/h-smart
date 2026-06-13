from pathlib import Path
from typing import Final, Union

import cv2
import numpy as np
from detectron2 import model_zoo
from detectron2.config import get_cfg
from detectron2.engine import DefaultPredictor

from app.domain.entities.detection import Detection


CLASS_NAMES: Final[list[str]] = [
    "air_conditioner",
    "bed",
    "bedspread",
    "bench",
    "blender",
    "bunk_bed",
    "cabinet",
    "chair",
    "coffee_table",
    "sofa_bed",
    "cupboard",
    "deck_chair",
    "desk",
    "dining_table",
    "drawer",
    "electric_chair",
    "refrigerator",
    "fan",
    "faucet",
    "file_cabinet",
    "folding_chair",
    "hand_glass",
    "highchair",
    "kettle",
    "kitchen_sink",
    "kitchen_table",
    "lamp",
    "mattress",
    "microwave_oven",
    "mirror",
    "music_stool",
    "oil_lamp",
    "oven",
    "pew_(church_bench)",
    "poker_(fire_stirring_tool)",
    "pool_table",
    "recliner",
    "rocking_chair",
    "sink",
    "sofa",
    "step_stool",
    "stool",
    "stove",
    "table-tennis_table",
    "table",
    "table_lamp",
    "television_camera",
    "television_set",
    "toaster_oven",
    "vacuum_cleaner",
    "wardrobe",
    "automatic_washer",
    "water_faucet",
]

LABEL_TRANSLATIONS: Final[dict[str, str]] = {
    "air_conditioner": "Điều hòa",
    "bed": "Giường",
    "bedspread": "Ga trải giường",
    "bench": "Ghế băng",
    "blender": "Máy xay sinh tố",
    "bunk_bed": "Giường tầng",
    "cabinet": "Tủ",
    "chair": "Ghế",
    "coffee_table": "Bàn cà phê",
    "sofa_bed": "Sofa giường",
    "cupboard": "Tủ chén",
    "deck_chair": "Ghế xếp",
    "desk": "Bàn làm việc",
    "dining_table": "Bàn ăn",
    "drawer": "Ngăn kéo",
    "electric_chair": "Ghế điện",
    "refrigerator": "Tủ lạnh",
    "fan": "Quạt",
    "faucet": "Vòi nước",
    "file_cabinet": "Tủ hồ sơ",
    "folding_chair": "Ghế gấp",
    "hand_glass": "Gương cầm tay",
    "highchair": "Ghế trẻ em",
    "kettle": "Ấm đun nước",
    "kitchen_sink": "Bồn rửa bếp",
    "kitchen_table": "Bàn bếp",
    "lamp": "Đèn",
    "mattress": "Nệm",
    "microwave_oven": "Lò vi sóng",
    "mirror": "Gương",
    "music_stool": "Ghế đàn",
    "oil_lamp": "Đèn dầu",
    "oven": "Lò nướng",
    "pew_(church_bench)": "Ghế dài nhà thờ",
    "poker_(fire_stirring_tool)": "Dụng cụ cời lửa",
    "pool_table": "Bàn bi-a",
    "recliner": "Ghế tựa",
    "rocking_chair": "Ghế bập bênh",
    "sink": "Bồn rửa",
    "sofa": "Sofa",
    "step_stool": "Ghế bước",
    "stool": "Ghế đẩu",
    "stove": "Bếp",
    "table-tennis_table": "Bàn bóng bàn",
    "table": "Bàn",
    "table_lamp": "Đèn bàn",
    "television_camera": "Camera truyền hình",
    "television_set": "Tivi",
    "toaster_oven": "Lò nướng bánh",
    "vacuum_cleaner": "Máy hút bụi",
    "wardrobe": "Tủ quần áo",
    "automatic_washer": "Máy giặt",
    "water_faucet": "Vòi nước",
}

MIN_INFERENCE_IMAGE_SIDE: Final[int] = 320
MAX_INFERENCE_IMAGE_SIDE: Final[int] = 640


class Detectron2Detector:
    def __init__(
        self,
        model_path: Union[str, Path],
        score_threshold: float = 0.4,
    ) -> None:
        self.model_path = Path(model_path)

        if not self.model_path.exists():
            raise FileNotFoundError(f"Missing model file: {self.model_path}")

        self.class_names = CLASS_NAMES
        self.cfg = get_cfg()
        self.cfg.merge_from_file(
            model_zoo.get_config_file("LVISv1-InstanceSegmentation/mask_rcnn_R_50_FPN_1x.yaml")
        )
        self.cfg.MODEL.ROI_HEADS.NUM_CLASSES = len(self.class_names)
        self.cfg.MODEL.WEIGHTS = str(self.model_path)
        self.cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = score_threshold
        self.cfg.MODEL.MASK_ON = False
        self.cfg.MODEL.KEYPOINT_ON = False
        self.cfg.MODEL.DEVICE = "cpu"
        self.cfg.INPUT.MIN_SIZE_TEST = MIN_INFERENCE_IMAGE_SIDE
        self.cfg.INPUT.MAX_SIZE_TEST = MAX_INFERENCE_IMAGE_SIDE
        self.cfg.DATASETS.TRAIN = ()
        self.cfg.DATASETS.TEST = ()
        self.cfg.DATALOADER.NUM_WORKERS = 0

        self.predictor = DefaultPredictor(self.cfg)

    def predict(self, image_bytes: bytes) -> list[Detection]:
        image = self._decode_image(image_bytes)
        outputs = self.predictor(image)
        instances = outputs["instances"].to("cpu")

        if not instances.has("scores") or len(instances.scores) == 0:
            return []

        scores = instances.scores.tolist()
        class_ids = instances.pred_classes.tolist() if instances.has("pred_classes") else []
        boxes = instances.pred_boxes.tensor.tolist() if instances.has("pred_boxes") else []

        best_index = max(range(len(scores)), key=lambda index: scores[index])
        if best_index >= len(class_ids):
            return []

        class_id = int(class_ids[best_index])
        if class_id < 0 or class_id >= len(self.class_names):
            return []

        label = self.class_names[class_id]
        bbox = boxes[best_index] if best_index < len(boxes) else []

        return [
            Detection(
                label=label,
                class_id=class_id,
                score=round(float(scores[best_index]), 4),
                bbox=[round(float(value), 2) for value in bbox],
                translated_label=LABEL_TRANSLATIONS.get(label, label),
            )
        ]

    @staticmethod
    def _decode_image(image_bytes: bytes) -> np.ndarray:
        np_buffer = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(np_buffer, cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Invalid image bytes")
        return Detectron2Detector._resize_for_inference(image)

    @staticmethod
    def _resize_for_inference(image: np.ndarray) -> np.ndarray:
        height, width = image.shape[:2]
        longest_side = max(height, width)
        if longest_side <= MAX_INFERENCE_IMAGE_SIDE:
            return image

        scale = MAX_INFERENCE_IMAGE_SIDE / float(longest_side)
        target_width = max(1, int(width * scale))
        target_height = max(1, int(height * scale))
        return cv2.resize(image, (target_width, target_height), interpolation=cv2.INTER_AREA)


class DemoDetector:
    def __init__(self) -> None:
        self.class_names = CLASS_NAMES

    def predict(self, image_bytes: bytes) -> list[Detection]:
        if not image_bytes:
            raise ValueError("Invalid image bytes")

        demo_labels = [
            "chair",
            "sofa",
            "automatic_washer",
            "television_set",
            "dining_table",
            "table_lamp",
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
