import Foundation

public enum MarkerCategoryGroup: String, Codable, CaseIterable {
    case wildlife = "WILDLIFE"
    case hunting = "HUNTING"
    case navigation = "NAVIGATION"
    case tracking = "TRACKING"
}

public enum MarkerCategory: String, Codable, CaseIterable {
    case deer = "DEER"
    case fox = "FOX"
    case rabbit = "RABBIT"
    case bird = "BIRD"
    case badger = "BADGER"
    case otherWildlife = "OTHER_WILDLIFE"
    case highSeat = "HIGH_SEAT"
    case hide = "HIDE"
    case cull = "CULL"
    case feedingArea = "FEEDING_AREA"
    case trailCamera = "TRAIL_CAMERA"
    case caller = "CALLER"
    case waterSource = "WATER_SOURCE"
    case saltLick = "SALT_LICK"
    case treeStand = "TREE_STAND"
    case parking = "PARKING"
    case gate = "GATE"
    case fence = "FENCE"
    case boundary = "BOUNDARY"
    case dangerArea = "DANGER_AREA"
    case shelter = "SHELTER"
    case homing = "HOMING"
    case footprint = "FOOTPRINT"
    case blood = "BLOOD"
    case hair = "HAIR"
    case droppings = "DROPPINGS"
    case beddingArea = "BEDDING_AREA"
    case lastSighting = "LAST_SIGHTING"
    
    public var label: String {
        switch self {
        case .deer: return "Deer"
        case .fox: return "Fox"
        case .rabbit: return "Rabbit"
        case .bird: return "Bird"
        case .badger: return "Badger"
        case .otherWildlife: return "Other"
        case .highSeat: return "High seat"
        case .hide: return "Hide"
        case .cull: return "Cull"
        case .feedingArea: return "Feeding area"
        case .trailCamera: return "Trail camera"
        case .caller: return "Caller"
        case .waterSource: return "Water source"
        case .saltLick: return "Salt lick"
        case .treeStand: return "Tree stand"
        case .parking: return "Parking"
        case .gate: return "Gate"
        case .fence: return "Fence"
        case .boundary: return "Boundary"
        case .dangerArea: return "Danger area"
        case .shelter: return "Shelter"
        case .homing: return "Homing"
        case .footprint: return "Footprint"
        case .blood: return "Blood"
        case .hair: return "Hair"
        case .droppings: return "Droppings"
        case .beddingArea: return "Bedding area"
        case .lastSighting: return "Last sighting"
        }
    }
}

public struct FieldMarker: Identifiable, Codable, Equatable {
    public var id: Int64
    public var category: MarkerCategory
    public var name: String
    public var notes: String
    public var latitude: Double
    public var longitude: Double
    public var elevationMeters: Double?
    public var createdAtEpochMs: Int64
    public var weatherSnapshotJson: String?
    public var photoUri: String?
    public var isPrivate: Bool
    public var speciesId: String?
    
    public init(
        id: Int64 = 0,
        category: MarkerCategory,
        name: String,
        notes: String = "",
        latitude: Double,
        longitude: Double,
        elevationMeters: Double? = nil,
        createdAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        weatherSnapshotJson: String? = nil,
        photoUri: String? = nil,
        isPrivate: Bool = true,
        speciesId: String? = nil
    ) {
        self.id = id
        self.category = category
        self.name = name
        self.notes = notes
        self.latitude = latitude
        self.longitude = longitude
        self.elevationMeters = elevationMeters
        self.createdAtEpochMs = createdAtEpochMs
        self.weatherSnapshotJson = weatherSnapshotJson
        self.photoUri = photoUri
        self.isPrivate = isPrivate
        self.speciesId = speciesId
    }
}

public struct GpsFix: Equatable {
    public let latitude: Double
    public let longitude: Double
    public let accuracyMeters: Float
    public let altitudeMeters: Double?
    public let speedMps: Float?
    public let bearingDegrees: Float?
    public let timestampEpochMs: Int64
    
    public init(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        altitudeMeters: Double? = nil,
        speedMps: Float? = nil,
        bearingDegrees: Float? = nil,
        timestampEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.latitude = latitude
        self.longitude = longitude
        self.accuracyMeters = accuracyMeters
        self.altitudeMeters = altitudeMeters
        self.speedMps = speedMps
        self.bearingDegrees = bearingDegrees
        self.timestampEpochMs = timestampEpochMs
    }
}

public enum ActivityLevel: String, Codable {
    case low = "LOW"
    case moderate = "MODERATE"
    case high = "HIGH"
}

