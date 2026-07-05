#!/usr/bin/env python3
"""
Seed the H-Smart VPS with 100 seller accounts and 100 listings backed by the
downloaded open-image manifest.

Each listing gets 3 unique images. The script:
  1. Creates or updates seller accounts via SQL over SSH.
  2. Logs in through the public API.
  3. Creates one listing per seller with 3 unique images.
  4. Approves the listings using an existing admin account.

Default image source:
  D:/H-smart/tmp/category-images-300-run/manifests/manifest.csv
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
from dataclasses import dataclass
from pathlib import Path

from seed_vps_marketplace import (
    build_accounts,
    build_user_sql,
    create_product,
    ensure_command,
    fetch_categories,
    json_get,
    login,
    approve_product,
    ssh_exec_psql,
)


DEFAULT_VPS_HOST = "root@100.66.247.41"
DEFAULT_VPS_REPO_DIR = "/home/thanh678x/h-smart"
DEFAULT_API_BASE_URL = "https://hsmart.thatcherdev.id.vn/api/v1"
DEFAULT_MANIFEST_CSV = Path("D:/H-smart/tmp/category-images-300-run/manifests/manifest.csv")
DEFAULT_PASSWORD = "Demo123@@"
DEFAULT_APPROVAL_ADMIN = "seed_admin_101"

API_CATEGORY_BY_IMAGE_CATEGORY = {
    "bed": "bed",
    "bookshelf": "cabinet",
    "cabinet": "cabinet",
    "chair": "chair",
    "coffee_maker": "kettle",
    "desk": "desk",
    "dishwasher": "oven",
    "fan": "fan",
    "faucet": "water_faucet",
    "kettle": "kettle",
    "lamp": "lamp",
    "mirror": "mirror",
    "oven_stove": "stove",
    "refrigerator": "refrigerator",
    "sink": "kitchen_sink",
    "sofa": "sofa",
    "table": "table",
    "television": "television_set",
    "toaster": "toaster_oven",
    "washing_machine": "automatic_washer",
}

TITLE_DATA = {
    "bed": {
        "nouns": ["Giường ngủ", "Khung giường", "Giường gỗ", "Giường đôi", "Giường đơn"],
        "features": ["1m6", "1m8", "kèm nệm mỏng", "khung chắc", "gỗ màu sáng"],
        "states": ["còn dùng tốt", "ít trầy", "đã vệ sinh sạch", "không rung lắc"],
        "reasons": ["bán vì đổi nội thất", "thanh lý do chuyển nhà", "không còn nhu cầu dùng"],
    },
    "bookshelf": {
        "nouns": ["Kệ sách", "Tủ sách", "Kệ gỗ để sách", "Kệ trang trí", "Kệ đựng đồ"],
        "features": ["nhiều tầng", "gỗ chắc", "dáng gọn", "dễ sát tường", "màu gỗ sáng"],
        "states": ["còn chắc chắn", "ít trầy", "để kệ đẹp", "đã lau sạch"],
        "reasons": ["đổi sang kệ lớn hơn", "thanh lý do sắp xếp lại nhà", "bán vì đổi decor"],
    },
    "cabinet": {
        "nouns": ["Tủ đựng đồ", "Tủ gỗ", "Kệ tủ đa năng", "Tủ nhỏ", "Tủ phòng khách"],
        "features": ["nhiều ngăn", "2 cánh", "gỗ chắc", "dáng gọn", "dễ kê"],
        "states": ["còn chắc", "ít xước", "đóng mở tốt", "đã vệ sinh sạch"],
        "reasons": ["bán vì đổi nội thất", "thanh lý do chuyển nhà", "nhượng lại giá tốt"],
    },
    "chair": {
        "nouns": ["Ghế gỗ", "Ghế ngồi", "Ghế ăn", "Ghế làm việc", "Ghế đơn"],
        "features": ["lưng tựa", "chân chắc", "mặt nệm", "dáng gọn", "dễ bố trí"],
        "states": ["còn chắc chắn", "ngồi êm", "ít dùng", "đã lau sạch"],
        "reasons": ["bán vì đổi bộ ghế mới", "thanh lý bớt đồ", "nhượng lại giá mềm"],
    },
    "coffee_maker": {
        "nouns": ["Máy pha cà phê", "Máy espresso", "Máy pha cà phê mini", "Máy pha cafe", "Máy pha cà phê gia đình"],
        "features": ["pha ổn", "còn dây nguồn", "dáng gọn", "dễ sử dụng", "hợp bếp nhỏ"],
        "states": ["vận hành tốt", "ít dùng", "đã vệ sinh sạch", "còn đẹp"],
        "reasons": ["bán vì đổi máy mới", "thanh lý do ít dùng", "nhượng lại giá tốt"],
    },
    "desk": {
        "nouns": ["Bàn làm việc", "Bàn học", "Bàn máy tính", "Bàn gỗ nhỏ", "Bàn workspace"],
        "features": ["mặt rộng", "chân sắt chắc", "có hộc kéo", "dáng tối giản", "dễ sát tường"],
        "states": ["còn cứng cáp", "ít trầy", "đã vệ sinh sạch", "dùng ổn định"],
        "reasons": ["đổi bàn lớn hơn", "thanh lý do chuyển nhà", "bán vì đổi setup"],
    },
    "dishwasher": {
        "nouns": ["Máy rửa chén", "Máy rửa bát", "Máy rửa chén mini", "Máy rửa chén gia đình", "Máy rửa chén độc lập"],
        "features": ["khoang máy sạch", "rửa ổn", "dáng gọn", "dễ bố trí", "hợp căn hộ"],
        "states": ["hoạt động bình thường", "ít dùng", "đã vệ sinh sạch", "còn ổn định"],
        "reasons": ["đổi sang máy lớn hơn", "thanh lý do chuyển nhà", "không còn nhu cầu dùng"],
    },
    "fan": {
        "nouns": ["Quạt điện", "Quạt đứng", "Quạt bàn", "Quạt treo tường", "Quạt máy"],
        "features": ["gió mạnh", "3 tốc độ", "chạy êm", "có đảo chiều", "dễ di chuyển"],
        "states": ["còn mát", "ít ồn", "đã lau sạch", "dùng tốt"],
        "reasons": ["đổi sang quạt mới", "thanh lý bớt đồ", "bán vì chuyển nhà"],
    },
    "faucet": {
        "nouns": ["Vòi nước", "Vòi rửa chén", "Vòi lavabo", "Vòi bếp", "Bộ vòi nước"],
        "features": ["inox", "đầu vòi còn tốt", "dễ lắp đặt", "đóng mở nhẹ", "dáng gọn"],
        "states": ["không rò rỉ", "còn sáng", "đã vệ sinh sạch", "dùng ổn"],
        "reasons": ["dư sau khi sửa nhà", "đổi mẫu mới", "thanh lý nhanh"],
    },
    "kettle": {
        "nouns": ["Ấm đun siêu tốc", "Bình đun nước", "Ấm điện", "Ấm đun mini", "Bình đun gia đình"],
        "features": ["1.5 lít", "1.8 lít", "tự ngắt", "thân inox", "đế rời"],
        "states": ["đun nhanh", "không rò nước", "còn sạch", "ít dùng"],
        "reasons": ["có ấm mới", "bán do ít dùng", "cần dọn bếp"],
    },
    "lamp": {
        "nouns": ["Đèn bàn", "Đèn ngủ", "Đèn học", "Đèn trang trí", "Đèn làm việc"],
        "features": ["ánh sáng ấm", "dáng gọn", "chân chắc", "dễ decor", "đầu đèn linh hoạt"],
        "states": ["sáng ổn", "còn đẹp", "đã lau sạch", "ít dùng"],
        "reasons": ["đổi mẫu đèn mới", "bán vì không dùng nữa", "thanh lý bớt đồ"],
    },
    "mirror": {
        "nouns": ["Gương soi", "Gương treo tường", "Gương trang trí", "Gương đứng", "Gương phòng ngủ"],
        "features": ["khung đẹp", "kích thước vừa", "dễ decor", "có móc treo", "dáng gọn"],
        "states": ["không nứt vỡ", "còn sáng", "ít xước", "đã lau sạch"],
        "reasons": ["đổi gương lớn hơn", "bán vì đổi decor", "thanh lý do chuyển nhà"],
    },
    "oven_stove": {
        "nouns": ["Bếp nướng", "Lò nướng", "Bếp điện", "Bếp gia đình", "Lò nướng nhỏ"],
        "features": ["có khay", "nhiệt đều", "có hẹn giờ", "dáng gọn", "dễ sử dụng"],
        "states": ["nướng ổn", "đã vệ sinh sạch", "ít dùng", "còn dùng tốt"],
        "reasons": ["đổi lò lớn hơn", "bán vì ít nấu", "cần dọn bếp"],
    },
    "refrigerator": {
        "nouns": ["Tủ lạnh", "Tủ lạnh mini", "Tủ mát", "Tủ lạnh gia đình", "Tủ lạnh nhỏ"],
        "features": ["làm lạnh tốt", "ngăn đá ổn", "ít hao điện", "dáng gọn", "2 cánh"],
        "states": ["chạy êm", "còn đẹp", "đã vệ sinh sạch", "dùng ổn định"],
        "reasons": ["đổi tủ lớn hơn", "thanh lý do chuyển nhà", "không còn nhu cầu dùng"],
    },
    "sink": {
        "nouns": ["Chậu rửa bếp", "Bồn rửa", "Chậu rửa inox", "Bồn rửa gia đình", "Chậu rửa đơn"],
        "features": ["lòng sâu", "inox dày", "có bộ xả", "dễ lắp đặt", "kích thước vừa"],
        "states": ["còn sáng", "không thủng", "ít trầy", "đã vệ sinh sạch"],
        "reasons": ["dư sau sửa nhà", "thanh lý do đổi bếp", "nhượng lại nhanh"],
    },
    "sofa": {
        "nouns": ["Sofa phòng khách", "Ghế sofa", "Sofa băng", "Sofa nhỏ", "Sofa vải"],
        "features": ["2 chỗ", "3 chỗ", "nệm dày", "màu trung tính", "kích thước gọn"],
        "states": ["ngồi êm", "khung chắc", "ít xước", "đã hút bụi sạch"],
        "reasons": ["đổi sofa mới", "bán vì đổi layout", "thanh lý do chuyển nhà"],
    },
    "table": {
        "nouns": ["Bàn gỗ", "Bàn ăn", "Bàn cafe", "Bàn phòng khách", "Bàn nhỏ"],
        "features": ["mặt rộng", "chân chắc", "mặt kính", "dáng gọn", "gỗ màu sáng"],
        "states": ["còn chắc chắn", "ít xước", "đã vệ sinh", "dùng tốt"],
        "reasons": ["đổi bàn mới", "bán vì chuyển nhà", "không còn nhu cầu dùng"],
    },
    "television": {
        "nouns": ["Tivi", "Smart TV", "Tivi màn hình phẳng", "Tivi phòng ngủ", "Tivi gia đình"],
        "features": ["32 inch", "40 inch", "có remote", "hình ảnh rõ", "âm thanh ổn"],
        "states": ["xem tốt", "màn hình sáng", "ít dùng", "ngoại hình còn đẹp"],
        "reasons": ["đổi tivi lớn hơn", "bán vì ít xem", "thanh lý do chuyển nhà"],
    },
    "toaster": {
        "nouns": ["Máy nướng bánh mì", "Lò nướng mini", "Máy nướng sandwich", "Máy nướng bánh", "Lò nướng nhỏ"],
        "features": ["2 khe", "nóng nhanh", "dễ vệ sinh", "có khay vụn", "dáng gọn"],
        "states": ["nướng ổn", "ít dùng", "còn sạch", "hoạt động tốt"],
        "reasons": ["đổi máy mới", "bán vì ít dùng", "cần dọn bếp"],
    },
    "washing_machine": {
        "nouns": ["Máy giặt", "Máy giặt mini", "Máy giặt cửa trước", "Máy giặt gia đình", "Máy giặt cửa trên"],
        "features": ["7kg", "8kg", "9kg", "vắt khỏe", "lồng giặt sạch"],
        "states": ["giặt vắt ổn", "chạy êm", "đã vệ sinh lồng giặt", "còn dùng tốt"],
        "reasons": ["đổi máy lớn hơn", "bán vì ít dùng", "thanh lý do chuyển nhà"],
    },
}

PRICE_RANGE_BY_CATEGORY = {
    "bed": (1_500_000, 8_000_000),
    "bookshelf": (400_000, 3_000_000),
    "cabinet": (500_000, 4_000_000),
    "chair": (120_000, 1_200_000),
    "coffee_maker": (350_000, 4_500_000),
    "desk": (500_000, 4_500_000),
    "dishwasher": (3_000_000, 12_000_000),
    "fan": (90_000, 900_000),
    "faucet": (80_000, 600_000),
    "kettle": (90_000, 600_000),
    "lamp": (100_000, 1_000_000),
    "mirror": (100_000, 900_000),
    "oven_stove": (700_000, 6_000_000),
    "refrigerator": (2_500_000, 18_000_000),
    "sink": (250_000, 2_500_000),
    "sofa": (1_500_000, 15_000_000),
    "table": (300_000, 4_000_000),
    "television": (1_200_000, 9_000_000),
    "toaster": (120_000, 700_000),
    "washing_machine": (2_500_000, 12_000_000),
}

CATEGORY_FAMILY = {
    "bed": "furniture",
    "bookshelf": "furniture",
    "cabinet": "furniture",
    "chair": "furniture",
    "desk": "furniture",
    "mirror": "furniture",
    "sink": "fixture",
    "faucet": "fixture",
    "sofa": "furniture",
    "table": "furniture",
    "coffee_maker": "appliance",
    "dishwasher": "appliance",
    "fan": "appliance",
    "kettle": "appliance",
    "lamp": "appliance",
    "oven_stove": "appliance",
    "refrigerator": "appliance",
    "television": "electronics",
    "toaster": "appliance",
    "washing_machine": "appliance",
}


@dataclass(frozen=True)
class ImageRecord:
    category: str
    path: Path
    title: str
    relevance_score: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed VPS with accounts and listings from the downloaded image manifest.")
    parser.add_argument("--vps-host", default=DEFAULT_VPS_HOST)
    parser.add_argument("--vps-repo-dir", default=DEFAULT_VPS_REPO_DIR)
    parser.add_argument("--api-base-url", default=DEFAULT_API_BASE_URL)
    parser.add_argument("--manifest-csv", type=Path, default=DEFAULT_MANIFEST_CSV)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--approval-admin-username", default=DEFAULT_APPROVAL_ADMIN)
    parser.add_argument("--approval-admin-password", default=DEFAULT_PASSWORD)
    parser.add_argument("--account-count", type=int, default=100)
    parser.add_argument("--account-start", type=int, default=200)
    parser.add_argument("--images-per-product", type=int, default=3)
    parser.add_argument("--title-batch", default="web-batch-1")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def stable_index(category: str, listing_index: int, salt: str, modulo: int) -> int:
    seed = f"{category}:{listing_index}:{salt}".encode("utf-8")
    value = int(hashlib.sha256(seed).hexdigest()[:12], 16)
    return value % modulo


def load_manifest(path: Path) -> list[ImageRecord]:
    if not path.exists():
        raise SystemExit(f"Manifest not found: {path}")
    records: list[ImageRecord] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            category = (row.get("category") or "").strip()
            file_path = Path((row.get("file") or "").strip())
            if not category or not file_path.exists():
                continue
            if category not in API_CATEGORY_BY_IMAGE_CATEGORY:
                continue
            records.append(
                ImageRecord(
                    category=category,
                    path=file_path,
                    title=(row.get("title") or "").strip(),
                    relevance_score=float(row.get("relevance_score") or 0),
                )
            )
    if not records:
        raise SystemExit(f"No usable image records found in {path}")
    return records


def build_triplets(records: list[ImageRecord], images_per_product: int, account_count: int) -> list[list[ImageRecord]]:
    if images_per_product != 3:
        raise SystemExit("This script currently expects exactly 3 images per product.")

    by_category: dict[str, list[ImageRecord]] = {}
    for record in records:
        by_category.setdefault(record.category, []).append(record)

    triplets: list[list[ImageRecord]] = []
    leftovers_by_family: dict[str, list[ImageRecord]] = {}
    global_leftovers: list[ImageRecord] = []

    for category, items in by_category.items():
        ordered = sorted(items, key=lambda item: (-item.relevance_score, str(item.path)))
        full_groups = len(ordered) // images_per_product
        for group_index in range(full_groups):
            start = group_index * images_per_product
            triplets.append(ordered[start:start + images_per_product])
        leftover = ordered[full_groups * images_per_product:]
        if leftover:
            family = CATEGORY_FAMILY.get(category, "misc")
            leftovers_by_family.setdefault(family, []).extend(leftover)

    for family in sorted(leftovers_by_family):
        family_items = sorted(leftovers_by_family[family], key=lambda item: (-item.relevance_score, item.category, str(item.path)))
        full_groups = len(family_items) // images_per_product
        for group_index in range(full_groups):
            start = group_index * images_per_product
            triplets.append(family_items[start:start + images_per_product])
        global_leftovers.extend(family_items[full_groups * images_per_product:])

    if global_leftovers:
        global_leftovers = sorted(global_leftovers, key=lambda item: (-item.relevance_score, item.category, str(item.path)))
        full_groups = len(global_leftovers) // images_per_product
        for group_index in range(full_groups):
            start = group_index * images_per_product
            triplets.append(global_leftovers[start:start + images_per_product])

    if len(triplets) < account_count:
        raise SystemExit(
            f"Need at least {account_count} triplets from manifest, found {len(triplets)}. "
            "Download more images or reduce account-count."
        )
    return triplets[:account_count]


def generate_title_and_description(category: str, listing_index: int, batch_tag: str) -> tuple[str, str]:
    data = TITLE_DATA[category]
    noun = data["nouns"][stable_index(category, listing_index, f"noun:{batch_tag}", len(data["nouns"]))]
    feature = data["features"][stable_index(category, listing_index, f"feature:{batch_tag}", len(data["features"]))]
    state = data["states"][stable_index(category, listing_index, f"state:{batch_tag}", len(data["states"]))]
    reason = data["reasons"][stable_index(category, listing_index, f"reason:{batch_tag}", len(data["reasons"]))]
    title_patterns = [
        f"{noun} {feature}, {state}",
        f"{noun} {feature}, {reason}",
        f"{noun} {state}, {reason}",
    ]
    description_patterns = [
        f"{noun} đang dùng thực tế trong nhà, tình trạng {state}. Bán vì {reason}, ai cần có thể xem thêm hình và trao đổi trực tiếp.",
        f"{noun} {feature}, hiện vẫn dùng ổn. Mình thanh lý vì {reason}, ưu tiên người lấy sớm.",
        f"Bài đăng mô phỏng dữ liệu thực tế cho đồ án: {noun} {feature}, {state}. Giá để đăng tham khảo và có thể trao đổi thêm.",
    ]
    title = title_patterns[stable_index(category, listing_index, f"title:{batch_tag}", len(title_patterns))]
    description = description_patterns[stable_index(category, listing_index, f"description:{batch_tag}", len(description_patterns))]
    return title, description


def choose_price(category: str, listing_index: int) -> int:
    low, high = PRICE_RANGE_BY_CATEGORY[category]
    rng = random.Random(20260630 + listing_index * 17)
    return int(rng.uniform(low, high) // 1000 * 1000)


def dominant_category(triplet: list[ImageRecord]) -> str:
    counts: dict[str, int] = {}
    for item in triplet:
        counts[item.category] = counts.get(item.category, 0) + 1
    return sorted(counts.items(), key=lambda item: (-item[1], item[0]))[0][0]


def main() -> None:
    args = parse_args()
    ensure_command("ssh")

    records = load_manifest(args.manifest_csv)
    triplets = build_triplets(records, args.images_per_product, args.account_count)
    accounts = build_accounts(args.account_count, args.password, admin_index=-1, shuffle=False, start_index=args.account_start)
    if len(accounts) != args.account_count:
        raise SystemExit("Account generation failed.")

    tmp_dir = Path("tmp")
    tmp_dir.mkdir(parents=True, exist_ok=True)
    sql_path = tmp_dir / "seed-users-web.sql"
    plan_path = tmp_dir / "seed-web-plan.json"
    result_path = tmp_dir / "seed-web-result.json"

    sql = build_user_sql(accounts, args.password)
    sql_path.write_text(sql, encoding="utf-8")

    plan = {
        "accountCount": args.account_count,
        "accountStart": args.account_start,
        "approvalAdminUsername": args.approval_admin_username,
        "manifestCsv": str(args.manifest_csv),
        "imagesPerProduct": args.images_per_product,
        "titleBatch": args.title_batch,
        "triplets": [
            {
                "account": account.username,
                "dominantCategory": dominant_category(triplet),
                "images": [str(item.path) for item in triplet],
            }
            for account, triplet in zip(accounts, triplets)
        ],
    }
    plan_path.write_text(json.dumps(plan, indent=2, ensure_ascii=False), encoding="utf-8")

    if args.dry_run:
        print(json.dumps(plan, indent=2, ensure_ascii=False))
        return

    print("Seeding seller accounts on VPS...")
    ssh_exec_psql(args.vps_host, args.vps_repo_dir, sql)

    print("Fetching live categories...")
    categories = fetch_categories(args.api_base_url)

    print("Logging in seller accounts...")
    seller_tokens: dict[str, str] = {}
    for account in accounts:
        seller_tokens[account.username] = login(args.api_base_url, account.username, args.password)

    print("Logging in approval admin...")
    admin_token = login(args.api_base_url, args.approval_admin_username, args.approval_admin_password)

    created_products: list[dict] = []
    print("Creating products with unique 3-image bundles...")
    for listing_index, (account, triplet) in enumerate(zip(accounts, triplets), start=1):
        category = dominant_category(triplet)
        api_category_name = API_CATEGORY_BY_IMAGE_CATEGORY[category]
        if api_category_name not in categories:
            raise SystemExit(f"Missing API category: {api_category_name}")
        category_id = categories[api_category_name]
        title, description = generate_title_and_description(category, listing_index, args.title_batch)
        price = choose_price(category, listing_index)
        response = create_product(
            args.api_base_url,
            seller_tokens[account.username],
            title,
            description,
            price,
            category_id,
            [item.path for item in triplet],
        )
        product = json_get(response, "data")
        if not isinstance(product, dict) or product.get("id") is None:
            raise SystemExit(f"Product creation failed for {account.username}: {response}")
        created_products.append(
            {
                "account": account.username,
                "productId": int(product["id"]),
                "sourceCategory": category,
                "apiCategoryName": api_category_name,
                "categoryId": category_id,
                "title": title,
                "images": [str(item.path) for item in triplet],
            }
        )
        print(f"[{listing_index:03d}] created product {product['id']} for {account.username}")

    print("Approving products as admin...")
    for item in created_products:
        approve_product(args.api_base_url, admin_token, item["productId"])
        print(f"approved product {item['productId']}")

    result = {
        "accounts": [account.username for account in accounts],
        "approvalAdminUsername": args.approval_admin_username,
        "products": created_products,
        "sqlPath": str(sql_path),
        "planPath": str(plan_path),
        "resultPath": str(result_path),
        "manifestCsv": str(args.manifest_csv),
        "accountStart": args.account_start,
        "accountCount": args.account_count,
        "imagesPerProduct": args.images_per_product,
    }
    result_path.write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
