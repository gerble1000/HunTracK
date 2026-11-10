import Foundation

/// Spreadsheet, print and structured-text copies of a field backup.
///
/// The re-importable zip (JSON, GPX, KML, GeoJSON) is a separate file.
/// Overlay zones are read from each boundary. A version 1 backup has none.
enum TextFormats {
    static let csvZipName = "hunttrack-field-tables.zip"
    static let permissionsCsv = "permissions.csv"
    static let boundariesCsv = "boundaries.csv"
    static let markersCsv = "markers.csv"
    static let zonesCsv = "zones.csv"
    static let tracksCsv = "tracks.csv"
    static let medicalCsv = "medical.csv"
    static let xmlName = "hunttrack-field.xml"
    static let pdfName = "hunttrack-field-summary.pdf"

    static func zones(of backup: FieldBackupFile) -> [OverlayZone] {
        backup.boundaries.flatMap { boundary in
            boundary.zones.map { zone in
                OverlayZone(
                    id: zone.id,
                    boundaryId: boundary.id,
                    boundaryName: boundary.name,
                    name: zone.name,
                    kind: zone.kind,
                    notes: zone.notes,
                    points: zone.points
                )
            }
        }
    }

    static func csvFiles(_ backup: FieldBackupFile, zones explicit: [OverlayZone]? = nil) -> [String: Data] {
        let zoneRows = explicit ?? zones(of: backup)
        var files: [String: Data] = [
            permissionsCsv: csvData(permissionsCsvText(backup)),
            boundariesCsv: csvData(boundariesCsvText(backup)),
            markersCsv: csvData(markersCsvText(backup)),
            zonesCsv: csvData(zonesCsvText(zoneRows)),
            tracksCsv: csvData(tracksCsvText(backup))
        ]
        if backup.includesMedicalProfile, let medical = backup.medicalProfile {
            files[medicalCsv] = csvData(medicalCsvText(medical))
        }
        return files
    }

    static func csvZip(_ backup: FieldBackupFile, zones explicit: [OverlayZone]? = nil) -> Data {
        StoredZip.write(csvFiles(backup, zones: explicit))
    }

