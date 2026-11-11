package uk.hunttrack.domain.export

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.hunttrack.domain.model.BoundaryZoneKind
import uk.hunttrack.domain.model.BoundaryZoneType
import uk.hunttrack.domain.model.MarkerCategory
import uk.hunttrack.domain.model.displayName

/**
 * Spreadsheet, print and structured-text copies of a field backup.
 *
 * The re-importable zip (JSON, GPX, KML, GeoJSON) is a separate file and is not
 * changed by anything in this object.
 *
 * Overlay zones (danger / sold ground drawn on a boundary) live on each
 * [BackupBoundary.zones]. [zonesOf] copies them into the flat list these
 * files use, with the parent boundary's id and name. A version 1 backup has
 * an empty list, so the zones sheet is just a header.
 */
object TextFormats {
    const val CSV_ZIP_NAME = "hunttrack-field-tables.zip"
    const val PERMISSIONS_CSV = "permissions.csv"
    const val BOUNDARIES_CSV = "boundaries.csv"
    const val MARKERS_CSV = "markers.csv"
    const val ZONES_CSV = "zones.csv"
    const val TRACKS_CSV = "tracks.csv"
    const val MEDICAL_CSV = "medical.csv"
    const val XML_NAME = "hunttrack-field.xml"
    const val PDF_NAME = "hunttrack-field-summary.pdf"

    fun zonesOf(backup: FieldBackup): List<OverlayZone> =
        backup.boundaries.flatMap { boundary ->
            boundary.zones.map { zone ->
                OverlayZone(
                    id = zone.id,
                    boundaryId = boundary.id,
                    boundaryName = boundary.name,
                    name = zone.name,
                    kind = zone.kind,
                    notes = zone.notes,
                    points = zone.points,
                )
            }
        }

    fun csvFiles(backup: FieldBackup, zones: List<OverlayZone> = zonesOf(backup)): Map<String, String> {
        val files = linkedMapOf(
            PERMISSIONS_CSV to permissionsCsv(backup),
            BOUNDARIES_CSV to boundariesCsv(backup),
            MARKERS_CSV to markersCsv(backup),
            ZONES_CSV to zonesCsv(zones),
            TRACKS_CSV to tracksCsv(backup),
        )
        val medical = backup.medicalProfile
        if (backup.includesMedicalProfile && medical != null) {
            files[MEDICAL_CSV] = medicalCsv(medical)
        }
        return files
    }

