package uk.hunttrack.domain.emergency

/**
 * Full-screen help beacon shown after the emergency text is handed to Messages.
 *
 * One red/white cycle per second (each colour held for [PHASE_MS]). That is two
 * colour changes a second, under the three-flashes-a-second epilepsy limit.
 */
object EmergencyBeacon {
    const val PHASE_MS = 500L
    const val HOLD_TO_STOP_MS = 3_000L
    const val MAX_FLASHES_PER_SECOND = 3.0

    /** Colour changes per second. */
    fun flashesPerSecond(): Double = 1_000.0 / PHASE_MS

    fun withinEpilepsyLimit(): Boolean = flashesPerSecond() <= MAX_FLASHES_PER_SECOND
}