public struct ActivityRating: Codable, Equatable {
    public let level: ActivityLevel
    public let title: String
    public let reasons: [String]
}

public enum UnitSystem: String, Codable {
    case metric = "METRIC"
    case imperial = "IMPERIAL"
}

public enum WindUnit: String, Codable {
    case kmh = "KMH"
    case mph = "MPH"
}

public enum MapLayerStyle: String, Codable {
    case street = "STREET"
    case outdoor = "OUTDOOR"
    case satellite = "SATELLITE"
}

public struct UserPreferences: Codable, Equatable {
    public var useCelsius: Bool = true
    public var windUnit: WindUnit = .mph
    public var unitSystem: UnitSystem = .metric
    public var soundsEnabled: Bool = true
    public var vibrationEnabled: Bool = true
    public var mapLayerStyle: MapLayerStyle = .outdoor
    public var themeMode: HuntThemeMode = .woodland
    public var showOsGridRef: Bool = true
    public var showCullPinsOnMap: Bool = false
    public var markAnywhereEnabled: Bool = true
    public var mapTrainingEnabled: Bool = false
    public var windTrainingEnabled: Bool = false
    public var tidesEnabled: Bool = false
    public var tideStationId: String = ""
    public var tideStationName: String = ""
    public var tideStationLatitude: Double? = nil
    public var tideStationLongitude: Double? = nil
    
    public init() {}
}

// MARK: - Routes & tracks

public struct Route: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var name: String
    public var startedAtEpochMs: Int64
    public var endedAtEpochMs: Int64?
    public var distanceMeters: Double = 0.0
    
    public init(
        id: Int64 = 0,
        name: String,
        startedAtEpochMs: Int64,
        endedAtEpochMs: Int64? = nil,
        distanceMeters: Double = 0.0
    ) {
        self.id = id
        self.name = name
        self.startedAtEpochMs = startedAtEpochMs
        self.endedAtEpochMs = endedAtEpochMs
        self.distanceMeters = distanceMeters
    }
}

public struct TrackPoint: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var routeId: Int64
    public var latitude: Double
    public var longitude: Double
    public var elevationMeters: Double?
    public var speedMps: Float?
    public var recordedAtEpochMs: Int64
    
    public init(
        id: Int64 = 0,
        routeId: Int64,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double? = nil,
        speedMps: Float? = nil,
        recordedAtEpochMs: Int64
    ) {
        self.id = id
        self.routeId = routeId
        self.latitude = latitude
        self.longitude = longitude
        self.elevationMeters = elevationMeters
        self.speedMps = speedMps
        self.recordedAtEpochMs = recordedAtEpochMs
    }
}

public enum OfflineMapStatus: String, Codable, CaseIterable {
    case pending = "PENDING"
    case downloading = "DOWNLOADING"
    case ready = "READY"
    case failed = "FAILED"
    
    public var label: String {
        switch self {
        case .pending: return "Pending"
        case .downloading: return "Downloading"
        case .ready: return "Ready"
        case .failed: return "Failed"
        }
    }
}

public struct OfflineMapRegion: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var name: String
    public var north: Double
    public var south: Double
    public var east: Double
    public var west: Double
    public var sizeBytes: Int64 = 0
    public var status: OfflineMapStatus = .pending
    public var path: String?
    
    public init(
        id: Int64 = 0,
        name: String,
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        sizeBytes: Int64 = 0,
        status: OfflineMapStatus = .pending,
        path: String? = nil
    ) {
        self.id = id
        self.name = name
        self.north = north
        self.south = south
        self.east = east
        self.west = west
        self.sizeBytes = sizeBytes
        self.status = status
        self.path = path
    }
}

// MARK: - Field calendar

public struct CalendarDayEntry: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var dayStartEpochMs: Int64
    public var title: String = ""
    public var notes: String = ""
    public var speciesId: String?
    public var location: String = ""
    public var updatedAtEpochMs: Int64 = 0
    
    public init(
        id: Int64 = 0,
        dayStartEpochMs: Int64,
        title: String = "",
        notes: String = "",
        speciesId: String? = nil,
        location: String = "",
        updatedAtEpochMs: Int64 = 0
    ) {
        self.id = id
        self.dayStartEpochMs = dayStartEpochMs
        self.title = title
        self.notes = notes
        self.speciesId = speciesId
        self.location = location
        self.updatedAtEpochMs = updatedAtEpochMs
    }
    
    public var hasContent: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !(speciesId ?? "").isEmpty
    }
}

