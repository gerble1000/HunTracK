package uk.hunttrack.domain.scent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScentPlumeGeometryTest {

    @Test
    fun steadyModerate_atLeast500Yards() {
        // 8 mph ≈ 12.9 km/h — mid steady band
        val plume = ScentPlumeGeometry.forWind(12.9)
        assertTrue("length was ${plume.lengthM}", plume.lengthM >= 457.2)
        assertTrue(plume.halfAngleDeg in 4.5..7.5)
    }

    @Test
    fun publishedCone_fiveDegHalfAngle_matches35ydAt200yd() {
        // 200 yd ≈ 182.88 m; 10° full / 5° half → width ≈ 35 yd ≈ 32 m
        val half = ScentPlumeGeometry.halfWidthAt(182.88, 5.0)
        val fullYards = half * 2 / 0.9144
        assertEquals(35.0, fullYards, 1.0)
    }

    @Test
    fun lightWind_under100Yards() {
        val plume = ScentPlumeGeometry.forWind(2.0) // ~1.2 mph
        assertTrue(plume.lengthM < 91.44)
        assertTrue(plume.scatter > 1.2)
    }

    @Test
    fun stiffWind_shorterThanSteadyModerate() {
        val steady = ScentPlumeGeometry.forWind(12.0)
        val stiff = ScentPlumeGeometry.forWind(30.0)
        assertTrue(steady.lengthM > stiff.lengthM)
        assertTrue(stiff.halfAngleDeg < steady.halfAngleDeg)
        assertTrue(stiff.dilution > steady.dilution)
    }

    @Test
    fun gustyWind_widensAndShortens() {
        val steady = ScentPlumeGeometry.forWind(12.0, 12.0)
        val gusty = ScentPlumeGeometry.forWind(12.0, 22.0)
        assertTrue(gusty.lengthM < steady.lengthM)
        assertTrue(gusty.halfAngleDeg > steady.halfAngleDeg * 1.3)
        assertTrue(gusty.scatter > steady.scatter)
    }

    @Test
    fun humidity_increasesLength() {
        val dry = ScentPlumeGeometry.forConditions(12.0, humidityPercent = 25)
        val humid = ScentPlumeGeometry.forConditions(12.0, humidityPercent = 90)
        assertTrue(humid.lengthM > dry.lengthM)
        assertTrue(humid.lengthM >= 457.2)
    }

    @Test
    fun afternoonThermal_shortensGroundPlume() {
        val morning = ScentPlumeGeometry.forConditions(
            12.0,
            humidityPercent = 60,
            temperatureC = 18.0,
            hourOfDay = 8,
        )
        val afternoon = ScentPlumeGeometry.forConditions(
            12.0,
            humidityPercent = 60,
            temperatureC = 18.0,
            hourOfDay = 14,
        )
        assertTrue(afternoon.lengthM < morning.lengthM)
        assertTrue(afternoon.dilution > morning.dilution)
    }

    @Test
    fun length_isCappedAt700m() {
        val plume = ScentPlumeGeometry.forConditions(12.0, humidityPercent = 100)
        assertTrue(plume.lengthM <= 700.0)
    }

    @Test
    fun approachStandoff_isFractionOfLength() {
        val length = ScentPlumeGeometry.plumeLengthM(12.0)
        val standoff = ScentPlumeGeometry.approachStandoffM(12.0)
        assertEquals(length * 0.85, standoff, 0.5)
    }

    @Test
    fun nullSpeed_usesDefaultSteadyBand() {
        val plume = ScentPlumeGeometry.forWind(null)
        assertTrue(plume.lengthM >= 457.2)
        assertTrue(abs(plume.lengthM - ScentPlumeGeometry.plumeLengthM(12.0)) < 0.01)
    }

    @Test
    fun forWind_delegatesToForConditions() {
        val a = ScentPlumeGeometry.forWind(10.0, 10.0)
        val b = ScentPlumeGeometry.forConditions(10.0, 10.0)
        assertEquals(a.lengthM, b.lengthM, 0.01)
        assertEquals(a.halfAngleDeg, b.halfAngleDeg, 0.01)
    }
}
