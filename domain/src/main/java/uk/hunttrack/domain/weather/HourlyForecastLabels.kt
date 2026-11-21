package uk.hunttrack.domain.weather

import uk.hunttrack.domain.model.HourlyForecast
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Labels for the "Next 24 hours" row.
 *
 * Open-Meteo is asked for Europe/London and returns the whole local day from
 * midnight. The row keeps hours that start after the current London hour, so
 * the first chip is the next hour and reads "+1", then "+2", "+3".
 */
object HourlyForecastLabels {
    val london: ZoneId = ZoneId.of("Europe/London")

    fun hourStartEpochMs(epochMs: Long, zone: ZoneId = london): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli()

    /** Start of the clock hour after the one that contains [nowEpochMs]. */
    fun nextHourEpochMs(nowEpochMs: Long, zone: ZoneId = london): Long =
        Instant.ofEpochMilli(hourStartEpochMs(nowEpochMs, zone)).plus(1, ChronoUnit.HOURS).toEpochMilli()

    fun offsetLabel(slotEpochMs: Long, nowEpochMs: Long, zone: ZoneId = london): String {
        val ahead = ChronoUnit.HOURS.between(
            Instant.ofEpochMilli(hourStartEpochMs(nowEpochMs, zone)),
            Instant.ofEpochMilli(hourStartEpochMs(slotEpochMs, zone)),
        )
        return when {
            ahead == 0L -> "Now"
            ahead > 0L -> "+$ahead"
            else -> "$ahead"
        }
    }

    fun upcoming(hours: List<HourlyForecast>, nowEpochMs: Long, limit: Int = 24, zone: ZoneId = london): List<HourlyForecast> {
        val start = nextHourEpochMs(nowEpochMs, zone)
        return hours.asSequence().filter { it.epochMs >= start }.take(limit).toList()
    }
}
