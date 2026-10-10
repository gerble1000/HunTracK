package uk.hunttrack.domain.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Ordnance Survey National Grid references (British National Grid / OSGB36).
 *
 * Display format is 10-figure (~1 m), e.g. `SU 37290 15552`.
 * Parsing accepts spaced or compact refs at 6, 8, or 10 figures.
 */
object OsGridRef {

    data class LatLng(val latitude: Double, val longitude: Double)

    data class GridLine(
        val points: List<LatLng>,
        /** True for 100 km / 10 km “major” lines; false for finer spacing. */
        val major: Boolean,
    )

    /** Convert WGS84 lat/lng to a 10-figure OS Grid Reference, or null if outside GB coverage. */
    fun latLngToOsGrid(latitude: Double, longitude: Double, digits: Int = 10): String? {
        require(digits in listOf(6, 8, 10)) { "digits must be 6, 8, or 10" }
        val (easting, northing) = wgs84ToEastNorth(latitude, longitude) ?: return null
        return eastNorthToGridRef(easting, northing, digits)
    }

    /** Parse an OS Grid Reference (e.g. `SU 37290 15552` or `SU3729015552`) to WGS84 lat/lng. */
    fun osGridToLatLng(ref: String): LatLng? {
        val (easting, northing) = gridRefToEastNorth(ref) ?: return null
        return eastNorthToWgs84(easting, northing)
    }

    fun isValidOsGridRef(ref: String): Boolean = gridRefToEastNorth(ref) != null

    /** WGS84 → metres on the British National Grid, or null outside coverage. */
    fun toEastNorth(latitude: Double, longitude: Double): Pair<Double, Double>? =
        wgs84ToEastNorth(latitude, longitude)

    /** National Grid metres → WGS84, or null outside coverage. */
    fun fromEastNorth(easting: Double, northing: Double): LatLng? =
        eastNorthToWgs84(easting, northing)

    /**
     * Build OS National Grid line polylines for a WGS84 viewport.
     * [spacingMeters] is typically 100_000 / 10_000 / 1_000 / 100 depending on zoom.
     */
    fun gridLinesForViewport(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        spacingMeters: Int,
        maxLinesPerAxis: Int = 48,
    ): List<GridLine> {
        require(spacingMeters > 0)
        val corners = listOf(
            south to west, south to east, north to west, north to east,
            (south + north) / 2 to west, (south + north) / 2 to east,
            south to (west + east) / 2, north to (west + east) / 2,
        )
        val ens = corners.mapNotNull { (lat, lon) -> wgs84ToEastNorth(lat, lon) }
        if (ens.isEmpty()) return emptyList()

        var eMin = ens.minOf { it.first } - spacingMeters
        var eMax = ens.maxOf { it.first } + spacingMeters
        var nMin = ens.minOf { it.second } - spacingMeters
        var nMax = ens.maxOf { it.second } + spacingMeters
        eMin = eMin.coerceIn(0.0, 700_000.0)
        eMax = eMax.coerceIn(0.0, 700_000.0)
        nMin = nMin.coerceIn(0.0, 1_300_000.0)
        nMax = nMax.coerceIn(0.0, 1_300_000.0)
        if (eMax <= eMin || nMax <= nMin) return emptyList()

        val spacing = spacingMeters.toDouble()
        val eStart = floor(eMin / spacing) * spacing
        val nStart = floor(nMin / spacing) * spacing
        val majorEvery = when {
            spacingMeters >= 100_000 -> 100_000.0
            spacingMeters >= 10_000 -> 100_000.0
            spacingMeters >= 1_000 -> 10_000.0
            else -> 1_000.0
        }

        val sampleStep = (spacing / 4.0).coerceAtLeast(50.0)
        val lines = mutableListOf<GridLine>()

        var e = eStart
        var eCount = 0
        while (e <= eMax + 0.5 && eCount < maxLinesPerAxis) {
            val pts = sampleVertical(e, nMin, nMax, sampleStep)
            if (pts.size >= 2) {
                lines += GridLine(pts, major = e % majorEvery < 0.5 || abs(e % majorEvery - majorEvery) < 0.5)
            }
            e += spacing
            eCount++
        }

        var n = nStart
        var nCount = 0
        while (n <= nMax + 0.5 && nCount < maxLinesPerAxis) {
            val pts = sampleHorizontal(n, eMin, eMax, sampleStep)
            if (pts.size >= 2) {
                lines += GridLine(pts, major = n % majorEvery < 0.5 || abs(n % majorEvery - majorEvery) < 0.5)
            }
            n += spacing
            nCount++
        }
        return lines
    }

