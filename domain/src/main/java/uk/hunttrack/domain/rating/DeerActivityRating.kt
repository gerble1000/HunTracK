package uk.hunttrack.domain.rating

import uk.hunttrack.domain.model.ActivityLevel
import uk.hunttrack.domain.model.ActivityRating
import uk.hunttrack.domain.model.AstronomyInfo
import uk.hunttrack.domain.model.WeatherSnapshot
import java.util.Calendar
import java.util.TimeZone

object DeerActivityRating {

    fun rate(weather: WeatherSnapshot?, astronomy: AstronomyInfo?, nowEpochMs: Long): ActivityRating {
        if (weather == null) {
            return ActivityRating(
                level = ActivityLevel.MODERATE,
                title = "Deer Activity",
                reasons = listOf("Waiting for weather data"),
            )
        }

        var score = 0
        val reasons = mutableListOf<String>()

        if (weather.windSpeedKmh < 12) {
            score += 2
            reasons += "Low wind"
        } else if (weather.windSpeedKmh < 25) {
            score += 1
            reasons += "Moderate wind"
        } else {
            reasons += "Strong wind"
        }

        if (weather.temperatureC in 2.0..14.0) {
            score += 2
            reasons += "Cool temperature"
        } else if (weather.temperatureC in 0.0..18.0) {
            score += 1
        }

        val hour = Calendar.getInstance(TimeZone.getTimeZone("Europe/London")).apply {
            timeInMillis = nowEpochMs
        }.get(Calendar.HOUR_OF_DAY)

        val inEveningNight = isEveningToNight(astronomy, nowEpochMs, hour)
        val atSunrise = isSunrisePeak(astronomy, nowEpochMs, hour)
        val atSunset = isSunsetPeak(astronomy, nowEpochMs)
        when {
            atSunrise -> {
                score += 3
                reasons += "Sunrise peak activity"
            }
            atSunset -> {
                score += 3
                reasons += "Sunset peak activity"
            }
            inEveningNight -> {
                score += 3
                reasons += "Evening–night high activity"
            }
            hour in 5..8 -> {
                score += 2
                reasons += "Early morning"
            }
        }

        val illumination = astronomy?.moonIllumination ?: 0.5
        if (illumination in 0.2..0.7) {
            score += 1
            reasons += "Good moon phase"
        }

        var level = when {
            score >= 5 -> ActivityLevel.HIGH
            score >= 3 -> ActivityLevel.MODERATE
            else -> ActivityLevel.LOW
        }
        // Evening through night is a prime deer window; keep HIGH unless wind is strong.
        if ((inEveningNight || atSunrise || atSunset) && weather.windSpeedKmh < 25) {
            level = ActivityLevel.HIGH
        }

        return ActivityRating(
            level = level,
            title = "Deer Activity",
            reasons = reasons.ifEmpty { listOf("Typical conditions") },
        )
    }

    /**
     * Roughly from ~90 min before sunset through the night until ~45 min before sunrise.
     * Clock fallback (UK): 17:00–04:59.
     */
    private fun isEveningToNight(
        astronomy: AstronomyInfo?,
        nowEpochMs: Long,
        hour: Int,
    ): Boolean {
        if (astronomy != null) {
            val eveningStart = astronomy.sunsetEpochMs - 90L * 60L * 1000L
            val nightEnd = astronomy.sunriseEpochMs - 45L * 60L * 1000L
            return nowEpochMs >= eveningStart || nowEpochMs < nightEnd
        }
        return hour >= 17 || hour <= 4
    }

    /** ~45 min before sunrise through ~90 min after — UK deer are highly active at dawn. */
    private fun isSunrisePeak(
        astronomy: AstronomyInfo?,
        nowEpochMs: Long,
        hour: Int,
    ): Boolean {
        if (astronomy != null) {
            val start = astronomy.sunriseEpochMs - 45L * 60L * 1000L
            val end = astronomy.sunriseEpochMs + 90L * 60L * 1000L
            return nowEpochMs in start..end
        }
        return hour in 5..8
    }

    /** Golden hour leading into sunset — deer peak before conditions switch to fox & rabbit. */
    private fun isSunsetPeak(astronomy: AstronomyInfo?, nowEpochMs: Long): Boolean {
        if (astronomy != null) {
            return nowEpochMs >= astronomy.goldenHourStartEpochMs &&
                nowEpochMs < astronomy.goldenHourEndEpochMs
        }
        return false
    }
}
