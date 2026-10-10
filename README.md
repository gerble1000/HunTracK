# HunTracK

Premium Android hunting, wildlife & field navigation companion for the United Kingdom.

## Phase 1 — Field Core MVP

- Dark woodland Compose UI
- Home dashboard (weather, astronomy, GPS, deer activity rating)
- MapLibre maps with layers, markers, track recording, offline packs registry
- Weather (Open-Meteo), compass, wind & scent tool
- Local guest mode (no cloud accounts yet)

## Phase 2 — Domain data & safety

- Offline UK species catalog + ID guides
- Legal seasons by nation (indicative; verify before use)
- Animal calls library (demo tones), recorder, call journal, offline pack downloads
- Permissions wallet + GPS boundary areas (hunting / no-go / safe)
- Emergency public profile with PIN / biometric unlock to edit

## Requirements

- Android Studio Ladybug+ / JDK 17
- Android SDK 35
- minSdk 29 (Android 10)

## Build

```bash
./gradlew :app:assembleDebug
```

Open the project in Android Studio and run on a device or emulator with Google Play Services (for fused location).
