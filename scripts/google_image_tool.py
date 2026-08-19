#!/usr/bin/env python3
"""
Download categorized product images from open image sources.

Supported backends:
  - Openverse (openly licensed images)
  - Pexels (optional, requires PEXELS_API_KEY)
  - Wikimedia Commons

The tool filters by actual pixel size, ranks candidates by metadata relevance,
removes duplicates with SHA-256 and perceptual hash checks, normalizes images
to JPEG, and writes manifests that can be reused by seed scripts.

Example:
  python scripts/google_image_tool.py --total 300 --out D:/H-smart/tmp/category-images
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import math
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image


DEFAULT_CATEGORY_QUERIES: dict[str, list[str]] = {
    "bed": ["bed furniture", "double bed", "wooden bed frame", "bedroom furniture", "hotel bed"],
    "cabinet": ["cabinet storage furniture", "wooden cabinet", "wardrobe cabinet", "storage cabinet", "closet furniture"],
    "chair": ["chair furniture", "dining chair", "wood chair"],
    "table": ["table furniture", "dining table", "coffee table"],
    "desk": ["desk office furniture", "computer desk", "study desk"],
    "sofa": ["sofa couch furniture", "living room sofa", "fabric sofa", "couch living room", "sectional sofa"],
    "blender": ["kitchen blender appliance", "electric blender", "food blender", "kitchen appliance", "food processor"],
    "dishwasher": ["dishwasher appliance", "kitchen dishwasher", "dish washer", "kitchen appliance", "home appliance"],
    "fan": ["fan appliance", "electric fan", "standing fan", "ceiling fan", "table fan"],
    "kettle": ["kettle appliance", "electric kettle", "water kettle", "kitchen appliance", "tea kettle"],
    "lamp": ["lamp lighting", "table lamp", "desk lamp"],
    "microwave": ["microwave oven appliance", "countertop microwave", "small microwave", "kitchen appliance", "countertop oven"],
    "mirror": ["mirror home decor", "wall mirror", "standing mirror"],
    "oven_stove": ["oven stove appliance", "kitchen oven", "baking oven", "kitchen appliance", "electric oven"],
    "refrigerator": ["refrigerator fridge appliance", "kitchen fridge", "small refrigerator", "kitchen appliance", "home appliance"],
    "sink": ["kitchen sink", "stainless steel sink", "washing sink", "bathroom sink", "sink basin"],
    "faucet": ["faucet tap", "kitchen faucet", "water tap", "bathroom faucet", "sink faucet"],
    "television": ["television tv", "smart tv", "flat screen tv", "home electronics", "led tv"],
    "toaster": ["toaster appliance", "bread toaster", "sandwich toaster", "kitchen appliance", "toaster oven"],
    "washing_machine": ["washing machine appliance", "laundry machine", "front load washer", "home appliance", "washer machine"],
    "vacuum_cleaner": ["vacuum cleaner appliance", "robot vacuum", "upright vacuum", "home cleaning appliance", "cordless vacuum"],
    "rice_cooker": ["rice cooker appliance", "electric rice cooker", "kitchen rice cooker", "cooking appliance", "multi cooker"],
    "air_conditioner": ["air conditioner appliance", "split air conditioner", "wall mounted air conditioner", "home cooling appliance", "portable air conditioner"],
    "coffee_maker": ["coffee maker appliance", "espresso machine", "drip coffee maker", "kitchen appliance", "coffee machine"],
    "bookshelf": ["bookshelf furniture", "bookcase", "wooden bookshelf", "storage shelf furniture", "home shelf"],
    "water_purifier": ["water purifier appliance", "water dispenser", "home water filter", "drinking water purifier", "countertop water dispenser"],
}

DEFAULT_OPENVERSE_SOURCES = ["flickr", "wikimedia", "wordpress"]
DEFAULT_ALLOWED_LICENSES = ["by", "by-sa", "cc0", "pdm"]
DEFAULT_WIKIMEDIA_LICENSE_HINTS = ["cc0", "cc by", "cc by-sa", "public domain", "pdm"]
DEFAULT_BLOCKLIST_TERMS = {
    "repair",
    "manual",
    "diagram",
    "schematic",
    "drawing",
    "vector",
    "icon",
    "logo",
    "clipart",
    "advertisement",
    "banner",
    "poster",
    "blueprint",
    "parts",
    "spare",
    "service",
    "technician",
    "broken",
    "damaged",
    "template",
}
DEFAULT_PEOPLE_HINTS = {
    "man",
    "woman",
    "people",
    "person",
    "child",
    "children",
    "baby",
    "portrait",
    "selfie",
}
CATEGORY_HINTS: dict[str, dict[str, list[str]]] = {
    "bed": {"include": ["bed", "mattress", "headboard", "bedroom"], "exclude": ["hospital", "baby", "bunk"]},
    "cabinet": {"include": ["cabinet", "wardrobe", "cupboard", "drawer", "closet"], "exclude": ["server", "electrical"]},
    "chair": {"include": ["chair", "stool", "bench", "seat"], "exclude": ["wheelchair", "car seat"]},
    "table": {"include": ["table", "dining", "coffee"], "exclude": ["periodic", "spreadsheet"]},
    "desk": {"include": ["desk", "workstation", "office", "study"], "exclude": ["helpdesk"]},
    "sofa": {"include": ["sofa", "couch", "sectional", "loveseat"], "exclude": ["studio portrait"]},
    "blender": {"include": ["blender", "mixer", "food processor"], "exclude": ["construction", "3d render"]},
    "dishwasher": {"include": ["dishwasher", "dish washer"], "exclude": ["repair", "parts"]},
    "fan": {"include": ["fan", "ceiling fan", "standing fan"], "exclude": ["sports fan", "celeb"]},
    "kettle": {"include": ["kettle", "electric kettle", "tea kettle"], "exclude": ["landscape", "bird"]},
    "lamp": {"include": ["lamp", "light", "lighting"], "exclude": ["street light"]},
    "microwave": {"include": ["microwave", "microwave oven"], "exclude": ["satellite", "tower"]},
    "mirror": {"include": ["mirror", "wall mirror", "standing mirror"], "exclude": ["rearview", "side mirror"]},
    "oven_stove": {"include": ["oven", "stove", "cooktop", "range"], "exclude": ["campfire"]},
    "refrigerator": {"include": ["refrigerator", "fridge", "freezer"], "exclude": ["truck"]},
    "sink": {"include": ["sink", "basin", "washbasin"], "exclude": ["sunset", "ship"]},
    "faucet": {"include": ["faucet", "tap", "spout"], "exclude": ["beer tap"]},
    "television": {"include": ["television", "tv", "smart tv", "screen"], "exclude": ["camera monitor"]},
    "toaster": {"include": ["toaster", "toaster oven"], "exclude": ["food closeup"]},
    "washing_machine": {"include": ["washing machine", "washer", "laundry"], "exclude": ["repair", "parts"]},
    "vacuum_cleaner": {"include": ["vacuum", "vacuum cleaner", "robot vacuum"], "exclude": ["space", "tube"]},
    "rice_cooker": {"include": ["rice cooker", "multi cooker", "cooker"], "exclude": ["rice field"]},
    "air_conditioner": {"include": ["air conditioner", "ac unit", "split ac"], "exclude": ["car ac"]},
    "coffee_maker": {"include": ["coffee maker", "coffee machine", "espresso machine"], "exclude": ["coffee beans"]},
    "bookshelf": {"include": ["bookshelf", "bookcase", "shelf"], "exclude": ["library hall"]},
    "water_purifier": {"include": ["water purifier", "water filter", "water dispenser"], "exclude": ["river", "waterfall"]},
}


@dataclass(frozen=True)
class Candidate:
    backend: str
    category: str
    query: str
    title: str
    url: str
    page_url: str | None
    source: str | None
    creator: str | None
    license: str | None
    width: int | None
    height: int | None
    metadata: dict


@dataclass(frozen=True)
class DownloadedRecord:
    category: str
    query: str
    backend: str
    source: str | None
    creator: str | None
    title: str
    page_url: str | None
    license: str | None
    width: int
    height: int
    sha256: str
    dhash: str
    file: str
    original_url: str
    relevance_score: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download categorized product images from open sources.")
    parser.add_argument(
        "--backend",
        choices=["openverse", "pexels", "wikimedia", "mixed"],
        default="mixed",
        help="Which backend(s) to use. mixed tries Pexels first if configured, then Openverse, then Wikimedia.",
    )
    parser.add_argument(
        "--pexels-api-key",
        default=os.environ.get("PEXELS_API_KEY"),
        help="Pexels API key. Optional unless backend is pexels.",
    )
    parser.add_argument(
        "--openverse-source",
        action="append",
        default=[],
        help="Limit Openverse results to specific source slugs. Repeatable. Example: --openverse-source wikimedia",
    )
    parser.add_argument(
        "--allowed-license",
        action="append",
        default=[],
        help="Allow only Openverse license slugs in this list. Repeatable.",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("tmp/category-images"),
        help="Output directory for downloaded images and manifests.",
    )
    parser.add_argument(
        "--total",
        type=int,
        default=300,
        help="Target number of unique images to download across all categories.",
    )
    parser.add_argument(
        "--per-category",
        type=int,
        default=None,
        help="Optional cap per category. Defaults to an even split of --total.",
    )
    parser.add_argument(
        "--categories",
        nargs="*",
        default=list(DEFAULT_CATEGORY_QUERIES.keys()),
        help="Categories to download. Defaults to all built-in household categories.",
    )
    parser.add_argument(
        "--max-pages",
        type=int,
        default=16,
        help="Maximum search pages to scan per query per backend.",
    )
    parser.add_argument(
        "--page-size",
        type=int,
        default=20,
        help="Number of results to fetch per search page.",
    )
    parser.add_argument(
        "--min-width",
        type=int,
        default=1000,
        help="Skip images narrower than this.",
    )
    parser.add_argument(
        "--min-height",
        type=int,
        default=750,
        help="Skip images shorter than this.",
    )
    parser.add_argument(
        "--max-edge",
        type=int,
        default=2200,
        help="Resize the longest edge to this value before saving. Use 0 to keep original size.",
    )
    parser.add_argument(
        "--jpeg-quality",
        type=int,
        default=90,
        help="JPEG quality used when normalizing files.",
    )
    parser.add_argument(
        "--pause",
        type=float,
        default=0.08,
        help="Pause in seconds between network calls.",
    )
    parser.add_argument(
        "--dhash-distance",
        type=int,
        default=6,
        help="Perceptual hash threshold for near-duplicate detection.",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=45,
        help="Network timeout in seconds.",
    )
    parser.add_argument(
        "--min-relevance-score",
        type=float,
        default=1.4,
        help="Minimum metadata relevance score before attempting a download.",
    )
    return parser.parse_args()


def sanitize_filename(text: str) -> str:
    cleaned = "".join(ch if ch.isalnum() or ch in {"-", "_"} else "_" for ch in text.strip().lower())
    return cleaned.strip("_") or "image"


def tokenize(text: str) -> set[str]:
    return {token for token in re.findall(r"[a-z0-9]+", text.lower()) if len(token) >= 2}


def ensure_headers() -> dict[str, str]:
    return {
        "User-Agent": "H-Smart-Category-Image-Tool/3.0",
        "Accept": "application/json, image/*;q=0.9, */*;q=0.8",
    }


def http_get_json(url: str, timeout: int, headers: dict[str, str] | None = None) -> dict:
    request = urllib.request.Request(url, headers={**ensure_headers(), **(headers or {})})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def download_bytes(url: str, timeout: int, headers: dict[str, str] | None = None) -> tuple[bytes, str | None]:
    request = urllib.request.Request(url, headers={**ensure_headers(), **(headers or {})})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        content_type = response.headers.get_content_type()
        return response.read(), content_type


def image_from_bytes(data: bytes) -> Image.Image:
    image = Image.open(io.BytesIO(data))
    image.load()
    return image


def dhash(image: Image.Image) -> int:
    grayscale = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    pixels = list(grayscale.tobytes())
    value = 0
    bit_index = 0
    for row in range(8):
        base = row * 9
        for col in range(8):
            left = pixels[base + col]
            right = pixels[base + col + 1]
            if left > right:
                value |= 1 << bit_index
            bit_index += 1
    return value


def hamming_distance(left: int, right: int) -> int:
    return (left ^ right).bit_count()


def is_near_duplicate(candidate: int, existing: list[int], threshold: int) -> bool:
    return any(hamming_distance(candidate, known) <= threshold for known in existing)


def normalize_image(image: Image.Image, max_edge: int, quality: int) -> tuple[bytes, str]:
    working = image.copy()
    if max_edge > 0 and max(working.width, working.height) > max_edge:
        working.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)

    if "A" in working.getbands():
        rgba = working.convert("RGBA")
        background = Image.new("RGBA", rgba.size, (255, 255, 255, 255))
        background.alpha_composite(rgba)
        working = background.convert("RGB")
    else:
        working = working.convert("RGB")

    buffer = io.BytesIO()
    working.save(buffer, format="JPEG", quality=quality, optimize=True, progressive=True)
    return buffer.getvalue(), ".jpg"


def openverse_query(
    query: str,
    page: int,
    page_size: int,
    timeout: int,
    sources: list[str],
) -> list[Candidate]:
    params: list[tuple[str, str]] = [
        ("q", query),
        ("page", str(page)),
        ("page_size", str(page_size)),
    ]
    for source in sources:
        params.append(("source", source))

    url = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode(params, doseq=True)
    payload = http_get_json(url, timeout)
    results = payload.get("results") or []
    candidates: list[Candidate] = []
    for item in results:
        direct_url = item.get("url")
        if not direct_url:
            continue
        candidates.append(
            Candidate(
                backend="openverse",
                category="",
                query=query,
                title=item.get("title") or "",
                url=direct_url,
                page_url=item.get("foreign_landing_url"),
                source=item.get("source") or item.get("provider"),
                creator=item.get("creator"),
                license=item.get("license"),
                width=int(item["width"]) if item.get("width") else None,
                height=int(item["height"]) if item.get("height") else None,
                metadata=item,
            )
        )
    return candidates


def pexels_query(query: str, page: int, page_size: int, timeout: int, api_key: str) -> list[Candidate]:
    params = urllib.parse.urlencode({"query": query, "page": str(page), "per_page": str(page_size)})
    url = "https://api.pexels.com/v1/search?" + params
    headers = {**ensure_headers(), "Authorization": api_key}
    payload = http_get_json(url, timeout, headers=headers)
    photos = payload.get("photos") or []
    candidates: list[Candidate] = []
    for photo in photos:
        src = photo.get("src") or {}
        direct_url = src.get("original") or src.get("large2x") or src.get("large")
        if not direct_url:
            continue
        candidates.append(
            Candidate(
                backend="pexels",
                category="",
                query=query,
                title=photo.get("alt") or "",
                url=direct_url,
                page_url=photo.get("url"),
                source="pexels",
                creator=photo.get("photographer"),
                license="pexels-license",
                width=int(photo["width"]) if photo.get("width") else None,
                height=int(photo["height"]) if photo.get("height") else None,
                metadata=photo,
            )
        )
    return candidates


def wikimedia_query(query: str, page: int, page_size: int, timeout: int) -> list[Candidate]:
    params = urllib.parse.urlencode(
        {
            "action": "query",
            "generator": "search",
            "gsrsearch": query,
            "gsrnamespace": "6",
            "gsrlimit": str(page_size),
            "gsroffset": str((page - 1) * page_size),
            "prop": "imageinfo",
            "iiprop": "url|size|mime|extmetadata",
            "iiurlwidth": "1200",
            "format": "json",
            "formatversion": "2",
        }
    )
    url = "https://commons.wikimedia.org/w/api.php?" + params
    payload = http_get_json(url, timeout)
    pages = (payload.get("query") or {}).get("pages") or []
    candidates: list[Candidate] = []
    for page_item in pages:
        if page_item.get("missing"):
            continue
        title = page_item.get("title") or ""
        imageinfo = page_item.get("imageinfo") or []
        if not imageinfo:
            continue
        info = imageinfo[0]
        direct_url = info.get("thumburl") or info.get("url")
        if not direct_url:
            continue
        extmetadata = info.get("extmetadata") or {}
        license_short = ""
        usage_terms = ""
        if isinstance(extmetadata, dict):
            license_short = ((extmetadata.get("LicenseShortName") or {}).get("value") or "").strip()
            usage_terms = ((extmetadata.get("UsageTerms") or {}).get("value") or "").strip()
        candidates.append(
            Candidate(
                backend="wikimedia",
                category="",
                query=query,
                title=title,
                url=direct_url,
                page_url=page_item.get("canonicalurl") or f"https://commons.wikimedia.org/wiki/{urllib.parse.quote(title.replace(' ', '_'))}",
                source="wikimedia",
                creator=((extmetadata.get("Artist") or {}).get("value") if isinstance(extmetadata, dict) else None),
                license=license_short or usage_terms or None,
                width=int(info.get("thumbwidth") or info.get("width")) if (info.get("thumbwidth") or info.get("width")) else None,
                height=int(info.get("thumbheight") or info.get("height")) if (info.get("thumbheight") or info.get("height")) else None,
                metadata=page_item,
            )
        )
    return candidates


def metadata_text(candidate: Candidate) -> str:
    parts = [
        candidate.title or "",
        candidate.page_url or "",
        candidate.source or "",
        candidate.creator or "",
        candidate.license or "",
        candidate.query or "",
    ]
    metadata = candidate.metadata or {}
    for key in ("title", "description", "alt", "caption"):
        value = metadata.get(key)
        if isinstance(value, str):
            parts.append(value)
    return " ".join(parts).lower()


def candidate_relevance_score(candidate: Candidate, category: str, query: str) -> float:
    text = metadata_text(candidate)
    tokens = tokenize(text)
    query_tokens = tokenize(query.replace("_", " "))
    category_tokens = tokenize(category.replace("_", " "))
    hints = CATEGORY_HINTS.get(category, {})
    include_terms = set(hints.get("include", [])) | query_tokens | category_tokens
    exclude_terms = set(hints.get("exclude", [])) | DEFAULT_BLOCKLIST_TERMS

    score = 0.0
    if candidate.width and candidate.height:
        megapixels = (candidate.width * candidate.height) / 1_000_000
        score += min(megapixels, 10.0) * 0.2

    include_hits = 0
    for term in include_terms:
        term_tokens = tokenize(term)
        if term_tokens and term_tokens.issubset(tokens):
            include_hits += 1
            score += 1.3 if len(term_tokens) > 1 else 0.9

    for term in exclude_terms:
        term_tokens = tokenize(term)
        if term_tokens and term_tokens.issubset(tokens):
            score -= 1.5 if len(term_tokens) > 1 else 0.9

    people_hits = sum(1 for term in DEFAULT_PEOPLE_HINTS if term in tokens)
    score -= people_hits * 0.35

    if candidate.backend == "pexels":
        score += 0.35
    if candidate.page_url and "commons.wikimedia.org" in candidate.page_url:
        score += 0.15
    if include_hits == 0:
        score -= 1.6

    return score


def write_jsonl(path: Path, records: Iterable[dict]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def write_csv(path: Path, records: list[dict]) -> None:
    if not records:
        path.write_text("", encoding="utf-8")
        return
    fieldnames = list(records[0].keys())
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for record in records:
            writer.writerow(record)


def candidate_is_allowed(candidate: Candidate, allowed_licenses: set[str]) -> bool:
    if candidate.backend == "openverse" and allowed_licenses:
        license_slug = (candidate.license or "").strip().lower()
        return license_slug in allowed_licenses
    if candidate.backend == "wikimedia" and allowed_licenses:
        license_text = (candidate.license or "").strip().lower()
        return any(hint in license_text for hint in DEFAULT_WIKIMEDIA_LICENSE_HINTS)
    return True


def save_candidate_image(
    candidate: Candidate,
    out_dir: Path,
    index: int,
    quality: int,
    max_edge: int,
    timeout: int,
    min_width: int,
    min_height: int,
    relevance_score: float,
) -> tuple[DownloadedRecord | None, str | None]:
    try:
        data, _ = download_bytes(candidate.url, timeout)
        image = image_from_bytes(data)
    except Exception as exc:  # noqa: BLE001
        return None, f"download failed: {exc}"

    if image.width < min_width or image.height < min_height:
        return None, "actual image smaller than required"

    normalized_bytes, extension = normalize_image(image, max_edge, quality)
    normalized_image = image_from_bytes(normalized_bytes)
    if normalized_image.width < min_width or normalized_image.height < min_height:
        return None, "normalized image smaller than required"

    sha256 = hashlib.sha256(normalized_bytes).hexdigest()
    perceptual = dhash(normalized_image)
    file_name = f"{sanitize_filename(candidate.category)}_{index:03d}_{sha256[:12]}{extension}"
    target = out_dir / file_name
    target.write_bytes(normalized_bytes)

    return (
        DownloadedRecord(
            category=candidate.category,
            query=candidate.query,
            backend=candidate.backend,
            source=candidate.source,
            creator=candidate.creator,
            title=candidate.title,
            page_url=candidate.page_url,
            license=candidate.license,
            width=normalized_image.width,
            height=normalized_image.height,
            sha256=sha256,
            dhash=f"{perceptual:016x}",
            file=str(target),
            original_url=candidate.url,
            relevance_score=relevance_score,
        ),
        None,
    )


def select_categories(requested: list[str]) -> list[str]:
    categories = [category for category in requested if category in DEFAULT_CATEGORY_QUERIES]
    unknown = [category for category in requested if category not in DEFAULT_CATEGORY_QUERIES]
    if unknown:
        raise SystemExit(
            "Unknown category name(s): "
            + ", ".join(unknown)
            + ". Available: "
            + ", ".join(DEFAULT_CATEGORY_QUERIES.keys())
        )
    if not categories:
        raise SystemExit("No categories selected.")
    return categories


def backend_order(backend: str, pexels_api_key: str | None) -> list[str]:
    if backend == "openverse":
        return ["openverse"]
    if backend == "wikimedia":
        return ["wikimedia"]
    if backend == "pexels":
        if not pexels_api_key:
            raise SystemExit("PEXELS_API_KEY is required when backend=pexels")
        return ["pexels"]

    order: list[str] = []
    if pexels_api_key:
        order.append("pexels")
    order.append("openverse")
    order.append("wikimedia")
    return order


def fill_categories(
    *,
    categories: list[str],
    category_limits: dict[str, int],
    args: argparse.Namespace,
    allowed_licenses: set[str],
    sources: list[str],
    backends: list[str],
    images_root: Path,
    seen_source_urls: set[str],
    seen_sha256: set[str],
    seen_dhashes: list[int],
    records: list[dict],
    summary: dict[str, int],
    total_downloaded: int,
) -> int:
    for category in categories:
        category_dir = images_root / category
        category_dir.mkdir(parents=True, exist_ok=True)
        queries = DEFAULT_CATEGORY_QUERIES[category]
        category_target = category_limits[category]

        for query in queries:
            if total_downloaded >= args.total or summary[category] >= category_target:
                break

            for backend in backends:
                if total_downloaded >= args.total or summary[category] >= category_target:
                    break

                for page in range(1, args.max_pages + 1):
                    if total_downloaded >= args.total or summary[category] >= category_target:
                        break

                    try:
                        if backend == "openverse":
                            candidates = openverse_query(query, page, args.page_size, args.timeout, sources)
                        elif backend == "wikimedia":
                            candidates = wikimedia_query(query, page, args.page_size, args.timeout)
                        else:
                            candidates = pexels_query(query, page, args.page_size, args.timeout, args.pexels_api_key or "")
                    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
                        print(f"[{category}/{backend}] page {page} failed: {exc}", flush=True)
                        time.sleep(max(args.pause, 0.0))
                        continue

                    if not candidates:
                        break

                    scored_candidates = sorted(
                        ((candidate_relevance_score(candidate, category, query), candidate) for candidate in candidates),
                        key=lambda item: item[0],
                        reverse=True,
                    )

                    for relevance_score, candidate in scored_candidates:
                        if total_downloaded >= args.total or summary[category] >= category_target:
                            break
                        if candidate.url in seen_source_urls:
                            continue
                        if not candidate_is_allowed(candidate, allowed_licenses):
                            continue
                        if relevance_score < args.min_relevance_score:
                            continue
                        if candidate.width and candidate.height:
                            if candidate.width < args.min_width or candidate.height < args.min_height:
                                continue

                        candidate = Candidate(
                            backend=candidate.backend,
                            category=category,
                            query=query,
                            title=candidate.title,
                            url=candidate.url,
                            page_url=candidate.page_url,
                            source=candidate.source,
                            creator=candidate.creator,
                            license=candidate.license,
                            width=candidate.width,
                            height=candidate.height,
                            metadata=candidate.metadata,
                        )

                        try:
                            record, error = save_candidate_image(
                                candidate,
                                category_dir,
                                summary[category] + 1,
                                args.jpeg_quality,
                                args.max_edge,
                                args.timeout,
                                args.min_width,
                                args.min_height,
                                relevance_score,
                            )
                        except Exception as exc:  # noqa: BLE001
                            print(f"[{category}] skip {candidate.url}: {exc}", flush=True)
                            time.sleep(max(args.pause, 0.0))
                            continue

                        if error or record is None:
                            print(f"[{category}] skip {candidate.url}: {error}", flush=True)
                            time.sleep(max(args.pause, 0.0))
                            continue

                        if record.sha256 in seen_sha256:
                            Path(record.file).unlink(missing_ok=True)
                            continue

                        perceptual = int(record.dhash, 16)
                        if is_near_duplicate(perceptual, seen_dhashes, args.dhash_distance):
                            Path(record.file).unlink(missing_ok=True)
                            continue

                        seen_source_urls.add(candidate.url)
                        seen_sha256.add(record.sha256)
                        seen_dhashes.append(perceptual)
                        records.append(record.__dict__)
                        summary[category] += 1
                        total_downloaded += 1

                        print(
                            f"[{category}] {summary[category]}/{category_target} "
                            f"{candidate.backend} score={relevance_score:.2f} -> {Path(record.file).name}",
                            flush=True,
                        )
                        time.sleep(max(args.pause, 0.0))

        if total_downloaded >= args.total:
            break

    return total_downloaded


def main() -> None:
    args = parse_args()
    categories = select_categories(args.categories)
    per_category = args.per_category or math.ceil(args.total / len(categories))
    allowed_licenses = {slug.strip().lower() for slug in (args.allowed_license or DEFAULT_ALLOWED_LICENSES)}
    sources = args.openverse_source or DEFAULT_OPENVERSE_SOURCES
    backends = backend_order(args.backend, args.pexels_api_key)

    out_root = args.out
    images_root = out_root / "images"
    manifests_root = out_root / "manifests"
    images_root.mkdir(parents=True, exist_ok=True)
    manifests_root.mkdir(parents=True, exist_ok=True)

    seen_source_urls: set[str] = set()
    seen_sha256: set[str] = set()
    seen_dhashes: list[int] = []
    records: list[dict] = []
    summary: dict[str, int] = {category: 0 for category in categories}
    total_downloaded = fill_categories(
        categories=categories,
        category_limits={category: per_category for category in categories},
        args=args,
        allowed_licenses=allowed_licenses,
        sources=sources,
        backends=backends,
        images_root=images_root,
        seen_source_urls=seen_source_urls,
        seen_sha256=seen_sha256,
        seen_dhashes=seen_dhashes,
        records=records,
        summary=summary,
        total_downloaded=0,
    )

    if total_downloaded < args.total:
        overflow_limits = {category: args.total for category in categories}
        total_downloaded = fill_categories(
            categories=categories,
            category_limits=overflow_limits,
            args=args,
            allowed_licenses=allowed_licenses,
            sources=sources,
            backends=backends,
            images_root=images_root,
            seen_source_urls=seen_source_urls,
            seen_sha256=seen_sha256,
            seen_dhashes=seen_dhashes,
            records=records,
            summary=summary,
            total_downloaded=total_downloaded,
        )

    manifest_jsonl = manifests_root / "manifest.jsonl"
    manifest_csv = manifests_root / "manifest.csv"
    summary_json = manifests_root / "summary.json"

    write_jsonl(manifest_jsonl, records)
    write_csv(manifest_csv, records)
    summary_json.write_text(
        json.dumps(
            {
                "targetTotal": args.total,
                "downloadedTotal": total_downloaded,
                "perCategoryTarget": per_category,
                "categories": summary,
                "backend": args.backend,
                "openverseSources": sources,
                "allowedLicenses": sorted(allowed_licenses),
                "output": str(out_root),
                "minWidth": args.min_width,
                "minHeight": args.min_height,
                "maxEdge": args.max_edge,
                "minRelevanceScore": args.min_relevance_score,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    print(
        json.dumps(
            {
                "downloadedTotal": total_downloaded,
                "perCategoryTarget": per_category,
                "summaryFile": str(summary_json),
                "manifestJsonl": str(manifest_jsonl),
                "manifestCsv": str(manifest_csv),
            },
            indent=2,
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
