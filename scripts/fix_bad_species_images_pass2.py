#!/usr/bin/env python3
"""Second pass — fix remaining bad species photos."""
from __future__ import annotations

import json
import time
from pathlib import Path

from download_species_images import (
    CREDITS,
    OUT,
    download_url,
    fetch_by_filename,
    safe_name,
    write_resolver,
)

FIXES: dict[str, str] = {
    "roe_deer_male": "Capreolus capreolus Wien Zentralfriedhof 20221021 06.jpg",
    "red_deer_female": "Hind Portrait (Red Deer) Full Frame (8054145950).jpg",
    "fallow_deer_male": "Male fallow deer with palmate antlers turning head.jpg",
    "chinese_water_deer_female": "Chinese water deer (Hydropotes inermis) - geograph.org.uk - 1442679.jpg",
    "canada_goose_female": "Canadagoose.jpg",
}


def main() -> None:
    credits: dict[str, str] = {}
    if CREDITS.exists():
        credits = json.loads(CREDITS.read_text(encoding="utf-8"))

    for key, filename in FIXES.items():
        species_id, sex = key.rsplit("_", 1)
        dest = OUT / f"{safe_name(species_id, sex)}.jpg"
        found = fetch_by_filename(filename)
        if not found:
            print(f"FAIL {key} (no file {filename})")
            continue
        title, url = found
        time.sleep(3)
        download_url(url, dest)
        credits[key] = f"Wikimedia Commons: {title}"
        print(f"OK  {key} <- {title}")

    map_entries: list[str] = []
    for path in sorted(OUT.glob("species_*_*.jpg")):
        if path.stat().st_size < 3000:
            continue
        stem = path.stem
        map_key = stem.replace("species_", "", 1)
        map_entries.append(f'        "{map_key}" to R.drawable.{stem},')
        credits.setdefault(map_key, f"Wikimedia Commons: {stem}")

    write_resolver(map_entries)
    CREDITS.write_text(json.dumps(credits, indent=2), encoding="utf-8")
    print(f"Done — {len(map_entries)} images")


if __name__ == "__main__":
    main()
