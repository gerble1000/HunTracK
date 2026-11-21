package uk.hunttrack.domain.weather

import uk.hunttrack.domain.astronomy.AstronomyCalculator

/**
 * Whether the sun is up at [epochMs] for a forecast hour.
 *
 * Open-Meteo hourly `is_day` is used when the response included it (1 = day, 0 = night).
 * Older cached hours omit the flag; those fall back to the same sunrise/sunset calculator
 * the home screen uses for legal light.
 */
object Daylight {
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
}
