# 1.1.32 (Android versionCode 33, iOS build 46)

Greg approved the two field fixes ("1A, 2B") and Jerry authorised the release.
Android and iOS share the marketing version **1.1.32**.

## What's new

- **Outdoor / Topo no longer blanks the map.** Esri World Hillshade was returning an
  HTTP 200 JPEG that says "Map data not yet available" from zoom 17 up, including at
  Wasdale. The Outdoor layer now stops at zoom 16 and overscales the last real terrain
  tiles, so the OpenFreeMap basemap stays visible when you pinch in. Streets and
  Satellite were already fine. iOS Outdoor uses Apple Maps and did not have this overlay.
- **iOS Focus on map matches Android.** From the Permissions wallet or a boundary area,
  Focus on map switches to the Map tab and centres the linked boundary. Previously the
  camera request was dropped while the map was off-screen, then the Map tab snapped
  back to GPS.
