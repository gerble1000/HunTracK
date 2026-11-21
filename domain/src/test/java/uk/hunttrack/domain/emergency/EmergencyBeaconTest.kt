package uk.hunttrack.domain.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyBeaconTest {

    @Test
    fun flashRateIsAboutOneHertzAndUnderThreePerSecond() {
        assertEquals(500L, EmergencyBeacon.PHASE_MS)
        assertEquals(2.0, EmergencyBeacon.flashesPerSecond(), 0.001)
        assertTrue(EmergencyBeacon.withinEpilepsyLimit())
        assertTrue(EmergencyBeacon.flashesPerSecond() <= EmergencyBeacon.MAX_FLASHES_PER_SECOND)
    }

    @Test
    fun holdToStopTakesThreeSeconds() {
        assertEquals(3_000L, EmergencyBeacon.HOLD_TO_STOP_MS)
    }
}
