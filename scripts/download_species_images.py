#!/usr/bin/env python3
"""Download Wikimedia Commons photos for UK species ID guide (male + female)."""
from __future__ import annotations

import json
import re
import ssl
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "core" / "designsystem" / "src" / "main" / "res" / "drawable-nodpi"
RESOLVER = (
    ROOT
    / "core"
    / "designsystem"
    / "src"
    / "main"
    / "java"
    / "uk"
    / "hunttrack"
    / "core"
    / "designsystem"
    / "species"
    / "SpeciesImageResolver.kt"
)
CREDITS = ROOT / "core" / "designsystem" / "src" / "main" / "assets" / "species_image_credits.json"

# species_id, sex, search queries (most specific first)
SPECIES_QUERIES: list[tuple[str, str, list[str]]] = [
    ("red_deer", "male", ["red deer stag", "Cervus elaphus stag"]),
    ("red_deer", "female", ["red deer hind", "Cervus elaphus hind"]),
    ("roe_deer", "male", ["roe deer buck", "Capreolus capreolus buck"]),
    ("roe_deer", "female", ["roe deer doe", "Capreolus capreolus doe"]),
    ("fallow_deer", "male", ["fallow deer buck", "Dama dama buck"]),
    ("fallow_deer", "female", ["fallow deer doe", "Dama dama doe"]),
    ("sika_deer", "male", ["sika deer stag", "Cervus nippon stag"]),
    ("sika_deer", "female", ["sika deer hind", "Cervus nippon hind"]),
    ("muntjac", "male", ["muntjac buck", "Muntiacus reevesi buck"]),
    ("muntjac", "female", ["muntjac doe", "Muntiacus reevesi doe"]),
    ("chinese_water_deer", "male", ["Chinese water deer buck", "Hydropotes inermis male"]),
    ("chinese_water_deer", "female", ["Chinese water deer doe", "Hydropotes inermis female"]),
    ("fox", "male", ["red fox dog", "Vulpes vulpes male"]),
    ("fox", "female", ["red fox vixen", "Vulpes vulpes female"]),
    ("rabbit", "male", ["European rabbit", "Oryctolagus cuniculus rabbit"]),
    ("rabbit", "female", ["European rabbit doe", "Oryctolagus cuniculus female"]),
    ("brown_hare", "male", ["European hare", "Lepus europaeus hare"]),
    ("brown_hare", "female", ["European hare female", "Lepus europaeus female"]),
    ("pheasant", "male", ["common pheasant cock", "Phasianus colchicus male"]),
    ("pheasant", "female", ["common pheasant hen", "Phasianus colchicus female"]),
    ("red_legged_partridge", "male", ["red-legged partridge male", "Alectoris rufa"]),
    ("red_legged_partridge", "female", ["red-legged partridge female", "Alectoris rufa hen"]),
    ("mallard", "male", ["mallard drake", "Anas platyrhynchos drake"]),
    ("mallard", "female", ["mallard hen", "Anas platyrhynchos female"]),
    ("teal", "male", ["common teal drake", "Anas crecca drake"]),
    ("teal", "female", ["common teal female", "Anas crecca hen"]),
    ("wigeon", "male", ["Eurasian wigeon drake", "Mareca penelope drake"]),
    ("wigeon", "female", ["Eurasian wigeon female", "Mareca penelope hen"]),
    ("pintail", "male", ["northern pintail drake", "Anas acuta drake"]),
    ("pintail", "female", ["northern pintail female", "Anas acuta hen"]),
    ("shoveler", "male", ["northern shoveler drake", "Spatula clypeata drake"]),
    ("shoveler", "female", ["northern shoveler female", "Spatula clypeata hen"]),
    ("gadwall", "male", ["gadwall drake", "Mareca strepera drake"]),
    ("gadwall", "female", ["gadwall female", "Mareca strepera hen"]),
    ("goldeneye", "male", ["common goldeneye drake", "Bucephala clangula drake"]),
    ("goldeneye", "female", ["common goldeneye female", "Bucephala clangula hen"]),
    ("tufted_duck", "male", ["tufted duck drake", "Aythya fuligula drake"]),
    ("tufted_duck", "female", ["tufted duck female", "Aythya fuligula hen"]),
    ("pochard", "male", ["common pochard drake", "Aythya ferina drake"]),
    ("pochard", "female", ["common pochard female", "Aythya ferina hen"]),
    ("scaup", "male", ["greater scaup drake", "Aythya marila drake"]),
    ("scaup", "female", ["greater scaup female", "Aythya marila hen"]),
    ("canada_goose", "male", ["Canada goose gander", "Branta canadensis"]),
    ("canada_goose", "female", ["Canada goose female", "Branta canadensis goose"]),
    ("greylag_goose", "male", ["greylag goose gander", "Anser anser"]),
    ("greylag_goose", "female", ["greylag goose female", "Anser anser goose"]),
    ("pink_footed_goose", "male", ["pink-footed goose", "Anser brachyrhynchus"]),
    ("pink_footed_goose", "female", ["pink-footed goose flock", "Anser brachyrhynchus goose"]),
    ("white_fronted_goose", "male", ["white-fronted goose", "Anser albifrons"]),
    ("white_fronted_goose", "female", ["white-fronted goose bird", "Anser albifrons flock"]),
    ("common_snipe", "male", ["common snipe", "Gallinago gallinago"]),
    ("common_snipe", "female", ["common snipe bird", "Gallinago gallinago flying"]),
    ("woodcock", "male", ["Eurasian woodcock", "Scolopax rusticola"]),
    ("woodcock", "female", ["woodcock bird", "Scolopax rusticola"]),
    ("woodpigeon", "male", ["common wood pigeon", "Columba palumbus"]),
    ("woodpigeon", "female", ["wood pigeon bird", "Columba palumbus"]),
    ("american_mink", "male", ["American mink", "Neogale vison"]),
    ("american_mink", "female", ["American mink female", "Neovison vison"]),
    ("grey_squirrel", "male", ["grey squirrel", "Sciurus carolinensis"]),
    ("grey_squirrel", "female", ["eastern gray squirrel", "Sciurus carolinensis squirrel"]),
    ("carrion_crow", "male", ["carrion crow", "Corvus corone"]),
    ("carrion_crow", "female", ["carrion crow bird", "Corvus corone corone"]),
    ("hooded_crow", "male", ["hooded crow", "Corvus cornix"]),
    ("hooded_crow", "female", ["hooded crow bird", "Corvus cornix"]),
    ("rook", "male", ["rook bird", "Corvus frugilegus"]),
    ("rook", "female", ["rook corvid", "Corvus frugilegus"]),
    ("jackdaw", "male", ["Eurasian jackdaw", "Coloeus monedula"]),
    ("jackdaw", "female", ["jackdaw bird", "Corvus monedula"]),
    ("magpie", "male", ["Eurasian magpie", "Pica pica"]),
    ("magpie", "female", ["magpie bird", "Pica pica"]),
    ("jay", "male", ["Eurasian jay", "Garrulus glandarius"]),
    ("jay", "female", ["jay bird", "Garrulus glandarius"]),
    ("brown_rat", "male", ["brown rat", "Rattus norvegicus"]),
    ("brown_rat", "female", ["Norway rat", "Rattus norvegicus female"]),
    ("black_rat", "male", ["black rat", "Rattus rattus"]),
    ("black_rat", "female", ["ship rat", "Rattus rattus"]),
]