    fun csvZipBytes(backup: FieldBackup, zones: List<OverlayZone> = zonesOf(backup)): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        csvFiles(backup, zones).forEach { (name, text) ->
            entries[name] = text.toByteArray(Charsets.UTF_8)
        }
        return StoredZip.write(entries)
    }

    fun xml(backup: FieldBackup, zones: List<OverlayZone> = zonesOf(backup)): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append("<hunttrack format=\"hunttrack-field-text\" backupVersion=\"")
        append(backup.version)
        append("\" exportedAt=\"")
        append(xmlAttr(stamp(backup.exportedAtEpochMs)))
        append("\" includesMedicalProfile=\"")
        append(backup.includesMedicalProfile)
        append("\">\n")
        append("  <note>")
        append(xmlText(backup.note))
        append("</note>\n")
        append("  <permissions>\n")
        backup.permissions.forEach { permission ->
            append("    <permission id=\"").append(permission.id).append('"')
            append(" docType=\"").append(xmlAttr(permission.docType)).append('"')
            append(" showOnMap=\"").append(permission.showOnMap).append('"')
            permission.boundaryAreaId?.let { append(" boundaryAreaId=\"").append(it).append('"') }
            append(" attachmentOmitted=\"").append(permission.attachmentOmitted).append("\">\n")
            element("label", permission.label, 3)
            element("farmName", permission.farmName, 3)
            element("permissionType", permission.permissionType, 3)
            element("species", permission.species, 3)
            element("validFrom", day(permission.validFromEpochMs), 3)
            element("validTo", day(permission.validToEpochMs), 3)
            element("ownerName", permission.ownerName, 3)
            element("ownerContact", permission.ownerContact, 3)
            element("notes", permission.notes, 3)
            append("    </permission>\n")
        }
        append("  </permissions>\n")
        append("  <boundaries>\n")
        backup.boundaries.forEach { area ->
            append("    <boundary id=\"").append(area.id).append('"')
            append(" zoneType=\"").append(xmlAttr(area.zoneType)).append('"')
            append(" areaSqMeters=\"").append(num(area.areaSqMeters, 2)).append('"')
            append(" areaHectares=\"").append(hectares(area.areaSqMeters)).append("\">\n")
            element("name", area.name, 3)
            element("notes", area.notes, 3)
            appendOutline(area.points, 3)
            append("    </boundary>\n")
        }
        append("  </boundaries>\n")
        appendZones(zones)
        append("  <markers>\n")
        backup.markers.forEach { marker ->
            append("    <marker id=\"").append(marker.id).append('"')
            append(" category=\"").append(xmlAttr(marker.category)).append('"')
            append(" lat=\"").append(coord(marker.latitude)).append('"')
            append(" lon=\"").append(coord(marker.longitude)).append('"')
            marker.elevationMeters?.let { append(" elevationMeters=\"").append(coord(it)).append('"') }
            marker.speciesId?.let { append(" speciesId=\"").append(xmlAttr(it)).append('"') }
            append(" private=\"").append(marker.isPrivate).append('"')
            append(" photoOmitted=\"").append(marker.photoOmitted).append("\">\n")
            element("name", marker.name, 3)
            element("notes", marker.notes, 3)
            element("createdAt", stamp(marker.createdAtEpochMs), 3)
            append("    </marker>\n")
        }
        append("  </markers>\n")
        append("  <tracks>\n")
        backup.routes.forEach { route ->
            append("    <track id=\"").append(route.id).append('"')
            append(" distanceMeters=\"").append(num(route.distanceMeters, 2)).append('"')
            append(" pointCount=\"").append(route.points.size).append("\">\n")
            element("name", route.name, 3)
            element("startedAt", stamp(route.startedAtEpochMs), 3)
            route.endedAtEpochMs?.let { element("endedAt", stamp(it), 3) }
            append("    </track>\n")
        }
        append("  </tracks>\n")
        val medical = backup.medicalProfile
        if (backup.includesMedicalProfile && medical != null) {
            append("  <medical>\n")
            element("fullName", medical.fullName, 2)
            element("bloodType", medical.bloodType, 2)
            element("allergies", medical.allergies, 2)
            element("medicalInfo", medical.medicalInfo, 2)
            element("medication", medical.medication, 2)
            element("notes", medical.notes, 2)
            append("    <contacts>\n")
            medical.contacts.forEach { contact ->
                append("      <contact>\n")
                element("name", contact.name, 4)
                element("phone", contact.phone, 4)
                element("relationship", contact.relationship, 4)
                append("      </contact>\n")
            }
            append("    </contacts>\n")
            append("  </medical>\n")
        }
        append("</hunttrack>\n")
    }

    fun pdf(backup: FieldBackup, zones: List<OverlayZone> = zonesOf(backup)): ByteArray {
        val pages = PdfPages()
        pages.title("HunTracK field summary")
        pages.body("Exported ${pretty(backup.exportedAtEpochMs)}.")
        pages.body(
            "This sheet is for printing and keeping. To put the ground back into HunTracK, " +
                "import the backup zip. The JSON file in that zip is the full backup.",
        )
        pages.body(
            "Permissions and boundary details below are text. " +
                "Only the map shapes need GPX (a GPS unit or most mapping apps), " +
                "KML (Google Earth) or GeoJSON (GIS and web maps).",
        )
        pages.heading("Permissions (${backup.permissions.size})")
        if (backup.permissions.isEmpty()) {
            pages.body("None recorded.")
        } else {
            backup.permissions.forEach { pages.bullet(permissionLine(it)) }
        }
        pages.heading("Boundaries (${backup.boundaries.size})")
        if (backup.boundaries.isEmpty()) {
            pages.body("None recorded.")
        } else {
            backup.boundaries.forEach { area ->
                val name = area.name.ifBlank { "Boundary" }
                val type = zoneTypeLabel(area.zoneType)
                val notes = area.notes.replace('\n', ' ').trim()
                val line = buildString {
                    append(name)
                    append(" — ")
                    append(type)
                    append(" — ")
                    append(areaText(area.areaSqMeters))
                    append(". ")
                    append(area.points.size)
                    append(if (area.points.size == 1) " corner." else " corners.")
                    if (notes.isNotEmpty()) {
                        append(' ')
                        append(notes)
                    }
                }
                pages.bullet(line)
            }
        }
        pages.heading("Zones (${zones.size})")
        if (zones.isEmpty()) {
            pages.body(
                "None in this backup. Hunting, no-go and safe are listed with each boundary. " +
                    "Danger and sold zones drawn on a boundary will be listed here when the backup includes them.",
            )
        } else {
            zones.forEach { zone ->
                val title = zone.name.ifBlank { kindLabel(zone.kind) }
                val where = zone.boundaryName.ifBlank { "boundary ${zone.boundaryId}" }
                val notes = zone.notes.replace('\n', ' ').trim()
                val line = buildString {
                    append(title)
                    append(" — ")
                    append(kindLabel(zone.kind))
                    append(" — on ")
                    append(where)
                    append(". ")
                    append(zone.points.size)
                    append(if (zone.points.size == 1) " corner." else " corners.")
                    if (notes.isNotEmpty()) {
                        append(' ')
                        append(notes)
                    }
                }
                pages.bullet(line)
            }
        }
        pages.heading("Markers (${backup.markers.size})")
        if (backup.markers.isEmpty()) {
            pages.body("None recorded.")
        } else {
            backup.markers.forEach { marker ->
                val name = marker.name.ifBlank { "Marker" }
                val notes = marker.notes.replace('\n', ' ').trim()
                val line = buildString {
                    append(name)
                    append(" — ")
                    append(categoryLabel(marker.category))
                    append(" — ")
                    append(coord(marker.latitude))
                    append(", ")
                    append(coord(marker.longitude))
                    append('.')
                    if (notes.isNotEmpty()) {
                        append(' ')
                        append(notes)
                    }
                    if (marker.photoOmitted) append(" A photo on the phone was left out.")
                }
                pages.bullet(line)
            }
        }
        pages.heading("Tracks (${backup.routes.size})")
        if (backup.routes.isEmpty()) {
            pages.body("None recorded.")
        } else {
            backup.routes.forEach { route ->
                pages.bullet(
                    "${route.name.ifBlank { "Track" }} — ${distanceText(route.distanceMeters)} — " +
                        "${route.points.size} points.",
                )
            }
        }
        val medical = backup.medicalProfile
        if (backup.includesMedicalProfile && medical != null) {
            pages.heading("Medical profile")
            pages.body("Included because that choice was turned on. The PIN and biometric unlock are not in this file.")
            listOf(
                "Name" to medical.fullName,
                "Blood type" to medical.bloodType,
                "Allergies" to medical.allergies,
                "Medical info" to medical.medicalInfo,
                "Medication" to medical.medication,
                "Notes" to medical.notes,
            ).forEach { (label, value) ->
                if (value.isNotBlank()) pages.bullet("$label: ${value.replace('\n', ' ')}")
            }
            medical.contacts.forEach { contact ->
                val who = listOf(contact.name, contact.phone, contact.relationship)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                if (who.isNotEmpty()) pages.bullet("Contact: $who")
            }
        }
        val sketch = sketchOf(backup, zones)
        if (sketch != null) {
            pages.heading("Sketch of the ground")
            pages.body("Not a map. North is up. Boundaries are outlines, markers are dots. There is no road map or aerial photo.")
            pages.sketch(sketch)
        }
        return pages.toBytes()
    }

    private fun permissionsCsv(backup: FieldBackup): String = csv(
        listOf(
            "id", "label", "farmName", "permissionType", "species", "validFrom", "validTo",
            "ownerName", "ownerContact", "notes", "docType", "boundaryAreaId", "showOnMap",
            "attachmentOmitted",
        ),
        backup.permissions.map { permission ->
            listOf(
                permission.id.toString(),
                permission.label,
                permission.farmName,
                permission.permissionType,
                permission.species,
                day(permission.validFromEpochMs),
                day(permission.validToEpochMs),
                permission.ownerName,
                permission.ownerContact,
                permission.notes,
                permission.docType,
                permission.boundaryAreaId?.toString().orEmpty(),
                permission.showOnMap.toString(),
                permission.attachmentOmitted.toString(),
            )
        },
    )

    private fun boundariesCsv(backup: FieldBackup): String = csv(
        listOf(
            "id", "name", "zoneType", "areaSqMeters", "areaHectares", "areaAcres",
            "notes", "vertexCount", "outline",
        ),
        backup.boundaries.map { area ->
            listOf(
                area.id.toString(),
                area.name,
                area.zoneType,
                num(area.areaSqMeters, 2),
                hectares(area.areaSqMeters),
                acres(area.areaSqMeters),
                area.notes,
                area.points.size.toString(),
                outline(area.points),
            )
        },
    )

    private fun markersCsv(backup: FieldBackup): String = csv(
        listOf(
            "id", "category", "name", "notes", "latitude", "longitude", "elevationMeters",
            "createdAt", "speciesId", "isPrivate", "photoOmitted",
        ),
        backup.markers.map { marker ->
            listOf(
                marker.id.toString(),
                marker.category,
                marker.name,
                marker.notes,
                coord(marker.latitude),
                coord(marker.longitude),
                marker.elevationMeters?.let(::coord).orEmpty(),
                stamp(marker.createdAtEpochMs),
                marker.speciesId.orEmpty(),
                marker.isPrivate.toString(),
                marker.photoOmitted.toString(),
            )
        },
    )

    private fun zonesCsv(zones: List<OverlayZone>): String = csv(
        listOf(
            "id", "boundaryId", "boundaryName", "name", "kind", "notes", "vertexCount", "outline",
        ),
        zones.map { zone ->
            listOf(
                zone.id.toString(),
                zone.boundaryId.toString(),
                zone.boundaryName,
                zone.name,
                zone.kind,
                zone.notes,
                zone.points.size.toString(),
                outline(zone.points),
            )
        },
    )

    private fun tracksCsv(backup: FieldBackup): String = csv(
        listOf("id", "name", "startedAt", "endedAt", "distanceMeters", "pointCount"),
        backup.routes.map { route ->
            listOf(
                route.id.toString(),
                route.name,
                stamp(route.startedAtEpochMs),
                route.endedAtEpochMs?.let(::stamp).orEmpty(),
                num(route.distanceMeters, 2),
                route.points.size.toString(),
            )
        },
    )

    private fun medicalCsv(medical: BackupMedicalProfile): String = csv(
        listOf("fullName", "bloodType", "allergies", "medicalInfo", "medication", "notes", "contacts"),
        listOf(
            listOf(
                medical.fullName,
                medical.bloodType,
                medical.allergies,
                medical.medicalInfo,
                medical.medication,
                medical.notes,
                medical.contacts.joinToString("; ") { contact ->
                    listOf(contact.name, contact.phone, contact.relationship)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                },
            ),
        ),
    )

    private fun csv(header: List<String>, rows: List<List<String>>): String = buildString {
        append('\uFEFF')
        append(csvRow(header))
        append('\n')
        rows.forEach { row ->
            append(csvRow(row))
            append('\n')
        }
    }

    private fun csvRow(cells: List<String>): String = cells.joinToString(",") { csvCell(it) }

    private fun csvCell(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun StringBuilder.element(name: String, value: String, indent: Int) {
        repeat(indent) { append("  ") }
        append('<').append(name).append('>')
        append(xmlText(value))
        append("</").append(name).append(">\n")
    }

    private fun StringBuilder.appendOutline(points: List<BackupPoint>, indent: Int) {
        repeat(indent) { append("  ") }
        append("<outline>\n")
        points.sortedBy { it.orderIndex }.forEach { point ->
            repeat(indent + 1) { append("  ") }
            append("<point order=\"").append(point.orderIndex).append('"')
            append(" lat=\"").append(coord(point.latitude)).append('"')
            append(" lon=\"").append(coord(point.longitude)).append("\"/>\n")
        }
        repeat(indent) { append("  ") }
        append("</outline>\n")
    }

    private fun StringBuilder.appendZones(zones: List<OverlayZone>) {
        if (zones.isEmpty()) {
            append("  <zones/>\n")
            return
        }
        append("  <zones>\n")
        zones.forEach { zone ->
            append("    <zone id=\"").append(zone.id).append('"')
            append(" boundaryId=\"").append(zone.boundaryId).append('"')
            append(" kind=\"").append(xmlAttr(zone.kind)).append('"')
            append(" vertexCount=\"").append(zone.points.size).append("\">\n")
            element("boundaryName", zone.boundaryName, 3)
            element("name", zone.name, 3)
            element("notes", zone.notes, 3)
            appendOutline(zone.points, 3)
            append("    </zone>\n")
        }
        append("  </zones>\n")
    }

    private fun permissionLine(permission: BackupPermission): String = buildString {
        append(permission.label.ifBlank { permission.farmName.ifBlank { "Permission" } })
        val bits = listOf(permission.permissionType, permission.species).filter { it.isNotBlank() }
        if (bits.isNotEmpty()) {
            append(" — ")
            append(bits.joinToString(", "))
        }
        append('.')
        if (permission.ownerName.isNotBlank() || permission.ownerContact.isNotBlank()) {
            append(" Owner ")
            append(listOf(permission.ownerName, permission.ownerContact).filter { it.isNotBlank() }.joinToString(", "))
            append('.')
        }
        if (permission.validFromEpochMs > 0L || permission.validToEpochMs > 0L) {
            append(" Valid ")
            append(day(permission.validFromEpochMs))
            append(" to ")
            append(day(permission.validToEpochMs))
            append('.')
        }
        permission.boundaryAreaId?.let {
            append(" Linked to boundary ")
            append(it)
            append('.')
        }
        if (permission.notes.isNotBlank()) {
            append(' ')
            append(permission.notes.replace('\n', ' '))
        }
        if (permission.attachmentOmitted) append(" A photo or PDF on the phone was left out.")
    }

    private fun zoneTypeLabel(raw: String): String =
        runCatching { BoundaryZoneType.valueOf(raw).displayName() }.getOrDefault(raw.ifBlank { "Boundary" })

    private fun categoryLabel(raw: String): String =
        runCatching { MarkerCategory.valueOf(raw).label }.getOrDefault(raw.ifBlank { "Marker" })

    private fun kindLabel(kind: String): String =
        runCatching { BoundaryZoneKind.valueOf(kind).displayName() }.getOrDefault(kind.ifBlank { "Zone" })

    private fun areaText(sqMeters: Double): String {
        if (sqMeters <= 0.0) return "area not calculated"
        return String.format(Locale.UK, "%.2f ha (%.1f acres)", sqMeters / 10_000.0, sqMeters / ACRES)
    }

    private fun distanceText(meters: Double): String = when {
        meters >= 1000.0 -> String.format(Locale.UK, "%.2f km", meters / 1000.0)
        meters > 0.0 -> String.format(Locale.UK, "%.0f m", meters)
        else -> "distance not recorded"
    }

    private fun sketchOf(backup: FieldBackup, zones: List<OverlayZone>): Sketch? {
        val polygons = backup.boundaries.map { area ->
            area.points.sortedBy { it.orderIndex }.map { it.latitude to it.longitude }
        }.filter { it.size >= 2 }
        val zoneLines = zones.map { zone ->
            zone.points.sortedBy { it.orderIndex }.map { it.latitude to it.longitude }
        }.filter { it.size >= 2 }
        val tracks = backup.routes.map { route ->
            simplify(route.points.map { it.latitude to it.longitude })
        }.filter { it.size >= 2 }
        val markers = backup.markers.map { marker ->
            marker.name.ifBlank { "Marker" } to (marker.latitude to marker.longitude)
        }
        if (polygons.isEmpty() && zoneLines.isEmpty() && tracks.isEmpty() && markers.isEmpty()) return null
        return Sketch(polygons, zoneLines, tracks, markers)
    }

    private fun simplify(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.size <= 400) return points
        val step = (points.size / 400).coerceAtLeast(1)
        val kept = points.filterIndexed { index, _ -> index % step == 0 }
        val last = points.last()
        return if (kept.lastOrNull() == last) kept else kept + last
    }

    private fun outline(points: List<BackupPoint>): String =
        points.sortedBy { it.orderIndex }.joinToString("; ") { "${coord(it.latitude)} ${coord(it.longitude)}" }

    private fun hectares(sqMeters: Double): String = num(sqMeters / 10_000.0, 4)

    private fun acres(sqMeters: Double): String = num(sqMeters / ACRES, 4)

    private fun num(value: Double, places: Int): String {
        if (!value.isFinite()) return ""
        return String.format(Locale.US, "%.${places}f", value)
    }

    private fun coord(value: Double): String {
        if (!value.isFinite()) return ""
        return String.format(Locale.US, "%.6f", value)
    }

    private fun day(epochMs: Long): String = format(epochMs, DAY)

    private fun stamp(epochMs: Long): String = format(epochMs, STAMP)

    private fun pretty(epochMs: Long): String = format(epochMs, PRETTY)

    private fun format(epochMs: Long, formatter: DateTimeFormatter): String =
        runCatching { formatter.format(Instant.ofEpochMilli(epochMs)) }.getOrDefault("")

    private fun xmlText(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(char)
            }
        }
    }

    private fun xmlAttr(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '"' -> append("&quot;")
                else -> append(char)
            }
        }
    }

    private val DAY: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(ZoneOffset.UTC)
    private val STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(ZoneOffset.UTC)
    private val PRETTY: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.UK).withZone(ZoneOffset.UTC)

    private const val ACRES = 4046.8564224
}

