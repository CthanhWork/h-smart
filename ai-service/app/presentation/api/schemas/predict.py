from pydantic import BaseModel


class DetectionResponse(BaseModel):
    label: str
    class_id: int
    score: float
    bbox: list[float]


class PredictResponse(BaseModel):
    num_detections: int
    detections: list[DetectionResponse]