    private fun sampleVertical(easting: Double, nMin: Double, nMax: Double, step: Double): List<LatLng> {
        val out = ArrayList<LatLng>()
        var n = nMin
        while (n <= nMax + 0.5) {
            eastNorthToWgs84(easting, n)?.let { out += it }
            n += step
        }
        eastNorthToWgs84(easting, nMax)?.let { if (out.lastOrNull() != it) out += it }
        return out
    }

    private fun sampleHorizontal(northing: Double, eMin: Double, eMax: Double, step: Double): List<LatLng> {
        val out = ArrayList<LatLng>()
        var e = eMin
        while (e <= eMax + 0.5) {
            eastNorthToWgs84(e, northing)?.let { out += it }
            e += step
        }
        eastNorthToWgs84(eMax, northing)?.let { if (out.lastOrNull() != it) out += it }
        return out
    }

    // --- WGS84 ↔ OSGB36 Helmert + Transverse Mercator ---

    private fun wgs84ToEastNorth(lat: Double, lon: Double): Pair<Double, Double>? {
        val (phi, lambda) = wgs84ToOsgb36(Math.toRadians(lat), Math.toRadians(lon))
        val (e, n) = latLonToEastNorth(phi, lambda)
        if (e < 0 || e > 700_000 || n < 0 || n > 1_300_000) return null
        return e to n
    }

    private fun eastNorthToWgs84(easting: Double, northing: Double): LatLng? {
        if (easting < 0 || easting > 700_000 || northing < 0 || northing > 1_300_000) return null
        val (phi, lambda) = eastNorthToLatLon(easting, northing)
        val (wgsPhi, wgsLambda) = osgb36ToWgs84(phi, lambda)
        return LatLng(Math.toDegrees(wgsPhi), Math.toDegrees(wgsLambda))
    }

    private fun wgs84ToOsgb36(phi: Double, lambda: Double): Pair<Double, Double> {
        val (x, y, z) = latLonToCartesian(phi, lambda, A_WGS84, F_WGS84)
        val (x2, y2, z2) = helmert(x, y, z, -446.448, 125.157, -542.060, 20.4894, -0.1502, -0.2470, -0.8421)
        return cartesianToLatLon(x2, y2, z2, A_AIRY, F_AIRY)
    }

    private fun osgb36ToWgs84(phi: Double, lambda: Double): Pair<Double, Double> {
        val (x, y, z) = latLonToCartesian(phi, lambda, A_AIRY, F_AIRY)
        val (x2, y2, z2) = helmert(x, y, z, 446.448, -125.157, 542.060, -20.4894, 0.1502, 0.2470, 0.8421)
        return cartesianToLatLon(x2, y2, z2, A_WGS84, F_WGS84)
    }

    private fun helmert(
        x: Double, y: Double, z: Double,
        tx: Double, ty: Double, tz: Double,
        sPpm: Double,
        rxArcsec: Double, ryArcsec: Double, rzArcsec: Double,
    ): Triple<Double, Double, Double> {
        val s = sPpm / 1e6 + 1.0
        val rx = rxArcsec * ARCSEC_TO_RAD
        val ry = ryArcsec * ARCSEC_TO_RAD
        val rz = rzArcsec * ARCSEC_TO_RAD
        return Triple(
            tx + s * (x - rz * y + ry * z),
            ty + s * (rz * x + y - rx * z),
            tz + s * (-ry * x + rx * y + z),
        )
    }

    private fun latLonToCartesian(
        phi: Double, lambda: Double, a: Double, f: Double,
    ): Triple<Double, Double, Double> {
        val e2 = 2 * f - f * f
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val nu = a / sqrt(1 - e2 * sinPhi * sinPhi)
        return Triple(
            nu * cosPhi * cos(lambda),
            nu * cosPhi * sin(lambda),
            nu * (1 - e2) * sinPhi,
        )
    }

