package uk.hunttrack.domain.astronomy

import uk.hunttrack.domain.model.AstronomyInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Lightweight sun/moon calculator sufficient for UK field dashboards (suncalc-inspired).
 */
object AstronomyCalculator {

    fun calculate(latitude: Double, longitude: Double, epochMs: Long): AstronomyInfo {
        val d = toDays(epochMs)
        val sunTimes = sunTimes(d, latitude, longitude)
        val moon = moonData(d, latitude, longitude)
        val moonTimes = moonTimes(d, latitude, longitude)

        return AstronomyInfo(
            sunriseEpochMs = sunTimes.rise,
            sunsetEpochMs = sunTimes.set,
            moonriseEpochMs = moonTimes.rise,
            moonsetEpochMs = moonTimes.set,
            moonPhase = moon.phase,
            moonIllumination = moon.illumination,
            goldenHourStartEpochMs = sunTimes.set - 60 * 60 * 1000L,
            goldenHourEndEpochMs = sunTimes.set,
            civilTwilightBeginEpochMs = sunTimes.rise - 30 * 60 * 1000L,
            civilTwilightEndEpochMs = sunTimes.set + 30 * 60 * 1000L,
        )
    }

    private data class SunTimes(val rise: Long, val set: Long)
    private data class MoonTimes(val rise: Long?, val set: Long?)
    private data class MoonData(val phase: Double, val illumination: Double)

    private fun toJulian(epochMs: Long): Double = epochMs / 86400000.0 + 2440587.5
    private fun toDays(epochMs: Long): Double = toJulian(epochMs) - 2451545.0
    /** Invert of toJulian (was wrongly +0.5 day → sun/moon times 12 hours late). */
    private fun fromJulian(j: Double): Long = ((j - 2440587.5) * 86400000.0).toLong()

    private fun rightAscension(l: Double, b: Double): Double =
        atan2(sin(l) * cos(obliquity()) - tan(b) * sin(obliquity()), cos(l))

    private fun declination(l: Double, b: Double): Double =
        asin(sin(b) * cos(obliquity()) + cos(b) * sin(obliquity()) * sin(l))

    private fun obliquity(): Double = rad(23.4397)

    private fun solarMeanAnomaly(d: Double): Double = rad(357.5291) + rad(0.98560028) * d
    private fun eclipticLongitude(m: Double): Double {
        val c = rad(1.9148) * sin(m) + rad(0.02) * sin(2 * m) + rad(0.0003) * sin(3 * m)
        val p = rad(102.9372)
        return m + c + p + PI
    }

    private fun sunCoords(d: Double): Pair<Double, Double> {
        val m = solarMeanAnomaly(d)
        val l = eclipticLongitude(m)
        return rightAscension(l, 0.0) to declination(l, 0.0)
    }

    private fun siderealTime(d: Double, lw: Double): Double =
        rad(280.16) + rad(360.9856235) * d - lw

    private fun julianCycle(d: Double, lw: Double): Double =
        floor(d - 0.0009 - lw / (2 * PI) + 0.5)

    private fun approxTransit(ht: Double, lw: Double, n: Double): Double =
        0.0009 + (ht + lw) / (2 * PI) + n

    private fun solarTransitJ(ds: Double, m: Double, l: Double): Double =
        2451545.0 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)

    private fun hourAngle(h: Double, phi: Double, d: Double): Double =
        acos((sin(h) - sin(phi) * sin(d)) / (cos(phi) * cos(d)))

    private fun getSetJ(h: Double, lw: Double, phi: Double, dec: Double, n: Double, m: Double, l: Double): Double {
        val w = hourAngle(h, phi, dec)
        val a = approxTransit(w, lw, n)
        return solarTransitJ(a, m, l)
    }

    private fun sunTimes(d: Double, lat: Double, lng: Double): SunTimes {
        val lw = rad(-lng)
        val phi = rad(lat)
        val n = julianCycle(d, lw)
        val ds = approxTransit(0.0, lw, n)
        val m = solarMeanAnomaly(ds)
        val l = eclipticLongitude(m)
        val dec = declination(l, 0.0)
        val jNoon = solarTransitJ(ds, m, l)
        val jSet = getSetJ(rad(-0.833), lw, phi, dec, n, m, l)
        val jRise = jNoon - (jSet - jNoon)
        return SunTimes(fromJulian(jRise), fromJulian(jSet))
    }

    private fun moonCoords(d: Double): Triple<Double, Double, Double> {
        val l = rad(218.316) + rad(13.176396) * d
        val m = rad(134.963) + rad(13.064993) * d
        val f = rad(297.850) + rad(0.1114034) * d
        val long = l + rad(6.289) * sin(m)
        val lat = rad(5.128) * sin(f)
        val dt = 385001 - 20905 * cos(m)
        return Triple(rightAscension(long, lat), declination(long, lat), dt)
    }

    private fun moonData(d: Double, lat: Double, lng: Double): MoonData {
        val (sra, sdec) = sunCoords(d)
        val (mra, mdec, dist) = moonCoords(d)
        // Geocentric elongation between Sun and Moon
        val phi = acos(
            (sin(sdec) * sin(mdec) + cos(sdec) * cos(mdec) * cos(sra - mra))
                .coerceIn(-1.0, 1.0),
        )
        // Phase angle of the Moon (Sun–Moon–Earth). Must use solar distance in the
        // numerator — swapped terms wrongly inverted new ↔ full.
        val sdist = 149598000.0
        val inc = atan2(sdist * sin(phi), dist - sdist * cos(phi))
        val angle = atan2(
            cos(sdec) * sin(sra - mra),
            sin(sdec) * cos(mdec) - cos(sdec) * sin(mdec) * cos(sra - mra),
        )
        val illumination = ((1 + cos(inc)) / 2).coerceIn(0.0, 1.0)
        // 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter
        val phase = 0.5 + 0.5 * inc * (if (angle < 0) -1 else 1) / PI
        return MoonData(phase = ((phase % 1.0) + 1.0) % 1.0, illumination = illumination)
    }

    private fun moonTimes(d: Double, lat: Double, lng: Double): MoonTimes {
        // Approximate moonrise/set by sampling the day (good enough for dashboard).
        var rise: Long? = null
        var set: Long? = null
        var prevAlt = moonAltitude(d - 1.0 / 24, lat, lng)
        for (i in 0 until 48) {
            val di = d + i / 48.0
            val alt = moonAltitude(di, lat, lng)
            if (prevAlt < 0 && alt >= 0 && rise == null) {
                rise = fromJulian(2451545.0 + di)
            }
            if (prevAlt >= 0 && alt < 0 && set == null) {
                set = fromJulian(2451545.0 + di)
            }
            prevAlt = alt
        }
        return MoonTimes(rise, set)
    }

    private fun moonAltitude(d: Double, lat: Double, lng: Double): Double {
        val (ra, dec, _) = moonCoords(d)
        val lw = rad(-lng)
        val phi = rad(lat)
        val h = siderealTime(d, lw) - ra
        return asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
    }

    private fun rad(d: Double): Double = d * PI / 180.0
}
