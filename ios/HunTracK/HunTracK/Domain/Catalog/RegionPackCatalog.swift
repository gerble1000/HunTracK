import Foundation

/// Country packs generated from the Android catalog (`region-packs.json`).
public enum RegionPackCatalog {
    public static let intendedPrice = "£1.00"
    public static let ukPackId = "uk-seasons"

    public static let file: RegionPackFile = {
        guard let url = Bundle.main.url(forResource: "region-packs", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(RegionPackFile.self, from: data) else {
            return RegionPackFile(
                format: "hunttrack-region-packs",
                version: 1,
                mapAttribution: "",
                speciesDirectory: [],
                seasonPacks: [],
                mapRegions: []
            )
        }
        return decoded
    }()

    public static func pack(_ id: String) -> SeasonPack? {
        file.seasonPacks.first { $0.id == id }
    }

    public static func regions(for countryCode: String) -> [MapPackRegion] {
        file.mapRegions.filter { $0.countryCode == countryCode }
    }
}

public struct RegionPackFile: Codable {
    public var format: String
    public var version: Int
    public var mapAttribution: String
    public var speciesDirectory: [PackSpeciesIdentity]
    public var seasonPacks: [SeasonPack]
    public var mapRegions: [MapPackRegion]
}

public struct PackSpeciesIdentity: Codable, Identifiable {
    public var id: String { speciesKey }
    public var speciesKey: String
    public var commonName: String
    public var scientificName: String
}

public enum SpeciesLegalStatus: String, Codable {
    case openSeason = "OPEN_SEASON"
    case closedNoSeason = "CLOSED_NO_SEASON"
    case yearRound = "YEAR_ROUND"
    case notStated = "NOT_STATED"
    case unverified = "UNVERIFIED"
    case protectedStatus = "PROTECTED"
}

public enum PackDataQuality: String, Codable {
    case catalog = "CATALOG"
    case reference = "REFERENCE"
    case placeholder = "PLACEHOLDER"
}

public enum SeasonPrecision: String, Codable {
    case dated = "DATED"
    case approximate = "APPROXIMATE"
    case unspecified = "UNSPECIFIED"
}

public enum RuleAnswer: String, Codable {
    case allowed = "ALLOWED"
    case prohibited = "PROHIBITED"
    case conditional = "CONDITIONAL"
    case notRequired = "NOT_REQUIRED"
    case unverified = "UNVERIFIED"

    public var shortLabel: String {
        switch self {
        case .allowed: return "Allowed"
        case .prohibited: return "Not allowed"
        case .conditional: return "Conditional"
        case .notRequired: return "Not required"
        case .unverified: return "Unverified"
        }
    }
}

public struct HuntingRule: Codable, Identifiable {
    public var id: String
    public var title: String
    public var answer: RuleAnswer
    public var notes: String
    public var sourceTitle: String
    public var sourceUrl: String
    public var lastVerified: String?
}

public struct SeasonPack: Codable, Identifiable {
    public var id: String
    public var countryCode: String
    public var countryName: String
    public var title: String
    public var summary: String
    public var sourceTitle: String
    public var sourceUrl: String
    public var disclaimer: String
    public var species: [PackSpeciesSeason]
    public var rules: [HuntingRule]
    public var productId: String
    public var intendedPriceLabel: String
    public var bundledWithApp: Bool
}

public struct PackSpeciesSeason: Codable, Identifiable {
    public var id: String { "\(speciesKey)-\(sexLabel)-\(regionLabel)" }
    public var speciesKey: String
    public var commonName: String
    public var scientificName: String
    public var sexLabel: String
    public var regionLabel: String
    public var legalStatus: SpeciesLegalStatus
    public var quality: PackDataQuality
    public var precision: SeasonPrecision
    public var openFromMonthDay: String?
    public var openToMonthDay: String?
    public var notes: String
    public var sourceUrl: String?
    public var sourceTitle: String?

    public var seasonText: String {
        switch legalStatus {
        case .yearRound: return "Year-round in the reference"
        case .closedNoSeason: return "No open season in the reference"
        case .protectedStatus: return "Protected in the cited law — no season stored"
        case .unverified: return "Unverified — no date stored"
        case .notStated: return "No date stored"
        case .openSeason:
            if let openFromMonthDay, let openToMonthDay {
                return "\(openFromMonthDay) – \(openToMonthDay)"
            }
            return "No date stored"
        }
    }

    public var statusLabel: String {
        switch legalStatus {
        case .openSeason:
            if quality == .catalog { return "Open season in the UK catalog" }
            return precision == .approximate ? "Approximate open season" : "Open season in the cited source"
        case .closedNoSeason: return "No open season"
        case .yearRound: return "Year-round in the reference"
        case .notStated: return "Season not stated"
        case .unverified: return "Unverified placeholder"
        case .protectedStatus: return "Protected — no season stored"
        }
    }
}

public struct MapPackRegion: Codable, Identifiable {
    public var id: String
    public var countryCode: String
    public var name: String
    public var summary: String
    public var centerLatitude: Double
    public var centerLongitude: Double
    public var sideKilometres: Double

    public func bounds() -> (north: Double, south: Double, east: Double, west: Double) {
        let halfLat = (sideKilometres / 2.0) / 111.32
        let cosLat = max(0.01, cos(centerLatitude * .pi / 180.0))
        let halfLon = (sideKilometres / 2.0) / (111.32 * cosLat)
        return (
            centerLatitude + halfLat,
            centerLatitude - halfLat,
            centerLongitude + halfLon,
            centerLongitude - halfLon
        )
    }
}