    private fun cartesianToLatLon(
        x: Double, y: Double, z: Double, a: Double, f: Double,
    ): Pair<Double, Double> {
        val e2 = 2 * f - f * f
        val p = sqrt(x * x + y * y)
        var phi = atan2(z, p * (1 - e2))
        repeat(10) {
            val sinPhi = sin(phi)
            val nu = a / sqrt(1 - e2 * sinPhi * sinPhi)
            val phiNext = atan2(z + e2 * nu * sinPhi, p)
            if (abs(phiNext - phi) < 1e-12) {
                phi = phiNext
                return@repeat
            }
            phi = phiNext
        }
        return phi to atan2(y, x)
    }

    private fun latLonToEastNorth(phi: Double, lambda: Double): Pair<Double, Double> {
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val tanPhi = tan(phi)
        val tan2 = tanPhi * tanPhi
        val tan4 = tan2 * tan2
        val nu = A_AIRY * F0 / sqrt(1 - E2 * sinPhi * sinPhi)
        val rho = A_AIRY * F0 * (1 - E2) / (1 - E2 * sinPhi * sinPhi).pow(1.5)
        val eta2 = nu / rho - 1

        val m = meridionalArc(phi)
        val dLon = lambda - LAMBDA0
        val dLon2 = dLon * dLon
        val dLon3 = dLon2 * dLon
        val dLon4 = dLon3 * dLon
        val dLon5 = dLon4 * dLon
        val dLon6 = dLon5 * dLon

        val i = m + N0
        val ii = (nu / 2) * sinPhi * cosPhi
        val iii = (nu / 24) * sinPhi * cosPhi.pow(3) * (5 - tan2 + 9 * eta2)
        val iiia = (nu / 720) * sinPhi * cosPhi.pow(5) * (61 - 58 * tan2 + tan4)
        val iv = nu * cosPhi
        val v = (nu / 6) * cosPhi.pow(3) * (nu / rho - tan2)
        val vi = (nu / 120) * cosPhi.pow(5) * (5 - 18 * tan2 + tan4 + 14 * eta2 - 58 * tan2 * eta2)

        val easting = E0 + iv * dLon + v * dLon3 + vi * dLon5
        val northing = i + ii * dLon2 + iii * dLon4 + iiia * dLon6
        return easting to northing
    }

    private fun eastNorthToLatLon(easting: Double, northing: Double): Pair<Double, Double> {
        var phi = PHI0
        var m: Double
        val nPrim = northing - N0
        repeat(20) {
            m = meridionalArc(phi)
            val delta = nPrim - m
            if (abs(delta) < 1e-5) return@repeat
            phi += delta / (A_AIRY * F0)
        }

        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val tanPhi = tan(phi)
        val tan2 = tanPhi * tanPhi
        val tan4 = tan2 * tan2
        val tan6 = tan4 * tan2
        val secPhi = 1.0 / cosPhi
        val nu = A_AIRY * F0 / sqrt(1 - E2 * sinPhi * sinPhi)
        val rho = A_AIRY * F0 * (1 - E2) / (1 - E2 * sinPhi * sinPhi).pow(1.5)
        val eta2 = nu / rho - 1

        val dE = easting - E0
        val dE2 = dE * dE
        val dE3 = dE2 * dE
        val dE4 = dE3 * dE
        val dE5 = dE4 * dE
        val dE6 = dE5 * dE
        val dE7 = dE6 * dE

        val vii = tanPhi / (2 * rho * nu)
        val viii = tanPhi / (24 * rho * nu.pow(3)) * (5 + 3 * tan2 + eta2 - 9 * tan2 * eta2)
        val ix = tanPhi / (720 * rho * nu.pow(5)) * (61 + 90 * tan2 + 45 * tan4)
        val x = secPhi / nu
        val xi = secPhi / (6 * nu.pow(3)) * (nu / rho + 2 * tan2)
        val xii = secPhi / (120 * nu.pow(5)) * (5 + 28 * tan2 + 24 * tan4)
        val xiia = secPhi / (5040 * nu.pow(7)) * (61 + 662 * tan2 + 1320 * tan4 + 720 * tan6)

        val lat = phi - vii * dE2 + viii * dE4 - ix * dE6
        val lon = LAMBDA0 + x * dE - xi * dE3 + xii * dE5 - xiia * dE7
        return lat to lon
    }

