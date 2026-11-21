package uk.hunttrack.domain.scent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachTrailTest {

    // Straight northbound route, about 222 m.
    private val startLat = 52.0
    private val startLng = -1.8
    private val endLat = 52.002
    private val endLng = -1.8

    @Test
    fun printsBehindTheHunterFade_printsAheadStay() {
        val userLat = 52.001
        assertTrue(
            ApproachTrail.walkedPast(
                printLat = 52.0002,
                printLng = startLng,
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                userLat = userLat,
                userLng = startLng,
            ),
        )
        assertFalse(
            ApproachTrail.walkedPast(
                printLat = 52.0016,
                printLng = startLng,
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                userLat = userLat,
                userLng = startLng,
            ),
        )
    }

    @Test
    fun hunterStillAtTheStart_keepsEveryPrint() {
        assertFalse(
            ApproachTrail.walkedPast(
                printLat = 52.0002,
                printLng = startLng,
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                userLat = startLat,
                userLng = startLng,
            ),
        )
    }

    @Test
    fun hunterOffTheRoute_doesNotFadeTheTrail() {
        assertFalse(
            ApproachTrail.walkedPast(
                printLat = 52.0002,
                printLng = startLng,
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                userLat = 52.001,
                userLng = -1.803,
            ),
        )
    }

    @Test
    fun noGps_keepsEveryPrint() {
        assertFalse(
            ApproachTrail.walkedPast(
                printLat = 52.0002,
                printLng = startLng,
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                userLat = null,
                userLng = null,
            ),
        )
    }
}
