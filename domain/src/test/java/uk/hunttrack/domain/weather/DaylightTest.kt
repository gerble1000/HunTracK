package uk.hunttrack.domain.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hunttrack.domain.astronomy.AstronomyCalculator
import java.time.Instant

class DaylightTest {
    private val latitude = 52.4862
    private val longitude = -1.8904

    @Test
    fun apiFlagWinsOverTheSunCalculator() {
        val night = AstronomyCalculator.calculate(latitude, longitude, 0L).sunsetEpochMs + 3 * 60 * 60 * 1000L
        assertTrue(Daylight.isDay(apiIsDay = true, latitude, longitude, night))
        val noon = (AstronomyCalculator.calculate(latitude, longitude, night).sunriseEpochMs +
            AstronomyCalculator.calculate(latitude, longitude, night).sunsetEpochMs) / 2
        assertFalse(Daylight.isDay(apiIsDay = false, latitude, longitude, noon))
    }

    @Test
    fun missingFlagUsesSunriseAndSunset() {
        val sample = 1_763_750_400_000L // 2026-11-21 16:00 UTC, after a mid-November Birmingham sunset
        val sun = AstronomyCalculator.calculate(latitude, longitude, sample)
        val day = (sun.sunriseEpochMs + sun.sunsetEpochMs) / 2
        assertTrue(day in sun.sunriseEpochMs..sun.sunsetEpochMs)
        assertTrue(Daylight.isDay(apiIsDay = null, latitude, longitude, day))
        val night = sun.sunsetEpochMs + 2 * 60 * 60 * 1000L
        assertFalse(Daylight.isDay(apiIsDay = null, latitude, longitude, night))
    }

    @Test
    fun eveningSkyIsEitherALitMoonOrStars() {
        val epoch = Instant.parse("2026-11-21T20:00:00Z").toEpochMilli()
        val sky = AstronomyCalculator.nightSky(latitude, longitude, epoch)
        assertTrue(sky.phase in 0.0..1.0)
        assertTrue(sky.illumination in 0.0..1.0)
        assertTrue(sky.showMoon == (sky.moonUp && sky.illumination >= 0.04))
    }
}