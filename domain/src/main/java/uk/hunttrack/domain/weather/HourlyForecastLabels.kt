package uk.hunttrack.domain.weather

import uk.hunttrack.domain.model.HourlyForecast
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Labels for the "Next 24 hours" row.
 *
 * Open-Meteo hours are requested in Europe/London and begin at midnight, so a
 * clock format such as 00:00 reads as the start of the day. These labels are
 * offsets from the current London hour instead: the matching hour is "Now",
 * and later hours are "+1", "+2", and so on.
 */
object HourlyForecastLabels {
    val london: ZoneId = ZoneId.of("Europe/London")

    fun hourStartEpochMs(epochMs: Long, zone: ZoneId = london): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli()

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
        val start = hourStartEpochMs(nowEpochMs, zone)
        return hours.asSequence().filter { it.epochMs >= start }.take(limit).toList()
    }
}