/**
 * One overlay zone drawn on a boundary. Schema v1 backups have none.
 * `kind` is the stable code (`DANGER`, `SOLD`); the print summary spells it out.
 */
data class OverlayZone(
    val id: Long,
    val boundaryId: Long,
    val boundaryName: String,
    val name: String,
    val kind: String,
    val notes: String = "",
    val points: List<BackupPoint> = emptyList(),
)

private data class Sketch(
    val boundaries: List<List<Pair<Double, Double>>>,
    val zones: List<List<Pair<Double, Double>>>,
    val tracks: List<List<Pair<Double, Double>>>,
    val markers: List<Pair<String, Pair<Double, Double>>>,
)

/**
 * A small text PDF (Helvetica, A4) with an optional line sketch. No map tiles.
 */
private class PdfPages {
    private val streams = mutableListOf<StringBuilder>()
    private var y = 0.0

    fun title(text: String) = line(text, 16.0, 20.0, gapBefore = 0.0)

    fun heading(text: String) = line(text, 13.0, 16.0, gapBefore = 12.0)

    fun body(text: String) = line(text, 10.0, 13.0, gapBefore = 2.0)

    fun bullet(text: String) = line("- $text", 10.0, 13.0, gapBefore = 1.0)

    fun sketch(sketch: Sketch) {
        val boxHeight = 230.0
        ensure(boxHeight + 8.0)
        y -= boxHeight
        val left = MARGIN
        val bottom = y
        val width = PAGE_WIDTH - MARGIN * 2
        current().append(sketchCommands(sketch, left, bottom, width, boxHeight))
        y -= 8.0
    }

