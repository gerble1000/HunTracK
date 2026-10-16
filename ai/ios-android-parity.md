# HunTracK — iOS vs Android feature parity audit

Measured from source on 2026-10-16. Every claim below is traced to a file.

## Headline

| | Android (Kotlin/Compose) | iOS (SwiftUI) |
|---|---|---|
| Source size | **21,781 LOC** (`app` 1,398 + `core` 5,417 + `domain` 2,912 + `feature` 12,054) | **2,586 LOC** across 25 `.swift` files |
| Feature modules / screens | 14 modules, **15 routes** | **11 screens** |
| Persistence | Room DB, **16 entities / 13 DAOs** + DataStore prefs | 2 `UserDefaults` blobs (markers, prefs) |
| Nav shell | `app/.../ui/HunTracKAppScaffold.kt` | `HunTracKApp.swift` (TabView) |

The iOS port is roughly **8× smaller** than the Android app. It is not a port with a few
missing screens — it is a **UI skeleton reusing the Android domain models**, where most
screens are static mockups rather than working features.

Both apps share the same 4 bottom tabs (Home, Map, Weather, More) and the same 3 themes,
palette, `MarkerCategory` set (28), `SpeciesGroup`/`UkNation` enums and `OsGridRef` maths.
That shared shell is why the two look identical at a glance — and why the gaps surprise testers.

---

## 0. Why the menu looks smaller on iOS

The most-reported complaint is specifically about the **menu**, so here is the row-by-row count.

### The "More" screen

| | Android `app/.../ui/more/MoreScreen.kt` | iOS `Features/More/MoreScreen.swift` |
|---|---|---|
| Rows | **9** | **7** |
| Section headers | **5** | **4** |

| Section | Android rows | iOS rows |
|---|---|---|
| Navigation | Compass | Compass, **Wind & Scent** |
| Field knowledge | Species, Calls | Species Catalog, Animal Calls |
| **Planning** | **Calendar, Cull log** | — *section absent* |
| Safety & access | Permissions, Emergency | Permissions Wallet, Emergency Profile |
| App | Settings, **About** | Settings |

So the menu is short by three rows — **Calendar**, **Cull log** and **About** — and one whole
section header ("Planning"). Subtitle differs too: Android reads "Tap any row to open", iOS
"Field tools & settings".

**But the row counts do not tell the whole story — iOS gains one back.** Android registers a
`Dest.Wind` route (`HunTracKAppScaffold.kt` L391) but **nothing navigates to it**: it is not a bottom
tab (`bottomTabs` = Home, Map, Weather, More), not in the More list, and there is no
`navigate(Dest.Wind.route)` call in the whole project. `WindScreen.kt` (291 LOC) and
`WindViewModel.kt` (180 LOC) are compiled and reachable from nowhere — the Wind & Scent tool is
effectively dead code on Android. iOS is therefore the **only** platform where Wind & Scent can
actually be opened, via its More row.

Verified by diffing registered routes against navigation targets:

```
registered:  About Calendar Calls Compass Culls Emergency Home Map More Permissions Settings Species Weather Wind
navigated:   About Calendar Calls Compass Culls Emergency Home Map More Permissions Settings Species
```

`Weather` is absent from that list only because it is reached through the bottom tab
(`navigateToTab` → generic `navigate(dest.route)` at L199). **Wind has no such path.**

### App chrome — the larger visual difference

Android wraps every screen in `HunTracKAppScaffold`'s `Scaffold`, which is always visible:

- **Top app bar** with a tappable "HunTracK" title (returns Home) and **two** icon actions:
  a theme cycler (Woodland → Night → Hunt) at L245 and a sound on/off toggle at L269
  (`HunTracKAppScaffold.kt` L245–L288)
- **Floating action button** — "Mark current location" — shown on all four tabs and the Map route
  (`HunTracKAppScaffold.kt` L291–L303)
- Bottom navigation, 4 tabs

iOS applies `.navigationBarHidden(true)` to **all four tabs** (`HunTracKApp.swift` L30/39/48/57) and
the only toolbars anywhere in the iOS target are the Settings title and the add-marker sheet's
"Cancel" button. There is **no top bar, no theme or sound quick-toggle, and no mark-location button**.

That is why the two apps feel different in *quantity* at a glance: Android shows 2 icon actions plus a
persistent FAB plus 9 menu rows; iOS shows 0 actions, no FAB, and 7 menu rows. Both keep the same
4 bottom tabs, so the tab bar is not the source of the impression.

