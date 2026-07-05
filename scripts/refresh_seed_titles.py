#!/usr/bin/env python3
"""Refresh seeded product titles/descriptions through the public API."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from seed_title_generator import generate_natural_listing
from seed_vps_marketplace import DEFAULT_API_BASE_URL, DEFAULT_PASSWORD, http_json, json_get, login


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Refresh seeded product copy with natural marketplace text.")
    parser.add_argument("--seed-result", type=Path, default=Path("tmp/seed-result.json"))
    parser.add_argument("--api-base-url", default=DEFAULT_API_BASE_URL)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--title-batch", default="")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--limit", type=int, default=None)
    return parser.parse_args()


def put_form(url: str, fields: dict[str, str], token: str) -> dict:
    payload = urllib.parse.urlencode(fields).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=payload,
        method="PUT",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def load_seed_products(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    products = data.get("products")
    if not isinstance(products, list):
        raise SystemExit(f"Seed result does not contain a products list: {path}")
    return products


def product_update_fields(product: dict, title: str, description: str) -> dict[str, str]:
    category_id = product.get("categoryId")
    price = product.get("price")
    negotiable = bool(product.get("negotiable"))
    fields = {
        "title": title,
        "description": description,
        "price": str(price),
        "negotiable": "true" if negotiable else "false",
        "categoryId": str(category_id) if category_id is not None else "",
        "titleModifiedByUser": "true",
    }
    min_price = product.get("minPrice")
    if negotiable and min_price is not None:
        fields["minPrice"] = str(min_price)
    return fields


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    args = parse_args()
    seed_data = json.loads(args.seed_result.read_text(encoding="utf-8"))
    products = seed_data.get("products")
    if not isinstance(products, list):
        raise SystemExit(f"Seed result does not contain a products list: {args.seed_result}")
    title_batch = str(seed_data.get("titleBatch") or args.title_batch or "")
    if args.limit is not None:
        products = products[: args.limit]

    tokens: dict[str, str] = {}
    updates: list[dict] = []

    for index, seed_item in enumerate(products, start=1):
        account = seed_item["account"]
        product_id = int(seed_item["productId"])
        source_class = seed_item["sourceClass"]
        new_title, new_description = generate_natural_listing(source_class, index, title_batch)

        current_response = http_json("GET", f"{args.api_base_url}/products/{product_id}")
        current_product = json_get(current_response, "data")
        if not isinstance(current_product, dict):
            raise SystemExit(f"Cannot load product {product_id}: {current_response}")

        update = {
            "productId": product_id,
            "account": account,
            "sourceClass": source_class,
            "oldTitle": current_product.get("title"),
            "newTitle": new_title,
            "newDescription": new_description,
        }
        updates.append(update)

        if args.dry_run:
            print(f"[preview] {product_id}: {new_title}")
            continue

        if account not in tokens:
            tokens[account] = login(args.api_base_url, account, args.password)

        fields = product_update_fields(current_product, new_title, new_description)
        try:
            response = put_form(f"{args.api_base_url}/products/{product_id}", fields, tokens[account])
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise SystemExit(f"Failed updating product {product_id}: HTTP {exc.code} {body}") from exc

        saved = json_get(response, "data")
        saved_title = saved.get("title") if isinstance(saved, dict) else new_title
        print(f"[{index:03d}] updated product {product_id}: {saved_title}")
        time.sleep(0.05)

    output = Path("tmp/refresh-title-result.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"updates": updates}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"updated": 0 if args.dry_run else len(updates), "planFile": str(output)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
