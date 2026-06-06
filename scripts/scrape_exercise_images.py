#!/usr/bin/env python3
"""Download exercise images using wger.de API + Wikimedia Commons fallback."""
from __future__ import annotations

import json
import re
import time
from difflib import SequenceMatcher
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
EXERCISES_JSON = ROOT / "app/src/main/assets/exercises.json"
OUTPUT_DIR = ROOT / "scrapped images"
MANIFEST_PATH = OUTPUT_DIR / "manifest.json"

WGER_BASE = "https://wger.de/api/v2"
HEADERS = {"User-Agent": "GymAI-ExerciseScraper/1.0 (local dev; contact: none)"}
REQUEST_TIMEOUT = 30
WGER_DELAY = 0.65
MIN_SIZE = 180

# Direct image URLs when automatic matching is unreliable (wger.de CDN)
EXPLICIT_IMAGE_URLS: dict[str, str] = {
    "Lat Pulldown": "https://wger.de/media/exercise-images/158/02e8a7c3-dc67-434e-a4bc-77fdecf84b49.webp",
    "Hack Squat": "https://wger.de/media/exercise-images/130/Narrow-stance-hack-squats-1-1024x721.png",
    "T-Bar Row": "https://wger.de/media/exercise-images/106/T-bar-row-1.png",
    "Chest Supported Row": "https://wger.de/media/exercise-images/1283/e7262f70-7512-408a-8d00-4c499ef632fc.jpg",
    "Deadlift": "https://wger.de/media/exercise-images/184/1709c405-620a-4d07-9658-fade2b66a2df.jpeg",
    "Barbell Curl": "https://wger.de/media/exercise-images/74/Bicep-curls-1.png",
    "Dumbbell Curl": "https://wger.de/media/exercise-images/81/Biceps-curl-1.png",
    "Barbell Squat": "https://wger.de/media/exercise-images/1801/60043328-1cfb-4289-9865-aaf64d5aaa28.jpg",
    "Front Squat": "https://wger.de/media/exercise-images/1640/bdea82f1-15ef-4649-8b5a-1303cfc178e7.webp",
    "Dumbbell Shoulder Press": "https://wger.de/media/exercise-images/123/dumbbell-shoulder-press-large-1.png",
    "Hanging Leg Raise": "https://wger.de/media/exercise-images/979/27097a3a-5749-428d-b94c-6082afe390f6.png",
    "Landmine Press": "https://wger.de/media/exercise-images/1901/046f0f42-0ed5-48c5-a9ee-41de25e3b6a0.png",
    "Glute Bridge": "https://wger.de/media/exercise-images/1642/a81ad922-caf5-47f8-99b4-640cb0717436.webp",
    "Cable Crunch": "https://wger.de/media/exercise-images/1648/63ae02d6-6dd9-4e9e-84da-d4905e78a33c.jpg",
    "Arnold Press": "https://wger.de/media/exercise-images/123/dumbbell-shoulder-press-large-1.png",
    "Farmer Walk": "https://wger.de/media/exercise-images/1087/d85f4e02-b20c-457c-bdfb-0b00e2d14150.jpg",
    "Battle Ropes": "https://wger.de/media/exercise-images/1634/9a4704d3-1b25-43e3-b244-3885f4d3db87.png",
    "Treadmill Run": "https://wger.de/media/exercise-images/1615/7792295c-83b6-4ea8-9353-ce02f0ad2559.jpg",
}

# Exercises always resolved via EXPLICIT_IMAGE_URLS (closest wger substitute if no exact image)
WIKIMEDIA_ONLY: set[str] = set()