// MARK: - Cull log

public enum CullSex: String, Codable, CaseIterable {
    case male = "MALE"
    case female = "FEMALE"
    case unknown = "UNKNOWN"
    
    public var label: String {
        switch self {
        case .male: return "Male"
        case .female: return "Female"
        case .unknown: return "Unknown"
        }
    }
}

public enum CullAgeClass: String, Codable, CaseIterable {
    case adult = "ADULT"
    case yearling = "YEARLING"
    case juvenile = "JUVENILE"
    case unknown = "UNKNOWN"
    
    public var label: String {
        switch self {
        case .adult: return "Adult"
        case .yearling: return "Yearling"
        case .juvenile: return "Juvenile"
        case .unknown: return "Unknown"
        }
    }
}

public enum CarcassTestStatus: String, Codable, CaseIterable {
    case notRequired = "NOT_REQUIRED"
    case pending = "PENDING"
    case passed = "PASSED"
    case failed = "FAILED"
    case sent = "SENT"
    
    public var label: String {
        switch self {
        case .notRequired: return "Not required"
        case .pending: return "Pending"
        case .passed: return "Passed"
        case .failed: return "Failed"
        case .sent: return "Sent for testing"
        }
    }
}

public struct CullRecord: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var culledAtEpochMs: Int64
    public var speciesId: String?
    public var speciesLabel: String
    public var sex: CullSex = .unknown
    public var ageClass: CullAgeClass = .unknown
    public var count: Int = 1
    public var latitude: Double?
    public var longitude: Double?
    public var osGridRef: String = ""
    public var locationName: String = ""
    public var carcassTestStatus: CarcassTestStatus = .notRequired
    public var notes: String = ""
    public var linkedMarkerId: Int64?
    
    public init(
        id: Int64 = 0,
        culledAtEpochMs: Int64,
        speciesId: String? = nil,
        speciesLabel: String,
        sex: CullSex = .unknown,
        ageClass: CullAgeClass = .unknown,
        count: Int = 1,
        latitude: Double? = nil,
        longitude: Double? = nil,
        osGridRef: String = "",
        locationName: String = "",
        carcassTestStatus: CarcassTestStatus = .notRequired,
        notes: String = "",
        linkedMarkerId: Int64? = nil
    ) {
        self.id = id
        self.culledAtEpochMs = culledAtEpochMs
        self.speciesId = speciesId
        self.speciesLabel = speciesLabel
        self.sex = sex
        self.ageClass = ageClass
        self.count = count
        self.latitude = latitude
        self.longitude = longitude
        self.osGridRef = osGridRef
        self.locationName = locationName
        self.carcassTestStatus = carcassTestStatus
        self.notes = notes
        self.linkedMarkerId = linkedMarkerId
    }
}

// MARK: - Permissions & boundaries

public enum PermissionDocType: String, Codable, CaseIterable {
    case pdf = "PDF"
    case photo = "PHOTO"
    case note = "NOTE"
    
    public var label: String {
        switch self {
        case .pdf: return "PDF"
        case .photo: return "Photo"
        case .note: return "Note"
        }
    }
}

public struct HuntingPermission: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var label: String = ""
    public var farmName: String
    public var permissionType: String
    public var species: String
    public var validFromEpochMs: Int64
    public var validToEpochMs: Int64
    public var ownerName: String
    public var ownerContact: String
    public var notes: String = ""
    public var docType: PermissionDocType = .note
    public var attachmentUri: String?
    public var boundaryAreaId: Int64?
    
    public init(
        id: Int64 = 0,
        label: String = "",
        farmName: String,
        permissionType: String,
        species: String,
        validFromEpochMs: Int64,
        validToEpochMs: Int64,
        ownerName: String,
        ownerContact: String,
        notes: String = "",
        docType: PermissionDocType = .note,
        attachmentUri: String? = nil,
        boundaryAreaId: Int64? = nil
    ) {
        self.id = id
        self.label = label
        self.farmName = farmName
        self.permissionType = permissionType
        self.species = species
        self.validFromEpochMs = validFromEpochMs
        self.validToEpochMs = validToEpochMs
        self.ownerName = ownerName
        self.ownerContact = ownerContact
        self.notes = notes
        self.docType = docType
        self.attachmentUri = attachmentUri
        self.boundaryAreaId = boundaryAreaId
    }
    
    public var displayLabel: String {
        if !label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return label }
        if !farmName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return farmName }
        return "Permission"
    }
    
    public var isExpired: Bool {
        validToEpochMs < Int64(Date().timeIntervalSince1970 * 1000)
    }
}

