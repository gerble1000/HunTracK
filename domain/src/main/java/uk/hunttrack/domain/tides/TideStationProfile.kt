package uk.hunttrack.domain.tides

import uk.hunttrack.domain.model.TideStation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class TideStationProfile(
    val station: TideStation,
    /** Path slug on tidetimes.org.uk (e.g. "dover", "liverpool-gladstone-dock"). */
    val slug: String,
    /** Typical spring tidal range (m) — used only for offline fallback. */
    val springRangeM: Double,
    /** Typical neap tidal range (m) — used only for offline fallback. */
    val neapRangeM: Double,
    /** Chart-datum offset for display heights (m) — offline fallback. */
    val meanLevelM: Double,
    /** Minutes after lunar transit until high water — offline fallback. */
    val hwLagMinutes: Double,
)

object UkTideStationCatalog {

    private val profiles: List<TideStationProfile> = listOf(
        port("dover", "Dover", "dover", 51.127, 1.314, spring = 6.5, neap = 4.0, level = 3.2, lag = 95.0),
        port("liverpool", "Liverpool (Gladstone Dock)", "liverpool-gladstone-dock", 53.450, -3.020, spring = 8.3, neap = 4.5, level = 4.0, lag = -25.0),
        port("holyhead", "Holyhead", "holyhead", 53.310, -4.620, spring = 5.6, neap = 3.2, level = 2.8, lag = 50.0),
        port("milford", "Milford Haven", "milford-haven", 51.714, -5.050, spring = 6.8, neap = 3.5, level = 3.4, lag = 110.0),
        port("plymouth", "Plymouth (Devonport)", "plymouth-devonport", 50.366, -4.192, spring = 4.8, neap = 2.4, level = 2.4, lag = 125.0),
        port("falmouth", "Falmouth", "falmouth", 50.152, -5.055, spring = 4.5, neap = 2.2, level = 2.2, lag = 130.0),
        port("southampton", "Southampton", "southampton", 50.898, -1.404, spring = 4.2, neap = 2.0, level = 2.1, lag = 105.0),
        port("portsmouth", "Portsmouth", "portsmouth", 50.805, -1.110, spring = 4.0, neap = 2.0, level = 2.0, lag = 100.0),
        port("brighton", "Brighton Marina", "brighton-marina", 50.820, -0.137, spring = 5.8, neap = 3.2, level = 2.9, lag = 90.0),
        port("london_bridge", "London Bridge (Tower Pier)", "london-bridge-tower-pier", 51.508, -0.075, spring = 7.0, neap = 4.5, level = 3.5, lag = 120.0),
        port("harwich", "Harwich", "harwich", 51.953, 1.288, spring = 3.8, neap = 2.0, level = 1.9, lag = 85.0),
        port("lowestoft", "Lowestoft", "lowestoft", 52.475, 1.751, spring = 2.8, neap = 1.5, level = 1.4, lag = 80.0),
        port("great_yarmouth", "Great Yarmouth (Britannia Pier)", "great-yarmouth-britannia-pier", 52.608, 1.730, spring = 2.5, neap = 1.3, level = 1.3, lag = 75.0),
        port("hull", "Hull (Albert Dock)", "hull-albert-dock", 53.741, -0.332, spring = 6.0, neap = 3.5, level = 3.0, lag = 70.0),
        port("immingham", "Immingham", "immingham", 53.630, -0.195, spring = 6.5, neap = 3.8, level = 3.2, lag = 68.0),
        port("tynemouth", "River Tyne (North Shields)", "river-tyne-north-shields", 55.010, -1.450, spring = 4.5, neap = 2.5, level = 2.3, lag = 75.0),
        port("leith", "Leith", "leith", 55.980, -3.170, spring = 5.0, neap = 2.8, level = 2.5, lag = 60.0),
        port("aberdeen", "Aberdeen", "aberdeen", 57.144, -2.080, spring = 3.9, neap = 2.0, level = 1.9, lag = 55.0),
        port("inverness", "Inverness", "inverness", 57.481, -4.224, spring = 4.5, neap = 2.5, level = 2.2, lag = 45.0),
        port("ullapool", "Ullapool", "ullapool", 57.896, -5.160, spring = 4.8, neap = 2.6, level = 2.4, lag = 40.0),
        port("oban", "Oban", "oban", 56.415, -5.475, spring = 4.0, neap = 2.2, level = 2.0, lag = 35.0),
        port("greenock", "Greenock", "greenock", 55.948, -4.756, spring = 3.5, neap = 1.8, level = 1.7, lag = 50.0),
        port("stranraer", "Stranraer", "stranraer", 54.903, -5.027, spring = 3.2, neap = 1.6, level = 1.6, lag = 45.0),
        port("belfast", "Belfast", "belfast", 54.617, -5.900, spring = 3.5, neap = 1.8, level = 1.7, lag = 55.0),
        port("larne", "Larne", "larne", 54.851, -5.807, spring = 3.2, neap = 1.6, level = 1.6, lag = 50.0),
        port("dublin", "Dublin (North Wall)", "dublin-north-wall", 53.350, -6.200, spring = 4.0, neap = 2.0, level = 2.0, lag = 65.0),
        port("cardiff", "Cardiff", "cardiff", 51.463, -3.180, spring = 12.0, neap = 6.5, level = 6.0, lag = 90.0),
        port("swansea", "Swansea", "swansea", 51.621, -3.936, spring = 8.5, neap = 4.5, level = 4.2, lag = 100.0),
        port("llandudno", "Llandudno", "llandudno", 53.330, -3.830, spring = 7.5, neap = 4.0, level = 3.8, lag = 55.0),
        port("workington", "Workington", "workington", 54.650, -3.570, spring = 6.0, neap = 3.2, level = 3.0, lag = 60.0),
        port("barrow", "Barrow (Ramsden Dock)", "barrow-ramsden-dock", 54.110, -3.230, spring = 8.0, neap = 4.2, level = 4.0, lag = 65.0),
        port("st_marys", "St Mary's (Isles of Scilly)", "st-marys", 49.917, -6.317, spring = 5.0, neap = 2.5, level = 2.5, lag = 140.0),
    )

    fun allStations(): List<TideStation> = profiles.map { it.station }

    fun profileFor(stationId: String): TideStationProfile? =
        profiles.firstOrNull { it.station.id == stationId }

    fun profileFor(station: TideStation): TideStationProfile =
        profileFor(station.id) ?: nearestProfile(station.latitude, station.longitude)

    fun nearestProfile(latitude: Double, longitude: Double): TideStationProfile {
        return profiles.minBy { profile ->
            haversineKm(latitude, longitude, profile.station.latitude, profile.station.longitude)
        }
    }

    private fun port(
        id: String,
        name: String,
        slug: String,
        lat: Double,
        lng: Double,
        spring: Double,
        neap: Double,
        level: Double,
        lag: Double,
    ): TideStationProfile {
        val station = TideStation(id = id, name = name, country = "UK", latitude = lat, longitude = lng)
        return TideStationProfile(station, slug, spring, neap, level, lag)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
