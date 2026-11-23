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

    /**
     * Daylight for one hourly chip. The chip counts as day when that clock hour
     * overlaps civil dawn through civil dusk at [latitude]/[longitude].
     * The forecast is_day flag is ignored: a flag that stayed night was drawing
     * a moon on every hour, including the hour dawn falls in.
     */
    fun isCivilDay(latitude: Double, longitude: Double, epochMs: Long): Boolean {
        val hourEnd = epochMs + 60L * 60L * 1000L
        if (hourOverlapsCivilDay(latitude, longitude, epochMs, hourEnd)) return true
        return hourOverlapsCivilDay(latitude, longitude, hourEnd - 1, hourEnd)
    }

    private fun hourOverlapsCivilDay(
        latitude: Double,
        longitude: Double,
        sampleMs: Long,
        hourEnd: Long,
    ): Boolean {
        val sun = AstronomyCalculator.calculate(latitude, longitude, sampleMs)
        val hourStart = hourEnd - 60L * 60L * 1000L
        return hourStart < sun.civilTwilightEndEpochMs && hourEnd > sun.civilTwilightBeginEpochMs
    }
}