    static func xml(_ backup: FieldBackupFile, zones explicit: [OverlayZone]? = nil) -> String {
        let zoneRows = explicit ?? zones(of: backup)
        var lines: [String] = []
        lines.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        lines.append("<hunttrack format=\"hunttrack-field-text\" backupVersion=\"\(backup.version)\" exportedAt=\"\(xmlAttr(stamp(backup.exportedAtEpochMs)))\" includesMedicalProfile=\"\(backup.includesMedicalProfile)\">")
        lines.append("  <note>\(xmlText(backup.note))</note>")
        lines.append("  <permissions>")
        for permission in backup.permissions {
            var open = "    <permission id=\"\(permission.id)\" docType=\"\(xmlAttr(permission.docType))\" showOnMap=\"\(permission.showOnMap)\""
            if let boundaryId = permission.boundaryAreaId {
                open += " boundaryAreaId=\"\(boundaryId)\""
            }
            open += " attachmentOmitted=\"\(permission.attachmentOmitted)\">"
            lines.append(open)
            lines.append(element("label", permission.label, 3))
            lines.append(element("farmName", permission.farmName, 3))
            lines.append(element("permissionType", permission.permissionType, 3))
            lines.append(element("species", permission.species, 3))
            lines.append(element("validFrom", day(permission.validFromEpochMs), 3))
            lines.append(element("validTo", day(permission.validToEpochMs), 3))
            lines.append(element("ownerName", permission.ownerName, 3))
            lines.append(element("ownerContact", permission.ownerContact, 3))
            lines.append(element("notes", permission.notes, 3))
            lines.append("    </permission>")
        }
        lines.append("  </permissions>")
        lines.append("  <boundaries>")
        for area in backup.boundaries {
            lines.append("    <boundary id=\"\(area.id)\" zoneType=\"\(xmlAttr(area.zoneType))\" areaSqMeters=\"\(num(area.areaSqMeters, 2))\" areaHectares=\"\(hectares(area.areaSqMeters))\">")
            lines.append(element("name", area.name, 3))
            lines.append(element("notes", area.notes, 3))
            lines.append(contentsOf: outlineElements(area.points, 3))
            lines.append("    </boundary>")
        }
        lines.append("  </boundaries>")
        lines.append(contentsOf: zoneElements(zoneRows))
        lines.append("  <markers>")
        for marker in backup.markers {
            var open = "    <marker id=\"\(marker.id)\" category=\"\(xmlAttr(marker.category))\" lat=\"\(coord(marker.latitude))\" lon=\"\(coord(marker.longitude))\""
            if let elevation = marker.elevationMeters {
                open += " elevationMeters=\"\(coord(elevation))\""
            }
            if let species = marker.speciesId {
                open += " speciesId=\"\(xmlAttr(species))\""
            }
            open += " private=\"\(marker.isPrivate)\" photoOmitted=\"\(marker.photoOmitted)\">"
            lines.append(open)
            lines.append(element("name", marker.name, 3))
            lines.append(element("notes", marker.notes, 3))
            lines.append(element("createdAt", stamp(marker.createdAtEpochMs), 3))
            lines.append("    </marker>")
        }
        lines.append("  </markers>")
        lines.append("  <tracks>")
        for route in backup.routes {
            lines.append("    <track id=\"\(route.id)\" distanceMeters=\"\(num(route.distanceMeters, 2))\" pointCount=\"\(route.points.count)\">")
            lines.append(element("name", route.name, 3))
            lines.append(element("startedAt", stamp(route.startedAtEpochMs), 3))
            if let ended = route.endedAtEpochMs {
                lines.append(element("endedAt", stamp(ended), 3))
            }
            lines.append("    </track>")
        }
        lines.append("  </tracks>")
        if backup.includesMedicalProfile, let medical = backup.medicalProfile {
            lines.append("  <medical>")
            lines.append(element("fullName", medical.fullName, 2))
            lines.append(element("bloodType", medical.bloodType, 2))
            lines.append(element("allergies", medical.allergies, 2))
            lines.append(element("medicalInfo", medical.medicalInfo, 2))
            lines.append(element("medication", medical.medication, 2))
            lines.append(element("notes", medical.notes, 2))
            lines.append("    <contacts>")
            for contact in medical.contacts {
                lines.append("      <contact>")
                lines.append(element("name", contact.name, 4))
                lines.append(element("phone", contact.phone, 4))
                lines.append(element("relationship", contact.relationship, 4))
                lines.append("      </contact>")
            }
            lines.append("    </contacts>")
            lines.append("  </medical>")
        }
        lines.append("</hunttrack>")
        return lines.joined(separator: "\n") + "\n"
    }

