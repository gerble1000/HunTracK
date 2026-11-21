package uk.hunttrack.domain.astronomy

import java.time.Instant

/**
 * Moon phase from the mean synodic month, anchored on the US Naval Observatory
 * new moon of 2026-11-09 07:02 UTC. 0 is new, 0.25 first quarter, 0.5 full,
 * 0.75 last quarter.
 */
object MoonPhase {
    val knownNewMoonEpochMs: Long = Instant.parse("2026-11-09T07:02:00Z").toEpochMilli()

    /** Mean synodic month, days. */
    const val SYNODIC_DAYS = 29.530588853

    private val synodicMillis = SYNODIC_DAYS * 86_400_000.0

    enum class Name {
        NEW,
        WAXING_CRESCENT,
        FIRST_QUARTER,
        WAXING_GIBBOUS,
        FULL,
        WANING_GIBBOUS,
        LAST_QUARTER,
        WANING_CRESCENT,
    }

    fun fraction(epochMs: Long): Double {
        val cycles = (epochMs - knownNewMoonEpochMs) / synodicMillis
        return ((cycles % 1.0) + 1.0) % 1.0
    }

    fun name(epochMs: Long): Name = nameOf(fraction(epochMs))

    fun nameOf(phase: Double): Name {
        val p = ((phase % 1.0) + 1.0) % 1.0
        return when {
            p < 0.0625 || p >= 0.9375 -> Name.NEW
            p < 0.1875 -> Name.WAXING_CRESCENT
            p < 0.3125 -> Name.FIRST_QUARTER
            p < 0.4375 -> Name.WAXING_GIBBOUS
            p < 0.5625 -> Name.FULL
            p < 0.6875 -> Name.WANING_GIBBOUS
            p < 0.8125 -> Name.LAST_QUARTER
            else -> Name.WANING_CRESCENT
        }
    }

    /** Lit fraction, 0 at new and 1 at full. */
    fun illumination(epochMs: Long): Double {
        val p = fraction(epochMs)
        return (1.0 - kotlin.math.cos(2.0 * Math.PI * p)) / 2.0
    }
}
