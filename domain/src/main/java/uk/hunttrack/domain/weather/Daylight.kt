package uk.hunttrack.domain.weather

import java.time.Instant
import java.time.ZoneId
import uk.hunttrack.domain.astronomy.AstronomyCalculator

/**
 * Whether the sun is up at [epochMs] for a forecast hour.
 *
 * Open-Meteo hourly `is_day` is used when the response included it (1 = day, 0 = night).
 * Older cached hours omit the flag; those fall back to the same sunrise/sunset calculator
 * the home screen uses for legal light.
 */
object Daylight {
    /** Same zone the hourly strip uses to label "+1", "+2", … */
    val forecastZone: ZoneId = ZoneId.of("Europe/London")

    fun isDay(
        apiIsDay: Boolean?,
        latitude: Double,
        longitude: Double,
        epochMs: Long,
    ): Boolean {
        apiIsDay?.let { return it }
        val sun = AstronomyCalculator.calculate(latitude, longitude, epochMs)
        return epochMs in sun.sunriseEpochMs..sun.sunsetEpochMs
    }

    /**
     * Daylight for one hourly chip, from that calendar date's sunrise to sunset
     * at [latitude]/[longitude].
     *
     * Sampling the hour itself can land the sun calculator on the previous solar
     * day, so a morning hour does not overlap yesterday's sunset and stays a moon.
     * Noon on the hour's London date forces that date's rise and set.
     * The forecast is_day flag is not used here: a stuck night flag was painting
     * the moon on daytime hours.
     */
    fun isSunlitHour(latitude: Double, longitude: Double, epochMs: Long): Boolean {
        val date = Instant.ofEpochMilli(epochMs).atZone(forecastZone).toLocalDate()
        val noon = date.atTime(12, 0).atZone(forecastZone).toInstant().toEpochMilli()
        val sun = AstronomyCalculator.calculate(latitude, longitude, noon)
        val hourEnd = epochMs + 60L * 60L * 1000L
        return epochMs < sun.sunsetEpochMs && hourEnd > sun.sunriseEpochMs
    }
}
