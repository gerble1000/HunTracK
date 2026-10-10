package uk.hunttrack.core.common

import java.util.Locale
import kotlin.math.roundToInt

fun Double.format1(): String = String.format(Locale.UK, "%.1f", this)
fun Float.format1(): String = String.format(Locale.UK, "%.1f", this)

fun Double.toMph(): Double = this * 0.621371
/** Kilometres → statute miles (same factor as km/h → mph). */
fun Double.toMiles(): Double = this * 0.621371
fun Double.toFahrenheit(): Double = this * 9.0 / 5.0 + 32.0
fun Double.toYards(): Double = this * 1.0936133
fun Double.toFeet(): Double = this * 3.2808399

fun formatVisibility(visibilityKm: Double, system: uk.hunttrack.domain.model.UnitSystem): String {
    return when (system) {
        uk.hunttrack.domain.model.UnitSystem.METRIC -> "${visibilityKm.format1()} km"
        uk.hunttrack.domain.model.UnitSystem.IMPERIAL -> "${visibilityKm.toMiles().format1()} mi"
    }
}

/** Primary field distance for UI (metric = metres, imperial = yards). */
fun formatFieldDistance(meters: Double, system: uk.hunttrack.domain.model.UnitSystem): String {
    return when (system) {
        uk.hunttrack.domain.model.UnitSystem.METRIC -> "${meters.toInt()} m"
        uk.hunttrack.domain.model.UnitSystem.IMPERIAL -> "${meters.toYards().toInt()} yd"
    }
}

fun formatFieldDistancePair(meters: Double, system: uk.hunttrack.domain.model.UnitSystem): String {
    val m = meters.toInt()
    val yd = meters.toYards().toInt()
    return when (system) {
        uk.hunttrack.domain.model.UnitSystem.METRIC -> "$m m  ·  $yd yd"
        uk.hunttrack.domain.model.UnitSystem.IMPERIAL -> "$yd yd  ·  $m m"
    }
}

/** Perimeter / fence-line length for boundary UI. */
fun formatBoundaryPerimeter(meters: Double): String {
    if (meters <= 0.0) return "—"
    return when {
        meters >= 1000.0 -> String.format(Locale.UK, "%.2f km (%.0f m)", meters / 1000.0, meters)
        else -> String.format(Locale.UK, "%.0f m", meters)
    }
}

/** Enclosed area — hectares, m², and acres for UK field use. */
fun formatBoundaryArea(sqMeters: Double): String {
    if (sqMeters <= 0.0) return "—"
    val ha = sqMeters / 10_000.0
    val acres = sqMeters / 4046.8564224
    return String.format(Locale.UK, "%.2f ha · %.0f m² · %.1f acres", ha, sqMeters, acres)
}

fun formatCoordinates(lat: Double, lng: Double): String {
    val latHem = if (lat >= 0) "N" else "S"
    val lngHem = if (lng >= 0) "E" else "W"
    return String.format(Locale.UK, "%.5f°%s  %.5f°%s", kotlin.math.abs(lat), latHem, kotlin.math.abs(lng), lngHem)
}

fun bearingToCardinal(degrees: Double): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((degrees % 360) + 360) % 360
    val index = ((normalized / 45.0).roundToInt()) % 8
    return dirs[index]
}

fun moonPhaseLabel(phase: Double): String = when {
    phase < 0.03 || phase > 0.97 -> "New Moon"
    phase < 0.22 -> "Waxing Crescent"
    phase < 0.28 -> "First Quarter"
    phase < 0.47 -> "Waxing Gibbous"
    phase < 0.53 -> "Full Moon"
    phase < 0.72 -> "Waning Gibbous"
    phase < 0.78 -> "Last Quarter"
    else -> "Waning Crescent"
}

fun weatherCodeLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2, 3 -> "Partly cloudy"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    61, 63, 65 -> "Rain"
    71, 73, 75 -> "Snow"
    80, 81, 82 -> "Showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "Mixed"
}
