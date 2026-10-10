import Foundation

public enum SpeciesGroup: String, Codable, CaseIterable {
    case deer = "DEER"
    case otherQuarry = "OTHER_QUARRY"
    case bird = "BIRD"
    case waterfowl = "WATERFOWL"
    case corvid = "CORVID"
    case pest = "PEST"
    
    public var label: String {
        switch self {
        case .deer: return "Deer"
        case .otherQuarry: return "Other quarry"
        case .bird: return "Gamebirds"
        case .waterfowl: return "Waterfowl"
        case .corvid: return "Corvids"
        case .pest: return "Pest"
        }
    }
}

public enum UkNation: String, Codable, CaseIterable {
    case england = "ENGLAND"
    case wales = "WALES"
    case scotland = "SCOTLAND"
    case northernIreland = "NORTHERN_IRELAND"
    
    public var label: String {
        switch self {
        case .england: return "England"
        case .wales: return "Wales"
        case .scotland: return "Scotland"
        case .northernIreland: return "N. Ireland"
        }
    }
}

public struct Species: Identifiable, Codable, Equatable {
    public let id: String
    public let commonName: String
    public let scientificName: String
    public let group: SpeciesGroup
    public let summary: String
    public let habitat: String
    public let behaviour: String
    public let distribution: String
    public let maleTraits: String
    public let femaleTraits: String
    public let youngTraits: String
    public let tracks: String
    public let droppings: String
    public let seasonsNote: String
}

public struct LegalSeason: Codable, Equatable {
    public let speciesId: String
    public let nation: UkNation
    public let sexLabel: String
    public let openFromMonthDay: String
    public let openToMonthDay: String
    public let notes: String
}

public struct AnimalCall: Identifiable, Codable, Equatable {
    public let id: String
    public let name: String
    public let speciesId: String
    public let category: String
    public let description: String
    public let packId: String
    public let remoteUrl: String
    public let isBuiltIn: Bool
    public let filePath: String?
    public let credit: String
}

