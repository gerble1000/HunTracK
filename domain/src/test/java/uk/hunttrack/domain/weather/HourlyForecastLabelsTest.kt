package uk.hunttrack.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.hunttrack.domain.model.HourlyForecast
import java.time.Instant

class HourlyForecastLabelsTest {
    private val now = Instant.parse("2026-11-21T15:14:00Z").toEpochMilli()

    @Test
    fun laterHoursArePlainOffsets() {
        val plusOne = Instant.parse("2026-11-21T16:00:00Z").toEpochMilli()
        val plusThree = Instant.parse("2026-11-21T18:00:00Z").toEpochMilli()
        assertEquals("+1", HourlyForecastLabels.offsetLabel(plusOne, now))
        assertEquals("+3", HourlyForecastLabels.offsetLabel(plusThree, now))
    }

    @Test
    fun upcomingStartsAtTheNextLondonHourNotMidnight() {
        val start = Instant.parse("2026-11-21T00:00:00Z")
        val hours = (0 until 48).map { index ->
            hour(start.plusSeconds(index * 3600L).toEpochMilli())
        }
        val upcoming = HourlyForecastLabels.upcoming(hours, now)
        assertEquals(24, upcoming.size)
        assertEquals(Instant.parse("2026-11-21T16:00:00Z").toEpochMilli(), upcoming.first().epochMs)
        assertEquals("+1", HourlyForecastLabels.offsetLabel(upcoming.first().epochMs, now))
        assertEquals("+2", HourlyForecastLabels.offsetLabel(upcoming[1].epochMs, now))
        assertEquals("+24", HourlyForecastLabels.offsetLabel(upcoming.last().epochMs, now))
    }

    private fun hour(epochMs: Long) = HourlyForecast(
        epochMs = epochMs,
        temperatureC = 0.0,
        windSpeedKmh = 0.0,
        windDirectionDeg = 0.0,
        rainProbabilityPercent = 0,
        weatherCode = 0,
    )
}
