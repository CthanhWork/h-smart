import argparse
import mimetypes
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFont


DEFAULT_API_URL = "http://localhost:8000/api/v1/predict"
OUTPUT_PATH = Path(__file__).resolve().parent / "output" / "result.jpg"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Test ai-service predict API with a local image.")
    parser.add_argument("image_path", type=Path, help="Duong dan toi file anh can test.")
    parser.add_argument(
        "--url",
        default=DEFAULT_API_URL,
        help=f"Predict API URL. Mac dinh: {DEFAULT_API_URL}",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=60.0,
        help="Thoi gian cho request HTTP, tinh theo giay.",
    )
    return parser.parse_args()


def validate_image_path(image_path: Path) -> Path:
    if not image_path.exists():
        raise FileNotFoundError(f"Khong tim thay file anh: {image_path}")
    if not image_path.is_file():
        raise ValueError(f"Duong dan khong phai file: {image_path}")
    return image_path


def call_predict_api(image_path: Path, url: str, timeout: float) -> dict:
    mime_type, _ = mimetypes.guess_type(str(image_path))
    if mime_type is None:
        mime_type = "application/octet-stream"

    with image_path.open("rb") as image_file:
        response = requests.post(
            url,
            files={"file": (image_path.name, image_file, mime_type)},
            timeout=timeout,
        )

    response.raise_for_status()
    return response.json()


def format_bbox(bbox: list[float]) -> str:
    return f"[{bbox[0]:.2f}, {bbox[1]:.2f}, {bbox[2]:.2f}, {bbox[3]:.2f}]"


def print_detections_table(detections: list[dict]) -> None:
    if not detections:
        print("AI khong tim thay do gia dung nao trong anh nay")
        return

    label_width = max(len("Ten mon do"), *(len(str(item.get("label", ""))) for item in detections))
    confidence_width = len("Confidence")
    bbox_values = [format_bbox(item.get("bbox", [0.0, 0.0, 0.0, 0.0])) for item in detections]
    bbox_width = max(len("BBox"), *(len(value) for value in bbox_values))

    header = (
        f"{'Ten mon do'.ljust(label_width)} | "
        f"{'Confidence'.ljust(confidence_width)} | "
        f"{'BBox'.ljust(bbox_width)}"
    )
    separator = f"{'-' * label_width}-+-{'-' * confidence_width}-+-{'-' * bbox_width}"

    print(header)
    print(separator)
    for item, bbox_text in zip(detections, bbox_values):
        label = str(item.get("label", "")).ljust(label_width)
        confidence = f"{float(item.get('score', 0.0)):.4f}".ljust(confidence_width)
        print(f"{label} | {confidence} | {bbox_text.ljust(bbox_width)}")


def draw_detections(image_path: Path, detections: list[dict], output_path: Path) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with Image.open(image_path) as image:
        image = image.convert("RGB")
        draw = ImageDraw.Draw(image)
        font = ImageFont.load_default()

        for item in detections:
            bbox = item.get("bbox", [])
            if len(bbox) != 4:
                continue

            x1, y1, x2, y2 = [float(value) for value in bbox]
            label = str(item.get("label", "unknown"))
            score = float(item.get("score", 0.0))
            text = f"{label} {score:.2f}"

            draw.rectangle((x1, y1, x2, y2), outline="red", width=3)

            text_bbox = draw.textbbox((x1, y1), text, font=font)
            text_bg = (
                text_bbox[0] - 4,
                text_bbox[1] - 2,
                text_bbox[2] + 4,
                text_bbox[3] + 2,
            )
            draw.rectangle(text_bg, fill="red")
            draw.text((x1, y1), text, fill="white", font=font)

        image.save(output_path, format="JPEG")

    return output_path


def main() -> None:
    args = parse_args()
    image_path = validate_image_path(args.image_path)

    try:
        payload = call_predict_api(image_path=image_path, url=args.url, timeout=args.timeout)
    except requests.RequestException as exc:
        raise SystemExit(f"Khong goi duoc API {args.url}: {exc}") from exc

    detections = payload.get("detections", [])
    if not isinstance(detections, list):
        raise SystemExit("Response JSON khong hop le: 'detections' khong phai list.")

    print_detections_table(detections)
    saved_path = draw_detections(image_path=image_path, detections=detections, output_path=OUTPUT_PATH)
    print(f"Da luu anh ket qua tai: {saved_path}")


if __name__ == "__main__":
    main()