    static func pdf(_ backup: FieldBackupFile, zones explicit: [OverlayZone]? = nil) -> Data {
        let zoneRows = explicit ?? zones(of: backup)
        let pages = PdfPages()
        pages.title("HunTracK field summary")
        pages.body("Exported \(pretty(backup.exportedAtEpochMs)).")
        pages.body("This sheet is for printing and keeping. To put the ground back into HunTracK, import the backup zip. The JSON file in that zip is the full backup.")
        pages.body("Permissions and boundary details below are text. Only the map shapes need GPX (a GPS unit or most mapping apps), KML (Google Earth) or GeoJSON (GIS and web maps).")
        pages.heading("Permissions (\(backup.permissions.count))")
        if backup.permissions.isEmpty {
            pages.body("None recorded.")
        } else {
            backup.permissions.forEach { pages.bullet(permissionLine($0)) }
        }
        pages.heading("Boundaries (\(backup.boundaries.count))")
        if backup.boundaries.isEmpty {
            pages.body("None recorded.")
        } else {
            for area in backup.boundaries {
                let name = area.name.isEmpty ? "Boundary" : area.name
                let notes = area.notes.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
                var line = "\(name) — \(zoneTypeLabel(area.zoneType)) — \(areaText(area.areaSqMeters)). \(area.points.count) \(area.points.count == 1 ? "corner." : "corners.")"
                if !notes.isEmpty { line += " \(notes)" }
                pages.bullet(line)
            }
        }
        pages.heading("Zones (\(zoneRows.count))")
        if zoneRows.isEmpty {
            pages.body("None in this backup. Hunting, no-go and safe are listed with each boundary. Danger and sold zones drawn on a boundary will be listed here when the backup includes them.")
        } else {
            for zone in zoneRows {
                let title = zone.name.isEmpty ? kindLabel(zone.kind) : zone.name
                let where = zone.boundaryName.isEmpty ? "boundary \(zone.boundaryId)" : zone.boundaryName
                let notes = zone.notes.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
                var line = "\(title) — \(kindLabel(zone.kind)) — on \(where). \(zone.points.count) \(zone.points.count == 1 ? "corner." : "corners.")"
                if !notes.isEmpty { line += " \(notes)" }
                pages.bullet(line)
            }
        }
        pages.heading("Markers (\(backup.markers.count))")
        if backup.markers.isEmpty {
            pages.body("None recorded.")
        } else {
            for marker in backup.markers {
                let name = marker.name.isEmpty ? "Marker" : marker.name
                let notes = marker.notes.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
                var line = "\(name) — \(categoryLabel(marker.category)) — \(coord(marker.latitude)), \(coord(marker.longitude))."
                if !notes.isEmpty { line += " \(notes)" }
                if marker.photoOmitted { line += " A photo on the phone was left out." }
                pages.bullet(line)
            }
        }
        pages.heading("Tracks (\(backup.routes.count))")
        if backup.routes.isEmpty {
            pages.body("None recorded.")
        } else {
            for route in backup.routes {
                let name = route.name.isEmpty ? "Track" : route.name
                pages.bullet("\(name) — \(distanceText(route.distanceMeters)) — \(route.points.count) points.")
            }
        }
        if backup.includesMedicalProfile, let medical = backup.medicalProfile {
            pages.heading("Medical profile")
            pages.body("Included because that choice was turned on. The PIN and biometric unlock are not in this file.")
            let fields = [
                ("Name", medical.fullName),
                ("Blood type", medical.bloodType),
                ("Allergies", medical.allergies),
                ("Medical info", medical.medicalInfo),
                ("Medication", medical.medication),
                ("Notes", medical.notes)
            ]
            for (label, value) in fields where !value.trimmingCharacters(in: .whitespaces).isEmpty {
                pages.bullet("\(label): \(value.replacingOccurrences(of: "\n", with: " "))")
            }
            for contact in medical.contacts {
                let who = [contact.name, contact.phone, contact.relationship].filter { !$0.isEmpty }.joined(separator: ", ")
                if !who.isEmpty { pages.bullet("Contact: \(who)") }
            }
        }
        if let sketch = sketchOf(backup, zoneRows) {
            pages.heading("Sketch of the ground")
            pages.body("Not a map. North is up. Boundaries are outlines, markers are dots. There is no road map or aerial photo.")
            pages.sketch(sketch)
        }
        return pages.toData()
    }

    private static func permissionsCsvText(_ backup: FieldBackupFile) -> String {
        csv(
            ["id", "label", "farmName", "permissionType", "species", "validFrom", "validTo", "ownerName", "ownerContact", "notes", "docType", "boundaryAreaId", "showOnMap", "attachmentOmitted"],
            backup.permissions.map { permission in
                [
                    "\(permission.id)", permission.label, permission.farmName, permission.permissionType, permission.species,
                    day(permission.validFromEpochMs), day(permission.validToEpochMs), permission.ownerName, permission.ownerContact,
                    permission.notes, permission.docType, permission.boundaryAreaId.map(String.init) ?? "",
                    "\(permission.showOnMap)", "\(permission.attachmentOmitted)"
                ]
            }
        )
    }