public struct PermissionMapFocusItem: Identifiable, Codable, Equatable {
    public var id: Int64 { permissionId }
    public var permissionId: Int64
    public var label: String
    public var boundaryAreaId: Int64
    
    public init(permissionId: Int64, label: String, boundaryAreaId: Int64) {
        self.permissionId = permissionId
        self.label = label
        self.boundaryAreaId = boundaryAreaId
    }
}

public enum BoundaryZoneType: String, Codable, CaseIterable {
    case hunting = "HUNTING"
    case noGo = "NO_GO"
    case safe = "SAFE"
    
    public var displayName: String {
        switch self {
        case .hunting: return "Hunting area"
        case .noGo: return "No-go zone"
        case .safe: return "Safe zone"
        }
    }
    
    public var summary: String {
        switch self {
        case .hunting: return "Your permitted shoot, beat, or paddock \u{2014} where you plan to hunt."
        case .noGo: return "Do not enter \u{2014} neighbour land, roads, buildings, or other off-limits ground."
        case .safe: return "Emergency fallback \u{2014} car park, farmyard, or rendezvous point if you need to pull out."
        }
    }
    
    public var mapColorHex: String {
        switch self {
        case .hunting: return "#C4A574"
        case .noGo: return "#E05A45"
        case .safe: return "#45B872"
        }
    }
    
    public var mapFillOpacity: Double {
        switch self {
        case .hunting: return 0.14
        case .noGo: return 0.20
        case .safe: return 0.16
        }
    }
}

public enum BoundaryCaptureMode: String, Codable, CaseIterable {
    case walk = "WALK"
    case draw = "DRAW"
    
    public var label: String {
        switch self {
        case .walk: return "Walk it"
        case .draw: return "Draw it"
        }
    }
}

public struct PendingBoundaryCreation: Codable, Equatable {
    public var mode: BoundaryCaptureMode
    public var name: String
    public var zoneType: BoundaryZoneType
    public var returnToPermissions: Bool = true
    public var permissionId: Int64?
    
    public init(
        mode: BoundaryCaptureMode,
        name: String,
        zoneType: BoundaryZoneType,
        returnToPermissions: Bool = true,
        permissionId: Int64? = nil
    ) {
        self.mode = mode
        self.name = name
        self.zoneType = zoneType
        self.returnToPermissions = returnToPermissions
        self.permissionId = permissionId
    }
}

public struct BoundaryPoint: Codable, Equatable {
    public var latitude: Double
    public var longitude: Double
    public var orderIndex: Int
    
    public init(latitude: Double, longitude: Double, orderIndex: Int) {
        self.latitude = latitude
        self.longitude = longitude
        self.orderIndex = orderIndex
    }
}

public struct BoundaryArea: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var name: String
    public var zoneType: BoundaryZoneType
    public var notes: String = ""
    public var points: [BoundaryPoint] = []
    public var areaSqMeters: Double = 0.0
    
    public init(
        id: Int64 = 0,
        name: String,
        zoneType: BoundaryZoneType,
        notes: String = "",
        points: [BoundaryPoint] = [],
        areaSqMeters: Double = 0.0
    ) {
        self.id = id
        self.name = name
        self.zoneType = zoneType
        self.notes = notes
        self.points = points
        self.areaSqMeters = areaSqMeters
    }
}

// MARK: - Emergency profile

public struct EmergencyContact: Codable, Equatable, Hashable {
    public var name: String
    public var phone: String
    public var relationship: String = ""
    
    public init(name: String, phone: String, relationship: String = "") {
        self.name = name
        self.phone = phone
        self.relationship = relationship
    }
}

public struct EmergencyProfile: Codable, Equatable {
    public var fullName: String = ""
    public var bloodType: String = ""
    public var allergies: String = ""
    public var medicalInfo: String = ""
    public var medication: String = ""
    public var notes: String = ""
    public var contacts: [EmergencyContact] = []
    public var pinHash: String?
    public var biometricEnabled: Bool = false
    
    public init(
        fullName: String = "",
        bloodType: String = "",
        allergies: String = "",
        medicalInfo: String = "",
        medication: String = "",
        notes: String = "",
        contacts: [EmergencyContact] = [],
        pinHash: String? = nil,
        biometricEnabled: Bool = false
    ) {
        self.fullName = fullName
        self.bloodType = bloodType
        self.allergies = allergies
        self.medicalInfo = medicalInfo
        self.medication = medication
        self.notes = notes
        self.contacts = contacts
        self.pinHash = pinHash
        self.biometricEnabled = biometricEnabled
    }
    
