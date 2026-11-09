import Foundation

enum FieldBackupFormat {
    static let format = "hunttrack-field-backup"
    static let version = 1
    static let jsonName = "hunttrack-backup.json"
    static let gpxName = "hunttrack-markers-tracks.gpx"
    static let kmlName = "hunttrack-boundaries.kml"
    static let geoJsonName = "hunttrack-field.geojson"
    static let zipName = "hunttrack-field-backup.zip"
    static let omissionNote =
        "Photos, PDFs and other files attached on the device are not included. " +
        "The medical profile is included only when that choice was turned on. " +
        "PIN and biometric unlock data are never exported."
}

struct FieldBackupFile: Codable {
    var format: String = FieldBackupFormat.format
    var version: Int = FieldBackupFormat.version
    var exportedAtEpochMs: Int64
    var includesMedicalProfile: Bool
    var localFilesOmitted: Bool = true
    var note: String = FieldBackupFormat.omissionNote
    var permissions: [BackupPermission]
    var boundaries: [BackupBoundary]
    var markers: [BackupMarker]
    var routes: [BackupRoute]
    var medicalProfile: BackupMedicalProfile?
}

struct BackupPermission: Codable {
    var id: Int64
    var label: String
    var farmName: String
    var permissionType: String
    var species: String
    var validFromEpochMs: Int64
    var validToEpochMs: Int64
    var ownerName: String
    var ownerContact: String
    var notes: String
    var docType: String
    var boundaryAreaId: Int64?
    var showOnMap: Bool
    var attachmentOmitted: Bool
}

struct BackupBoundary: Codable {
    var id: Int64
    var name: String
    var zoneType: String
    var notes: String
    var areaSqMeters: Double
    var points: [BackupPoint]
}

struct BackupPoint: Codable {
    var latitude: Double
    var longitude: Double
    var orderIndex: Int
}

struct BackupMarker: Codable {
    var id: Int64
    var category: String
    var name: String
    var notes: String
    var latitude: Double
    var longitude: Double
    var elevationMeters: Double?
    var createdAtEpochMs: Int64
    var speciesId: String?
    var isPrivate: Bool
    var photoOmitted: Bool
}

struct BackupRoute: Codable {
    var id: Int64
    var name: String
    var startedAtEpochMs: Int64
    var endedAtEpochMs: Int64?
    var distanceMeters: Double
    var points: [BackupTrackPoint]
}

struct BackupTrackPoint: Codable {
    var latitude: Double
    var longitude: Double
    var elevationMeters: Double?
    var speedMps: Float?
    var recordedAtEpochMs: Int64
}

struct BackupMedicalProfile: Codable {
    var fullName: String
    var bloodType: String
    var allergies: String
    var medicalInfo: String
    var medication: String
    var notes: String
    var contacts: [BackupEmergencyContact]
}

struct BackupEmergencyContact: Codable {
    var name: String
    var phone: String
    var relationship: String
}

enum FieldBackupCodec {
    static func build(from db: DatabaseService, includeMedical: Bool) -> FieldBackupFile {
        let pointsByRoute = Dictionary(grouping: db.trackPoints, by: { $0.routeId })
        let medical = includeMedical ? db.emergency : nil
        return FieldBackupFile(
            exportedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            includesMedicalProfile: includeMedical,
            permissions: db.permissions.map { permission in
                BackupPermission(
                    id: permission.id,
                    label: permission.label,
                    farmName: permission.farmName,
                    permissionType: permission.permissionType,
                    species: permission.species,
                    validFromEpochMs: permission.validFromEpochMs,
                    validToEpochMs: permission.validToEpochMs,
                    ownerName: permission.ownerName,
                    ownerContact: permission.ownerContact,
                    notes: permission.notes,
                    docType: permission.docType.rawValue,
                    boundaryAreaId: permission.boundaryAreaId,
                    showOnMap: permission.showOnMap,
                    attachmentOmitted: !(permission.attachmentUri ?? "").isEmpty
                )
            },
            boundaries: db.boundaries.map { area in
                BackupBoundary(
                    id: area.id,
                    name: area.name,
                    zoneType: area.zoneType.rawValue,
                    notes: area.notes,
                    areaSqMeters: area.areaSqMeters,
                    points: area.points.sorted { $0.orderIndex < $1.orderIndex }.map {
                        BackupPoint(latitude: $0.latitude, longitude: $0.longitude, orderIndex: $0.orderIndex)
                    }
                )
            },
            markers: db.markers.map { marker in
                BackupMarker(
                    id: marker.id,
                    category: marker.category.rawValue,
                    name: marker.name,
                    notes: marker.notes,
                    latitude: marker.latitude,
                    longitude: marker.longitude,
                    elevationMeters: marker.elevationMeters,
                    createdAtEpochMs: marker.createdAtEpochMs,
                    speciesId: marker.speciesId,
                    isPrivate: marker.isPrivate,
                    photoOmitted: !(marker.photoUri ?? "").isEmpty
                )
            },
            routes: db.routes.map { route in
                BackupRoute(
                    id: route.id,
                    name: route.name,
                    startedAtEpochMs: route.startedAtEpochMs,
                    endedAtEpochMs: route.endedAtEpochMs,
                    distanceMeters: route.distanceMeters,
                    points: (pointsByRoute[route.id] ?? []).sorted { $0.recordedAtEpochMs < $1.recordedAtEpochMs }.map {
                        BackupTrackPoint(
                            latitude: $0.latitude,
                            longitude: $0.longitude,
                            elevationMeters: $0.elevationMeters,
                            speedMps: $0.speedMps,
                            recordedAtEpochMs: $0.recordedAtEpochMs
                        )
                    }
                )
            },
            medicalProfile: medical.map {
                BackupMedicalProfile(
                    fullName: $0.fullName,
                    bloodType: $0.bloodType,
                    allergies: $0.allergies,
                    medicalInfo: $0.medicalInfo,
                    medication: $0.medication,
                    notes: $0.notes,
                    contacts: $0.contacts.map {
                        BackupEmergencyContact(name: $0.name, phone: $0.phone, relationship: $0.relationship)
                    }
                )
            }
        )
    }

