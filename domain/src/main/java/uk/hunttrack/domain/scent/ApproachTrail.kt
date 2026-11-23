package uk.hunttrack.domain.scent

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Which prints on a straight into-wind approach the hunter has already walked past.
 *
 * A print fades grey only once GPS is on the route (within [CORRIDOR_M] of the
 * line) and further toward the marker than that print, by [PASS_MARGIN_M].
 * Prints stay visible either way so the way back is still on the map.
 */
object ApproachTrail {
    const val CORRIDOR_M = 45.0
    const val PASS_MARGIN_M = 10.0

    fun walkedPast(
        printLat: Double,
        printLng: Double,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        userLat: Double?,
        userLng: Double?,
    ): Boolean {
        if (userLat == null || userLng == null) return false
        val routeEast = eastMeters(endLng, startLng, startLat)
        val routeNorth = northMeters(endLat, startLat)
        val routeLen = hypot(routeEast, routeNorth)
        if (routeLen < 12.0) return false
        val userEast = eastMeters(userLng, startLng, startLat)
        val userNorth = northMeters(userLat, startLat)
        val along = (userEast * routeEast + userNorth * routeNorth) / routeLen
        val cross = abs(userEast * routeNorth - userNorth * routeEast) / routeLen
        if (cross > CORRIDOR_M || along < PASS_MARGIN_M) return false
        val printEast = eastMeters(printLng, startLng, startLat)
        val printNorth = northMeters(printLat, startLat)
        val printAlong = (printEast * routeEast + printNorth * routeNorth) / routeLen
        return along > printAlong + PASS_MARGIN_M
    }

    /**
     * How far the hunter has walked from the downwind start toward the marker, 0 to 1.
     * Off the route, or with no GPS, the whole trail is still ahead (0).
     */
    fun progressFraction(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        userLat: Double?,
        userLng: Double?,
    ): Double {
        if (userLat == null || userLng == null) return 0.0
        val routeEast = eastMeters(endLng, startLng, startLat)
        val routeNorth = northMeters(endLat, startLat)
        val routeLen = hypot(routeEast, routeNorth)
        if (routeLen < 12.0) return 0.0
        val userEast = eastMeters(userLng, startLng, startLat)
        val userNorth = northMeters(userLat, startLat)
        val along = (userEast * routeEast + userNorth * routeNorth) / routeLen
        val cross = abs(userEast * routeNorth - userNorth * routeEast) / routeLen
        if (cross > CORRIDOR_M) return 0.0
        return (along / routeLen).coerceIn(0.0, 1.0)
    }

    private fun northMeters(lat: Double, originLat: Double): Double = (lat - originLat) * 110_540.0

    private fun eastMeters(lng: Double, originLng: Double, originLat: Double): Double =
        (lng - originLng) * 111_320.0 * cos(Math.toRadians(originLat))
}
