package uk.hunttrack.domain.rating

import uk.hunttrack.domain.model.ActivityLevel
import uk.hunttrack.domain.model.ActivityRating
import uk.hunttrack.domain.model.AstronomyInfo
import uk.hunttrack.domain.model.WeatherSnapshot

object RabbitFoxActivityRating {

    fun rate(weather: WeatherSnapshot?, astronomy: AstronomyInfo?, nowEpochMs: Long): ActivityRating {
        if (weather == null) {
            return ActivityRating(
                level = ActivityLevel.MODERATE,
                title = "Rabbit & Fox",
                reasons = listOf("Prime night window", "Waiting for weather data"),
            )
        }

        var score = 3
        val reasons = mutableListOf("After sunset — prime fox & rabbit window")

        if (weather.windSpeedKmh < 12) {
            score += 2
            reasons += "Low wind"
        } else if (weather.windSpeedKmh < 25) {
            score += 1
            reasons += "Moderate wind"
        } else {
            score -= 1
            reasons += "Strong wind"
        }

        if (weather.temperatureC in 0.0..12.0) {
            score += 2
            reasons += "Cool night temperature"
        } else if (weather.temperatureC in -2.0..16.0) {
            score += 1
        }

        val illumination = astronomy?.moonIllumination ?: 0.5
        if (illumination in 0.15..0.65) {
            score += 1
            reasons += "Good moonlight for spotting"
        }

        var level = when {
            score >= 5 -> ActivityLevel.HIGH
            score >= 3 -> ActivityLevel.MODERATE
            else -> ActivityLevel.LOW
        }
        if (weather.windSpeedKmh < 25) {
            level = ActivityLevel.HIGH
        }

        return ActivityRating(
            level = level,
            title = "Rabbit & Fox",
            reasons = reasons,
        )
    }
}
