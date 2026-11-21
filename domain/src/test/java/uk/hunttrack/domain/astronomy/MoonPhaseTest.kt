package uk.hunttrack.domain.astronomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Dates checked against the US Naval Observatory primary phases for 2026:
 * new moon 2026-11-09 07:02 UTC, full moon 2026-11-24 14:53 UTC.
 */
class MoonPhaseTest {
    @Test
    fun newMoonOn9November2026() {
        val epoch = Instant.parse("2026-11-09T07:02:00Z").toEpochMilli()
        assertEquals(0.0, MoonPhase.fraction(epoch), 1.0e-9)
        assertEquals(MoonPhase.Name.NEW, MoonPhase.name(epoch))
        assertTrue(MoonPhase.illumination(epoch) < 0.01)
    }

    @Test
    fun fullMoonOn24November2026() {
        val epoch = Instant.parse("2026-11-24T14:53:00Z").toEpochMilli()
        val phase = MoonPhase.fraction(epoch)
        assertEquals(MoonPhase.Name.FULL, MoonPhase.name(epoch))
        assertTrue(phase in 0.47..0.53)
        assertTrue(MoonPhase.illumination(epoch) > 0.95)
    }

    @Test
    fun threeDaysAfterNewMoonIsAWaxingCrescent() {
        val epoch = Instant.parse("2026-11-12T07:02:00Z").toEpochMilli()
        assertEquals(MoonPhase.Name.WAXING_CRESCENT, MoonPhase.name(epoch))
    }
}
