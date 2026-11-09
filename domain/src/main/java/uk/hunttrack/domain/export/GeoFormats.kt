package uk.hunttrack.domain.export

import java.time.Instant
import java.util.Locale

/**
 * GPX, KML and GeoJSON views of a field backup. Coordinates stay in WGS84.
 */
object GeoFormats {
    fun gpx(backup: FieldBackup): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("\n")
        append("""<gpx version="1.1" creator="HunTracK" xmlns="http://www.topografix.com/GPX/1/1">""")
        append("\n")
        backup.markers.forEach { marker ->
            append("  <wpt lat=\"${coord(marker.latitude)}\" lon=\"${coord(marker.longitude)}\">\n")
            marker.elevationMeters?.let { append("    <ele>${coord(it)}</ele>\n") }
            append("    <time>${iso(marker.createdAtEpochMs)}</time>\n")
            append("    <name>${xml(marker.name)}</name>\n")
            append("    <desc>${xml(markerDescription(marker))}</desc>\n")
            append("    <type>${xml(marker.category)}</type>\n")
            append("  </wpt>\n")
        }
        backup.routes.forEach { route ->
            append("  <trk>\n")
            append("    <name>${xml(route.name)}</name>\n")
            append("    <trkseg>\n")
            route.points.forEach { point ->
                append("      <trkpt lat=\"${coord(point.latitude)}\" lon=\"${coord(point.longitude)}\">\n")
                point.elevationMeters?.let { append("        <ele>${coord(it)}</ele>\n") }
                append("        <time>${iso(point.recordedAtEpochMs)}</time>\n")
                append("      </trkpt>\n")
            }
            append("    </trkseg>\n")
            append("  </trk>\n")
        }
        append("</gpx>\n")
    }

    fun kml(backup: FieldBackup): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("\n")
        append("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        append("\n<Document>\n")
        append("  <name>HunTracK field data</name>\n")
        append("  <Folder><name>Boundaries</name>\n")
        backup.boundaries.forEach { area ->
            val ordered = area.points.sortedBy { it.orderIndex }
            append("    <Placemark>\n")
            append("      <name>${xml(area.name)}</name>\n")
            append("      <description>${xml("${area.zoneType}. ${area.notes}")}</description>\n")
            if (ordered.size >= 3) {
                val ring = ordered + ordered.first()
                append("      <Polygon><outerBoundaryIs><LinearRing><coordinates>")
                append(ring.joinToString(" ") { "${coord(it.longitude)},${coord(it.latitude)},0" })
                append("</coordinates></LinearRing></outerBoundaryIs></Polygon>\n")
            } else if (ordered.isNotEmpty()) {
                append("      <LineString><coordinates>")
                append(ordered.joinToString(" ") { "${coord(it.longitude)},${coord(it.latitude)},0" })
                append("</coordinates></LineString>\n")
            }
            append("    </Placemark>\n")
        }
        append("  </Folder>\n")
        append("  <Folder><name>Markers</name>\n")
        backup.markers.forEach { marker ->
            append("    <Placemark>\n")
            append("      <name>${xml(marker.name)}</name>\n")
            append("      <description>${xml(markerDescription(marker))}</description>\n")
            append("      <Point><coordinates>${coord(marker.longitude)},${coord(marker.latitude)},0</coordinates></Point>\n")
            append("    </Placemark>\n")
        }
        append("  </Folder>\n")
        append("</Document>\n</kml>\n")
    }

    fun geoJson(backup: FieldBackup): String {
        val features = mutableListOf<String>()
        backup.markers.forEach { marker ->
            features += feature(
                geometry = """{"type":"Point","coordinates":[${coord(marker.longitude)},${coord(marker.latitude)}]}""",
                properties = mapOf(
                    "kind" to jsonString("marker"),
                    "name" to jsonString(marker.name),
                    "category" to jsonString(marker.category),
                    "notes" to jsonString(marker.notes),
                    "speciesId" to (marker.speciesId?.let(::jsonString) ?: "null"),
                ),
            )
        }
        backup.boundaries.forEach { area ->
            val ordered = area.points.sortedBy { it.orderIndex }
            if (ordered.size >= 3) {
                val ring = (ordered + ordered.first()).joinToString(",") {
                    "[${coord(it.longitude)},${coord(it.latitude)}]"
                }
                features += feature(
                    geometry = """{"type":"Polygon","coordinates":[[$ring]]}""",
                    properties = mapOf(
                        "kind" to jsonString("boundary"),
                        "name" to jsonString(area.name),
                        "zoneType" to jsonString(area.zoneType),
                        "notes" to jsonString(area.notes),
                    ),
                )
            }
        }
        backup.routes.forEach { route ->
            if (route.points.isEmpty()) return@forEach
            val line = route.points.joinToString(",") {
                "[${coord(it.longitude)},${coord(it.latitude)}]"
            }
            features += feature(
                geometry = """{"type":"LineString","coordinates":[$line]}""",
                properties = mapOf(
                    "kind" to jsonString("track"),
                    "name" to jsonString(route.name),
                ),
            )
        }
        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }

    private fun feature(geometry: String, properties: Map<String, String>): String {
        val props = properties.entries.joinToString(",") { (key, value) ->
            "${jsonString(key)}:$value"
        }
        return """{"type":"Feature","geometry":$geometry,"properties":{$props}}"""
    }

    private fun markerDescription(marker: BackupMarker): String = buildString {
        append(marker.category)
        if (marker.notes.isNotBlank()) {
            append(". ")
            append(marker.notes)
        }
        marker.speciesId?.let {
            append(". Species ")
            append(it)
        }
    }

    private fun coord(value: Double): String {
        if (!value.isFinite()) return "0"
        return String.format(Locale.US, "%.7f", value).trimEnd('0').trimEnd('.')
    }

    private fun iso(epochMs: Long): String =
        runCatching { Instant.ofEpochMilli(epochMs).toString() }.getOrDefault("1970-01-01T00:00:00Z")

    private fun xml(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
