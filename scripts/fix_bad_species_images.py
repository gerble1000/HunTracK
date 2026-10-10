#!/usr/bin/env python3
"""Replace bad species reference photos with verified Wikimedia files."""
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

# Verified Commons filenames — full animal visible, correct species.
FIXES: dict[str, str] = {
    # Muntjac — Reeves' muntjac (UK species), not Indian muntjac
    "muntjac_male": "Muntjac (Muntiacus reevesi) male.jpg",
    "muntjac_female": "Reeves's Muntjac (Muntiacus reevesi) female.jpg",
    # Roe buck — close portrait
    "roe_deer_male": "Roe Deer Buck (Capreolus capreolus) (5112884383).jpg",
    # Chinese water deer doe
    "chinese_water_deer_female": "Chinese water deer female.jpg",
    # Red hind — full body, head visible
    "red_deer_female": "Cervus elaphus female Rödinghausen.jpg",
    # Fallow buck — palmated antlers
    "fallow_deer_male": "Fallow deer buck (Dama dama) with antlers.jpg",
    # Red fox — not WWII landing craft
    "fox_male": "Red fox (Vulpes vulpes) in snow.jpg",
    # Canada goose — actual Branta canadensis
    "canada_goose_male": "Branta canadensis (Canada goose) male.jpg",
    "canada_goose_female": "Canada goose (Branta canadensis) - nature images.jpg",
}

FALLBACK_SEARCH: dict[str, list[str]] = {
    "muntjac_male": ["Muntiacus reevesi buck UK", "Reeves muntjac male"],
    "muntjac_female": ["Muntiacus reevesi doe", "Reeves muntjac female"],
    "roe_deer_male": ["roe deer buck close Capreolus", "roe buck antlers portrait"],
    "chinese_water_deer_female": ["Hydropotes inermis female", "Chinese water deer doe"],
    "red_deer_female": ["red deer hind Cervus elaphus", "red deer female portrait"],
    "fallow_deer_male": ["fallow deer buck palmate antlers Dama dama", "Damhirsch antlers"],
    "fox_male": ["Vulpes vulpes red fox portrait", "red fox UK wildlife"],
    "canada_goose_male": ["Branta canadensis Canada goose", "Canada goose gander"],
    "canada_goose_female": ["Branta canadensis goose female", "Canada goose bird"],
}


def main() -> None:
    from download_species_images import resolve_image

    credits: dict[str, str] = {}
    if CREDITS.exists():
        credits = json.loads(CREDITS.read_text(encoding="utf-8"))

    for key, filename in FIXES.items():
        species_id, sex = key.rsplit("_", 1)
        dest = OUT / f"{safe_name(species_id, sex)}.jpg"
        found = fetch_by_filename(filename)
        if not found:
            print(f"  curated miss: {filename}")
            found = resolve_image(key, FALLBACK_SEARCH.get(key, []))
        if not found:
            print(f"FAIL {key}")
            continue
        title, url = found
        time.sleep(3)
        try:
            download_url(url, dest)
            credits[key] = f"Wikimedia Commons: {title}"
            print(f"OK  {key} <- {title}")
        except Exception as exc:
            print(f"FAIL {key} ({exc})")

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
    print(f"Done — {len(map_entries)} images in resolver")


if __name__ == "__main__":
    main()