    static func encode(_ backup: FieldBackupFile) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(backup)
    }

    static func decode(_ data: Data) throws -> FieldBackupFile {
        let backup = try JSONDecoder().decode(FieldBackupFile.self, from: data)
        guard backup.format == FieldBackupFormat.format, backup.version == FieldBackupFormat.version else {
            throw FieldBackupError("This backup version is not supported.")
        }
        return backup
    }
}

struct FieldBackupError: Error, LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

enum GeoFormats {
    static func gpx(_ backup: FieldBackupFile) -> String {
        var lines = ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                     "<gpx version=\"1.1\" creator=\"HunTracK\" xmlns=\"http://www.topografix.com/GPX/1/1\">"]
        for marker in backup.markers {
            lines.append("  <wpt lat=\"\(coord(marker.latitude))\" lon=\"\(coord(marker.longitude))\">")
            lines.append("    <name>\(xml(marker.name))</name>")
            lines.append("    <type>\(xml(marker.category))</type>")
            lines.append("  </wpt>")
        }
        for route in backup.routes {
            lines.append("  <trk><name>\(xml(route.name))</name><trkseg>")
            for point in route.points {
                lines.append("    <trkpt lat=\"\(coord(point.latitude))\" lon=\"\(coord(point.longitude))\"></trkpt>")
            }
            lines.append("  </trkseg></trk>")
        }
        lines.append("</gpx>")
        return lines.joined(separator: "\n")
    }

    static func kml(_ backup: FieldBackupFile) -> String {
        var lines = ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                     "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>HunTracK field data</name>"]
        for area in backup.boundaries where area.points.count >= 3 {
            let ring = area.points + [area.points[0]]
            let coords = ring.map { "\(coord($0.longitude)),\(coord($0.latitude)),0" }.joined(separator: " ")
            lines.append("<Placemark><name>\(xml(area.name))</name><Polygon><outerBoundaryIs><LinearRing><coordinates>\(coords)</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>")
        }
        for marker in backup.markers {
            lines.append("<Placemark><name>\(xml(marker.name))</name><Point><coordinates>\(coord(marker.longitude)),\(coord(marker.latitude)),0</coordinates></Point></Placemark>")
        }
        lines.append("</Document></kml>")
        return lines.joined(separator: "\n")
    }

    static func geoJson(_ backup: FieldBackupFile) -> String {
        var features: [String] = []
        for marker in backup.markers {
            features.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[\(coord(marker.longitude)),\(coord(marker.latitude))]},\"properties\":{\"kind\":\"marker\",\"name\":\(json(marker.name))}}")
        }
        for area in backup.boundaries where area.points.count >= 3 {
            let ring = (area.points + [area.points[0]]).map { "[\(coord($0.longitude)),\(coord($0.latitude))]" }.joined(separator: ",")
            features.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[\(ring)]]},\"properties\":{\"kind\":\"boundary\",\"name\":\(json(area.name)),\"zoneType\":\(json(area.zoneType))}}")
        }
        for route in backup.routes where !route.points.isEmpty {
            let line = route.points.map { "[\(coord($0.longitude)),\(coord($0.latitude))]" }.joined(separator: ",")
            features.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[\(line)]},\"properties\":{\"kind\":\"track\",\"name\":\(json(route.name))}}")
        }
        return "{\"type\":\"FeatureCollection\",\"features\":[\(features.joined(separator: ","))]}"
    }

    private static func coord(_ value: Double) -> String {
        var text = String(format: "%.7f", value)
        while text.contains(".") && (text.hasSuffix("0") || text.hasSuffix(".")) {
            text.removeLast()
        }
        return text
    }

    private static func xml(_ value: String) -> String {
        value.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
    }

    private static func json(_ value: String) -> String {
        let escaped = value.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }
}

