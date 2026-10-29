# iOS beta 1.1.29 (40)

Build 40 is the revision after 1.1.28 (39), which is currently the newest approved
TestFlight build but shows zero installs, so tester feedback so far describes 1.1.27 (38).

## Changes in this build

- **Animated scent plume on the map.** The static two-polygon cone is replaced by a
  `MKOverlayRenderer` that draws 32 drifting puffs downwind, sized from
  `ScentPlumeGeometry` (length, half-width, gust/humidity/temperature). It refreshes on a
  0.12 s timer that only runs while the map is on screen and the app is active, and is
  cancelled in `dismantleUIView`.
- **Shared weather freshness rule.** `ScentPlumeGeometry.usableWeather` centralises the
  "recent and local" check (under 30 minutes old, within 15 km) and is now used by both the
  map plume and the Wind screen instead of duplicated inline conditions.
- **Wind screen is honest about missing data.** It no longer fabricates a position with a
  fallback fix; with no GPS or stale/distant weather it shows "Waiting for GPS and recent
  local wind data…", requests a fix on appear, and refreshes at most once a minute. A
  "clear" verdict is only claimed when a real assessment was possible.
- **Photo pins on the map.** Field markers and cull pins are drawn as teardrop pins with
  the species photo (or category symbol / skull for culls) inside a bordered circle,
  instead of stock MapKit balloons.
- **Cull pins follow the visibility preference** — linked markers are no longer hidden
  from the map when cull pins are switched off.
- **Permissions link to boundaries.** The editor has a boundary picker, cards show the
  linked boundary name, several permissions can share one boundary, and the boundary
  sheet warns before replacing an existing link. The "verified written permission" claim
  is replaced with neutral wording.
- **Boundary capture bar:** Save/Undo/Cancel reflow so the controls are no longer cramped,
  and map controls are hidden while drawing.

## Version metadata

`Info.plist` and both project configurations agree on **1.1.29 (40)**. The marketing
version was bumped so testers can tell 40 apart from 39 in TestFlight and in
Settings → About HunTracK.

## Verification in this session (Windows, no Xcode)

- `node --test .github/scripts/testflight-distribute.test.mjs`: **6 passed** (mocked API).
- Tree-sitter Swift parse of the five edited files: **zero syntax diagnostics**. This
  checks syntax only — not Swift types, SDK availability or runtime behaviour.
- Asset and version consistency checked with grep.

## Not verified

No compiler or simulator was available here. The macOS workflow must archive the exact
revision; if it fails, the error comes from type checking or an SDK API and needs a fix
and a new build. Device behaviour of the smoke animation (frame rate, appearance at
zoom), the photo pins and the wind/permission flows still needs a real iPhone.