---

## 1. Screens/features missing from iOS entirely

| Feature | Android evidence | iOS |
|---|---|---|
| **Calendar** — day/month/year field diary, per-day entries (title, location/beat, notes), month grid, year grid, day detail panel, persisted | `feature/calendar/` `CalendarScreen.kt` 454 LOC + `CalendarViewModel.kt` 194, `core/data/phase2/CalendarRepository.kt`, `CalendarDayEntryEntity`, `domain/geo/CalendarDates.kt` | **Absent.** Not in `MoreScreen.swift`, no route. |
| **Cull log** — cull records with count, location name, OS Grid ref (apply-grid / use-GPS), notes, sex + age class + carcass test status, "also place map pin", CSV export, delete | `feature/culls/` `CullScreen.kt` 454 LOC + `CullViewModel.kt` 261, `core/data/cull/CullRepository.kt`, `CullRecordEntity`, `domain/model/CullModels.kt` | **Absent.** No route, no model usage. |
| **Tides** — UK tide station catalog, lunar tide calculator, live tide-times API + cache, station picker, station map dialog, home tide card | **1,297 LOC**: `feature/tides/` (`TideSettingsSection.kt` 151, `TideStationMapDialog.kt` 314, `TidesViewModel.kt` 385), `core/data/tides/` (`TideRepository` 132, `TideTimesClient` 93, `TideCacheModels` 31), `domain/tides/` (`UkTideStationCatalog`/`TideStationProfile` 97, `LunarTideCalculator` 94) | **Absent.** `UserPreferences.tidesEnabled`/`tideStationId` exist in `Models.swift` but nothing reads them. |
| **About screen** — app story, developer, helping hand | `app/.../ui/about/AboutScreen.kt`, routed as `Dest.About` | **Absent.** |
| **Splash + Studio intro** — branded launch animation | `app/.../ui/splash/SplashScreen.kt`, `StudioIntroScreen.kt` | **Absent.** `UILaunchScreen` only sets a colour. |
| **Field training overlays** — Map training, Wind & scent training, "tap ? to reopen", auto-open toggles | `MapScreen.kt` L1696, `WindScreen.kt` L284, `SettingsScreen.kt` L86/L94 | **Absent.** `mapTrainingEnabled`/`windTrainingEnabled` prefs exist, unused. |
| **ComingSoon scaffold screen** | `app/.../ui/ComingSoonScreen.kt` | **Absent.** |

## 2. Screens present on both, but gutted on iOS

### Map — the largest single gap
Android `MapScreen.kt` (3,305) + `MapViewModel.kt` (769) + `MapViewHost.kt` (132) = **4,206 LOC** uses **MapLibre** and supports:

- 4 selectable basemaps + layer picker; style JSON patching (`core/common/MapLibreStyleSupport.kt`)
- **OS Grid** overlay: grid lines for viewport, grid ref readout, "Go to OS Grid" input
- **Track recording** — start route, `TrackPoint` stream, distance calculation, path overlay (`RouteRepository`, `RouteEntity`, `TrackPointEntity`)
- **Offline maps** — "Download 15 sq mile pack around me", region registry, delete (`OfflineMapRepository`)
- **Wind smoke plume** particle overlay, wind arrow animation
- **Cull pins** overlay toggle
- **Permission boundary** overlays (HUNTING / NO_GO / SAFE with per-zone colour + fill opacity)
- **Homing retrieve** — guides you back to a downed animal with arrival alert, back/end-homing
- "Home here" house marker, delete last/all
- **Fence** capture — walk it or draw it, save fence, pan mode
- Marker placement modes, "mark anywhere" toggle, place-next-marker prompt, delete marker
- Scale/overlays panel, sync indicator

iOS `Map/MapScreen.swift` is **114 lines**: `Map(coordinateRegion:)` with pin annotations and a
`+` button. No layers, no tracks, no offline packs, no OS grid overlay, no boundaries,
no homing, no fence tools. Its add-marker sheet collects name/category/notes only, where the
Android sheet also handles species, GPS-readiness gating, error surfacing and "save area as offline map".

### Weather — no forecast at all
- Android: current conditions **+ hourly `HourCard` + daily `DayCard` (7-day)**, rain probability,
  visibility, weather-code labels, Room-backed weather cache, per-unit formatting, clear-cache action.