    private fun meridionalArc(phi: Double): Double {
        val n = (A_AIRY - B_AIRY) / (A_AIRY + B_AIRY)
        val n2 = n * n
        val n3 = n2 * n
        val phiDiff = phi - PHI0
        val phiSum = phi + PHI0
        return B_AIRY * F0 * (
            (1 + n + 5.0 / 4 * n2 + 5.0 / 4 * n3) * phiDiff -
                (3 * n + 3 * n2 + 21.0 / 8 * n3) * sin(phiDiff) * cos(phiSum) +
                (15.0 / 8 * n2 + 15.0 / 8 * n3) * sin(2 * phiDiff) * cos(2 * phiSum) -
                (35.0 / 24 * n3) * sin(3 * phiDiff) * cos(3 * phiSum)
            )
    }

    // --- Grid letters ---

    fun eastNorthToGridRef(easting: Double, northing: Double, digits: Int = 10): String? {
        if (easting < 0 || easting > 700_000 || northing < 0 || northing > 1_300_000) return null
        val e100k = floor(easting / 100_000).toInt()
        val n100k = floor(northing / 100_000).toInt()
        if (e100k !in 0..6 || n100k !in 0..12) return null

        // Numeric equivalents of the grid letters (skip 'I')
        val l1 = (19 - n100k) - (19 - n100k) % 5 + ((e100k + 10) / 5)
        val l2 = ((19 - n100k) * 5) % 25 + e100k % 5
        fun letter(index: Int): Char {
            var i = index
            if (i > 7) i++
            return ('A' + i)
        }
        val letters = "${letter(l1)}${letter(l2)}"

        val e = floor(easting % 100_000).toInt()
        val n = floor(northing % 100_000).toInt()
        val pairs = digits / 2
        val factor = 10.0.pow(5 - pairs).toInt()
        val eStr = (e / factor).toString().padStart(pairs, '0')
        val nStr = (n / factor).toString().padStart(pairs, '0')
        return "$letters $eStr $nStr"
    }

    fun gridRefToEastNorth(ref: String): Pair<Double, Double>? {
        val compact = ref.trim().uppercase()
            .replace(",", " ")
            .replace(Regex("\\s+"), "")
        // Letters + even digit run (6/8/10 figures)
        val match = Regex("^([A-HJ-Z]{2})(\\d{6}|\\d{8}|\\d{10})$").matchEntire(compact) ?: return null
        val letters = match.groupValues[1]
        val digits = match.groupValues[2]
        val pairs = digits.length / 2
        val ePart = digits.substring(0, pairs)
        val nPart = digits.substring(pairs)

        fun letterIndex(c: Char): Int? {
            if (c == 'I') return null
            var i = c - 'A'
            if (i > 7) i-- // skip I
            return if (i in 0..24) i else null
        }
        val l1 = letterIndex(letters[0]) ?: return null
        val l2 = letterIndex(letters[1]) ?: return null

        // 100km-square indexes from false origin (grid square SV) — Movable Type / OS
        val e100km = floorMod(l1 - 2, 5) * 5 + (l2 % 5)
        val n100km = (19 - (l1 / 5) * 5) - (l2 / 5)

        val resolution = 10.0.pow(5 - pairs)
        val easting = e100km * 100_000.0 + ePart.toInt() * resolution + resolution / 2.0
        val northing = n100km * 100_000.0 + nPart.toInt() * resolution + resolution / 2.0
        if (easting < 0 || easting > 700_000 || northing < 0 || northing > 1_300_000) return null
        return easting to northing
    }

    /** Positive modulo (matches JS % behaviour used by OS grid letter maths). */
    private fun floorMod(a: Int, m: Int): Int {
        val r = a % m
        return if (r < 0) r + m else r
    }

    private const val A_WGS84 = 6378137.0
    private const val F_WGS84 = 1 / 298.257223563
    private const val A_AIRY = 6377563.396
    private const val B_AIRY = 6356256.909
    private const val F_AIRY = 1 / 299.3249646
    private val E2 = (A_AIRY * A_AIRY - B_AIRY * B_AIRY) / (A_AIRY * A_AIRY)
    private const val F0 = 0.9996012717
    private val PHI0 = Math.toRadians(49.0)
    private val LAMBDA0 = Math.toRadians(-2.0)
    private const val E0 = 400_000.0
    private const val N0 = -100_000.0
    private const val ARCSEC_TO_RAD = PI / (180.0 * 3600.0)
}