    fun toBytes(): ByteArray {
        if (streams.isEmpty()) newPage()
        val pageCount = streams.size
        val fontId = 3 + pageCount * 2
        val objects = mutableListOf<PdfObject>()
        val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 2} 0 R" }
        objects += PdfObject.dict("<< /Type /Catalog /Pages 2 0 R >>")
        objects += PdfObject.dict("<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
        streams.forEachIndexed { index, stream ->
            val contentId = 4 + index * 2
            objects += PdfObject.dict(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                    "/Contents $contentId 0 R /Resources << /Font << /F1 $fontId 0 R >> >> >>",
            )
            objects += PdfObject.stream(stream.toString().toByteArray(Charsets.US_ASCII))
        }
        objects += PdfObject.dict(
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        )
        return writePdf(objects)
    }

    private fun line(text: String, size: Double, leading: Double, gapBefore: Double) {
        val wrapped = wrap(text, size, PAGE_WIDTH - MARGIN * 2)
        wrapped.forEachIndexed { index, row ->
            val extra = if (index == 0) gapBefore else 0.0
            ensure(leading + extra)
            y -= extra + leading
            current().append(textOp(size, MARGIN, y, row))
        }
    }

    private fun ensure(height: Double) {
        if (streams.isEmpty() || y - height < MARGIN) newPage()
    }