# Known-good Wikimedia filenames when search is inconclusive (verified Commons files).
CURATED: dict[str, str] = {
    "red_deer_male": "RedDeerStag.jpg",
    "red_deer_female": "Hind Portrait (Red Deer) Full Frame (8054145950).jpg",
    "roe_deer_male": "Capreolus capreolus Wien Zentralfriedhof 20221021 06.jpg",
    "roe_deer_female": "Capreolus capreolus (F).jpg",
    "fallow_deer_male": "Male fallow deer with palmate antlers turning head.jpg",
    "fallow_deer_female": "Dama dama (F).jpg",
    "muntjac_male": "Muntjac Deer Thetford Forest - geograph.org.uk - 6193296.jpg",
    "muntjac_female": "Mundzak chinski Muntiacus reevesi RB2.jpg",
    "chinese_water_deer_male": "Chinese Water Deer (Hydropotes inermis).jpg",
    "chinese_water_deer_female": "Chinese water deer (Hydropotes inermis) - geograph.org.uk - 1442679.jpg",
    "fox_male": "Alaska Red Fox (Vulpes vulpes).jpg",
    "fox_female": "Red Fox, Vulpes vulpes (14729855617).jpg",
    "canada_goose_male": "Branta canadensis - Canada goose - Kharkiv Gorky Park - 01.jpg",
    "canada_goose_female": "Canadagoose.jpg",
    "mallard_male": "Mallard drake.jpg",
    "mallard_female": "Mallard hen.jpg",
    "pheasant_male": "Phasianus colchicus male.jpg",
    "pheasant_female": "Phasianus colchicus female.jpg",
}

