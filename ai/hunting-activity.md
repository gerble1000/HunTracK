# Hunting activity windows (HunTracK)

Reference for AI assistants and developers working on the home dashboard **Hunting conditions** card.

## Species activity (UK field behaviour)

| Species | Peak activity |
|---------|----------------|
| **Deer** | Highly active at **sunrise** and **sunset** (including the golden hour before sunset). |
| **Rabbit** | Active from **sunset through to early morning** (night and dawn). |
| **Fox** | Same pattern as rabbit — **sunset through early morning**. |

## How the app switches conditions

Logic lives in `domain/.../rating/HuntingConditionsRating.kt`.

1. **Deer Activity** — shown from sunrise until **golden hour ends** (local sunset).
   - Golden hour starts **1 hour before sunset** (`goldenHourStartEpochMs`).
   - Deer rating still applies during that last hour; deer are at sunset peak.
2. **Rabbit & Fox** — shown **after sunset** until **sunrise**.
   - The night window crosses midnight, so the check must be: `now >= sunset OR now < sunrise` (not AND).

Supporting calculators:

- `DeerActivityRating.kt` — weather + sunrise/sunset peaks for deer.
- `RabbitFoxActivityRating.kt` — night quarry window after dusk.
- `AstronomyCalculator.kt` — sunrise, sunset, golden hour from GPS lat/lng.
- `HomeViewModel.kt` — recalculates every minute via `clockTick`.

## UI

Home → **Hunting conditions** panel shows the active rating title (`Deer Activity` or `Rabbit & Fox`), level (HIGH/MODERATE/LOW), and reason bullets.

## When changing this behaviour

- Do not use `now >= sunset && now < sunrise` for the night window — sunrise on the same calendar day is always before sunset, so that condition is never true after dusk.
- Keep golden-hour deer window ending exactly at sunset so fox/rabbit take over when the sun is down.