# Manual aliases to improve wger name matching
MANUAL_ALIASES: dict[str, list[str]] = {
    "Bench Press": ["Barbell Bench Press", "Bench press"],
    "Incline DB Press": ["Incline Dumbbell Press", "Incline bench press"],
    "Dumbbell Bench Press": ["Dumbbell bench press"],
    "Pull Up": ["Pull-ups", "Pull-up", "Chin-ups"],
    "Barbell Row": ["Bent-over row", "Bent over row with barbell"],
    "Overhead Press": ["Shoulder press", "Military press"],
    "Romanian Deadlift": ["Romanian Deadlifts", "RDL"],
    "Barbell Squat": ["Squat", "Barbell squat"],
    "Leg Curl": ["Lying leg curl", "Leg curls"],
    "Tricep Pushdown": ["Triceps pushdown", "Cable pushdown"],
    "Tricep Dip": ["Triceps dips", "Bench dips"],
    "Face Pull": ["Face pulls"],
    "Lateral Raise": ["Lateral raises", "Side lateral raise"],
    "Calf Raise": ["Standing calf raises", "Calf raises"],
    "Hip Thrust": ["Barbell hip thrust"],
    "Pec Deck": ["Butterfly", "Pec deck fly"],
    "Cable Fly": ["Cable crossover fly", "Cable chest fly"],
    "Rowing Machine": ["Rowing", "Seated row machine"],
    "Treadmill Run": ["Treadmill", "Running treadmill"],
    "Battle Ropes": ["Battle rope"],
    "Farmer Walk": ["Farmer's walk", "Farmers walk"],
    "Kettlebell Swing": ["Kettlebell swings"],
    "Smith Machine Bench Press": ["Smith machine bench press"],
    "Smith Machine Squat": ["Smith machine squat"],
    "Close Grip Bench Press": ["Close-grip bench press"],
    "Hanging Leg Raise": ["Hanging leg raises"],
    "Russian Twist": ["Russian twists"],
    "Walking Lunge": ["Lunges", "Walking lunges"],
    "Bulgarian Split Squat": ["Split squat", "Bulgarian split squats"],
}


def sanitize_filename(name: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*]', "", name).strip()
    return cleaned or "exercise"


def normalize(text: str) -> str:
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def load_exercises() -> list[dict]:
    with EXERCISES_JSON.open(encoding="utf-8") as f:
        raw = json.load(f)["exercises"]
    seen: set[str] = set()
    out: list[dict] = []
    for entry in raw:
        if entry["id"] in seen:
            continue
        seen.add(entry["id"])
        out.append(entry)
    return out


def fetch_all_wger_exercises() -> list[dict]:
    exercises: list[dict] = []
    url = f"{WGER_BASE}/exerciseinfo/?language=2&limit=100"
    while url:
        response = requests.get(url, headers=HEADERS, timeout=REQUEST_TIMEOUT)
        response.raise_for_status()
        payload = response.json()
        exercises.extend(payload.get("results", []))
        url = payload.get("next")
        print(f"  Loaded {len(exercises)} wger exercises...", flush=True)
        time.sleep(WGER_DELAY)
    return exercises


def english_names(entry: dict) -> list[str]:
    names: list[str] = []
    for translation in entry.get("translations", []):
        if translation.get("language") == 2:
            name = translation.get("name", "").strip()
            if name:
                names.append(name)
            for alias in translation.get("aliases", []) or []:
                alias_name = alias.get("alias", "").strip()
                if alias_name:
                    names.append(alias_name)
    return names


def build_wger_index(entries: list[dict]) -> tuple[dict[str, dict], dict[str, str]]:
    """Map normalized name -> entry, and normalized name -> image url."""
    by_name: dict[str, dict] = {}
    image_by_name: dict[str, str] = {}
    for entry in entries:
        images = entry.get("images") or []
        if not images:
            continue
        main = next((img for img in images if img.get("is_main")), images[0])
        image_url = main.get("image")
        if not image_url:
            continue
        for name in english_names(entry):
            key = normalize(name)
            if key and key not in by_name:
                by_name[key] = entry
                image_by_name[key] = image_url
    return by_name, image_by_name


def score_match(query: str, candidate: str) -> float:
    q = normalize(query)
    c = normalize(candidate)
    if not q or not c:
        return 0.0
    if q == c:
        return 1.0
    if q in c or c in q:
        return 0.92
    return SequenceMatcher(None, q, c).ratio()


def find_wger_image(
    exercise: dict,
    by_name: dict[str, dict],
    image_by_name: dict[str, str],
) -> tuple[str | None, str | None, str | None]:
    candidates = [exercise["name"]]
    candidates.extend(exercise.get("aliases") or [])
    candidates.extend(MANUAL_ALIASES.get(exercise["name"], []))

    best_score = 0.0
    best_url: str | None = None
    best_match: str | None = None

    for candidate in candidates:
        key = normalize(candidate)
        if key in image_by_name:
            return image_by_name[key], candidate, "exact"

        for wger_name, url in image_by_name.items():
            score = score_match(candidate, wger_name)
            if score > best_score:
                best_score = score
                best_url = url
                best_match = wger_name

    if best_score >= 0.86 and best_url:
        return best_url, best_match, f"fuzzy:{best_score:.2f}"
    return None, None, None