UA = "HunTracK/1.2 (species-image-fetch; CC Wikimedia Commons; contact: local-dev)"
CTX = ssl._create_unverified_context()
DELAY_SEC = 3.0
MAX_RETRIES = 5

SKIP_TITLE = re.compile(
    r"(map|distribution|skull|skeleton|track|footprint|droppings|scat|diagram|illustration|"
    r"drawing|icon|logo|stamp|coin|fossil|hunting|trophy|antler rack|mount|"
    r"range|habitat|chart|graph|anatomy|infographic|silhouette|vector|svg|"
    r"egg|nest only|camera trap)",
    re.I,
)


def safe_name(species_id: str, sex: str) -> str:
    return f"species_{species_id}_{sex}"


def api_get(params: dict) -> dict:
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(MAX_RETRIES):
        try:
            with urllib.request.urlopen(req, timeout=45, context=CTX) as resp:
                return json.loads(resp.read().decode())
        except urllib.error.HTTPError as exc:
            if exc.code == 429 and attempt + 1 < MAX_RETRIES:
                wait = DELAY_SEC * (attempt + 2)
                print(f"  rate limited, waiting {wait:.0f}s...")
                time.sleep(wait)
                continue
            raise


def imageinfo_for_titles(titles: list[str]) -> list[dict]:
    if not titles:
        return []
    time.sleep(DELAY_SEC)
    data = api_get(
        {
            "action": "query",
            "format": "json",
            "titles": "|".join(titles),
            "prop": "imageinfo",
            "iiprop": "url|size|extmetadata|mime",
            "iiurlwidth": 960,
        }
    )
    out: list[dict] = []
    for page in data.get("query", {}).get("pages", {}).values():
        title = page.get("title", "").replace("File:", "")
        info = (page.get("imageinfo") or [{}])[0]
        if not info:
            continue
        out.append(
            {
                "title": title,
                "width": info.get("width") or 0,
                "url": info.get("thumburl") or info.get("url"),
                "mime": info.get("mime") or "",
            }
        )
    return out


def score_candidate(title: str, width: int, mime: str, query: str) -> int:
    if width < 350:
        return -1
    if mime and not mime.startswith("image/"):
        return -1
    if mime in ("image/svg+xml", "image/gif"):
        return -1
    if SKIP_TITLE.search(title):
        return -1
    score = min(width, 2400)
    lower = title.lower()
    qwords = [w for w in query.lower().split() if len(w) > 3]
    score += sum(40 for w in qwords if w in lower)
    if lower.endswith((".jpg", ".jpeg")):
        score += 30
    if "photo" in lower or "wildlife" in lower:
        score += 20
    return score


def find_image_file(queries: list[str]) -> tuple[str, str] | None:
    best: tuple[str, str] | None = None
    best_score = -1
    for query in queries:
        time.sleep(DELAY_SEC)
        try:
            data = api_get(
                {
                    "action": "query",
                    "format": "json",
                    "list": "search",
                    "srsearch": query,
                    "srnamespace": 6,
                    "srlimit": 12,
                }
            )
        except Exception as exc:
            print(f"  search error ({query}): {exc}")
            continue
        titles = [hit["title"] for hit in data.get("query", {}).get("search", [])]
        for item in imageinfo_for_titles(titles):
            s = score_candidate(item["title"], item["width"], item["mime"], query)
            if s > best_score and item["url"]:
                best_score = s
                best = (item["title"], item["url"])
        if best_score >= 500:
            break
    return best


