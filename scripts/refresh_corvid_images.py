#!/usr/bin/env python3
"""Refresh corvid reference photos with clear head-and-body shots."""
from __future__ import annotations

import json
import time
from pathlib import Path

from download_species_images import (
    CREDITS,
    OUT,
    RESOLVER,
    download_url,
    fetch_by_filename,
    resolve_image,
    safe_name,
    write_resolver,
)

# Curated Wikimedia filenames — full bird visible, UK/European where possible.
CURATED: dict[str, str] = {
    "carrion_crow_male": "Carrion Crow in Jersey.jpg",
    "carrion_crow_female": "Carrion Crow 2023 11 19 01.jpg",
    "magpie_male": "Eurasian magpie (Pica pica).jpg",
    "magpie_female": "Common magpie (Pica pica).jpg",
    "rook_male": "Rook (Corvus frugilegus) Heligan.jpg",
    "rook_female": "Rook (Corvus frugilegus) - geograph.org.uk - 173050.jpg",
    "jackdaw_male": "Western Jackdaw 2025 05 23 01.jpg",
    "jackdaw_female": "Jackdaw (Corvus monedula).jpg",
}

SEARCH: dict[str, list[str]] = {
    "carrion_crow_male": ["carrion crow Corvus corone", "common crow UK"],
    "carrion_crow_female": ["carrion crow bird", "Corvus corone corone"],
    "magpie_male": ["Eurasian magpie Pica pica", "magpie UK bird"],
    "magpie_female": ["magpie bird full", "Pica pica magpie"],
    "rook_male": ["rook Corvus frugilegus bird", "rook bird UK"],
    "rook_female": ["rook Corvus frugilegus", "rook farmland"],
    "jackdaw_male": ["Eurasian jackdaw", "jackdaw Corvus monedula"],
    "jackdaw_female": ["jackdaw bird", "Coloeus monedula"],
}


def main() -> None:
    for key, filename in CURATED.items():
        species_id, sex = key.rsplit("_", 1)
        dest = OUT / f"{safe_name(species_id, sex)}.jpg"
        found = fetch_by_filename(filename)
        if not found:
            found = resolve_image(key, SEARCH.get(key, []))
        if not found:
            print(f"FAIL {key}")
            continue
        title, url = found
        time.sleep(3)
        try:
            download_url(url, dest)
            print(f"OK  {key} <- {title}")
        except Exception as exc:
            print(f"FAIL {key} ({exc})")

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
        if map_key in CURATED:
            credits[map_key] = f"Wikimedia Commons: {CURATED[map_key]}"
    write_resolver(map_entries)
    CREDITS.write_text(json.dumps(credits, indent=2), encoding="utf-8")
    print(f"Regenerated {len(map_entries)} resolver entries")


if __name__ == "__main__":
    main()