    private fun newPage() {
        val stream = StringBuilder()
        streams += stream
        y = PAGE_HEIGHT - MARGIN
        stream.append(textOp(8.0, MARGIN, 28.0, "HunTracK  ·  print summary, not the import file"))
    }

    private fun current(): StringBuilder = streams.last()

    private fun textOp(size: Double, x: Double, y: Double, value: String): String =
        "BT /F1 ${num(size)} Tf 1 0 0 1 ${num(x)} ${num(y)} Tm (${pdfText(value)}) Tj ET\n"

    private fun sketchCommands(
        sketch: Sketch,
        left: Double,
        bottom: Double,
        width: Double,
        height: Double,
    ): String {
        val all = mutableListOf<Pair<Double, Double>>()
        (sketch.boundaries + sketch.zones + sketch.tracks).forEach { all += it }
        sketch.markers.forEach { all += it.second }
        if (all.isEmpty()) return ""
        val minLat = all.minOf { it.first }
        val maxLat = all.maxOf { it.first }
        val minLon = all.minOf { it.second }
        val maxLon = all.maxOf { it.second }
        val spanLat = (maxLat - minLat).coerceAtLeast(0.002)
        val spanLon = (maxLon - minLon).coerceAtLeast(0.002)
        val padX = width * 0.08
        val padY = height * 0.12
        fun mapX(lon: Double) = left + padX + ((lon - minLon) / spanLon) * (width - padX * 2)
        fun mapY(lat: Double) = bottom + padY + ((lat - minLat) / spanLat) * (height - padY * 2)
        val sb = StringBuilder()
        sb.append("q\n0.96 0.94 0.88 rg\n")
        sb.append("${num(left)} ${num(bottom)} ${num(width)} ${num(height)} re\nf\nQ\n")
        sketch.tracks.forEach { line ->
            sb.append(stroke(line.map { mapX(it.second) to mapY(it.first) }, close = false, "0.55 0.55 0.52 RG", 0.6))
        }
        sketch.boundaries.forEach { ring ->
            val close = ring.size >= 3
            sb.append(stroke(ring.map { mapX(it.second) to mapY(it.first) }, close, "0.55 0.42 0.28 RG", 1.3))
        }
        sketch.zones.forEach { ring ->
            val close = ring.size >= 3
            sb.append(stroke(ring.map { mapX(it.second) to mapY(it.first) }, close, "0.72 0.18 0.16 RG", 1.0))
        }
        sketch.markers.take(12).forEach { (name, point) ->
            val x = mapX(point.second)
            val y = mapY(point.first)
            sb.append("q\n0.15 0.22 0.16 rg\n${num(x - 1.6)} ${num(y - 1.6)} 3.2 3.2 re\nf\nQ\n")
            val labelX = if (x + 70 > left + width) x - 70 else x + 4
            sb.append(textOp(7.0, labelX, y + 3, name.take(24)))
        }
        sb.append("q\n0.45 0.48 0.42 RG\n0.6 w\n")
        sb.append("${num(left)} ${num(bottom)} ${num(width)} ${num(height)} re\nS\nQ\n")
        sb.append(textOp(8.0, left + 6, bottom + height - 12, "N"))
        return sb.toString()
    }