    private static func boundariesCsvText(_ backup: FieldBackupFile) -> String {
        csv(
            ["id", "name", "zoneType", "areaSqMeters", "areaHectares", "areaAcres", "notes", "vertexCount", "outline"],
            backup.boundaries.map { area in
                [
                    "\(area.id)", area.name, area.zoneType, num(area.areaSqMeters, 2), hectares(area.areaSqMeters),
                    acres(area.areaSqMeters), area.notes, "\(area.points.count)", outline(area.points)
                ]
            }
        )
    }

    private static func markersCsvText(_ backup: FieldBackupFile) -> String {
        csv(
            ["id", "category", "name", "notes", "latitude", "longitude", "elevationMeters", "createdAt", "speciesId", "isPrivate", "photoOmitted"],
            backup.markers.map { marker in
                [
                    "\(marker.id)", marker.category, marker.name, marker.notes, coord(marker.latitude), coord(marker.longitude),
                    marker.elevationMeters.map(coord) ?? "", stamp(marker.createdAtEpochMs), marker.speciesId ?? "",
                    "\(marker.isPrivate)", "\(marker.photoOmitted)"
                ]
            }
        )
    }

    private static func zonesCsvText(_ zones: [OverlayZone]) -> String {
        csv(
            ["id", "boundaryId", "boundaryName", "name", "kind", "notes", "vertexCount", "outline"],
            zones.map { zone in
                [
                    "\(zone.id)", "\(zone.boundaryId)", zone.boundaryName, zone.name, zone.kind, zone.notes,
                    "\(zone.points.count)", outline(zone.points)
                ]
            }
        )
    }

    private static func tracksCsvText(_ backup: FieldBackupFile) -> String {
        csv(
            ["id", "name", "startedAt", "endedAt", "distanceMeters", "pointCount"],
            backup.routes.map { route in
                [
                    "\(route.id)", route.name, stamp(route.startedAtEpochMs),
                    route.endedAtEpochMs.map(stamp) ?? "", num(route.distanceMeters, 2), "\(route.points.count)"
                ]
            }
        )
    }

    private static func medicalCsvText(_ medical: BackupMedicalProfile) -> String {
        let contacts = medical.contacts.map { contact in
            [contact.name, contact.phone, contact.relationship].filter { !$0.isEmpty }.joined(separator: " ")
        }.joined(separator: "; ")
        return csv(
            ["fullName", "bloodType", "allergies", "medicalInfo", "medication", "notes", "contacts"],
            [[medical.fullName, medical.bloodType, medical.allergies, medical.medicalInfo, medical.medication, medical.notes, contacts]]
        )
    }

    private static func csv(_ header: [String], _ rows: [[String]]) -> String {
        ([csvRow(header)] + rows.map(csvRow)).joined(separator: "\n") + "\n"
    }

    private static func csvData(_ text: String) -> Data {
        var data = Data([0xEF, 0xBB, 0xBF])
        data.append(Data(text.utf8))
        return data
    }

    private static func csvRow(_ cells: [String]) -> String {
        cells.map(csvCell).joined(separator: ",")
    }

    private static func csvCell(_ value: String) -> String {
        let needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        let escaped = value.replacingOccurrences(of: "\"", with: "\"\"")
        return needsQuotes ? "\"\(escaped)\"" : escaped
    }

    private static func element(_ name: String, _ value: String, _ indent: Int) -> String {
        String(repeating: "  ", count: indent) + "<\(name)>\(xmlText(value))</\(name)>"
    }

    private static func outlineElements(_ points: [BackupPoint], _ indent: Int) -> [String] {
        let pad = String(repeating: "  ", count: indent)
        var lines = ["\(pad)<outline>"]
        for point in points.sorted(by: { $0.orderIndex < $1.orderIndex }) {
            lines.append("\(pad)  <point order=\"\(point.orderIndex)\" lat=\"\(coord(point.latitude))\" lon=\"\(coord(point.longitude))\"/>")
        }
        lines.append("\(pad)</outline>")
        return lines
    }