def fetch_by_filename(filename: str) -> tuple[str, str] | None:
    try:
        title = f"File:{filename}"
        items = imageinfo_for_titles([title])
        if not items or not items[0]["url"]:
            return None
        return items[0]["title"], items[0]["url"]
    except Exception as exc:
        print(f"  curated fetch failed ({filename}): {exc}")
        return None


def download_url(url: str, dest: Path) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(MAX_RETRIES):
        try:
            with urllib.request.urlopen(req, timeout=90, context=CTX) as resp:
                data = resp.read()
            break
        except urllib.error.HTTPError as exc:
            if exc.code == 429 and attempt + 1 < MAX_RETRIES:
                wait = DELAY_SEC * (attempt + 2)
                print(f"  download rate limited, waiting {wait:.0f}s...")
                time.sleep(wait)
                continue
            raise
    if len(data) < 3000:
        raise RuntimeError("file too small")
    dest.write_bytes(data)


def write_resolver(map_entries: list[str]) -> None:
    body = "\n".join(map_entries)
    kotlin = f'''package uk.hunttrack.core.designsystem.species

import androidx.annotation.DrawableRes
import uk.hunttrack.core.designsystem.R
import uk.hunttrack.core.designsystem.markers.MarkerIconResolver
import uk.hunttrack.domain.catalog.UkFieldCatalog

/** Bundled male/female reference photos for the species ID guide (drawable-nodpi). */
object SpeciesImageResolver {{

    @DrawableRes
    fun maleImage(speciesId: String): Int = imageFor(speciesId, isMale = true)

    @DrawableRes
    fun femaleImage(speciesId: String): Int = imageFor(speciesId, isMale = false)

    @DrawableRes
    private fun imageFor(speciesId: String, isMale: Boolean): Int {{
        val key = "${{speciesId}}_${{if (isMale) "male" else "female"}}"
        return drawableByKey[key] ?: fallbackFor(speciesId)
    }}

    @DrawableRes
    private fun fallbackFor(speciesId: String): Int {{
        val species = UkFieldCatalog.speciesById(speciesId) ?: return R.drawable.marker_ic_other
        return MarkerIconResolver.iconForSpecies(species)
    }}

    fun hasPhoto(speciesId: String, isMale: Boolean): Boolean {{
        val key = "${{speciesId}}_${{if (isMale) "male" else "female"}}"
        return drawableByKey.containsKey(key)
    }}

    private val drawableByKey: Map<String, Int> = mapOf(
{body}
    )
}}
'''
    RESOLVER.write_text(kotlin, encoding="utf-8")


def resolve_image(key: str, queries: list[str]) -> tuple[str, str] | None:
    found = find_image_file(queries)
    if found:
        return found
    curated = CURATED.get(key)
    if curated:
        print(f"  trying curated {curated}")
        return fetch_by_filename(curated)
    return None


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    CREDITS.parent.mkdir(parents=True, exist_ok=True)
    credits: dict[str, str] = {}
    map_entries: list[str] = []
    ok = 0
    fail: list[str] = []

    for species_id, sex, queries in SPECIES_QUERIES:
        dest = OUT / f"{safe_name(species_id, sex)}.jpg"
        key = f"{species_id}_{sex}"
        if dest.exists() and dest.stat().st_size > 3000:
            ok += 1
            map_entries.append(f'        "{key}" to R.drawable.{safe_name(species_id, sex)},')
            print(f"SKIP {key} (exists)")
            continue
        found = resolve_image(key, queries)
        if not found:
            fail.append(key)
            print(f"FAIL {key}")
            continue
        title, url = found
        try:
            time.sleep(DELAY_SEC)
            download_url(url, dest)
            ok += 1
            credits[key] = f"Wikimedia Commons: {title}"
            map_entries.append(f'        "{key}" to R.drawable.{safe_name(species_id, sex)},')
            print(f"OK  {key} <- {title}")
        except Exception as exc:
            fail.append(f"{key}: {exc}")
            print(f"FAIL {key} ({exc})")

    CREDITS.write_text(json.dumps(credits, indent=2), encoding="utf-8")
    write_resolver(map_entries)
    print(f"\nReady {ok}/{len(SPECIES_QUERIES)} in {OUT}")
    if fail:
        print("Missing:", ", ".join(fail[:25]), ("..." if len(fail) > 25 else ""))


if __name__ == "__main__":
    main()
