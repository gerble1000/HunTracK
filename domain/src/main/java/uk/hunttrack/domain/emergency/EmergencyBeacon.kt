package uk.hunttrack.domain.emergency

/**
 * Full-screen distress beacon. Each colour is held for [PHASE_MS] (about 2 seconds),
 * so the screen changes colour once every 2 seconds. That is well under the
 * three-flashes-a-second epilepsy limit.
 */
object EmergencyBeacon {
    const val PHASE_MS = 2_000L
    const val HOLD_TO_STOP_MS = 3_000L
    const val MAX_FLASHES_PER_SECOND = 3.0

    /** Colour changes per second. */
    fun flashesPerSecond(): Double = 1_000.0 / PHASE_MS

    fun withinEpilepsyLimit(): Boolean = flashesPerSecond() <= MAX_FLASHES_PER_SECOND
}
