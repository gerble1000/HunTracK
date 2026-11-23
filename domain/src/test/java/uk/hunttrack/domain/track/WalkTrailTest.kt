package uk.hunttrack.domain.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import uk.hunttrack.domain.model.TrackPoint

class WalkTrailTest {

    @Test
    fun firstFixStartsTheTrail() {
        val trail = WalkTrail.append(emptyList(), 55.95, -3.19, null, null, 1_000L)
        assertEquals(1, trail.size)
        assertEquals(55.95, trail.single().latitude, 0.00001)
        assertEquals(1_000L, trail.single().recordedAtEpochMs)
    }

    @Test
    fun aFixCloserThanEightMetresIsNotAccepted() {
        val start = WalkTrail.append(emptyList(), 55.95, -3.19, null, null, 1_000L)
        val stayed = WalkTrail.append(start, 55.95001, -3.19, null, null, 2_000L)
        assertSame(start, stayed)
        assertEquals(1, stayed.size)
    }

    @Test
    fun aFixFurtherThanEightMetresIsAppendedAndEarlierPointsStay() {
        val start = point(55.95, -3.19, 1_000L)
        val trail = WalkTrail.append(listOf(start), 55.9502, -3.19, 12.0, 1.5f, 9_000L)
        assertEquals(2, trail.size)
        assertEquals(start, trail.first())
        assertEquals(55.9502, trail.last().latitude, 0.00001)
        assertEquals(9_000L, trail.last().recordedAtEpochMs)
    }

    @Test
    fun theOldestPointDropsOnlyAfterTheCap() {
        val points = (0 until WalkTrail.MAX_POINTS).map { index ->
            point(55.0 + index * 0.001, -3.0, index.toLong())
        }
        val trail = WalkTrail.append(points, 55.0 + WalkTrail.MAX_POINTS * 0.001, -3.0, null, null, 50_000L)
        assertEquals(WalkTrail.MAX_POINTS, trail.size)
        assertEquals(points[1], trail.first())
        assertEquals(50_000L, trail.last().recordedAtEpochMs)
    }

    private fun point(lat: Double, lon: Double, time: Long) = TrackPoint(
        routeId = 0,
        latitude = lat,
        longitude = lon,
        recordedAtEpochMs = time,
    )
}