    private fun stroke(
        points: List<Pair<Double, Double>>,
        close: Boolean,
        color: String,
        width: Double,
    ): String {
        if (points.size < 2) return ""
        val sb = StringBuilder("q\n")
        sb.append(color).append('\n')
        sb.append(num(width)).append(" w\n")
        points.forEachIndexed { index, (x, y) ->
            sb.append(num(x)).append(' ').append(num(y)).append(if (index == 0) " m\n" else " l\n")
        }
        sb.append(if (close) "s\nQ\n" else "S\nQ\n")
        return sb.toString()
    }

    private fun wrap(text: String, fontSize: Double, maxWidth: Double): List<String> {
        val maxChars = (maxWidth / (fontSize * 0.52)).toInt().coerceAtLeast(20)
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { paragraph ->
            var rest = paragraph
            if (rest.isEmpty()) {
                lines += ""
                return@forEach
            }
            while (rest.isNotEmpty()) {
                if (rest.length <= maxChars) {
                    lines += rest
                    break
                }
                val window = rest.take(maxChars)
                val breakAt = window.lastIndexOf(' ').let { if (it < 8) maxChars else it }
                lines += rest.take(breakAt).trimEnd()
                rest = rest.drop(breakAt).trimStart()
            }
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun pdfText(value: String): String = buildString {
        value.forEach { char ->
            val code = when (char) {
                '\u2018', '\u2019', '\u2032' -> '\''.code
                '\u201C', '\u201D' -> '"'.code
                '\u2013', '\u2014' -> '-'.code
                '\u00A0', '\n', '\r', '\t' -> ' '.code
                else -> char.code
            }
            when {
                code == '('.code || code == ')'.code || code == '\\'.code -> {
                    append('\\')
                    append(code.toChar())
                }
                code in 32..126 -> append(code.toChar())
                code in 160..255 -> append('\\').append(code.toString(8).padStart(3, '0'))
                else -> append('?')
            }
        }
    }

    private fun num(value: Double): String = String.format(Locale.US, "%.2f", value)

    private sealed class PdfObject {
        class Dict(val body: String) : PdfObject()
        class Stream(val data: ByteArray) : PdfObject()

        companion object {
            fun dict(body: String) = Dict(body)
            fun stream(data: ByteArray) = Stream(data)
        }
    }

    private fun writePdf(objects: List<PdfObject>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))
        val offsets = IntArray(objects.size)
        objects.forEachIndexed { index, obj ->
            offsets[index] = out.size()
            out.write("${index + 1} 0 obj\n".toByteArray(Charsets.US_ASCII))
            when (obj) {
                is PdfObject.Dict -> {
                    out.write(obj.body.toByteArray(Charsets.US_ASCII))
                    out.write("\nendobj\n".toByteArray(Charsets.US_ASCII))
                }
                is PdfObject.Stream -> {
                    out.write("<< /Length ${obj.data.size} >>\nstream\n".toByteArray(Charsets.US_ASCII))
                    out.write(obj.data)
                    out.write("\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))
                }
            }
        }
        val xrefAt = out.size()
        val xref = StringBuilder()
        xref.append("xref\n0 ${objects.size + 1}\n")
        xref.append("0000000000 65535 f \n")
        offsets.forEach { offset ->
            xref.append(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        xref.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        xref.append("startxref\n")
        xref.append(xrefAt)
        xref.append("\n%%EOF\n")
        out.write(xref.toString().toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48.0
    }
}