    private static func zoneElements(_ zones: [OverlayZone]) -> [String] {
        if zones.isEmpty { return ["  <zones/>"] }
        var lines = ["  <zones>"]
        for zone in zones {
            lines.append("    <zone id=\"\(zone.id)\" boundaryId=\"\(zone.boundaryId)\" kind=\"\(xmlAttr(zone.kind))\" vertexCount=\"\(zone.points.count)\">")
            lines.append(element("boundaryName", zone.boundaryName, 3))
            lines.append(element("name", zone.name, 3))
            lines.append(element("notes", zone.notes, 3))
            lines.append(contentsOf: outlineElements(zone.points, 3))
            lines.append("    </zone>")
        }
        lines.append("  </zones>")
        return lines
    }

    private static func permissionLine(_ permission: BackupPermission) -> String {
        var text = permission.label.isEmpty ? (permission.farmName.isEmpty ? "Permission" : permission.farmName) : permission.label
        let bits = [permission.permissionType, permission.species].filter { !$0.isEmpty }
        if !bits.isEmpty { text += " — " + bits.joined(separator: ", ") }
        text += "."
        let owner = [permission.ownerName, permission.ownerContact].filter { !$0.isEmpty }
        if !owner.isEmpty { text += " Owner " + owner.joined(separator: ", ") + "." }
        if permission.validFromEpochMs > 0 || permission.validToEpochMs > 0 {
            text += " Valid \(day(permission.validFromEpochMs)) to \(day(permission.validToEpochMs))."
        }
        if let boundaryId = permission.boundaryAreaId {
            text += " Linked to boundary \(boundaryId)."
        }
        if !permission.notes.isEmpty {
            text += " " + permission.notes.replacingOccurrences(of: "\n", with: " ")
        }
        if permission.attachmentOmitted { text += " A photo or PDF on the phone was left out." }
        return text
    }

    private static func zoneTypeLabel(_ raw: String) -> String {
        BoundaryZoneType(rawValue: raw)?.displayName ?? (raw.isEmpty ? "Boundary" : raw)
    }

    private static func categoryLabel(_ raw: String) -> String {
        MarkerCategory(rawValue: raw)?.label ?? (raw.isEmpty ? "Marker" : raw)
    }

    private static func kindLabel(_ kind: String) -> String {
        BoundaryZoneKind(rawValue: kind)?.displayName ?? (kind.isEmpty ? "Zone" : kind)
    }

    private static func areaText(_ sqMeters: Double) -> String {
        if sqMeters <= 0 { return "area not calculated" }
        return String(format: "%.2f ha (%.1f acres)", sqMeters / 10_000.0, sqMeters / acresFactor)
    }

    private static func distanceText(_ meters: Double) -> String {
        if meters >= 1000 { return String(format: "%.2f km", meters / 1000.0) }
        if meters > 0 { return String(format: "%.0f m", meters) }
        return "distance not recorded"
    }

    private static func sketchOf(_ backup: FieldBackupFile, _ zones: [OverlayZone]) -> Sketch? {
        let polygons = backup.boundaries.map { area in
            area.points.sorted { $0.orderIndex < $1.orderIndex }.map { ($0.latitude, $0.longitude) }
        }.filter { $0.count >= 2 }
        let zoneLines = zones.map { zone in
            zone.points.sorted { $0.orderIndex < $1.orderIndex }.map { ($0.latitude, $0.longitude) }
        }.filter { $0.count >= 2 }
        let tracks = backup.routes.map { route in
            simplify(route.points.map { ($0.latitude, $0.longitude) })
        }.filter { $0.count >= 2 }
        let markers = backup.markers.map { marker in
            (marker.name.isEmpty ? "Marker" : marker.name, (marker.latitude, marker.longitude))
        }
        if polygons.isEmpty && zoneLines.isEmpty && tracks.isEmpty && markers.isEmpty { return nil }
        return Sketch(boundaries: polygons, zones: zoneLines, tracks: tracks, markers: markers)
    }

