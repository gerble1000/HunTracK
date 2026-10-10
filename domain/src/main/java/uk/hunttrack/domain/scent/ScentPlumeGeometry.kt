package uk.hunttrack.domain.scent

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.tan

/**
 * Geographic scent-cone model for hunting wind visualisation.
 *
 * Field bands (mph → km/h):
 * - Light / variable (&lt;2 mph): scent pools near source, &lt;100 yd
 * - Steady moderate (5–10 mph): long linear cone, ≥500 yd
 * - High / stiff (&gt;15 mph): wind burn — narrow, diluted past ~300–500 yd
 * - Gusty: plume breaks into pockets; directional range cut sharply
 *
 * Humidity traps scent longer; afternoon thermals lift scent off the ground;
 * evening cooling pools scent downhill / near ground.
 */
object ScentPlumeGeometry {

    data class Plume(
        /** Downwind detection length in metres. */
        val lengthM: Double,
        /** Cone half-angle in degrees (from centreline to edge). */
        val halfAngleDeg: Double,
        /** 0–1 how quickly opacity falls along the cone (higher = wind burn / lift). */
        val dilution: Double = 0.25,
        /** Extra lateral scatter factor for calm meander / gusty eddies. */
        val scatter: Double = 1.0,
    ) {
        /** Crosswind half-width at [distanceM] along the centreline. */
        fun halfWidthAt(distanceM: Double): Double =
            halfWidthAt(distanceM, halfAngleDeg)
    }

    /**
     * @param speedKmh mean wind speed (km/h)
     * @param gustKmh optional gust speed; when gust − speed ≥ 8, plume widens and shortens
     * @param humidityPercent optional relative humidity (0–100)
     * @param temperatureC optional air temperature for thermal lift / pooling
     * @param hourOfDay optional local hour 0–23 (Europe/London if null and temp given)
     */
    fun forConditions(
        speedKmh: Double?,
        gustKmh: Double? = null,
        humidityPercent: Int? = null,
        temperatureC: Double? = null,
        hourOfDay: Int? = null,
    ): Plume {
        val speed = (speedKmh ?: DEFAULT_SPEED_KMH).coerceIn(0.0, 80.0)
        val gust = (gustKmh ?: speed).coerceAtLeast(speed)
        val gusty = gust - speed >= GUST_DELTA_KMH

        // Base length from wind band (metres). Steady moderate floor = 500 yd.
        var lengthM = when {
            speed < LIGHT_MAX_KMH -> lerp(speed, 0.0, LIGHT_MAX_KMH, 55.0, 90.0)
            speed < STEADY_MIN_KMH -> lerp(speed, LIGHT_MAX_KMH, STEADY_MIN_KMH, 90.0, STEADY_MIN_LENGTH_M)
            speed <= STEADY_MAX_KMH -> lerp(speed, STEADY_MIN_KMH, STEADY_MAX_KMH, STEADY_MIN_LENGTH_M, 620.0)
            speed < STIFF_MIN_KMH -> lerp(speed, STEADY_MAX_KMH, STIFF_MIN_KMH, 620.0, 450.0)
            else -> lerp(speed, STIFF_MIN_KMH, 60.0, 450.0, 280.0)
        }

        var baseHalfAngle = when {
            speed < LIGHT_MAX_KMH -> lerp(speed, 0.0, LIGHT_MAX_KMH, 11.0, 9.0)
            speed < STEADY_MIN_KMH -> lerp(speed, LIGHT_MAX_KMH, STEADY_MIN_KMH, 9.0, 6.0)
            speed <= STEADY_MAX_KMH -> lerp(speed, STEADY_MIN_KMH, STEADY_MAX_KMH, 6.0, 5.0)
            speed < STIFF_MIN_KMH -> lerp(speed, STEADY_MAX_KMH, STIFF_MIN_KMH, 5.0, 4.0)
            else -> lerp(speed, STIFF_MIN_KMH, 60.0, 4.0, 2.8)
        }

        var dilution = when {
            speed < LIGHT_MAX_KMH -> 0.15
            speed <= STEADY_MAX_KMH -> 0.22
            else -> lerp(speed, STIFF_MIN_KMH, 60.0, 0.38, 0.55)
        }

        var scatter = when {
            speed < LIGHT_MAX_KMH -> 1.55
            speed <= STEADY_MAX_KMH -> 1.0
            else -> 0.75
        }

        if (gusty) {
            lengthM *= GUST_LENGTH_FACTOR
            baseHalfAngle *= GUST_ANGLE_FACTOR
            scatter *= 1.7
            dilution = (dilution + 0.12).coerceAtMost(0.7)
        }

        // Humidity: high moisture suspends scent longer.
        val humidityFactor = when (val h = humidityPercent) {
            null -> 1.0
            else -> lerp(h.coerceIn(0, 100).toDouble(), 20.0, 95.0, 0.88, 1.22)
        }
        lengthM *= humidityFactor

        // Thermals: afternoon warm air lifts scent; evening cool pools near ground.
        val hour = hourOfDay ?: if (temperatureC != null) localHourLondon() else null
        val temp = temperatureC
        if (hour != null && temp != null) {
            val afternoonLift = hour in 12..17 && temp >= 12.0
            val eveningPool = hour in 17..22 || (hour in 5..9 && temp <= 10.0)
            when {
                afternoonLift -> {
                    lengthM *= 0.82
                    dilution = (dilution + 0.1).coerceAtMost(0.7)
                }
                eveningPool -> {
                    baseHalfAngle *= 1.15
                    scatter *= 1.12
                }
            }
        }

        // Steady band floor: never below 500 yd after humidity (gusty/stiff/calm exempt).
        val inSteadyBand = !gusty && speed >= STEADY_MIN_KMH && speed <= STEADY_MAX_KMH
        if (inSteadyBand) {
            lengthM = lengthM.coerceAtLeast(STEADY_MIN_LENGTH_M)
        }

        lengthM = lengthM.coerceIn(MIN_LENGTH_M, MAX_LENGTH_M)
        val halfAngleDeg = baseHalfAngle.coerceIn(MIN_HALF_ANGLE_DEG, MAX_HALF_ANGLE_DEG)

        return Plume(
            lengthM = lengthM,
            halfAngleDeg = halfAngleDeg,
            dilution = dilution.coerceIn(0.1, 0.7),
            scatter = scatter.coerceIn(0.5, 2.5),
        )
    }

