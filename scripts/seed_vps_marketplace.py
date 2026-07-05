#!/usr/bin/env python3
"""
Seed the live H-Smart VPS with realistic fake marketplace data.

What it does:
  1. Builds 100 SQL users on the VPS with bcrypt hashes generated in PostgreSQL.
  2. Uses the existing Open Images household dataset already on disk to collect
     300 unique real images (3 per listing).
  3. Logs into the public API as each seeded user.
  4. Creates 100 product listings with 3 unique images each.
  5. Uses the seeded admin account to approve the created products.

Default target:
  - VPS: root@100.66.247.41
  - API: https://hsmart.thatcherdev.id.vn/api/v1
  - Dataset: D:/AI_Datasource/open_images_v7_household

This script prefers repeatability over cleverness: if rerun, it will upsert
accounts by username and re-use the same deterministic image selection order.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import math
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from seed_title_generator import generate_natural_listing


DEFAULT_VPS_HOST = "root@instance-20260601-031713.tail0e1958.ts.net"
DEFAULT_VPS_REPO_DIR = "/home/thanh678x/h-smart"
DEFAULT_API_BASE_URL = "https://hsmart.thatcherdev.id.vn/api/v1"
DEFAULT_DATASET_ROOT = Path("D:/AI_Datasource/open_images_v7_household")
DEFAULT_PASSWORD = "Demo123@@"

TARGET_CLASSES = [
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

PRODUCT_CATEGORY_BY_SOURCE_CLASS = {
    "bed": "bed",
    "cabinet": "cabinet",
    "chair": "chair",
    "table": "table",
    "desk": "desk",
    "sofa": "sofa",
    "blender": "blender",
    "dishwasher": "oven",
    "fan": "fan",
    "kettle": "kettle",
    "lamp": "lamp",
    "microwave": "microwave_oven",
    "mirror": "mirror",
    "oven_stove": "stove",
    "refrigerator": "refrigerator",
    "sink": "kitchen_sink",
    "faucet": "water_faucet",
    "television": "television_set",
    "toaster": "toaster_oven",
    "washing_machine": "automatic_washer",
}

TITLE_BY_SOURCE_CLASS = {
    "bed": "Giường ngủ",
    "cabinet": "Tủ đựng đồ",
    "chair": "Ghế ngồi",
    "table": "Bàn gia đình",
    "desk": "Bàn làm việc",
    "sofa": "Sofa phòng khách",
    "blender": "Máy xay sinh tố",
    "dishwasher": "Máy rửa chén",
    "fan": "Quạt điện",
    "kettle": "Ấm đun siêu tốc",
    "lamp": "Đèn bàn",
    "microwave": "Lò vi sóng",
    "mirror": "Gương treo tường",
    "oven_stove": "Bếp nướng",
    "refrigerator": "Tủ lạnh",
    "sink": "Chậu rửa bếp",
    "faucet": "Vòi nước",
    "television": "Tivi",
    "toaster": "Máy nướng bánh mì",
    "washing_machine": "Máy giặt",
}

PRICE_RANGE_BY_SOURCE_CLASS = {
    "bed": (1_500_000, 8_000_000),
    "cabinet": (500_000, 4_000_000),
    "chair": (120_000, 1_200_000),
    "table": (300_000, 4_000_000),
    "desk": (500_000, 4_500_000),
    "sofa": (1_500_000, 15_000_000),
    "blender": (120_000, 900_000),
    "dishwasher": (3_000_000, 12_000_000),
    "fan": (90_000, 900_000),
    "kettle": (90_000, 600_000),
    "lamp": (100_000, 1_000_000),
    "microwave": (500_000, 3_500_000),
    "mirror": (100_000, 900_000),
    "oven_stove": (700_000, 6_000_000),
    "refrigerator": (2_500_000, 18_000_000),
    "sink": (250_000, 2_500_000),
    "faucet": (80_000, 600_000),
    "television": (1_200_000, 9_000_000),
    "toaster": (120_000, 700_000),
    "washing_machine": (2_500_000, 12_000_000),
}

VN_PROVINCES = [
    ("79", "Ho Chi Minh"),
    ("01", "Ha Noi"),
    ("48", "Da Nang"),
    ("31", "Hai Phong"),
    ("92", "Can Tho"),
]

VN_DISTRICTS = [
    ("760", "Thu Duc"),
    ("760A", "District 1"),
    ("760B", "District 3"),
    ("760C", "Binh Thanh"),
    ("760D", "Go Vap"),
]

VN_WARDS = [
    ("26734", "Linh Trung"),
    ("26735", "Ben Nghe"),
    ("26736", "Vo Thi Sau"),
    ("26737", "Ward 5"),
    ("26738", "Ward 6"),
]


@dataclass(frozen=True)
class ImageRecord:
    source_class: str
    image_id: str
    path: Path
    label_count: int


@dataclass(frozen=True)
class AccountSpec:
    username: str
    email: str
    full_name: str
    role: str
    province_code: str
    province: str
    district_code: str
    district: str
    ward_code: str
    ward: str
    street_detail: str
    phone_number: str
    trust_score: float
    review_count: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed the live VPS with fake marketplace data.")
    parser.add_argument("--vps-host", default=DEFAULT_VPS_HOST, help="SSH target, default root@100.66.247.41")
    parser.add_argument("--vps-repo-dir", default=DEFAULT_VPS_REPO_DIR, help="Repo path on the VPS")
    parser.add_argument("--api-base-url", default=DEFAULT_API_BASE_URL, help="Public API base URL")
    parser.add_argument("--dataset-root", type=Path, default=DEFAULT_DATASET_ROOT, help="Local Open Images household dataset root")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="Plain password used for all seeded accounts")
    parser.add_argument("--account-count", type=int, default=100, help="How many user accounts to create")
    parser.add_argument("--account-start", type=int, default=0, help="0-based offset used when numbering new accounts")
    parser.add_argument("--images-per-product", type=int, default=3, help="How many images to attach to each listing")
    parser.add_argument("--image-batch-index", type=int, default=0, help="Which 15-image slice per class to use")
    parser.add_argument("--title-batch", default="batch-1", help="Salt used to generate a distinct title set")
    parser.add_argument("--start-listing-index", type=int, default=0, help="0-based listing offset for resume runs")
    parser.add_argument("--admin-index", type=int, default=0, help="0-based index of the account that will be ADMIN")
    parser.add_argument("--dry-run", action="store_true", help="Print the plan but do not write SQL or call APIs")
    parser.add_argument("--skip-download-check", action="store_true", help="Do not enforce the 300-image availability check")
    parser.add_argument("--shuffle", action="store_true", help="Shuffle accounts after generation")
    return parser.parse_args()


def json_get(data: dict, path: str):
    cur = data
    for part in path.split("."):
        if part.isdigit():
            cur = cur[int(part)]
        else:
            cur = cur.get(part)
        if cur is None:
            return None
    return cur


def ensure_command(command: str) -> None:
    if subprocess.run(["where", command], capture_output=True, text=True).returncode != 0:
        raise SystemExit(f"Missing required command: {command}")


def run_ssh(vps_host: str, remote_command: str, input_text: str | None = None) -> subprocess.CompletedProcess[str]:
    cmd = ["ssh", vps_host, remote_command]
    return subprocess.run(cmd, input=input_text, text=True, capture_output=True, check=True)


def http_json(method: str, url: str, payload: dict | None = None, token: str | None = None) -> dict:
    body = None
    headers = {}
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def http_multipart(url: str, fields: dict[str, str], files: list[tuple[str, str, bytes, str]], token: str) -> dict:
    boundary = "----HSMARTBOUNDARY" + hashlib.sha1(os.urandom(16)).hexdigest()
    body = io.BytesIO()

    def write_line(text: str = "") -> None:
        body.write(text.encode("utf-8"))
        body.write(b"\r\n")

    for key, value in fields.items():
        write_line(f"--{boundary}")
        write_line(f'Content-Disposition: form-data; name="{key}"')
        write_line()
        write_line(value)

    for field_name, filename, content, content_type in files:
        write_line(f"--{boundary}")
        write_line(
            f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"'
        )
        write_line(f"Content-Type: {content_type}")
        write_line()
        body.write(content)
        body.write(b"\r\n")

    write_line(f"--{boundary}--")
    payload = body.getvalue()
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Authorization": f"Bearer {token}",
    }
    request = urllib.request.Request(url, data=payload, method="POST", headers=headers)
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def load_dataset_manifest(dataset_root: Path) -> tuple[list[str], list[Path]]:
    classes_path = dataset_root / "classes.txt"
    if not classes_path.exists():
        raise SystemExit(f"Missing classes file: {classes_path}")

    classes = [line.strip() for line in classes_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not classes:
        raise SystemExit(f"No classes found in {classes_path}")

    labels_root = dataset_root / "labels"
    images_root = dataset_root / "images"
    if not labels_root.exists() or not images_root.exists():
        raise SystemExit(f"Dataset root is incomplete: {dataset_root}")

    label_files: list[Path] = []
    for split in ("train", "val"):
        label_dir = labels_root / split
        image_dir = images_root / split
        if not label_dir.exists() or not image_dir.exists():
            continue
        for label_file in sorted(label_dir.glob("*.txt")):
            label_files.append(label_file)

    if not label_files:
        raise SystemExit(f"No label files found in {dataset_root}")

    return classes, label_files


def collect_usable_images(
    dataset_root: Path,
    classes: list[str],
    label_files: list[Path],
    per_class_limit: int,
) -> list[ImageRecord]:
    buckets: dict[str, list[ImageRecord]] = {name: [] for name in TARGET_CLASSES}
    images_root = dataset_root / "images"
    labels_root = dataset_root / "labels"

    for label_file in label_files:
        split = label_file.parent.name
        image_dir = images_root / split
        lines = [line.strip() for line in label_file.read_text(encoding="utf-8").splitlines() if line.strip()]
        if not lines:
            continue

        class_counts: dict[int, int] = {}
        for line in lines:
            parts = line.split()
            if not parts:
                continue
            class_id = int(parts[0])
            class_counts[class_id] = class_counts.get(class_id, 0) + 1
        if not class_counts:
            continue

        dominant_class_id = max(class_counts.items(), key=lambda item: item[1])[0]
        source_class = classes[dominant_class_id]
        if source_class not in buckets:
            continue

        if len(buckets[source_class]) >= per_class_limit:
            continue

        image_path = image_dir / f"{label_file.stem}.jpg"
        if not image_path.exists():
            image_path = image_dir / f"{label_file.stem}.png"
        if not image_path.exists():
            continue

        buckets[source_class].append(
            ImageRecord(
                source_class=source_class,
                image_id=label_file.stem,
                path=image_path,
                label_count=len(lines),
            )
        )

        if all(len(items) >= per_class_limit for items in buckets.values()):
            break

    missing = [source_class for source_class, items in buckets.items() if len(items) < per_class_limit]
    if missing:
        raise SystemExit(
            "Not enough images for: " + ", ".join(missing) + ". "
            "The local dataset did not contain enough candidates."
        )

    selected: list[ImageRecord] = []
    for source_class in TARGET_CLASSES:
        selected.extend(sorted(buckets[source_class], key=lambda rec: rec.image_id)[:per_class_limit])
    return selected


def build_accounts(count: int, password: str, admin_index: int, shuffle: bool, start_index: int = 0) -> list[AccountSpec]:
    first_names = [
        "Minh",
        "Anh",
        "Trang",
        "Huy",
        "Linh",
        "Khoa",
        "Thao",
        "Tuan",
        "Ngoc",
        "Quan",
        "Vy",
        "Phuc",
        "Thu",
        "Nam",
        "Ha",
        "Duc",
        "Bich",
        "Long",
        "My",
        "Son",
    ]
    middle_names = [
        "Van",
        "Thi",
        "Gia",
        "Hoang",
        "Quoc",
        "Dinh",
        "Thanh",
        "Huu",
        "Nguyen",
        "Kim",
    ]
    last_names = ["Nguyen", "Tran", "Le", "Pham", "Hoang", "Huynh", "Phan", "Vu", "Dang", "Bui"]

    accounts: list[AccountSpec] = []
    for index in range(count):
        global_index = start_index + index
        role = "ADMIN" if index == admin_index else "USER"
        surname = last_names[global_index % len(last_names)]
        middle = middle_names[(global_index // len(last_names)) % len(middle_names)]
        first = first_names[global_index % len(first_names)]
        full_name = f"{surname} {middle} {first}"
        username = f"seed_{'admin' if role == 'ADMIN' else 'seller'}_{global_index + 1:03d}"
        email = f"{username}@hsmart.local"
        province_code, province = VN_PROVINCES[global_index % len(VN_PROVINCES)]
        district_code, district = VN_DISTRICTS[global_index % len(VN_DISTRICTS)]
        ward_code, ward = VN_WARDS[global_index % len(VN_WARDS)]
        street_detail = f"{10 + global_index} Nguyen Trai, {district}"
        phone_number = f"09{global_index:08d}"[-10:]
        trust_score = round(4.1 + (global_index % 8) * 0.11, 2)
        review_count = 3 + (global_index % 13)
        accounts.append(
            AccountSpec(
                username=username,
                email=email,
                full_name=full_name,
                role=role,
                province_code=province_code,
                province=province,
                district_code=district_code,
                district=district,
                ward_code=ward_code,
                ward=ward,
                street_detail=street_detail,
                phone_number=phone_number,
                trust_score=trust_score,
                review_count=review_count,
            )
        )

    if shuffle:
        random.Random(20260627).shuffle(accounts)
    return accounts


def build_user_sql(accounts: list[AccountSpec], password: str) -> str:
    escaped_password = password.replace("'", "''")
    lines = [
        "BEGIN;",
        "CREATE EXTENSION IF NOT EXISTS pgcrypto;",
        "",
    ]

    def quote_sql(value: str | None) -> str:
        if value is None:
            return "NULL"
        return "'" + value.replace("'", "''") + "'"

    for account in accounts:
        values = ", ".join(
            [
                quote_sql(account.username),
                f"crypt('{escaped_password}', gen_salt('bf'))",
                quote_sql(account.email),
                quote_sql(account.role),
                quote_sql(account.full_name),
                quote_sql(account.phone_number),
                quote_sql(account.province_code),
                quote_sql(account.province),
                quote_sql(account.district_code),
                quote_sql(account.district),
                quote_sql(account.ward_code),
                quote_sql(account.ward),
                quote_sql(account.street_detail),
                "NULL",
                f"{account.trust_score}",
                f"{account.review_count}",
                "TRUE",
                "TRUE",
            ]
        )
        lines.append(
            "INSERT INTO users "
            "(username, password, email, role, full_name, phone_number, province_code, province, "
            "district_code, district, ward_code, ward, street_detail, avatar_url, trust_score, review_count, "
            "is_active, email_verified) "
            f"VALUES ({values}) "
            "ON CONFLICT (username) DO UPDATE SET "
            "password = EXCLUDED.password, "
            "email = EXCLUDED.email, "
            "role = EXCLUDED.role, "
            "full_name = EXCLUDED.full_name, "
            "phone_number = EXCLUDED.phone_number, "
            "province_code = EXCLUDED.province_code, "
            "province = EXCLUDED.province, "
            "district_code = EXCLUDED.district_code, "
            "district = EXCLUDED.district, "
            "ward_code = EXCLUDED.ward_code, "
            "ward = EXCLUDED.ward, "
            "street_detail = EXCLUDED.street_detail, "
            "avatar_url = EXCLUDED.avatar_url, "
            "trust_score = EXCLUDED.trust_score, "
            "review_count = EXCLUDED.review_count, "
            "is_active = EXCLUDED.is_active, "
            "email_verified = EXCLUDED.email_verified;"
        )
    lines.extend(["", "COMMIT;"])
    return "\n".join(lines) + "\n"


def ssh_exec_psql(vps_host: str, vps_repo_dir: str, sql: str) -> None:
    remote_command = (
        f"cd {vps_repo_dir} && "
        "docker compose -f docker-compose-gcp.yml exec -T user-postgres-db "
        "psql -U hsmart -d hsmart_user_db -v ON_ERROR_STOP=1"
    )
    subprocess.run(["ssh", vps_host, remote_command], input=sql, text=True, check=True)


def fetch_categories(api_base_url: str) -> dict[str, int]:
    response = http_json("GET", f"{api_base_url}/products/categories")
    categories = json_get(response, "data")
    if not isinstance(categories, list):
        raise SystemExit("Unable to fetch categories from API")
    result: dict[str, int] = {}
    for category in categories:
        name = category.get("name")
        category_id = category.get("id")
        if name and category_id is not None:
            result[name] = int(category_id)
    return result


def login(api_base_url: str, username: str, password: str) -> str:
    response = http_json(
        "POST",
        f"{api_base_url}/auth/login",
        {"usernameOrEmail": username, "password": password},
    )
    token = json_get(response, "data.accessToken")
    if not token:
        raise SystemExit(f"Login failed for {username}: {response}")
    return token


def create_product(
    api_base_url: str,
    token: str,
    title: str,
    description: str,
    price: int,
    category_id: int,
    images: list[Path],
) -> dict:
    fields = {
        "title": title,
        "description": description,
        "price": str(price),
        "negotiable": "false",
        "categoryId": str(category_id),
        "titleModifiedByUser": "true",
        "analysisImageIndex": "0",
    }
    files: list[tuple[str, str, bytes, str]] = []
    for image_path in images:
        content, content_type = prepare_upload_image(image_path)
        files.append((
            "files",
            image_path.name,
            content,
            content_type,
        ))
    return http_multipart(f"{api_base_url}/products", fields, files, token)


def prepare_upload_image(image_path: Path) -> tuple[bytes, str]:
    content = image_path.read_bytes()
    suffix = image_path.suffix.lower()
    if len(content) <= 800_000 and suffix in {".jpg", ".jpeg"}:
        return content, "image/jpeg"

    try:
        from PIL import Image
        import io as _io

        with Image.open(image_path) as image:
            image = image.convert("RGB")
            image.thumbnail((1280, 1280))
            buffer = _io.BytesIO()
            image.save(buffer, format="JPEG", quality=82, optimize=True)
            return buffer.getvalue(), "image/jpeg"
    except Exception:
        return content, "image/jpeg" if suffix in {".jpg", ".jpeg"} else "image/png"


def approve_product(api_base_url: str, admin_token: str, product_id: int) -> dict:
    return http_json(
        "POST",
        f"{api_base_url}/admin/products/{product_id}/moderate",
        {"action": "APPROVE"},
        admin_token,
    )


def group_triplets_by_category(selected_images: list[ImageRecord], batch_index: int) -> list[tuple[str, list[ImageRecord]]]:
    buckets: dict[str, list[ImageRecord]] = {name: [] for name in TARGET_CLASSES}
    for record in selected_images:
        buckets[record.source_class].append(record)

    ordered_groups: list[tuple[str, list[ImageRecord]]] = []
    for source_class in TARGET_CLASSES:
        images = sorted(buckets[source_class], key=lambda rec: rec.image_id)
        start = batch_index * 15
        end = start + 15
        chunk_source = images[start:end]
        if len(chunk_source) != 15:
            raise SystemExit(f"Not enough images to build batch {batch_index + 1} for {source_class}")
        for offset in range(0, 15, 3):
            chunk = chunk_source[offset:offset + 3]
            if len(chunk) != 3:
                raise SystemExit(f"Not enough images to build 3-image listing for {source_class}")
            ordered_groups.append((source_class, chunk))
    return ordered_groups


def build_description(source_class: str, product_index: int) -> str:
    label = TITLE_BY_SOURCE_CLASS[source_class]
    return (
        f"{label} dùng thực tế, còn hoạt động tốt. "
        f"Bài đăng mô phỏng {product_index:03d} phục vụ kiểm thử dữ liệu trên hệ thống."
    )


def choose_price(source_class: str, product_index: int) -> int:
    low, high = PRICE_RANGE_BY_SOURCE_CLASS[source_class]
    rng = random.Random(20260627 + product_index * 17)
    return int(rng.uniform(low, high) // 1000 * 1000)


def main() -> None:
    args = parse_args()
    ensure_command("ssh")

    if not args.dataset_root.exists():
        raise SystemExit(f"Dataset root does not exist: {args.dataset_root}")

    classes, label_files = load_dataset_manifest(args.dataset_root)
    required_images_per_class = (args.image_batch_index + 1) * 15
    selected_images = collect_usable_images(args.dataset_root, classes, label_files, required_images_per_class)

    all_accounts = build_accounts(args.account_count, args.password, args.admin_index, args.shuffle, args.account_start)
    if len(all_accounts) != args.account_count:
        raise SystemExit("Account generation failed")

    grouped_triplets = group_triplets_by_category(selected_images, args.image_batch_index)
    if len(grouped_triplets) < args.account_count:
        raise SystemExit(f"Need {args.account_count} listings, found {len(grouped_triplets)}")

    if args.start_listing_index < 0 or args.start_listing_index >= len(grouped_triplets):
        if args.start_listing_index != 0:
            raise SystemExit("start-listing-index is out of range for the current batch")
    product_accounts = all_accounts
    if args.start_listing_index:
        product_accounts = all_accounts[args.start_listing_index:]
        grouped_triplets = grouped_triplets[args.start_listing_index:]
        if len(product_accounts) != len(grouped_triplets):
            raise SystemExit("Resume slicing produced mismatched account and listing counts")

    if args.dry_run:
        print(json.dumps(
            {
                "accounts": [account.username for account in product_accounts],
                "listings": len(grouped_triplets),
                "dataset_root": str(args.dataset_root),
                "account_start": args.account_start,
                "image_batch_index": args.image_batch_index,
                "title_batch": args.title_batch,
                "start_listing_index": args.start_listing_index,
            },
            indent=2,
            ensure_ascii=False,
        ))
        return

    tmp_dir = Path("tmp")
    tmp_dir.mkdir(parents=True, exist_ok=True)
    sql_path = tmp_dir / "seed-users.sql"
    manifest_path = tmp_dir / "seed-plan.json"

    sql = build_user_sql(all_accounts, args.password)
    sql_path.write_text(sql, encoding="utf-8")

    plan = {
        "accountCount": len(all_accounts),
        "listingCount": len(grouped_triplets),
        "adminAccount": all_accounts[args.admin_index].username,
        "datasetRoot": str(args.dataset_root),
        "accountStart": args.account_start,
        "imageBatchIndex": args.image_batch_index,
        "titleBatch": args.title_batch,
        "startListingIndex": args.start_listing_index,
        "imageSelection": [
            {
                "sourceClass": source_class,
                "imageId": record.image_id,
                "path": str(record.path),
            }
            for source_class, chunk in grouped_triplets
            for record in chunk
        ],
    }
    manifest_path.write_text(json.dumps(plan, indent=2, ensure_ascii=False), encoding="utf-8")

    print("Seeding accounts on VPS...")
    ssh_exec_psql(args.vps_host, args.vps_repo_dir, sql)

    print("Fetching live product categories...")
    categories = fetch_categories(args.api_base_url)

    created_products: list[dict] = []
    tokens: dict[str, str] = {}
    admin_username = all_accounts[args.admin_index].username

    for account in all_accounts:
        tokens[account.username] = login(args.api_base_url, account.username, args.password)

    admin_token = tokens[admin_username]

    print("Creating products...")
    for index, ((source_class, chunk), account) in enumerate(zip(grouped_triplets, product_accounts), start=args.start_listing_index + 1):
        product_category_name = PRODUCT_CATEGORY_BY_SOURCE_CLASS[source_class]
        if product_category_name not in categories:
            raise SystemExit(f"Missing category in live API: {product_category_name}")
        category_id = categories[product_category_name]
        images = [record.path for record in chunk]
        title, description = generate_natural_listing(source_class, index, args.title_batch)
        price = choose_price(source_class, index)

        response = create_product(
            args.api_base_url,
            tokens[account.username],
            title,
            description,
            price,
            category_id,
            images,
        )
        product = json_get(response, "data")
        if not isinstance(product, dict) or product.get("id") is None:
            raise SystemExit(f"Product creation failed for {account.username}: {response}")
        created_products.append({
            "account": account.username,
            "productId": product["id"],
            "sourceClass": source_class,
            "categoryId": category_id,
            "title": title,
            "images": [str(path) for path in images],
        })
        print(f"[{index:03d}] created product {product['id']} for {account.username}")
        time.sleep(0.05)

    print("Approving products as admin...")
    for item in created_products:
        product_id = int(item["productId"])
        approve_product(args.api_base_url, admin_token, product_id)
        print(f"approved product {product_id}")
        time.sleep(0.03)

    result = {
        "accounts": [account.username for account in product_accounts],
        "adminAccount": admin_username,
        "products": created_products,
        "sqlPath": str(sql_path),
        "manifestPath": str(manifest_path),
        "datasetRoot": str(args.dataset_root),
        "accountStart": args.account_start,
        "imageBatchIndex": args.image_batch_index,
        "titleBatch": args.title_batch,
        "startListingIndex": args.start_listing_index,
        "apiBaseUrl": args.api_base_url,
    }
    (tmp_dir / "seed-result.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