def wikimedia_image_url(query: str) -> str | None:
    params = {
        "action": "query",
        "generator": "search",
        "gsrsearch": f"{query} weight training exercise",
        "gsrlimit": 5,
        "prop": "imageinfo",
        "iiprop": "url|mime",
        "iiurlwidth": 800,
        "format": "json",
    }
    response = requests.get(
        "https://commons.wikimedia.org/w/api.php",
        params=params,
        headers=HEADERS,
        timeout=REQUEST_TIMEOUT,
    )
    response.raise_for_status()
    pages = response.json().get("query", {}).get("pages", {})
    for page in pages.values():
        for info in page.get("imageinfo", []):
            mime = info.get("mime", "")
            url = info.get("thumburl") or info.get("url")
            if url and mime.startswith("image/"):
                return url
    return None


def download_bytes(url: str) -> bytes | None:
    try:
        response = requests.get(url, headers=HEADERS, timeout=REQUEST_TIMEOUT)
        response.raise_for_status()
        data = response.content
        if len(data) < 4000:
            return None
        with Image.open(BytesIO(data)) as img:
            if img.width < MIN_SIZE or img.height < MIN_SIZE:
                return None
        return data
    except Exception:
        return None


def save_as_jpg(name: str, data: bytes) -> Path:
    out_path = OUTPUT_DIR / f"{sanitize_filename(name)}.jpg"
    with Image.open(BytesIO(data)) as img:
        rgb = img.convert("RGB")
        rgb.save(out_path, format="JPEG", quality=88, optimize=True)
    return out_path


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    exercises = load_exercises()
    manifest: dict[str, dict] = {}
    if MANIFEST_PATH.exists():
        try:
            manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            manifest = {}

    print("Fetching wger exercise database...")
    wger_entries = fetch_all_wger_exercises()
    _, image_by_name = build_wger_index(wger_entries)
    print(f"Indexed {len(image_by_name)} wger exercise names with images.")

    ok = skipped = 0
    failed: list[str] = []

    for index, exercise in enumerate(exercises, 1):
        name = exercise["name"]
        out_path = OUTPUT_DIR / f"{sanitize_filename(name)}.jpg"
        force = name in EXPLICIT_IMAGE_URLS or name in WIKIMEDIA_ONLY

        if out_path.exists() and out_path.stat().st_size > 5000 and not force:
            print(f"[{index}/{len(exercises)}] SKIP (exists): {name}")
            skipped += 1
            continue

        print(f"[{index}/{len(exercises)}] {name} ...", end=" ", flush=True)
        source = "wger"
        image_url: str | None = None
        matched_name: str | None = None
        match_type: str | None = None

        if name in EXPLICIT_IMAGE_URLS:
            image_url = EXPLICIT_IMAGE_URLS[name]
            matched_name = name
            match_type = "explicit"
        elif name in WIKIMEDIA_ONLY:
            source = "wikimedia"
            image_url = wikimedia_image_url(name)
            matched_name = name if image_url else None
            match_type = "wikimedia"
            time.sleep(0.4)
        else:
            image_url, matched_name, match_type = find_wger_image(exercise, {}, image_by_name)

        if not image_url:
            source = "wikimedia"
            image_url = wikimedia_image_url(name)
            matched_name = name if image_url else None
            match_type = "wikimedia"
            time.sleep(0.4)

        if not image_url:
            print("NO IMAGE")
            failed.append(name)
            continue

        data = download_bytes(image_url)
        if not data:
            print("DOWNLOAD FAILED")
            failed.append(name)
            continue

        saved = save_as_jpg(name, data)
        manifest[name] = {
            "id": exercise["id"],
            "file": saved.name,
            "source": source,
            "source_url": image_url,
            "matched_name": matched_name,
            "match_type": match_type,
            "license_note": (
                "wger.de images are typically CC BY-SA (check source page). "
                "Verify license before production use."
                if source == "wger"
                else "Wikimedia Commons — check file license on commons.wikimedia.org"
            ),
        }
        MANIFEST_PATH.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
        print(f"OK ({source}: {matched_name}) -> {saved.name}")
        ok += 1
        time.sleep(0.25)

    print()
    print(f"Done. Downloaded: {ok}, Skipped: {skipped}, Failed: {len(failed)}")
    if failed:
        print("Failed:")
        for name in failed:
            print(f"  - {name}")


if __name__ == "__main__":
    main()