enum StoredZip {
    static func write(_ entries: [String: Data]) -> Data {
        var locals = Data()
        var central = Data()
        var offset: UInt32 = 0
        for (name, bytes) in entries {
            let nameBytes = Data(name.utf8)
            let crc = crc32(bytes)
            var local = Data()
            local.appendU32(0x04034b50)
            local.appendU16(20)
            local.appendU16(0)
            local.appendU16(0)
            local.appendU16(0)
            local.appendU16(0)
            local.appendU32(crc)
            local.appendU32(UInt32(bytes.count))
            local.appendU32(UInt32(bytes.count))
            local.appendU16(UInt16(nameBytes.count))
            local.appendU16(0)
            local.append(nameBytes)
            local.append(bytes)
            var centralEntry = Data()
            centralEntry.appendU32(0x02014b50)
            centralEntry.appendU16(20)
            centralEntry.appendU16(20)
            centralEntry.appendU16(0)
            centralEntry.appendU16(0)
            centralEntry.appendU16(0)
            centralEntry.appendU16(0)
            centralEntry.appendU32(crc)
            centralEntry.appendU32(UInt32(bytes.count))
            centralEntry.appendU32(UInt32(bytes.count))
            centralEntry.appendU16(UInt16(nameBytes.count))
            centralEntry.appendU16(0)
            centralEntry.appendU16(0)
            centralEntry.appendU16(0)
            centralEntry.appendU16(0)
            centralEntry.appendU32(0)
            centralEntry.appendU32(offset)
            centralEntry.append(nameBytes)
            offset += UInt32(local.count)
            locals.append(local)
            central.append(centralEntry)
        }
        var end = Data()
        end.appendU32(0x06054b50)
        end.appendU16(0)
        end.appendU16(0)
        end.appendU16(UInt16(entries.count))
        end.appendU16(UInt16(entries.count))
        end.appendU32(UInt32(central.count))
        end.appendU32(offset)
        end.appendU16(0)
        var zip = Data()
        zip.append(locals)
        zip.append(central)
        zip.append(end)
        return zip
    }

    static func read(_ data: Data) throws -> [String: Data] {
        var found: [String: Data] = [:]
        var index = 0
        let bytes = [UInt8](data)
        while index + 30 <= bytes.count {
            let signature = readU32(bytes, index)
            if signature == 0x06054b50 || signature == 0x02014b50 { break }
            guard signature == 0x04034b50 else { throw FieldBackupError("This zip is not a HunTracK backup.") }
            let method = readU16(bytes, index + 8)
            guard method == 0 else { throw FieldBackupError("This zip is compressed. Share the backup again from HunTracK.") }
            let size = Int(readU32(bytes, index + 22))
            let nameLength = Int(readU16(bytes, index + 26))
            let extraLength = Int(readU16(bytes, index + 28))
            let nameStart = index + 30
            let dataStart = nameStart + nameLength + extraLength
            guard dataStart + size <= bytes.count else { throw FieldBackupError("The zip is incomplete.") }
            let name = String(decoding: bytes[nameStart..<(nameStart + nameLength)], as: UTF8.self)
            found[name] = Data(bytes[dataStart..<(dataStart + size)])
            index = dataStart + size
        }
        return found
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                crc = (crc & 1) == 1 ? (crc >> 1) ^ 0xEDB8_8320 : crc >> 1
            }
        }
        return crc ^ 0xFFFF_FFFF
    }

    private static func readU16(_ bytes: [UInt8], _ index: Int) -> UInt16 {
        UInt16(bytes[index]) | (UInt16(bytes[index + 1]) << 8)
    }

    private static func readU32(_ bytes: [UInt8], _ index: Int) -> UInt32 {
        UInt32(bytes[index]) | (UInt32(bytes[index + 1]) << 8) | (UInt32(bytes[index + 2]) << 16) | (UInt32(bytes[index + 3]) << 24)
    }
}

