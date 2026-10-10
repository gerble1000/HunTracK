#!/usr/bin/env python3
"""Fix missing species images and regenerate SpeciesImageResolver."""
from __future__ import annotations

import json
import time
from pathlib import Path

from download_species_images import (
    CREDITS,
    OUT,
    download_url,
    fetch_by_filename,
    write_resolver,
)

FIXES = [
    ("brown_hare_female", "European hare (Lepus europaeus) Marken 2.jpg"),
    ("rook_female", "Rook (Corvus frugilegus) Heligan.jpg"),
]


def main() -> None:
    for key, filename in FIXES:
        species_id, sex = key.rsplit("_", 1)
        dest = OUT / f"species_{species_id}_{sex}.jpg"
        found = fetch_by_filename(filename)
        if not found:
            print(f"FAIL {key}")
            continue
        title, url = found
        time.sleep(3)
        download_url(url, dest)
        print(f"OK  {key} <- {title}")

    map_entries: list[str] = []
    credits: dict[str, str] = {}
    if CREDITS.exists():
        credits = json.loads(CREDITS.read_text(encoding="utf-8"))
    for path in sorted(OUT.glob("species_*_*.jpg")):
        if path.stat().st_size < 3000:
            continue
        stem = path.stem
        map_key = stem.replace("species_", "", 1)
        map_entries.append(f'        "{map_key}" to R.drawable.{stem},')
        credits.setdefault(map_key, f"Wikimedia Commons: {stem}")

    write_resolver(map_entries)
    CREDITS.write_text(json.dumps(credits, indent=2), encoding="utf-8")
    print(f"Regenerated resolver with {len(map_entries)} images")


if __name__ == "__main__":
    main()
