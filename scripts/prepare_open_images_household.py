import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path


TARGETS = [
    ("bed", ["Bed", "Infant bed"]),
    (
        "cabinet",
        [
            "Bathroom cabinet",
            "Bookcase",
            "Cabinetry",
            "Chest of drawers",
            "Cupboard",
            "Drawer",
            "Filing cabinet",
            "Shelf",
            "Wardrobe",
        ],
    ),
    ("chair", ["Bench", "Chair", "Stool"]),
    ("table", ["Coffee table", "Kitchen & dining room table", "Table"]),
    ("desk", ["Desk"]),
    ("sofa", ["Couch", "Sofa bed", "Studio couch"]),
    ("blender", ["Blender"]),
    ("dishwasher", ["Dishwasher"]),
    ("fan", ["Ceiling fan", "Mechanical fan"]),
    ("kettle", ["Kettle"]),
    ("lamp", ["Lamp"]),
    ("microwave", ["Microwave oven"]),
    ("mirror", ["Mirror"]),
    ("oven_stove", ["Gas stove", "Oven", "Wood-burning stove"]),
    ("refrigerator", ["Refrigerator"]),
    ("sink", ["Sink"]),
    ("faucet", ["Tap"]),
    ("television", ["Television"]),
    ("toaster", ["Toaster"]),
    ("washing_machine", ["Washing machine"]),
]


def load_display_name_to_mid(path: Path) -> dict[str, str]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return {display_name: mid for mid, display_name in csv.reader(handle)}


def build_mid_mapping(class_csv: Path) -> tuple[list[str], dict[str, int]]:
    display_name_to_mid = load_display_name_to_mid(class_csv)
    names = [target for target, _ in TARGETS]
    mid_to_target: dict[str, int] = {}
    missing: list[str] = []

    for target_id, (_, source_names) in enumerate(TARGETS):
        for source_name in source_names:
            mid = display_name_to_mid.get(source_name)
            if mid is None:
                missing.append(source_name)
            else:
                mid_to_target[mid] = target_id

    if missing:
        raise ValueError(f"Missing Open Images classes: {', '.join(missing)}")
    return names, mid_to_target


def select_images(
    annotations_csv: Path,
    mid_to_target: dict[str, int],
    class_count: int,
    per_class_limit: int,
) -> tuple[set[str], list[set[str]]]:
    selected_by_class = [set() for _ in range(class_count)]

    with annotations_csv.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            target_id = mid_to_target.get(row["LabelName"])
            if target_id is None:
                continue
            if row.get("IsGroupOf") == "1" or row.get("IsDepiction") == "1":
                continue
            selected = selected_by_class[target_id]
            if len(selected) < per_class_limit:
                selected.add(row["ImageID"])

    return set().union(*selected_by_class), selected_by_class


def export_labels(
    annotations_csv: Path,
    selected_images: set[str],
    mid_to_target: dict[str, int],
    labels_dir: Path,
) -> tuple[int, int, dict[int, int]]:
    labels_dir.mkdir(parents=True, exist_ok=True)
    labels_by_image: dict[str, list[str]] = defaultdict(list)
    boxes_by_class: dict[int, int] = defaultdict(int)

    with annotations_csv.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            image_id = row["ImageID"]
            if image_id not in selected_images:
                continue
            target_id = mid_to_target.get(row["LabelName"])
            if target_id is None:
                continue
            if row.get("IsGroupOf") == "1" or row.get("IsDepiction") == "1":
                continue

            x_min = float(row["XMin"])
            x_max = float(row["XMax"])
            y_min = float(row["YMin"])
            y_max = float(row["YMax"])
            width = x_max - x_min
            height = y_max - y_min
            if width <= 0 or height <= 0:
                continue

            x_center = (x_min + x_max) / 2
            y_center = (y_min + y_max) / 2
            labels_by_image[image_id].append(
                f"{target_id} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}"
            )
            boxes_by_class[target_id] += 1

    for image_id, lines in labels_by_image.items():
        (labels_dir / f"{image_id}.txt").write_text(
            "\n".join(lines) + "\n", encoding="utf-8"
        )

    return len(labels_by_image), sum(map(len, labels_by_image.values())), boxes_by_class


def prepare_split(
    split: str,
    downloader_split: str,
    annotations_csv: Path,
    output_root: Path,
    names: list[str],
    mid_to_target: dict[str, int],
    per_class_limit: int,
) -> dict:
    selected_images, selected_by_class = select_images(
        annotations_csv,
        mid_to_target,
        len(names),
        per_class_limit,
    )
    labels_dir = output_root / "labels" / split
    image_count, box_count, boxes_by_class = export_labels(
        annotations_csv,
        selected_images,
        mid_to_target,
        labels_dir,
    )

    usable_ids = sorted(path.stem for path in labels_dir.glob("*.txt"))
    image_list_path = output_root / "metadata" / f"{split}_image_ids.txt"
    image_list_path.write_text(
        "\n".join(f"{downloader_split}/{image_id}" for image_id in usable_ids) + "\n",
        encoding="utf-8",
    )

    return {
        "split": split,
        "images": image_count,
        "boxes": box_count,
        "per_class_selected_images": {
            names[index]: len(image_ids)
            for index, image_ids in enumerate(selected_by_class)
        },
        "per_class_boxes": {
            names[index]: boxes_by_class.get(index, 0) for index in range(len(names))
        },
        "image_list": str(image_list_path),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        type=Path,
        default=Path("D:/AI_Datasource/open_images_v7_household"),
    )
    parser.add_argument("--train-per-class", type=int, default=2000)
    parser.add_argument("--val-per-class", type=int, default=300)
    args = parser.parse_args()

    root = args.root
    metadata_dir = root / "metadata"
    metadata_dir.mkdir(parents=True, exist_ok=True)

    names, mid_to_target = build_mid_mapping(
        metadata_dir / "oidv7-class-descriptions-boxable.csv"
    )
    train = prepare_split(
        "train",
        "train",
        metadata_dir / "oidv6-train-annotations-bbox.csv",
        root,
        names,
        mid_to_target,
        args.train_per_class,
    )
    val = prepare_split(
        "val",
        "validation",
        metadata_dir / "validation-annotations-bbox.csv",
        root,
        names,
        mid_to_target,
        args.val_per_class,
    )

    dataset_yaml = (
        f"path: {root.as_posix()}\n"
        "train: images/train\n"
        "val: images/val\n"
        f"nc: {len(names)}\n"
        "names:\n"
        + "\n".join(f"  {index}: {name}" for index, name in enumerate(names))
        + "\n"
    )
    (root / "dataset.yaml").write_text(dataset_yaml, encoding="utf-8")
    (root / "classes.txt").write_text("\n".join(names) + "\n", encoding="utf-8")
    (metadata_dir / "selection_summary.json").write_text(
        json.dumps({"classes": names, "train": train, "val": val}, indent=2),
        encoding="utf-8",
    )

    print(json.dumps({"classes": names, "train": train, "val": val}, indent=2))


if __name__ == "__main__":
    main()