public enum UkFieldCatalog {
    public static let species: [Species] = [
        Species(
            id: "red_deer",
            commonName: "Red Deer",
            scientificName: "Cervus elaphus",
            group: .deer,
            summary: "Britain's largest native land mammal. Stags carry impressive branched antlers with up to 16+ points in mature beasts.",
            habitat: "Highland open moorland, commercial conifer forestry, ancient deciduous woodland, and lowland parklands.",
            behaviour: "Crepuscular and nocturnal grazers. Herd-dwelling; stags and hinds segregate for most of the year outside the autumn rut.",
            distribution: "Abundant across the Scottish Highlands and Islands; locally thriving in Galloway, Lake District, Exmoor, Quantocks, East Anglia.",
            maleTraits: "Large body up to 190–240 kg. Branching antlers, prominent rutting mane, strong neck, deep bellowing roar in autumn.",
            femaleTraits: "Smaller (120–170 kg), antlerless, elegant long head, reddish-brown summer coat turning grey-brown in winter.",
            youngTraits: "Spotted calves born June; spots fade by autumn.",
            tracks: "Large pointed cleaves, 8–9 cm long, distinct dewhaw marks in soft mud.",
            droppings: "Cylindrical pellets, pointed at one end, flattened or indented at the other, often clumped.",
            seasonsNote: "Seasons vary by nation and sex. Check local regulations."
        ),
        Species(
            id: "roe_deer",
            commonName: "Roe Deer",
            scientificName: "Capreolus capreolus",
            group: .deer,
            summary: "Small, agile native woodland deer. Elegant russet summer coat turning dull brown-grey with prominent white anal patch in winter.",
            habitat: "Deciduous and mixed woodland, copses, field margins, agricultural edges, and suburban woodland corridors.",
            behaviour: "Solitary or in small family groups (doe with kids). Highly territorial; bucks mark trees with antler fraying and scent glands.",
            distribution: "Widespread and common across Scotland, England, and Wales. Absent from Northern Ireland.",
            maleTraits: "18–30 kg. Short, rough, three-tined antlers (pearled coronets). Distinct white throat patch in summer.",
            femaleTraits: "Antlerless, slightly smaller, distinctive inverted heart-shaped white anal patch with downward-pointing hair tuft in winter.",
            youngTraits: "Twin fawns common, heavily spotted until autumn.",
            tracks: "Small, narrow cleaves 4–5 cm long, very neat and sharp.",
            droppings: "Small glossy black/dark brown oval pellets, 10–14 mm long, scattered or in small latrines.",
            seasonsNote: "Buck and doe seasons strictly separated across UK nations."
        ),
        Species(
            id: "fallow_deer",
            commonName: "Fallow Deer",
            scientificName: "Dama dama",
            group: .deer,
            summary: "Medium-sized introduced deer with four distinct coat varieties: common spotted, menil, melanistic (black), and white.",
            habitat: "Established broadleaf woodland, parklands, and agricultural landscapes with dense cover.",
            behaviour: "Gregarious herd animals. Bucks produce a rhythmic groaning call during the October rut.",
            distribution: "Widespread throughout England and Wales, localized herds in Scotland and Northern Ireland.",
            maleTraits: "60–100 kg. Broad, palmate flattened antlers with points projecting from the rear edge.",
            femaleTraits: "35–55 kg, antlerless, long black-and-white tail constantly flicking.",
            youngTraits: "Spotted fawns born June/July.",
            tracks: "Neat cleaves 6–7 cm long, narrower than red deer.",
            droppings: "Cylindrical pellets with one pointed end and one dimpled end.",
            seasonsNote: "Regulated seasons by sex and nation."
        ),
        Species(
            id: "fox",
            commonName: "Red Fox",
            scientificName: "Vulpes vulpes",
            group: .otherQuarry,
            summary: "Widespread opportunistic predator and quarry. Renowned for intelligence, acute hearing, and keen sense of smell.",
            habitat: "Farmland, woodland, moorland, coastal dunes, urban and suburban areas across the UK.",
            behaviour: "Solitary hunter, primarily nocturnal and crepuscular. Highly vocal in winter mating season.",
            distribution: "Universal throughout Great Britain and Northern Ireland.",
            maleTraits: "Dog fox: broader skull, slightly heavier (6–9 kg), bold barking vocalisation.",
            femaleTraits: "Vixen: narrower snout, high-pitched screeching scream during winter rut.",
            youngTraits: "Cubs born underground in earths in spring.",
            tracks: "Compact four-toed pad prints, 5 cm long, claws always visible.",
            droppings: "Tapered, twisted scats containing fur, feathers, and bone fragments; strong musky scent.",
            seasonsNote: "Unprotected pest quarry under open general licences, subject to lawful methods."
        ),
        Species(
            id: "rabbit",
            commonName: "European Rabbit",
            scientificName: "Oryctolagus cuniculus",
            group: .otherQuarry,
            summary: "Abundant small herbivore, key quarry for airgun, rimfire, and shotgun field sports.",
            habitat: "Grassland, field banks, hedgerows, woodland fringes with well-drained sandy or loamy soil.",
            behaviour: "Colony living in underground warrens. Feeds within 100–200 m of cover.",
            distribution: "Universal across Great Britain and Northern Ireland.",
            maleTraits: "Buck: slightly broader blocky head.",
            femaleTraits: "Doe: narrower head, dewlap beneath chin in mature does.",
            youngTraits: "Kittens emerge from breeding stops at 3–4 weeks.",
            tracks: "Characteristic asymmetrical grouping: two small foreprints behind two elongated hind prints.",
            droppings: "Small fibrous spherical pellets, 8–10 mm diameter, deposited in heaps (scrapes).",
            seasonsNote: "Open quarry throughout the year; landowners have legal obligation to control."
        )
    ]
    
    public static func speciesById(_ id: String) -> Species? {
        species.first { $0.id == id }
    }
}