private extension Data {
    mutating func appendU16(_ value: UInt16) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
    }

    mutating func appendU32(_ value: UInt32) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
        append(UInt8((value >> 16) & 0xFF))
        append(UInt8((value >> 24) & 0xFF))
    }
}

extension DatabaseService {
    public func importFieldBackup(_ data: Data, applyMedical: Bool) throws -> String {
        let backup: FieldBackupFile
        if let text = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
           text.hasPrefix("{") {
            backup = try FieldBackupCodec.decode(data)
        } else {
            let entries = try StoredZip.read(data)
            guard let json = entries[FieldBackupFormat.jsonName] else {
                throw FieldBackupError("The zip has no \(FieldBackupFormat.jsonName).")
            }
            backup = try FieldBackupCodec.decode(json)
        }
        var boundaryMap: [Int64: Int64] = [:]
        for area in backup.boundaries {
            let newId = addBoundary(BoundaryArea(
                name: area.name,
                zoneType: BoundaryZoneType(rawValue: area.zoneType) ?? .hunting,
                notes: area.notes,
                points: area.points.map { BoundaryPoint(latitude: $0.latitude, longitude: $0.longitude, orderIndex: $0.orderIndex) },
                areaSqMeters: area.areaSqMeters
            ))
            if area.id != 0 { boundaryMap[area.id] = newId }
        }
        for permission in backup.permissions {
            addPermission(HuntingPermission(
                label: permission.label,
                farmName: permission.farmName,
                permissionType: permission.permissionType,
                species: permission.species,
                validFromEpochMs: permission.validFromEpochMs,
                validToEpochMs: permission.validToEpochMs,
                ownerName: permission.ownerName,
                ownerContact: permission.ownerContact,
                notes: permission.notes,
                docType: PermissionDocType(rawValue: permission.docType) ?? .note,
                attachmentUri: nil,
                boundaryAreaId: permission.boundaryAreaId.flatMap { boundaryMap[$0] },
                showOnMap: permission.showOnMap
            ))
        }
        for marker in backup.markers {
            addMarker(FieldMarker(
                category: MarkerCategory(rawValue: marker.category) ?? .otherWildlife,
                name: marker.name,
                notes: marker.notes,
                latitude: marker.latitude,
                longitude: marker.longitude,
                elevationMeters: marker.elevationMeters,
                createdAtEpochMs: marker.createdAtEpochMs,
                photoUri: nil,
                isPrivate: marker.isPrivate,
                speciesId: marker.speciesId
            ))
        }
        for route in backup.routes {
            let routeId = startRoute(name: route.name)
            if let index = routes.firstIndex(where: { $0.id == routeId }) {
                routes[index].startedAtEpochMs = route.startedAtEpochMs
                routes[index].endedAtEpochMs = route.endedAtEpochMs
                routes[index].distanceMeters = route.distanceMeters
            }
            for point in route.points {
                trackPoints.append(TrackPoint(
                    id: nextId(trackPoints.map { $0.id }),
                    routeId: routeId,
                    latitude: point.latitude,
                    longitude: point.longitude,
                    elevationMeters: point.elevationMeters,
                    speedMps: point.speedMps,
                    recordedAtEpochMs: point.recordedAtEpochMs
                ))
            }
        }
        var medicalNote = " Medical profile was not changed."
        if applyMedical, backup.includesMedicalProfile, let medical = backup.medicalProfile {
            let existing = emergency
            emergency = EmergencyProfile(
                fullName: medical.fullName,
                bloodType: medical.bloodType,
                allergies: medical.allergies,
                medicalInfo: medical.medicalInfo,
                medication: medical.medication,
                notes: medical.notes,
                contacts: medical.contacts.map { EmergencyContact(name: $0.name, phone: $0.phone, relationship: $0.relationship) },
                pinHash: existing.pinHash,
                biometricEnabled: existing.biometricEnabled
            )
            medicalNote = " Medical profile text was replaced. PIN and biometric unlock were kept."
        }
        return "Added \(backup.permissions.count) permissions, \(backup.boundaries.count) boundaries, \(backup.markers.count) markers, \(backup.routes.count) tracks.\(medicalNote)"
    }

    /// Saves a small named map area. iOS keeps the outline only; Apple Maps does not bulk-download tiles.
    @discardableResult
    public func createNamedPackRegion(name: String, north: Double, south: Double, east: Double, west: Double) -> OfflineMapRegion {
        let region = OfflineMapRegion(
            id: nextId(offlineRegions.map { $0.id }),
            name: name,
            north: north,
            south: south,
            east: east,
            west: west,
            sizeBytes: 0,
            status: .ready,
            path: "outline-only"
        )
        offlineRegions.append(region)
        return region
    }
}
