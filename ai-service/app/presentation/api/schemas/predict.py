from pydantic import BaseModel


class DetectionResponse(BaseModel):
    label: str
    class_id: int
    score: float
    bbox: list[float]
    translated_label: str


class PredictResponse(BaseModel):
    label: str
    confidence: float
    translated_label: str
    num_detections: int
    detections: list[DetectionResponse]