    private static func simplify(_ points: [(Double, Double)]) -> [(Double, Double)] {
        if points.count <= 400 { return points }
        let step = max(1, points.count / 400)
        var kept = points.enumerated().compactMap { index, point in index % step == 0 ? point : nil }
        if let last = points.last, kept.last?.0 != last.0 || kept.last?.1 != last.1 {
            kept.append(last)
        }
        return kept
    }

    private static func outline(_ points: [BackupPoint]) -> String {
        points.sorted { $0.orderIndex < $1.orderIndex }
            .map { "\(coord($0.latitude)) \(coord($0.longitude))" }
            .joined(separator: "; ")
    }

    private static func hectares(_ sqMeters: Double) -> String { num(sqMeters / 10_000.0, 4) }
    private static func acres(_ sqMeters: Double) -> String { num(sqMeters / acresFactor, 4) }

    private static func num(_ value: Double, _ places: Int) -> String {
        guard value.isFinite else { return "" }
        return String(format: "%.\(places)f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    private static func coord(_ value: Double) -> String {
        guard value.isFinite else { return "" }
        return String(format: "%.6f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    private static func day(_ epochMs: Int64) -> String { format(epochMs, dayFormatter) }
    private static func stamp(_ epochMs: Int64) -> String { format(epochMs, stampFormatter) }
    private static func pretty(_ epochMs: Int64) -> String { format(epochMs, prettyFormatter) }

    private static func format(_ epochMs: Int64, _ formatter: DateFormatter) -> String {
        formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000.0))
    }

    private static func xmlText(_ value: String) -> String {
        value.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
    }

    private static func xmlAttr(_ value: String) -> String {
        xmlText(value).replacingOccurrences(of: "\"", with: "&quot;")
    }

    private static let acresFactor = 4046.8564224
    private static let dayFormatter = utcFormatter("yyyy-MM-dd")
    private static let stampFormatter = utcFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'")
    private static let prettyFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "d MMM yyyy, HH:mm 'UTC'"
        return formatter
    }()

    private static func utcFormatter(_ pattern: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = pattern
        return formatter
    }
}

struct OverlayZone {
    var id: Int64
    var boundaryId: Int64
    var boundaryName: String
    var name: String
    var kind: String
    var notes: String
    var points: [BackupPoint]
}

private struct Sketch {
    var boundaries: [[(Double, Double)]]
    var zones: [[(Double, Double)]]
    var tracks: [[(Double, Double)]]
    var markers: [(String, (Double, Double))]
}

/// A small text PDF (Helvetica, A4) with an optional line sketch. No map tiles.
private final class PdfPages {
    private var streams: [String] = []
    private var y = 0.0
    private let pageWidth = 595.0
    private let pageHeight = 842.0
    private let margin = 48.0

    func title(_ text: String) { line(text, size: 16, leading: 20, gapBefore: 0) }
    func heading(_ text: String) { line(text, size: 13, leading: 16, gapBefore: 12) }
    func body(_ text: String) { line(text, size: 10, leading: 13, gapBefore: 2) }
    func bullet(_ text: String) { line("- \(text)", size: 10, leading: 13, gapBefore: 1) }

    func sketch(_ sketch: Sketch) {
        let boxHeight = 230.0
        ensure(boxHeight + 8)
        y -= boxHeight
        let left = margin
        let bottom = y
        let width = pageWidth - margin * 2
        streams[streams.count - 1] += sketchCommands(sketch, left: left, bottom: bottom, width: width, height: boxHeight)
        y -= 8
    }