    /** Convenience when only wind speed / gust are known. */
    fun forWind(speedKmh: Double?, gustKmh: Double? = null): Plume =
        forConditions(speedKmh, gustKmh)

    fun plumeLengthM(
        speedKmh: Double?,
        gustKmh: Double? = null,
        humidityPercent: Int? = null,
        temperatureC: Double? = null,
        hourOfDay: Int? = null,
    ): Double = forConditions(speedKmh, gustKmh, humidityPercent, temperatureC, hourOfDay).lengthM

    fun halfAngleDeg(
        speedKmh: Double?,
        gustKmh: Double? = null,
        humidityPercent: Int? = null,
        temperatureC: Double? = null,
        hourOfDay: Int? = null,
    ): Double = forConditions(speedKmh, gustKmh, humidityPercent, temperatureC, hourOfDay).halfAngleDeg

    fun halfWidthAt(distanceM: Double, halfAngleDeg: Double): Double {
        if (distanceM <= 0.0) return 0.0
        return distanceM * tan(Math.toRadians(halfAngleDeg))
    }

    /** Into-wind approach standoff ≈ 85% of detection length for current conditions. */
    fun approachStandoffM(
        speedKmh: Double?,
        gustKmh: Double? = null,
        humidityPercent: Int? = null,
        temperatureC: Double? = null,
        hourOfDay: Int? = null,
    ): Double =
        (plumeLengthM(speedKmh, gustKmh, humidityPercent, temperatureC, hourOfDay) * APPROACH_FRACTION)
            .coerceIn(80.0, 560.0)

    private fun localHourLondon(): Int =
        Calendar.getInstance(TimeZone.getTimeZone("Europe/London")).get(Calendar.HOUR_OF_DAY)

    private fun lerp(x: Double, x0: Double, x1: Double, y0: Double, y1: Double): Double {
        if (x1 <= x0) return y0
        val t = ((x - x0) / (x1 - x0)).coerceIn(0.0, 1.0)
        return y0 + t * (y1 - y0)
    }

    private const val DEFAULT_SPEED_KMH = 12.0 // ~7.5 mph — mid steady band
    private const val GUST_DELTA_KMH = 8.0
    private const val GUST_ANGLE_FACTOR = 1.55
    private const val GUST_LENGTH_FACTOR = 0.62

    /** &lt;2 mph */
    private const val LIGHT_MAX_KMH = 3.2
    /** 5 mph */
    private const val STEADY_MIN_KMH = 8.0
    /** 10 mph */
    private const val STEADY_MAX_KMH = 16.1
    /** 15 mph */
    private const val STIFF_MIN_KMH = 24.1

    /** 500 yards */
    private const val STEADY_MIN_LENGTH_M = 457.2

    private const val MIN_LENGTH_M = 50.0
    private const val MAX_LENGTH_M = 700.0
    private const val MIN_HALF_ANGLE_DEG = 2.5
    private const val MAX_HALF_ANGLE_DEG = 14.0
    private const val APPROACH_FRACTION = 0.85
}