- iOS (`WeatherScreen.swift`, 59 lines): current conditions only — temperature, feels-like, wind,
  direction, humidity, pressure, visibility. **No hourly or daily forecast is fetched or shown**,
  and `WeatherService` only reads the `current` block of the Open-Meteo response.

### Species catalog — data and filters
- Android: **38 species** in `UkFieldCatalog.kt` (963 LOC) with habitat, behaviour, distribution,
  male/female/young traits, tracks, droppings, **per-nation legal seasons** (`LegalSeason` by nation
  and sex), per-species sex terminology, season open/closed logic (`SeasonStatus`), group filter
  (6 groups), **nation selector** (England/Wales/Scotland/NI), `SeasonCard`, species imagery
  (`SpeciesImageResolver` male/female), prose formatter, cross-links into calls.
- iOS: **5 species** (red deer, roe deer, fallow deer, fox, rabbit) and **zero `LegalSeason` instances** —
  the struct exists but nothing populates it. `seasonsNote` is free text ("Seasons vary by nation and
  sex. Check local regulations."). No images, no nation selector, no season status. The screen
  declares `@State selectedGroup` and **never uses it** — the group filter is dead code.

### Animal calls — iOS plays UI sound effects, not animal calls
- Android (`CallsScreen.kt` 553 + `CallsViewModel.kt` 336): built-in call library from the catalog,
  **audio packs with download progress and offline-ready state** (`AudioPackDownloader`),
  **user call recorder** (`CallAudioEngine.startRecording`, `UserCallAudioImporter`, mic permission),
  **persisted call journal** (response heard, distance in metres, notes), loop playback,
  custom call editor (name, notes/"when to use"), delete, species-linked.
- iOS (`CallsScreen.swift`, 81 lines): **three hardcoded cards** — "Red Deer Stag Roar",
  "Roe Deer Doe Squeak", "Mallard Duck Quack" — wired to `HuntSound.appLoaded`, `.click` and `.arrival`.
  These are the app's own UI effects, not real calls. No journal, no recorder, no packs, no persistence.

### Emergency profile — not persisted, hardcoded demo data
- Android: public ICE display + edit mode **locked behind PIN hash or biometrics**
  (`EmergencyRepository.hashPin/verifyPin`), fields for full name, blood type, allergies,
  medical information, medication, notes, and a **contacts list** (`EmergencyContact`);
  persisted in `EmergencyProfileEntity`.
- iOS (`EmergencyScreen.swift`, 86 lines): `@State` defaults literally reading
  `"Hunter"`, `"O Positive"`, `"Penicillin"`, `"Jane Doe: +44 7700 900456"`. **Nothing is saved** —
  edits reset on relaunch. Biometric unlock exists but there is no PIN path.

### Permissions wallet — a single static card
- Android (`PermissionsScreen.kt` 661 + `PermissionsViewModel.kt` 231): permission records with document types (PDF / photo / note), landowner
  records, permission↔boundary linking, boundary areas in 3 zone types with GPS **walk** or **draw**
  capture, `BoundaryMath` real area (ha/acres) and perimeter, map focus items, pending-creation session.
- iOS (`PermissionsScreen.swift`, 45 lines): one hardcoded example — "Manor Farm Shoot",
  "John Thornhill", "+44 7700 900123", "Roe Deer, Muntjac, Fox", "01 Sep 2027".
  No storage, no creation, no boundaries, no map.

### Wind & scent — dial only
- Android: custom canvas `WindDirectionDial`, `WindSmokeCloud` particle animation, training overlay,
  plume maths with gust, humidity and time-of-day thermal effects, jumps to the map.
- iOS: `ScentPlumeGeometry.swift` is ported but **drops the thermal/time-of-day term**.
  `WindScreen.swift` draws a circle with a rotated arrow and a downwind sentence. Its
  "Field Markers Check" section renders **every marker with a hardcoded green "Clear" badge** —
  it does not evaluate scent risk at all.

### Settings — half the controls are missing
| Setting | Android | iOS |
|---|---|---|
| Theme mode (3) | ✅ | ✅ |
| Use Celsius | ✅ | ✅ |
| Unit system metric/imperial | ✅ | ❌ (`unitSystem` pref exists, no UI) |
| Wind in mph | ✅ | ❌ |
| Map style / basemap picker | ✅ | ❌ (`mapLayerStyle` pref exists, no UI) |
| Show OS grid lines | ✅ | ✅ |
| Show cull pins on map | ✅ | ❌ |
| Mark anywhere | ✅ | ❌ |
| UI sounds / vibration | ✅ | ✅ |
| Map training / wind training | ✅ | ❌ |
| Tides section (enable + station) | ✅ | ❌ |
| Clear weather cache | ✅ | ❌ |
| About entry | ✅ | ❌ |

### Home dashboard
- Android: conditions rating, weather, **astronomy panel** (sunrise, sunset, golden hour,
  "fox & rabbit from", moon phase, illumination %, moonrise, moonset), GPS card with a
  **copy-location sheet in 3 formats** (Cardinal GPS / Decimal GPS / OS Grid), activity rating,
  tide card, quick-add-marker button, theme and sound toggles in the top bar.
- iOS: conditions rating, 5 weather metrics, GPS + OS grid. **Astronomy is calculated but never
  displayed anywhere in the app** (`AstronomyInfo` is only fed into the rating functions).
  No copy-location sheet, no tide card, no quick-add, no in-app theme/sound toggle.

## 3. Data layer

Android Room schema (`core/data/db/Entities.kt`, **16 entities**; `Daos.kt`, **13 DAOs**):
`Marker`, `Route`, `TrackPoint`, `OfflineMapRegion`, `WeatherCache`, `CallJournal`, `UserCall`,
`AudioPack`, `HuntingPermission`, `BoundaryArea`, `BoundaryPoint`, `CalendarDayEntry`,
`TideStationCache`, `TideEventsCache`, `EmergencyProfile`, `CullRecord`.
Counted with `grep -cE '^data class \w+Entity\('` → 16, `grep -cE '^interface \w+Dao'` → 13.

iOS `DatabaseService.swift` persists exactly two things to `UserDefaults`: `markers` and
`preferences`. **Every other feature has nowhere to store data**, which is why Calendar, Culls,
Tides, Permissions, Emergency and the Calls journal are absent rather than merely unfinished.

## 4. What iOS does have at parity

- 3-theme palette system and colour tokens (`HuntColors.swift` ↔ `HuntColors.kt`)
- 28 `MarkerCategory` values in 4 groups, `MarkerCategoryGroup`
- `OsGridRef` conversions (266 iOS / 386 Android LOC)
- **Full astronomy maths** — sunrise/sunset/moonrise/moonset/phase/illumination/golden hour/twilight
  (162 iOS vs 165 Android LOC), though only used internally
- Deer + Rabbit/Fox activity ratings and the rabbit/fox time-window switch
- `ScentPlumeGeometry` (simplified)
- Compass with magnetic true + GPS course — comparable
- Weather fetch from Open-Meteo (current block only)

## 5. Recommended order to close the gap

1. **Tides, Calendar, Culls** — entirely missing screens; highest-visibility complaint.
2. **Weather hourly/daily** — small change (extend `WeatherService` to the `hourly`/`daily` blocks
   plus `HourCard`/`DayCard` views) with a large perceived gain.
3. **Species seasons + full catalog** — port the remaining 33 species and the `LegalSeason` table;
   the structs already exist on iOS, so this is data plus a nation selector.
4. **Map parity** — tracks, OS grid overlay, offline packs, boundary overlays. Largest single item;
   MapKit needs `MKMapView`/`UIViewRepresentable` or MapLibre for iOS to match.
5. **Persist Emergency + Permissions** — move them into a real store (SQLite/Core Data) so the
   hardcoded demo values can be replaced with the same fields Android has.
6. **Calls** — real audio assets plus journal/recorder; today it plays UI sound effects.
7. **Settings leftovers** — wire the six preferences that exist in `UserPreferences` but have no UI.

## 6. Bug found on Android while auditing

**The Wind & Scent screen is unreachable on Android.** `Dest.Wind` is registered at
`HunTracKAppScaffold.kt` L391 and the feature module is wired with Hilt, but nothing ever calls
`navigate(Dest.Wind.route)`:

- not in `bottomTabs` (`listOf(Dest.Home, Dest.Map, Dest.Weather, Dest.More)`)
- not listed in `MoreScreen.kt`
- not linked from `HomeScreen.kt` or `WeatherScreen.kt`
- no deep-link or external entry point

291 LOC of screen plus 180 LOC of view model, `WindDirectionDial`, `WindSmokeCloud` and the
`ScentPlumeGeometry` maths it drives are all unreachable from the UI. Adding one `MoreItem` to
`MoreScreen.kt` and one `onOpenWind` callback restores it. Worth confirming in a running build
before shipping a fix, since this was determined from static source only.