    public var hasContent: Bool {
        !fullName.isEmpty || !bloodType.isEmpty || !allergies.isEmpty ||
        !medicalInfo.isEmpty || !medication.isEmpty || !notes.isEmpty ||
        !contacts.isEmpty
    }
}

// MARK: - Calls journal & audio packs

public struct CallJournalEntry: Identifiable, Codable, Equatable {
    public var id: Int64 = 0
    public var callId: String?
    public var speciesId: String?
    public var notes: String
    public var response: String
    public var distanceEstimateMeters: Int?
    public var latitude: Double?
    public var longitude: Double?
    public var weatherJson: String?
    public var recordedAtEpochMs: Int64
    
    public init(
        id: Int64 = 0,
        callId: String? = nil,
        speciesId: String? = nil,
        notes: String = "",
        response: String = "",
        distanceEstimateMeters: Int? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        weatherJson: String? = nil,
        recordedAtEpochMs: Int64
    ) {
        self.id = id
        self.callId = callId
        self.speciesId = speciesId
        self.notes = notes
        self.response = response
        self.distanceEstimateMeters = distanceEstimateMeters
        self.latitude = latitude
        self.longitude = longitude
        self.weatherJson = weatherJson
        self.recordedAtEpochMs = recordedAtEpochMs
    }
}

public struct AudioPackTrack: Codable, Equatable, Hashable {
    public var callId: String
    public var fileName: String
    public var remoteUrl: String
    
    public init(callId: String, fileName: String, remoteUrl: String) {
        self.callId = callId
        self.fileName = fileName
        self.remoteUrl = remoteUrl
    }
}

public struct AudioPack: Identifiable, Codable, Equatable {
    public var id: String
    public var name: String
    public var description: String
    public var recordingCount: Int
    public var sizeBytes: Int64
    public var tracks: [AudioPackTrack] = []
    public var downloaded: Bool = false
    public var downloadProgress: Float = 0
    
    public init(
        id: String,
        name: String,
        description: String,
        recordingCount: Int,
        sizeBytes: Int64,
        tracks: [AudioPackTrack] = [],
        downloaded: Bool = false,
        downloadProgress: Float = 0
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.recordingCount = recordingCount
        self.sizeBytes = sizeBytes
        self.tracks = tracks
        self.downloaded = downloaded
        self.downloadProgress = downloadProgress
    }
}

// MARK: - Tides

public struct TideStation: Identifiable, Codable, Equatable, Hashable {
    public var id: String
    public var name: String
    public var country: String
    public var latitude: Double
    public var longitude: Double
    
    public init(id: String, name: String, country: String, latitude: Double, longitude: Double) {
        self.id = id
        self.name = name
        self.country = country
        self.latitude = latitude
        self.longitude = longitude
    }
}

public enum TideEventType: String, Codable, CaseIterable {
    case high = "HIGH"
    case low = "LOW"
    case unknown = "UNKNOWN"
    
    public var label: String {
        switch self {
        case .high: return "High"
        case .low: return "Low"
        case .unknown: return "Tide"
        }
    }
}

public struct TideEvent: Identifiable, Codable, Equatable {
    public var id: Int64 { epochMs }
    public var epochMs: Int64
    public var type: TideEventType
    public var heightMeters: Double
    public var isApproximate: Bool
    
    public init(epochMs: Int64, type: TideEventType, heightMeters: Double, isApproximate: Bool) {
        self.epochMs = epochMs
        self.type = type
        self.heightMeters = heightMeters
        self.isApproximate = isApproximate
    }
}

public enum TideDataSource: String, Codable, CaseIterable {
    case calculated = "CALCULATED"
    case live = "LIVE"
    case cache = "CACHE"
    case unavailable = "UNAVAILABLE"
    
    public var label: String {
        switch self {
        case .calculated: return "Calculated"
        case .live: return "Live"
        case .cache: return "Cached"
        case .unavailable: return "Unavailable"
        }
    }
}

public struct TideForecast: Codable, Equatable {
    public var station: TideStation
    public var events: [TideEvent]
    public var fetchedAtEpochMs: Int64
    public var source: TideDataSource
    
    public init(
        station: TideStation,
        events: [TideEvent],
        fetchedAtEpochMs: Int64,
        source: TideDataSource
    ) {
        self.station = station
        self.events = events
        self.fetchedAtEpochMs = fetchedAtEpochMs
        self.source = source
    }
}
