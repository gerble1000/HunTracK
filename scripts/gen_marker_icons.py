"""Generate simple hunt marker vector drawables."""
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "core" / "designsystem" / "src" / "main" / "res" / "drawable"
OUT.mkdir(parents=True, exist_ok=True)

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="96dp"
    android:viewportWidth="96"
    android:viewportHeight="96">
    <path
        android:pathData="M48,10a34,34 0 1,1 0,68a34,34 0 1,1 0,-68z"
        android:fillColor="#1A1208"
        android:fillAlpha="0.55" />
    <path
        android:pathData="M48,14a30,30 0 1,1 0,60a30,30 0 1,1 0,-60z"
        android:fillColor="#E2B05C" />
{inner}
</vector>
"""

ICONS = {
    "marker_ic_deer": '<path android:pathData="M30,38 C24,28 18,24 14,18 C20,22 26,26 30,32 Z M66,38 C72,28 78,24 82,18 C76,22 70,26 66,32 Z M48,30 C38,30 32,38 32,48 C32,56 36,62 42,66 L42,74 L54,74 L54,66 C60,62 64,56 64,48 C64,38 58,30 48,30 Z" android:fillColor="#2A1A0C"/>',
    "marker_ic_fox": '<path android:pathData="M30,36 L22,22 L36,30 Z M66,36 L74,22 L60,30 Z M48,32 C40,32 34,40 34,50 C34,58 38,64 44,68 L44,76 L52,76 L52,68 C58,64 62,58 62,50 C62,40 56,32 48,32 Z M42,50a3,3 0 1,1 0,6a3,3 0 1,1 0,-6z M54,50a3,3 0 1,1 0,6a3,3 0 1,1 0,-6z" android:fillColor="#8B3A1A"/>',
    "marker_ic_rabbit": '<path android:pathData="M38,24 L34,14 L42,22 Z M58,24 L62,14 L54,22 Z M48,28 C40,28 34,36 34,46 C34,54 38,60 44,64 L44,74 L52,74 L52,64 C58,60 62,54 62,46 C62,36 56,28 48,28 Z" android:fillColor="#C4B8A8"/>',
    "marker_ic_hare": '<path android:pathData="M36,20 L32,8 L40,18 Z M60,20 L64,8 L56,18 Z M48,26 C40,26 34,34 34,44 C34,52 38,58 44,62 L44,74 L52,74 L52,62 C58,58 62,52 62,44 C62,34 56,26 48,26 Z" android:fillColor="#B8A898"/>',
    "marker_ic_bird": '<path android:pathData="M20,52 C32,40 44,36 56,40 C68,44 76,52 76,52 C64,48 52,50 44,56 C36,62 28,68 20,52 Z M44,44 C46,40 50,38 54,40 C52,44 48,46 44,44 Z" android:fillColor="#5A4A32"/>',
    "marker_ic_duck": '<path android:pathData="M24,54 C36,42 52,38 68,44 C60,48 50,52 42,58 C34,64 28,62 24,54 Z M52,40 C56,36 62,36 66,40 C64,44 58,46 52,40 Z M58,48 L72,46 L70,52 Z" android:fillColor="#3A5A48"/>',
    "marker_ic_goose": '<path android:pathData="M18,56 C34,42 54,38 74,46 C62,50 48,54 38,60 C30,66 22,64 18,56 Z M50,42 C56,38 64,40 68,46 C62,48 54,48 50,42 Z" android:fillColor="#6A7A88"/>',
    "marker_ic_snipe": '<path android:pathData="M22,58 C34,46 48,42 62,46 C54,50 44,54 36,60 C30,64 24,62 22,58 Z M48,44 L58,40 L56,48 Z" android:fillColor="#4A5A40"/>',
    "marker_ic_woodcock": '<path android:pathData="M24,56 C36,44 50,40 64,44 C56,48 46,52 38,58 C32,62 26,60 24,56 Z M46,42 C50,38 56,40 58,44 Z" android:fillColor="#6A5040"/>',
    "marker_ic_badger": '<path android:pathData="M48,30 C38,30 32,38 32,48 C32,58 38,66 48,66 C58,66 64,58 64,48 C64,38 58,30 48,30 Z M36,44 L60,44 L60,52 L36,52 Z" android:fillColor="#2A2A2A"/>',
    "marker_ic_pest": '<path android:pathData="M48,28 C40,28 34,36 34,46 C34,56 40,64 48,64 C56,64 62,56 62,46 C62,36 56,28 48,28 Z M40,42 L56,42 L56,50 L40,50 Z" android:fillColor="#5A4030"/>',
    "marker_ic_crow": '<path android:pathData="M22,50 C34,38 48,34 62,38 C54,42 44,46 36,52 C30,56 24,54 22,50 Z M40,36 L34,26 L42,34 Z M56,36 L62,26 L54,34 Z M48,34 C42,34 38,40 38,48 C38,54 42,58 46,60 L46,66 L50,66 L50,60 C54,58 58,54 58,48 C58,40 54,34 48,34 Z" android:fillColor="#1A1A1A"/>',
    "marker_ic_rat": '<path android:pathData="M24,50 C34,42 46,40 58,44 C50,48 40,52 32,56 C28,58 24,54 24,50 Z M58,44 L72,42 L70,48 Z M36,46 C38,44 42,44 44,46 C42,48 38,48 36,46 Z M52,46 C54,44 58,44 60,46 C58,48 54,48 52,46 Z M42,56 C46,58 50,58 54,56 L56,62 C50,64 46,64 40,62 Z" android:fillColor="#6A5A48"/>',
    "marker_ic_parking": '<path android:pathData="M30,68 L30,32 L50,32 C58,32 64,38 64,46 C64,54 58,60 50,60 L42,60 L42,68 Z M42,40 L42,52 L50,52 C54,52 56,50 56,46 C56,42 54,40 50,40 Z" android:fillColor="#2A4A6A"/><path android:pathData="M22,72 L74,72 L74,76 L22,76 Z" android:fillColor="#1A1A1A"/>',
    "marker_ic_gate": '<path android:pathData="M28,72 L28,28 L32,28 L32,72 Z M64,72 L64,28 L68,28 L68,72 Z M32,40 L64,40 L64,44 L32,44 Z M32,56 L64,56 L64,60 L32,60 Z" android:fillColor="#5A4030"/>',
    "marker_ic_fence": '<path android:pathData="M24,72 L24,32 L28,32 L28,72 Z M40,72 L40,24 L44,24 L44,72 Z M56,72 L56,32 L60,32 L60,72 Z M72,72 L72,28 L76,28 L76,72 Z M28,36 L40,32 L44,36 L32,40 Z M44,28 L56,32 L60,28 L48,24 Z" android:fillColor="#6A5038"/>',
    "marker_ic_boundary": '<path android:pathData="M20,48 L76,48" android:strokeWidth="4" android:strokeColor="#C84848"/><path android:pathData="M20,48 L28,40 L28,56 Z M76,48 L68,40 L68,56 Z" android:fillColor="#C84848"/>',
    "marker_ic_danger": '<path android:pathData="M48,24 L72,68 L24,68 Z" android:fillColor="#C84848"/><path android:pathData="M48,40 L48,56 M48,60 L48,62" android:strokeWidth="4" android:strokeColor="#F5E6C8"/>',
    "marker_ic_shelter": '<path android:pathData="M48,26 L78,56 L18,56 Z" android:fillColor="#4A3828"/><path android:pathData="M34,56 L34,72 L62,72 L62,56 Z" android:fillColor="#3A2A1C"/>',
    "marker_ic_high_seat": '<path android:pathData="M40,72 L40,40 L56,40 L56,72 Z M36,40 L60,40 L60,36 L36,36 Z M44,32 L52,32 L52,36 L44,36 Z" android:fillColor="#4A3828"/>',
    "marker_ic_hide": '<path android:pathData="M24,68 C32,52 40,44 48,44 C56,44 64,52 72,68 Z M32,68 L64,68 L64,72 L32,72 Z" android:fillColor="#3A4A30"/>',
    "marker_ic_feed": '<path android:pathData="M32,56 C36,48 42,44 48,44 C54,44 60,48 64,56 Z M28,56 L68,56 L68,68 L28,68 Z" android:fillColor="#6A5030"/>',
    "marker_ic_camera": '<path android:pathData="M28,40 L36,32 L60,32 L68,40 L68,64 L28,64 Z M48,46 C42,46 38,50 38,56 C38,62 42,66 48,66 C54,66 58,62 58,56 C58,50 54,46 48,46 Z" android:fillColor="#2A2A2A"/>',
    "marker_ic_water": '<path android:pathData="M48,24 C40,36 34,44 34,52 C34,62 40,68 48,68 C56,68 62,62 62,52 C62,44 56,36 48,24 Z" android:fillColor="#3A6A8A"/>',
    "marker_ic_salt": '<path android:pathData="M32,68 L32,44 L64,44 L64,68 Z M36,48 L60,48 L60,52 L36,52 Z" android:fillColor="#C8C8C8"/>',
    "marker_ic_tree_stand": '<path android:pathData="M48,20 C40,32 36,40 36,48 C36,54 40,58 48,58 C56,58 60,54 60,48 C60,40 56,32 48,20 Z M44,58 L44,72 L52,72 L52,58 Z" android:fillColor="#2A4A20"/>',
    "marker_ic_footprint": '<path android:pathData="M36,58a6,8 0 1,1 0,16a6,8 0 1,1 0,-16z M48,52a5,7 0 1,1 0,14a5,7 0 1,1 0,-14z M58,58a6,8 0 1,1 0,16a6,8 0 1,1 0,-16z" android:fillColor="#4A3420"/>',
    "marker_ic_blood": '<path android:pathData="M48,22 C40,36 34,46 34,54 C34,64 40,70 48,70 C56,70 62,64 62,54 C62,46 56,36 48,22 Z" android:fillColor="#8B1A1A"/>',
    "marker_ic_hair": '<path android:pathData="M30,60 C38,48 46,44 54,48 C50,52 44,56 38,62 C34,66 30,64 30,60 Z M54,48 C62,52 66,58 64,64 C60,62 56,58 54,52 Z" android:fillColor="#8A7A60"/>',
    "marker_ic_droppings": '<path android:pathData="M36,58a5,6 0 1,1 0,12a5,6 0 1,1 0,-12z M48,54a6,7 0 1,1 0,14a6,7 0 1,1 0,-14z M58,60a5,6 0 1,1 0,12a5,6 0 1,1 0,-12z" android:fillColor="#3A2A18"/>',
    "marker_ic_bedding": '<path android:pathData="M26,58 C34,50 42,46 50,46 C58,46 66,50 74,58 L74,68 L26,68 Z" android:fillColor="#5A4A30"/>',
    "marker_ic_sighting": '<path android:pathData="M48,28a20,20 0 1,1 0,40a20,20 0 1,1 0,-40z M48,36a12,12 0 1,1 0,24a12,12 0 1,1 0,-24z" android:fillColor="#2A4A6A"/>',
    "marker_ic_other": '<path android:pathData="M48,30a18,18 0 1,1 0,36a18,18 0 1,1 0,-36z" android:fillColor="#4A4A4A"/>',
}

for name, inner in ICONS.items():
    (OUT / f"{name}.xml").write_text(TEMPLATE.format(inner=inner), encoding="utf-8")

print(f"Wrote {len(ICONS)} icons to {OUT}")
