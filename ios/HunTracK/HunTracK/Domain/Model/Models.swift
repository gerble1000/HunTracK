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
