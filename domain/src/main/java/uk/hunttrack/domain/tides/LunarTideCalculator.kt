package uk.hunttrack.domain.tides

import uk.hunttrack.domain.astronomy.AstronomyCalculator
import uk.hunttrack.domain.model.TideEvent
import uk.hunttrack.domain.model.TideEventType
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Offline UK semidiurnal tide model from lunar hour angle, spring/neap range, and per-port lag.
 * Approximate — verify against local charts before safety-critical use.
 */
object LunarTideCalculator {

    /** M2 lunar semidiurnal period (hours). */
    private const val SEMIDIURNAL_HOURS = 12.42

    fun forecast(profile: TideStationProfile, fromEpochMs: Long, days: Int = 7): List<TideEvent> {
        val endMs = fromEpochMs + days * 86_400_000L
        val stepMs = 5 * 60 * 1000L
        val samples = mutableListOf<Pair<Long, Double>>()
        var t = fromEpochMs - stepMs
        while (t <= endMs + stepMs) {
            samples += t to heightAt(profile, t)
            t += stepMs
        }
        return findExtrema(samples, fromEpochMs)
    }

    fun heightAt(profile: TideStationProfile, epochMs: Long): Double {
        val lat = profile.station.latitude
        val lng = profile.station.longitude
        val moonPhase = AstronomyCalculator.calculate(lat, lng, epochMs).moonPhase
        val springNeap = cos(2 * PI * moonPhase).let { it * it }
        val range = profile.neapRangeM + (profile.springRangeM - profile.neapRangeM) * springNeap

        val hourAngle = moonHourAngleRad(lat, lng, epochMs)
        val lagRad = (profile.hwLagMinutes / (SEMIDIURNAL_HOURS * 60.0)) * 2 * PI
        val phase = 2 * hourAngle + lagRad
        return profile.meanLevelM + (range / 2.0) * cos(phase)
    }

    private fun findExtrema(samples: List<Pair<Long, Double>>, fromEpochMs: Long): List<TideEvent> {
        if (samples.size < 3) return emptyList()
        val events = mutableListOf<TideEvent>()
        for (i in 1 until samples.lastIndex) {
            val (tPrev, hPrev) = samples[i - 1]
            val (tMid, hMid) = samples[i]
            val (tNext, hNext) = samples[i + 1]
            if (tMid < fromEpochMs - 30 * 60 * 1000L) continue

            if (hMid > hPrev && hMid >= hNext) {
                events += TideEvent(tMid, TideEventType.HIGH, hMid.coerceAtLeast(0.0), isApproximate = true)
            } else if (hMid < hPrev && hMid <= hNext) {
                events += TideEvent(tMid, TideEventType.LOW, hMid.coerceAtLeast(0.0), isApproximate = true)
            }
        }
        return events.sortedBy { it.epochMs }
    }

    private fun moonHourAngleRad(latitude: Double, longitude: Double, epochMs: Long): Double {
        val d = toDays(epochMs)
        val (ra, _) = moonCoords(d)
        val lw = rad(-longitude)
        return siderealTime(d, lw) - ra
    }

    private fun toJulian(epochMs: Long): Double = epochMs / 86_400_000.0 + 2_440_587.5
    private fun toDays(epochMs: Long): Double = toJulian(epochMs) - 2_451_545.0

    private fun siderealTime(d: Double, lw: Double): Double =
        rad(280.16) + rad(360.9856235) * d - lw

    private fun moonCoords(d: Double): Pair<Double, Double> {
        val l = rad(218.316) + rad(13.176396) * d
        val m = rad(134.963) + rad(13.064993) * d
        val f = rad(297.850) + rad(0.1114034) * d
        val long = l + rad(6.289) * sin(m)
        val lat = rad(5.128) * sin(f)
        val ra = atan2(
            sin(long) * cos(obliquity()) - tan(lat) * sin(obliquity()),
            cos(long),
        )
        val dec = asin(sin(lat) * cos(obliquity()) + cos(lat) * sin(obliquity()) * sin(long))
        return ra to dec
    }

    private fun obliquity(): Double = rad(23.4397)
    private fun rad(deg: Double): Double = deg * PI / 180.0
    private fun tan(x: Double): Double = sin(x) / cos(x)
}