    func toData() -> Data {
        if streams.isEmpty { newPage() }
        let pageCount = streams.count
        let fontId = 3 + pageCount * 2
        var objects: [PdfObject] = []
        let kids = (0..<pageCount).map { "\(3 + $0 * 2) 0 R" }.joined(separator: " ")
        objects.append(.dict("<< /Type /Catalog /Pages 2 0 R >>"))
        objects.append(.dict("<< /Type /Pages /Kids [\(kids)] /Count \(pageCount) >>"))
        for (index, stream) in streams.enumerated() {
            let contentId = 4 + index * 2
            objects.append(.dict("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 \(Int(pageWidth)) \(Int(pageHeight))] /Contents \(contentId) 0 R /Resources << /Font << /F1 \(fontId) 0 R >> >> >>"))
            objects.append(.stream(Data(stream.utf8)))
        }
        objects.append(.dict("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"))
        return writePdf(objects)
    }

    private func line(_ text: String, size: Double, leading: Double, gapBefore: Double) {
        let wrapped = wrap(text, fontSize: size, maxWidth: pageWidth - margin * 2)
        for (index, row) in wrapped.enumerated() {
            let extra = index == 0 ? gapBefore : 0
            ensure(leading + extra)
            y -= extra + leading
            streams[streams.count - 1] += textOp(size: size, x: margin, y: y, value: row)
        }
    }

    private func ensure(_ height: Double) {
        if streams.isEmpty || y - height < margin { newPage() }
    }

    private func newPage() {
        streams.append("")
        y = pageHeight - margin
        streams[streams.count - 1] += textOp(size: 8, x: margin, y: 28, value: "HunTracK  ·  print summary, not the import file")
    }

    private func textOp(size: Double, x: Double, y: Double, value: String) -> String {
        "BT /F1 \(num(size)) Tf 1 0 0 1 \(num(x)) \(num(y)) Tm (\(pdfText(value))) Tj ET\n"
    }

    private func sketchCommands(_ sketch: Sketch, left: Double, bottom: Double, width: Double, height: Double) -> String {
        var all: [(Double, Double)] = []
        (sketch.boundaries + sketch.zones + sketch.tracks).forEach { all.append(contentsOf: $0) }
        sketch.markers.forEach { all.append($0.1) }
        if all.isEmpty { return "" }
        let minLat = all.map(\.0).min() ?? 0
        let maxLat = all.map(\.0).max() ?? 0
        let minLon = all.map(\.1).min() ?? 0
        let maxLon = all.map(\.1).max() ?? 0
        let spanLat = max(maxLat - minLat, 0.002)
        let spanLon = max(maxLon - minLon, 0.002)
        let padX = width * 0.08
        let padY = height * 0.12
        func mapX(_ lon: Double) -> Double { left + padX + ((lon - minLon) / spanLon) * (width - padX * 2) }
        func mapY(_ lat: Double) -> Double { bottom + padY + ((lat - minLat) / spanLat) * (height - padY * 2) }
        var text = "q\n0.96 0.94 0.88 rg\n\(num(left)) \(num(bottom)) \(num(width)) \(num(height)) re\nf\nQ\n"
        for line in sketch.tracks {
            text += stroke(line.map { (mapX($0.1), mapY($0.0)) }, close: false, color: "0.55 0.55 0.52 RG", width: 0.6)
        }
        for ring in sketch.boundaries {
            text += stroke(ring.map { (mapX($0.1), mapY($0.0)) }, close: ring.count >= 3, color: "0.55 0.42 0.28 RG", width: 1.3)
        }
        for ring in sketch.zones {
            text += stroke(ring.map { (mapX($0.1), mapY($0.0)) }, close: ring.count >= 3, color: "0.72 0.18 0.16 RG", width: 1.0)
        }
        for (name, point) in sketch.markers.prefix(12) {
            let x = mapX(point.1)
            let py = mapY(point.0)
            text += "q\n0.15 0.22 0.16 rg\n\(num(x - 1.6)) \(num(py - 1.6)) 3.2 3.2 re\nf\nQ\n"
            let labelX = x + 70 > left + width ? x - 70 : x + 4
            text += textOp(size: 7, x: labelX, y: py + 3, value: String(name.prefix(24)))
        }
        text += "q\n0.45 0.48 0.42 RG\n0.6 w\n\(num(left)) \(num(bottom)) \(num(width)) \(num(height)) re\nS\nQ\n"
        text += textOp(size: 8, x: left + 6, y: bottom + height - 12, value: "N")
        return text
    }

