package uk.hunttrack.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers for MapLibre style JSON.
 *
 * Avoid full [JSONObject] round-trips on large vector styles (OpenFreeMap Liberty) —
 * re-serializing expressions can produce a “loaded” style that paints nothing.
 */
object MapLibreStyleSupport {
    const val OPEN_FREEMAP_LIBERTY_URL = "https://tiles.openfreemap.org/styles/liberty"

    /**
     * Keyless Esri World Hillshade raster used as an online-only terrain overlay for the
     * Outdoor layer. The underlying vector basemap still works fully offline.
     */
    const val ESRI_HILLSHADE_TILE_URL =
        "https://services.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}"

    /** True when the device currently has a network with internet capability. */
    fun isNetworkAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private const val RENDERER_KEY = "maputnik:renderer"
    private const val RENDERER_VALUE = "mbgljs"

    /**
     * Inject renderer metadata without parsing the whole style tree.
     * Needed on some MapLibre Android 11.5+ / OEM combos to avoid MapRenderer SIGSEGV.
     */
    fun ensureRendererMetadata(styleJson: String): String {
        if (styleJson.contains("\"$RENDERER_KEY\"")) return styleJson
        val trimmed = styleJson.trimStart()
        if (!trimmed.startsWith("{")) return styleJson
        val injection =
            """{"metadata":{"$RENDERER_KEY":"$RENDERER_VALUE"},"""
        return injection + trimmed.substring(1)
    }

    /** Fetch a remote style JSON and inject renderer metadata. Null on network failure. */
    fun loadPatchedStyleJson(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        connection.inputStream.bufferedReader().use { reader ->
            ensureRendererMetadata(reader.readText())
        }
    }.getOrNull()

    fun rasterBasemapStyleJson(
        tileUrls: List<String>,
        minZoom: Int,
        maxZoom: Int,
        tileSize: Int = 256,
        attribution: String? = null,
    ): String {
        val tiles = JSONArray().also { arr -> tileUrls.forEach { arr.put(it) } }
        val source = JSONObject()
            .put("type", "raster")
            .put("tiles", tiles)
            .put("tileSize", tileSize)
            .put("minzoom", minZoom)
            .put("maxzoom", maxZoom)
        attribution?.let { source.put("attribution", it) }
        val layer = JSONObject()
            .put("id", "basemap-layer")
            .put("type", "raster")
            .put("source", "basemap")
        return JSONObject()
            .put("version", 8)
            .put(
                "metadata",
                JSONObject().put(RENDERER_KEY, RENDERER_VALUE),
            )
            .put("sources", JSONObject().put("basemap", source))
            .put("layers", JSONArray().put(layer))
            .toString()
    }
}
