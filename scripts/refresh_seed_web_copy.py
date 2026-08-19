#!/usr/bin/env python3
"""Refresh web-seeded product titles/descriptions with Vietnamese diacritics."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from seed_vps_marketplace import DEFAULT_API_BASE_URL, DEFAULT_PASSWORD, http_json, json_get, login
from seed_vps_marketplace_web import generate_title_and_description


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Refresh web-seeded product copy with Vietnamese diacritics.")
    parser.add_argument("--seed-result", type=Path, default=Path("tmp/seed-web-result.json"))
    parser.add_argument("--api-base-url", default=DEFAULT_API_BASE_URL)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--title-batch", default="web-batch-1")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--start-index", type=int, default=0, help="0-based product offset for resume runs.")
    parser.add_argument("--retries", type=int, default=3, help="How many times to retry GET/PUT on transient errors.")
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


def with_retries(fn, retries: int, label: str):
    attempt = 0
    while True:
        try:
            return fn()
        except (urllib.error.URLError, TimeoutError) as exc:
            attempt += 1
            if attempt > retries:
                raise SystemExit(f"{label} failed after {retries} retries: {exc}") from exc
            time.sleep(min(2.0 * attempt, 6.0))


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
    data = json.loads(args.seed_result.read_text(encoding="utf-8"))
    products = data.get("products")
    if not isinstance(products, list):
        raise SystemExit(f"Seed result does not contain a products list: {args.seed_result}")
    if args.start_index:
        products = products[args.start_index:]
    if args.limit is not None:
        products = products[: args.limit]

    tokens: dict[str, str] = {}
    updates: list[dict] = []

    for index, item in enumerate(products, start=1):
        account = item["account"]
        product_id = int(item["productId"])
        source_category = item["sourceCategory"]
        new_title, new_description = generate_title_and_description(source_category, index, args.title_batch)

        current_response = with_retries(
            lambda: http_json("GET", f"{args.api_base_url}/products/{product_id}"),
            args.retries,
            f"Loading product {product_id}",
        )
        current_product = json_get(current_response, "data")
        if not isinstance(current_product, dict):
            raise SystemExit(f"Cannot load product {product_id}: {current_response}")

        updates.append(
            {
                "productId": product_id,
                "account": account,
                "sourceCategory": source_category,
                "oldTitle": current_product.get("title"),
                "newTitle": new_title,
                "newDescription": new_description,
            }
        )

        if args.dry_run:
            print(f"[preview] {product_id}: {new_title}")
            continue

        if account not in tokens:
            tokens[account] = login(args.api_base_url, account, args.password)

        fields = product_update_fields(current_product, new_title, new_description)
        try:
            response = with_retries(
                lambda: put_form(f"{args.api_base_url}/products/{product_id}", fields, tokens[account]),
                args.retries,
                f"Updating product {product_id}",
            )
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise SystemExit(f"Failed updating product {product_id}: HTTP {exc.code} {body}") from exc

        saved = json_get(response, "data")
        saved_title = saved.get("title") if isinstance(saved, dict) else new_title
        print(f"[{index:03d}] updated product {product_id}: {saved_title}")
        time.sleep(0.05)

    output = Path("tmp/refresh-seed-web-copy-result.json")
    output.write_text(json.dumps({"updates": updates}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"updated": 0 if args.dry_run else len(updates), "planFile": str(output)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