    private func stroke(_ points: [(Double, Double)], close: Bool, color: String, width: Double) -> String {
        if points.count < 2 { return "" }
        var text = "q\n\(color)\n\(num(width)) w\n"
        for (index, point) in points.enumerated() {
            text += "\(num(point.0)) \(num(point.1)) \(index == 0 ? "m" : "l")\n"
        }
        text += close ? "s\nQ\n" : "S\nQ\n"
        return text
    }

    private func wrap(_ text: String, fontSize: Double, maxWidth: Double) -> [String] {
        let maxChars = max(20, Int(maxWidth / (fontSize * 0.52)))
        var lines: [String] = []
        for paragraph in text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").split(separator: "\n", omittingEmptySubsequences: false) {
            var rest = String(paragraph)
            if rest.isEmpty {
                lines.append("")
                continue
            }
            while !rest.isEmpty {
                if rest.count <= maxChars {
                    lines.append(rest)
                    break
                }
                let window = String(rest.prefix(maxChars))
                let space = window.lastIndex(of: " ")
                let breakAt: String.Index
                if let space, window.distance(from: window.startIndex, to: space) >= 8 {
                    breakAt = rest.index(rest.startIndex, offsetBy: window.distance(from: window.startIndex, to: space))
                } else {
                    breakAt = rest.index(rest.startIndex, offsetBy: maxChars)
                }
                lines.append(String(rest[..<breakAt]).trimmingCharacters(in: .whitespaces))
                rest = String(rest[breakAt...]).trimmingCharacters(in: .whitespaces)
            }
        }
        return lines.isEmpty ? [""] : lines
    }

    private func pdfText(_ value: String) -> String {
        var out = ""
        for char in value {
            let code: UInt32
            switch char {
            case "\u{2018}", "\u{2019}", "\u{2032}": code = 39
            case "\u{201C}", "\u{201D}": code = 34
            case "\u{2013}", "\u{2014}": code = 45
            case "\u{00A0}", "\n", "\r", "\t": code = 32
            default: code = char.unicodeScalars.first?.value ?? 63
            }
            if code == 40 || code == 41 || code == 92 {
                out.append("\\")
                out.append(Character(UnicodeScalar(code)!))
            } else if code >= 32 && code <= 126 {
                out.append(Character(UnicodeScalar(code)!))
            } else if code >= 160 && code <= 255 {
                out.append("\\")
                out.append(String(format: "%03o", code))
            } else {
                out.append("?")
            }
        }
        return out
    }

    private func num(_ value: Double) -> String {
        String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    private enum PdfObject {
        case dict(String)
        case stream(Data)
    }

    private func writePdf(_ objects: [PdfObject]) -> Data {
        var out = Data([0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25, 0xE2, 0xE3, 0xCF, 0xD3, 0x0A])
        var offsets: [Int] = []
        for (index, object) in objects.enumerated() {
            offsets.append(out.count)
            out.append(Data("\(index + 1) 0 obj\n".utf8))
            switch object {
            case .dict(let body):
                out.append(Data(body.utf8))
                out.append(Data("\nendobj\n".utf8))
            case .stream(let data):
                out.append(Data("<< /Length \(data.count) >>\nstream\n".utf8))
                out.append(data)
                out.append(Data("\nendstream\nendobj\n".utf8))
            }
        }
        let xrefAt = out.count
        var xref = "xref\n0 \(objects.count + 1)\n0000000000 65535 f \n"
        for offset in offsets {
            xref += String(format: "%010d 00000 n \n", offset)
        }
        xref += "trailer\n<< /Size \(objects.count + 1) /Root 1 0 R >>\nstartxref\n\(xrefAt)\n%%EOF\n"
        out.append(Data(xref.utf8))
        return out
    }
}
