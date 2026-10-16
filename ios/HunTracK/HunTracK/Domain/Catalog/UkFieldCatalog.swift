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
    
    public static let legalDisclaimer = "Legal seasons and regulations change. Always verify current law with the relevant authority (e.g. GOV.UK, Deer Initiative, NatureScot, Natural Resources Wales, DAERA) before shooting."
    
    public static let species: [Species] = [
        Species(
            id: "red_deer",
            commonName: "Red deer",
            scientificName: "Cervus elaphus",
            group: .deer,
            summary: "Largest UK deer. Often found on open hills, woodland margins and estates.",
            habitat: "Moorland, open hill, woodland and park estates across Scotland and parts of England/Wales.",
            behaviour: "Hinds form herds; stags more solitary outside the rut. Peak activity evening through night; also active at dawn.",
            distribution: "Strongholds in Scotland; also Exmoor, Quantocks, New Forest, East Anglia parks.",
            maleTraits: "Large branched antlers in mature stags; darker mane in rut.",
            femaleTraits: "No antlers; cleaner neck line; often with calves.",
            youngTraits: "Spotted calves in first months; stay close to hind.",
            tracks: "Large heart-shaped slots; hind usually smaller than stag.",
            droppings: "Cylindrical pellets, sometimes clumped when soft feed available.",
            seasonsNote: "Sex and nation specific open seasons apply."
        ),
        Species(
            id: "roe_deer",
            commonName: "Roe deer",
            scientificName: "Capreolus capreolus",
            group: .deer,
            summary: "Small native deer of woodland edge and farmland.",
            habitat: "Woodland, scrub, farmland margins and young plantations.",
            behaviour: "Territorial bucks in summer; often seen singly or in small family groups.",
            distribution: "Widespread across much of mainland Britain.",
            maleTraits: "Short upright antlers with usually 6 points in mature bucks.",
            femaleTraits: "No antlers; rump patch with no white 'scut' like fallow.",
            youngTraits: "Kids often twin; heavily spotted early on.",
            tracks: "Small neat slots; parallel when walking.",
            droppings: "Small dark pellets, pointed at one end.",
            seasonsNote: "Buck and doe seasons differ by nation."
        ),
        Species(
            id: "fallow_deer",
            commonName: "Fallow deer",
            scientificName: "Dama dama",
            group: .deer,
            summary: "Medium deer, often palmate antlers on bucks; variable coat colour.",
            habitat: "Parkland, deciduous woodland and mixed farmland.",
            behaviour: "Gregarious; herds common. Rut in autumn with groaning bucks.",
            distribution: "Widespread in England and Wales; more local in Scotland.",
            maleTraits: "Palmated antlers in mature bucks; laryngeal Adam\u{2019}s apple visible.",
            femaleTraits: "No antlers; often in herds with fawns.",
            youngTraits: "Spotted fawns; stay with doe groups.",
            tracks: "Medium slots, slightly pointed.",
            droppings: "Medium pellets, often in clusters.",
            seasonsNote: "Check local byelaws on park escapes."
        ),
        Species(
            id: "sika_deer",
            commonName: "Sika deer",
            scientificName: "Cervus nippon",
            group: .deer,
            summary: "Introduced deer, often spotted; can hybridise with red deer.",
            habitat: "Forestry, heath and coastal woodland; densest in Scotland & some English forests.",
            behaviour: "Wary; nocturnal pressure high where hunted. Stags whistle in rut.",
            distribution: "Parts of Scotland, NW England, New Forest and East Anglia.",
            maleTraits: "Antlers typically 8 points; darker neck in rut.",
            femaleTraits: "Spotted coats common; white caudal patch with black border.",
            youngTraits: "Spotted calves.",
            tracks: "Similar to red but usually smaller.",
            droppings: "Cylindrical pellets.",
            seasonsNote: "Follow national deer close seasons."
        ),
        Species(
            id: "muntjac",
            commonName: "Muntjac",
            scientificName: "Muntiacus reevesi",
            group: .deer,
            summary: "Small barking deer; no national close season in England/Wales for either sex.",
            habitat: "Dense woodland, scrub and gardens.",
            behaviour: "Often heard barking; breeds year-round; territorial bucks.",
            distribution: "Common across much of England and parts of Wales; spreading.",
            maleTraits: "Short antlers on long pedicles; visible canine tusks.",
            femaleTraits: "No antlers; dark V on forehead often visible.",
            youngTraits: "Tiny fawns hidden in cover.",
            tracks: "Very small slots; often in thick undergrowth.",
            droppings: "Small pellets.",
            seasonsNote: "No statutory close season E&W; still apply best welfare practice."
        ),
        Species(
            id: "chinese_water_deer",
            commonName: "Chinese water deer",
            scientificName: "Hydropotes inermis",
            group: .deer,
            summary: "Small deer without antlers; males have prominent tusks.",
            habitat: "Wetland edges, reedbeds, wet grassland \u{2014} East Anglia stronghold.",
            behaviour: "Often solitary; whistle/alarm call; rush into cover.",
            distribution: "Mainly Bedfordshire, Cambridgeshire, Norfolk, Buckinghamshire.",
            maleTraits: "No antlers; long upper canines.",
            femaleTraits: "No tusks of note; similar body size.",
            youngTraits: "Spotted kids.",
            tracks: "Small neat slots near water.",
            droppings: "Small pellets.",
            seasonsNote: "Follow Deer Act close seasons where applicable."
        ),
        Species(
            id: "fox",
            commonName: "Fox",
            scientificName: "Vulpes vulpes",
            group: .otherQuarry,
            summary: "Widespread predator; taken under pest / land management rules.",
            habitat: "Farmland, woodland, urban fringe.",
            behaviour: "Mostly nocturnal; dens in spring with cubs.",
            distribution: "Throughout UK.",
            maleTraits: "Dog foxes usually larger; broader head.",
            femaleTraits: "Vixens smaller; dens with cubs spring/summer.",
            youngTraits: "Cubs grey-brown then redden.",
            tracks: "Dog-like but more oval; claw marks often clear.",
            droppings: "Twisted scats with fur/bone; often on prominences.",
            seasonsNote: "Not deer legislation \u{2014} follow firearms/pest control law & landowner permission."
        ),
        Species(
            id: "rabbit",
            commonName: "Rabbit",
            scientificName: "Oryctolagus cuniculus",
            group: .otherQuarry,
            summary: "Common farmland and woodland edge quarry.",
            habitat: "Grassland, woodland edge, dunes, warrens.",
            behaviour: "Crep\u{00FA}scular; thumps alarm; bolt to warren.",
            distribution: "Widespread.",
            maleTraits: "Bucks slightly larger heads.",
            femaleTraits: "Does may have denser winter coat.",
            youngTraits: "Kittens in underground nests.",
            tracks: "Hind prints longer; often in pairs.",
            droppings: "Round dry pellets in latrines.",
            seasonsNote: "Pest / sporting use under permission \u{2014} no deer-style close season."
        ),
        Species(
            id: "brown_hare",
            commonName: "Brown hare",
            scientificName: "Lepus europaeus",
            group: .otherQuarry,
            summary: "Open-field lagomorph; longer ears and legs than rabbit.",
            habitat: "Arable and mixed farmland.",
            behaviour: "Forms; boxing in spring; very fast when flushed.",
            distribution: "England, Wales, lowland Scotland; scarce NI.",
            maleTraits: "Jacks slightly smaller than does often.",
            femaleTraits: "Does larger on average.",
            youngTraits: "Leverets born furred, eyes open.",
            tracks: "Long hind prints; widely spaced when galloping.",
            droppings: "Larger flatter pellets than rabbit.",
            seasonsNote: "Check local Game Acts / closed periods where they apply."
        ),
        Species(
            id: "pheasant",
            commonName: "Pheasant",
            scientificName: "Phasianus colchicus",
            group: .bird,
            summary: "Principal British gamebird on driven and walked-up shoots.",
            habitat: "Woodland edge, game cover, farmland.",
            behaviour: "Roosts in trees; coveys uncommon \u{2014} flush individually.",
            distribution: "Widespread where released/managed.",
            maleTraits: "Colourful plumage; long tail; red facial wattles.",
            femaleTraits: "Mottled brown camouflage; shorter tail.",
            youngTraits: "Poults duller, shorter tails.",
            tracks: "Three toes forward; walking trail in soft ground.",
            droppings: "Elongated, often with white urea tip.",
            seasonsNote: "England/Wales typical open season 1 Oct \u{2013} 1 Feb (verify)."
        ),
        Species(
            id: "red_legged_partridge",
            commonName: "Red-legged partridge",
            scientificName: "Alectoris rufa",
            group: .bird,
            summary: "Common released partridge on lowland shoots.",
            habitat: "Arable, game cover, chalk downs.",
            behaviour: "Runs strongly; coveys; less inclined to sit tight than grey.",
            distribution: "Widespread in England; more local elsewhere.",
            maleTraits: "Similar plumage; slightly larger.",
            femaleTraits: "Similar; hard to sex in field.",
            youngTraits: "Juveniles duller flank barring.",
            tracks: "Small bird tracks in covey lines.",
            droppings: "Small cylindrical.",
            seasonsNote: "Typical open 1 Sep \u{2013} 1 Feb (verify)."
        ),
        Species(
            id: "mallard",
            commonName: "Mallard",
            scientificName: "Anas platyrhynchos",
            group: .waterfowl,
            summary: "Most familiar quarry duck on flight ponds and foreshore.",
            habitat: "Freshwater, marshes, coastal waters.",
            behaviour: "Flighting dawn/dusk; dabbling.",
            distribution: "UK-wide.",
            maleTraits: "Green head, yellow bill in eclipse seasons vary.",
            femaleTraits: "Mottled brown; orange bill with dark saddle.",
            youngTraits: "Ducklings yellow-brown striped.",
            tracks: "Webbed footprints.",
            droppings: "Greenish elongated.",
            seasonsNote: "Inland wildfowl typically 1 Sep \u{2013} 31 Jan (foreshore may differ \u{2014} verify)."
        ),
        Species(
            id: "teal",
            commonName: "Teal",
            scientificName: "Anas crecca",
            group: .waterfowl,
            summary: "Small dabbling duck; often flighted on ponds and foreshore.",
            habitat: "Marshes, flight ponds, estuaries.",
            behaviour: "Fast flight; whistling calls; often in small parties.",
            distribution: "UK-wide winter visitor and local breeder.",
            maleTraits: "Chestnut head with green eye-patch in breeding plumage.",
            femaleTraits: "Mottled brown; green speculum.",
            youngTraits: "Ducklings small, dark.",
            tracks: "Small webbed prints.",
            droppings: "Small elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (varies foreshore vs inland)."
        ),
        Species(
            id: "wigeon",
            commonName: "Wigeon",
            scientificName: "Mareca penelope",
            group: .waterfowl,
            summary: "Grazing duck of marshes and coastal grassland.",
            habitat: "Estuaries, wet grassland, flood meadows.",
            behaviour: "Whistles; often large winter flocks.",
            distribution: "Widespread in winter.",
            maleTraits: "Chestnut head, cream crown stripe.",
            femaleTraits: "Grey-brown; short bill.",
            youngTraits: "Juvenile mottled.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "pintail",
            commonName: "Pintail",
            scientificName: "Anas acuta",
            group: .waterfowl,
            summary: "Elegant dabbling duck with long central tail feathers on drakes.",
            habitat: "Floods, estuaries, large marshes.",
            behaviour: "Often wary; surface feeder.",
            distribution: "Mainly winter visitor.",
            maleTraits: "Chocolate head, white breast, long pin tail.",
            femaleTraits: "Mottled; pointed tail shorter.",
            youngTraits: "Dull juvenile plumage.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "shoveler",
            commonName: "Shoveler",
            scientificName: "Spatula clypeata",
            group: .waterfowl,
            summary: "Broad-billed dabbling duck of shallow marshes.",
            habitat: "Shallow lakes, scrapes, marshes.",
            behaviour: "Filter-feeds with bill sweeping side to side.",
            distribution: "UK-wide; higher numbers in winter.",
            maleTraits: "Green head, white breast, chestnut flanks, huge bill.",
            femaleTraits: "Mottled brown; large spatulate bill.",
            youngTraits: "Juvenile duller.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "gadwall",
            commonName: "Gadwall",
            scientificName: "Mareca strepera",
            group: .waterfowl,
            summary: "Subtle grey dabbling duck of freshwater sites.",
            habitat: "Lakes, gravel pits, marshes.",
            behaviour: "Often quiet; surface feeds.",
            distribution: "Increasing; widely wintering.",
            maleTraits: "Grey body, black rear, white speculum.",
            femaleTraits: "Mottled; orange-sided bill.",
            youngTraits: "Juvenile mottled.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "goldeneye",
            commonName: "Goldeneye",
            scientificName: "Bucephala clangula",
            group: .waterfowl,
            summary: "Diving duck of lakes and coasts; whistling wings.",
            habitat: "Deep freshwater, coastal waters.",
            behaviour: "Dives for food; distinctive wing noise.",
            distribution: "Winter visitor; scarce breeder Scotland.",
            maleTraits: "Black/white body; circular white face patch.",
            femaleTraits: "Grey body, chocolate head.",
            youngTraits: "Juvenile greyish.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "tufted_duck",
            commonName: "Tufted duck",
            scientificName: "Aythya fuligula",
            group: .waterfowl,
            summary: "Common diving duck with hanging crest on drakes.",
            habitat: "Lakes, reservoirs, gravel pits.",
            behaviour: "Dives; often in flocks.",
            distribution: "UK-wide.",
            maleTraits: "Black/white; long crest.",
            femaleTraits: "Brown; shorter crest; yellow eye.",
            youngTraits: "Brown juvenile.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "pochard",
            commonName: "Pochard",
            scientificName: "Aythya ferina",
            group: .waterfowl,
            summary: "Diving duck; chestnut-headed drake.",
            habitat: "Lakes, reservoirs, coastal lagoons.",
            behaviour: "Often rafts on open water.",
            distribution: "Widespread in winter.",
            maleTraits: "Chestnut head, black breast, grey body.",
            femaleTraits: "Brownish; diffuse face pattern.",
            youngTraits: "Dull brown.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "scaup",
            commonName: "Scaup",
            scientificName: "Aythya marila",
            group: .waterfowl,
            summary: "Marine and large-water diving duck.",
            habitat: "Coasts, large reservoirs.",
            behaviour: "Often coastal rafts in winter.",
            distribution: "Mainly winter visitor.",
            maleTraits: "Black head with green sheen, grey back.",
            femaleTraits: "Brown; white face patch at bill base.",
            youngTraits: "Brown juvenile.",
            tracks: "Webbed.",
            droppings: "Elongated.",
            seasonsNote: "Duck: 1 Sep \u{2013} 31 Jan (verify foreshore)."
        ),
        Species(
            id: "canada_goose",
            commonName: "Canada goose",
            scientificName: "Branta canadensis",
            group: .waterfowl,
            summary: "Large introduced goose; common farmland and parkland bird.",
            habitat: "Grassland, lakes, farmland.",
            behaviour: "Grazing flocks; loud honking.",
            distribution: "UK-wide feral/naturalised.",
            maleTraits: "Black head/neck, white chinstrap.",
            femaleTraits: "As male; sexes similar.",
            youngTraits: "Goslings yellow-olive.",
            tracks: "Large webbed.",
            droppings: "Large cylindrical.",
            seasonsNote: "Geese: 1 Sep \u{2013} 31 Jan (verify)."
        ),
        Species(
            id: "greylag_goose",
            commonName: "Greylag goose",
            scientificName: "Anser anser",
            group: .waterfowl,
            summary: "Large grey goose; ancestral domestic goose.",
            habitat: "Wet grassland, marshes, farmland.",
            behaviour: "Grazing; loud calls.",
            distribution: "UK-wide; wild and feral.",
            maleTraits: "Orange bill; grey-brown body.",
            femaleTraits: "As male.",
            youngTraits: "Goslings yellow.",
            tracks: "Large webbed.",
            droppings: "Large cylindrical.",
            seasonsNote: "Geese: 1 Sep \u{2013} 31 Jan (verify)."
        ),
        Species(
            id: "pink_footed_goose",
            commonName: "Pink-footed goose",
            scientificName: "Anser brachyrhynchus",
            group: .waterfowl,
            summary: "Wintering goose of farmland and estuaries.",
            habitat: "Coastal farmland, mosses, estuaries.",
            behaviour: "Large winter skeins; pink legs/feet.",
            distribution: "Strong winter concentrations (esp. Scotland/NW England).",
            maleTraits: "Dark head/neck; pink bill band; pink feet.",
            femaleTraits: "As male.",
            youngTraits: "Juvenile duller.",
            tracks: "Webbed.",
            droppings: "Cylindrical.",
            seasonsNote: "Geese: 1 Sep \u{2013} 31 Jan (verify)."
        ),
        Species(
            id: "white_fronted_goose",
            commonName: "European white-fronted goose",
            scientificName: "Anser albifrons",
            group: .waterfowl,
            summary: "Wintering goose with white face blaze and belly bars.",
            habitat: "Flood meadows, coastal marshes.",
            behaviour: "Often with other grey geese.",
            distribution: "Local winter sites (e.g. SW England, Wales).",
            maleTraits: "White frontal blaze; black belly bars in adults.",
            femaleTraits: "As male.",
            youngTraits: "Juveniles lack full belly bars.",
            tracks: "Webbed.",
            droppings: "Cylindrical.",
            seasonsNote: "Geese: 1 Sep \u{2013} 31 Jan (verify)."
        ),
        Species(
            id: "common_snipe",
            commonName: "Common snipe",
            scientificName: "Gallinago gallinago",
            group: .waterfowl,
            summary: "Cryptic wader of wet ground; zigzag flush.",
            habitat: "Bogs, wet meadows, rushy pasture.",
            behaviour: "Sits tight; explosive zig-zag flight.",
            distribution: "UK-wide; more numerous in winter.",
            maleTraits: "Long bill; cryptic plumage; sexes similar.",
            femaleTraits: "As male.",
            youngTraits: "Chicks precocial.",
            tracks: "Narrow wader prints.",
            droppings: "Small.",
            seasonsNote: "12 Aug \u{2013} 31 Jan (verify nation)."
        ),
        Species(
            id: "woodcock",
            commonName: "Woodcock",
            scientificName: "Scolopax rusticola",
            group: .waterfowl,
            summary: "Woodland wader; crepuscular; prized flown bird.",
            habitat: "Damp woodland, rides, coverts.",
            behaviour: "Roding at dusk; sits tight by day.",
            distribution: "UK-wide; strong winter influx.",
            maleTraits: "Cryptic; large eye; long bill; sexes similar.",
            femaleTraits: "As male.",
            youngTraits: "Chicks in leaf litter.",
            tracks: "Rarely seen in soft mud.",
            droppings: "Occasional white splash under roost.",
            seasonsNote: "1 Oct \u{2013} 31 Jan (verify)."
        ),
        Species(
            id: "woodpigeon",
            commonName: "Woodpigeon",
            scientificName: "Columba palumbus",
            group: .bird,
            summary: "Large pigeon; major farmland crop feeder.",
            habitat: "Farmland, woodland edge, gardens.",
            behaviour: "Flocks on crops; clattering take-off.",
            distribution: "UK-wide and abundant.",
            maleTraits: "White neck patch; pinkish breast; sexes similar.",
            femaleTraits: "As male.",
            youngTraits: "Squabs duller, no white neck.",
            tracks: "Small bird tracks.",
            droppings: "White/uric spots under roosts.",
            seasonsNote: "Often year-round under General Licences for crop protection; also seasonal quarry rules may apply \u{2014} verify."
        ),
        Species(
            id: "american_mink",
            commonName: "American mink",
            scientificName: "Neovison vison",
            group: .pest,
            summary: "Invasive mustelid; significant predator of waterbirds and water voles.",
            habitat: "Rivers, wetlands, coasts, farm buildings.",
            behaviour: "Mostly solitary; strong swimmer; nocturnal/crepuscular.",
            distribution: "Widespread across much of Britain.",
            maleTraits: "Larger; dark brown/black fur; white chin patch variable.",
            femaleTraits: "Smaller; similar colour.",
            youngTraits: "Kits in dens near water.",
            tracks: "Five-toed prints; often paired near water.",
            droppings: "Twisted scats with bone/fur/fish, musky.",
            seasonsNote: "Pest species \u{2014} control under relevant licences/law and landowner permission; no Game Act open season."
        ),
        Species(
            id: "grey_squirrel",
            commonName: "Grey squirrel",
            scientificName: "Sciurus carolinensis",
            group: .pest,
            summary: "Invasive tree squirrel; damages woodland and competes with red squirrel.",
            habitat: "Broadleaf woodland, parks, gardens, hedgerows.",
            behaviour: "Diurnal; caches nuts; nests (dreys) in trees.",
            distribution: "Widespread England/Wales; expanding in Scotland.",
            maleTraits: "Grey coat; bushy tail; sexes similar in field.",
            femaleTraits: "As male.",
            youngTraits: "Kits in dreys.",
            tracks: "Four toes front / five hind; often on soft ground under trees.",
            droppings: "Small dark pellets; not always obvious.",
            seasonsNote: "Pest species \u{2014} control under General Licence / landowner rules; no sporting close season."
        ),
        Species(
            id: "carrion_crow",
            commonName: "Common crow",
            scientificName: "Corvus corone (carrion crow)",
            group: .corvid,
            summary: "The familiar all-black crow of lowland Britain \u{2014} also called the carrion crow. A bold, intelligent generalist often seen alone or in pairs.",
            habitat: "Farmland, woodland edge, heath, coast, parks and towns. Uses trees, cliffs and buildings for nesting.",
            behaviour: "Territorial pairs for much of the year; walks and hops on pasture and plough. Omnivorous \u{2014} takes carrion, invertebrates, eggs and grain. Deep, hoarse caw; acrobatic flight in wind.",
            distribution: "England, Wales and much of lowland Scotland. Replaced by hooded crow in north and west Scotland and Northern Ireland.",
            maleTraits: "All black with slight purple-blue sheen in strong light. Heavy black bill; sturdy legs. Sexes look alike in the field.",
            femaleTraits: "As male; slightly smaller on average but not reliably separable without close handling.",
            youngTraits: "Duller, browner black when fledging; pink gape visible in very young birds.",
            tracks: "Large crow prints \u{2014} three toes forward, one back \u{2014} often on soft arable, mud or snow.",
            droppings: "White uric splashes with dark centres; accumulates under roosts and nest trees.",
            seasonsNote: "May be controlled under General Licences for crop, livestock and wildlife protection \u{2014} verify current GL terms and nesting restrictions."
        ),
        Species(
            id: "hooded_crow",
            commonName: "Hooded crow",
            scientificName: "Corvus cornix",
            group: .corvid,
            summary: "Grey body with black head, wings and tail; replaces carrion crow in north and west.",
            habitat: "Coast, moorland edge, farmland, towns.",
            behaviour: "Similar to carrion crow; scavenges and predates eggs/chicks.",
            distribution: "Scotland, Northern Ireland, Isle of Man and NW England.",
            maleTraits: "Grey mantle; black head, throat, wings and tail.",
            femaleTraits: "As male.",
            youngTraits: "Browner, less contrast when fledging.",
            tracks: "As carrion crow.",
            droppings: "As carrion crow.",
            seasonsNote: "General Licence control may apply \u{2014} verify nation and current GL terms."
        ),
        Species(
            id: "rook",
            commonName: "Rook",
            scientificName: "Corvus frugilegus",
            group: .corvid,
            summary: "Colonial farmland crow with a bare grey-white face and shaggy thigh feathers. Feeds in noisy flocks on plough and pasture.",
            habitat: "Open farmland with mature trees for large rookeries \u{2014} often in parkland, village edges and broadleaf woods.",
            behaviour: "Nests in crowded colonies from late winter. Feeds in flocks probing grassland and arable for leatherjackets and worms. More sociable than carrion crow; bill often visible as pale at base.",
            distribution: "UK-wide except treeless upland core. Absent from some inner-city areas.",
            maleTraits: "Black plumage; peaked crown; shaggy \u{2018}trousers\u{2019} on thighs. Bare grey-white skin at bill base \u{2014} the key ID feature. Sexes alike.",
            femaleTraits: "As male.",
            youngTraits: "Face fully feathered until first autumn moult \u{2014} can be confused with carrion crow when young.",
            tracks: "Crow-like; often many birds together on recently worked arable.",
            droppings: "White splashes concentrated under rookery trees in winter and spring.",
            seasonsNote: "General Licence control may apply \u{2014} rookeries and active nests are protected during the breeding season."
        ),
        Species(
            id: "jackdaw",
            commonName: "Jackdaw",
            scientificName: "Corvus monedula",
            group: .corvid,
            summary: "Britain\u{2019}s smallest crow \u{2014} compact, sociable and pale-eyed, with a grey nape and black cap. Nests in holes in trees, cliffs and buildings.",
            habitat: "Farmland, parkland, towns, cliffs and quarries. Often around churches, chimneys and old woodland.",
            behaviour: "Highly social \u{2014} chattering groups wheel over fields or roost together. Pairs nest in cavities. Frequently associates with rooks and starlings on pasture.",
            distribution: "UK-wide and common.",
            maleTraits: "Grey nape and shoulders; black cap, wings and body. Striking pale silvery-white eye. Smaller and neater than carrion crow. Sexes alike.",
            femaleTraits: "As male.",
            youngTraits: "Duller plumage; eye colour takes time to become fully pale.",
            tracks: "Smaller than other crow prints; often near nest holes or livestock pens.",
            droppings: "Small white splashes under chimney and cavity nest sites.",
            seasonsNote: "General Licence control may apply \u{2014} verify current GL terms and avoid active nest sites."
        ),
        Species(
            id: "magpie",
            commonName: "Magpie",
            scientificName: "Pica pica",
            group: .corvid,
            summary: "Distinctive black-and-white corvid with an exceptionally long tail. Bold, noisy and territorial \u{2014} a significant predator of eggs and young birds.",
            habitat: "Woodland edge, hedgerows, thickets, gardens, farmland and suburban areas with cover for nesting.",
            behaviour: "Pairs hold territory year-round. Conspicuous chattering and rattling calls. Builds a large domed nest of sticks in dense cover. Hops along ground and fence lines when foraging.",
            distribution: "UK-wide and common.",
            maleTraits: "Bold black-and-white pattern; iridescent blue-green sheen on wings and tail in good light. Long graduated tail obvious in flight. Sexes alike.",
            femaleTraits: "As male.",
            youngTraits: "Shorter tail and duller plumage when recently fledged.",
            tracks: "Hops rather than walks; long tail sometimes drags in soft mud or snow.",
            droppings: "White and dark splashes under nest trees and regular perches.",
            seasonsNote: "General Licence control may apply for crop and wildlife protection \u{2014} verify current GL terms."
        ),
        Species(
            id: "jay",
            commonName: "Jay",
            scientificName: "Garrulus glandarius",
            group: .corvid,
            summary: "Pinkish woodland corvid with blue wing patch; caches acorns.",
            habitat: "Deciduous and mixed woodland, especially oak.",
            behaviour: "Shy; explosive alarm; stores acorns; nests in cover.",
            distribution: "England, Wales and S Scotland; patchy further north.",
            maleTraits: "Pink-brown body; black moustache; bright blue wing panel.",
            femaleTraits: "As male.",
            youngTraits: "Duller; less blue on wing.",
            tracks: "Heavy bird tracks in mud/snow.",
            droppings: "Often under oak feeding areas.",
            seasonsNote: "General Licence control may apply in some circumstances \u{2014} verify current GL terms."
        ),
        Species(
            id: "brown_rat",
            commonName: "Brown rat",
            scientificName: "Rattus norvegicus",
            group: .pest,
            summary: "Common commensal rodent; burrows and damages grain stores and wildlife.",
            habitat: "Farm buildings, hedgerows, watercourses, rubbish tips.",
            behaviour: "Nocturnal; burrows; excellent swimmer; breeds year-round.",
            distribution: "UK-wide and abundant.",
            maleTraits: "Brown-grey fur; blunt nose; tail shorter than body; sexes similar.",
            femaleTraits: "As male; slightly smaller.",
            youngTraits: "Pale grey kits.",
            tracks: "Four-toed front / five-toed hind; tail drag common.",
            droppings: "Dark spindle-shaped pellets, 1\u{2013}2 cm.",
            seasonsNote: "Pest species \u{2014} control under landowner permission and relevant law; no Game Act season."
        ),
        Species(
            id: "black_rat",
            commonName: "Black rat",
            scientificName: "Rattus rattus",
            group: .pest,
            summary: "Slender ship rat; now rare but still found near some ports and poultry units.",
            habitat: "Buildings, ports, warehouses, roof spaces.",
            behaviour: "Agile climber; nests off ground; less common than brown rat.",
            distribution: "Scattered coastal/port areas; much rarer than brown rat.",
            maleTraits: "Sleek black or brown coat; pointed nose; tail longer than body.",
            femaleTraits: "As male.",
            youngTraits: "Pale grey kits.",
            tracks: "Finer than brown rat; often on beams/ledges.",
            droppings: "Smaller spindle pellets than brown rat.",
            seasonsNote: "Pest species \u{2014} control under landowner permission and relevant law."
        )
    ]
    
    public static let seasons: [LegalSeason] = {
        var list: [LegalSeason] = []
        
        func deer(_ speciesId: String, _ nation: UkNation, _ sex: String, _ from: String, _ to: String, _ notes: String = "") {
            list.append(LegalSeason(
                speciesId: speciesId,
                nation: nation,
                sexLabel: sex,
                openFromMonthDay: from,
                openToMonthDay: to,
                notes: notes
            ))
        }
        
        for n in [UkNation.england, UkNation.wales] {
            deer("red_deer", n, "Stag", "01-08", "30-04")
            deer("red_deer", n, "Hind", "01-11", "31-03")
            deer("fallow_deer", n, "Buck", "01-08", "30-04")
            deer("fallow_deer", n, "Doe", "01-11", "31-03")
            deer("sika_deer", n, "Stag", "01-08", "30-04")
            deer("sika_deer", n, "Hind", "01-11", "31-03")
            deer("roe_deer", n, "Buck", "01-04", "31-10")
            deer("roe_deer", n, "Doe", "01-11", "31-03")
            deer("chinese_water_deer", n, "Buck", "01-11", "31-03")
            deer("chinese_water_deer", n, "Doe", "01-11", "31-03")
            deer("muntjac", n, "Either", "01-01", "31-12", "No statutory close season \u{2014} still apply best practice.")
        }
        
        deer("red_deer", .scotland, "Stag", "01-01", "31-12", "Male deer permanently open in Scotland.")
        deer("red_deer", .scotland, "Hind", "21-10", "15-02")
        deer("roe_deer", .scotland, "Buck", "01-01", "31-12", "Male deer permanently open in Scotland.")
        deer("roe_deer", .scotland, "Doe", "21-10", "31-03")
        deer("sika_deer", .scotland, "Stag", "01-01", "31-12", "Male deer permanently open in Scotland.")
        deer("sika_deer", .scotland, "Hind", "21-10", "15-02")
        deer("fallow_deer", .scotland, "Buck", "01-01", "31-12", "Male deer permanently open in Scotland.")
        deer("fallow_deer", .scotland, "Doe", "21-10", "15-02")
        deer("red_deer", .northernIreland, "Stag", "01-08", "30-04", "Confirm with DAERA.")
        deer("red_deer", .northernIreland, "Hind", "01-11", "31-03", "Confirm with DAERA.")
        deer("roe_deer", .northernIreland, "Buck", "01-04", "31-10")
        deer("roe_deer", .northernIreland, "Doe", "01-11", "31-03")
        
        list.append(LegalSeason(speciesId: "pheasant", nation: .england, sexLabel: "Either", openFromMonthDay: "01-10", openToMonthDay: "01-02", notes: ""))
        list.append(LegalSeason(speciesId: "pheasant", nation: .wales, sexLabel: "Either", openFromMonthDay: "01-10", openToMonthDay: "01-02", notes: ""))
        list.append(LegalSeason(speciesId: "pheasant", nation: .scotland, sexLabel: "Either", openFromMonthDay: "01-10", openToMonthDay: "01-02", notes: ""))
        list.append(LegalSeason(speciesId: "red_legged_partridge", nation: .england, sexLabel: "Either", openFromMonthDay: "01-09", openToMonthDay: "01-02", notes: ""))
        
        let ducks = [
            "mallard", "teal", "wigeon", "pintail", "shoveler", "gadwall",
            "goldeneye", "tufted_duck", "pochard", "scaup"
        ]
        let geese = ["canada_goose", "greylag_goose", "pink_footed_goose", "white_fronted_goose"]
        
        for n in [UkNation.england, UkNation.wales, UkNation.scotland] {
            for id in ducks {
                list.append(LegalSeason(
                    speciesId: id,
                    nation: n,
                    sexLabel: "Either",
                    openFromMonthDay: "01-09",
                    openToMonthDay: "31-01",
                    notes: "Waterfowl: dates can vary foreshore vs inland \u{2014} verify for your region."
                ))
            }
            for id in geese {
                list.append(LegalSeason(speciesId: id, nation: n, sexLabel: "Either", openFromMonthDay: "01-09", openToMonthDay: "31-01", notes: "Goose open season typically 1 Sep \u{2013} 31 Jan \u{2014} verify."))
            }
            list.append(LegalSeason(speciesId: "common_snipe", nation: n, sexLabel: "Either", openFromMonthDay: "12-08", openToMonthDay: "31-01", notes: "Common snipe \u{2014} verify nation."))
            list.append(LegalSeason(speciesId: "woodcock", nation: n, sexLabel: "Either", openFromMonthDay: "01-10", openToMonthDay: "31-01", notes: "Woodcock \u{2014} verify nation."))
        }
        
        for n in [UkNation.england, UkNation.wales, UkNation.scotland, UkNation.northernIreland] {
            list.append(LegalSeason(
                speciesId: "woodpigeon",
                nation: n,
                sexLabel: "n/a",
                openFromMonthDay: "01-01",
                openToMonthDay: "31-12",
                notes: "May be taken year-round under General Licences for crop protection; quarry seasons can also apply \u{2014} verify current GL terms."
            ))
            list.append(LegalSeason(
                speciesId: "american_mink",
                nation: n,
                sexLabel: "n/a",
                openFromMonthDay: "01-01",
                openToMonthDay: "31-12",
                notes: "Pest control \u{2014} licences, welfare and landowner permission apply; not a Game Act open/close season."
            ))
            list.append(LegalSeason(
                speciesId: "grey_squirrel",
                nation: n,
                sexLabel: "n/a",
                openFromMonthDay: "01-01",
                openToMonthDay: "31-12",
                notes: "Pest control under General Licence / landowner rules \u{2014} verify current law."
            ))
            for id in ["carrion_crow", "hooded_crow", "rook", "jackdaw", "magpie", "jay"] {
                list.append(LegalSeason(
                    speciesId: id,
                    nation: n,
                    sexLabel: "n/a",
                    openFromMonthDay: "01-01",
                    openToMonthDay: "31-12",
                    notes: "May be controlled under General Licences for crop/livestock/wildlife protection \u{2014} verify current GL terms and nesting restrictions."
                ))
            }
            for id in ["brown_rat", "black_rat"] {
                list.append(LegalSeason(
                    speciesId: id,
                    nation: n,
                    sexLabel: "n/a",
                    openFromMonthDay: "01-01",
                    openToMonthDay: "31-12",
                    notes: "Pest control \u{2014} landowner permission and relevant law apply; no Game Act open season."
                ))
            }
        }
        
        list.append(LegalSeason(speciesId: "fox", nation: .england, sexLabel: "n/a", openFromMonthDay: "\u{2014}", openToMonthDay: "\u{2014}", notes: "Not under Deer Act \u{2014} permission & firearms law apply."))
        list.append(LegalSeason(speciesId: "rabbit", nation: .england, sexLabel: "n/a", openFromMonthDay: "\u{2014}", openToMonthDay: "\u{2014}", notes: "Pest / sporting under permission."))
        
        return list
    }()
    
    public static let calls: [AnimalCall] = [
        AnimalCall(
            id: "roe_growl",
            name: "Roe buck growl",
            speciesId: "roe_deer",
            category: "Deer",
            description: "Real male roe deer growl recording",
            packId: "uk_deer_pack",
            // Wikimedia auto-generated MP3 transcode of the same recording.
            // iOS AVFoundation cannot decode Ogg Vorbis, so the original
            // .ogg URL is unusable on device even though it serves fine.
            remoteUrl: "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c8/Male_roe_deer_growl.ogg/Male_roe_deer_growl.ogg.mp3",
            isBuiltIn: true,
            filePath: nil,
            credit: "Wikimedia Commons \u{00B7} Male roe deer growl"
        ),
        AnimalCall(
            id: "fox_field",
            name: "Red fox field call",
            speciesId: "fox",
            category: "Fox",
            description: "Real red fox vocalisation",
            packId: "uk_fox_pack",
            // MP3 transcode of the same recording — see roe_growl note above.
            remoteUrl: "https://upload.wikimedia.org/wikipedia/commons/transcoded/7/79/Red_Fox_%28Vulpes_vulpes%29_%28W1CDR0001529_BD12%29.ogg/Red_Fox_%28Vulpes_vulpes%29_%28W1CDR0001529_BD12%29.ogg.mp3",
            isBuiltIn: true,
            filePath: nil,
            credit: "Wikimedia Commons / BL \u{00B7} Red Fox"
        ),
        AnimalCall(
            id: "duck_pack_a",
            name: "Ducks (wild)",
            speciesId: "mallard",
            category: "Bird",
            description: "Outdoor duck calls \u{2014} BigSoundBank CC0",
            packId: "uk_wildfowl_pack",
            remoteUrl: "https://bigsoundbank.com/UPLOAD/mp3/0276.mp3",
            isBuiltIn: true,
            filePath: nil,
            credit: "BigSoundBank \u{00B7} Ducks (CC0)"
        ),
        AnimalCall(
            id: "mallard_xc",
            name: "Mallard call",
            speciesId: "mallard",
            category: "Bird",
            description: "Mallard recording via xeno-canto",
            packId: "uk_wildfowl_pack",
            remoteUrl: "https://xeno-canto.org/62258/download",
            isBuiltIn: true,
            filePath: nil,
            credit: "xeno-canto XC62258 \u{00B7} Mallard"
        ),
        AnimalCall(
            id: "mallard_xc2",
            name: "Mallard call 2",
            speciesId: "mallard",
            category: "Bird",
            description: "Second mallard field recording",
            packId: "uk_wildfowl_pack",
            remoteUrl: "https://xeno-canto.org/112828/download",
            isBuiltIn: true,
            filePath: nil,
            credit: "xeno-canto XC112828 \u{00B7} Mallard"
        ),
        AnimalCall(
            id: "goose_hail",
            name: "Canada goose",
            speciesId: "canada_goose",
            category: "Bird",
            description: "Canada goose calls (introduced UK bird)",
            packId: "uk_wildfowl_pack",
            remoteUrl: "https://upload.wikimedia.org/wikipedia/commons/a/a7/Branta_canadensis_-_Canada_Goose_XC62259.mp3",
            isBuiltIn: true,
            filePath: nil,
            credit: "Wikimedia / xeno-canto XC62259"
        ),
        AnimalCall(
            id: "owl_long_eared",
            name: "Long-eared owl",
            speciesId: "bird",
            category: "Bird",
            description: "UK woodland owl \u{2014} useful night field reference",
            packId: "uk_woodland_pack",
            remoteUrl: "https://bigsoundbank.com/UPLOAD/mp3/0936.mp3",
            isBuiltIn: true,
            filePath: nil,
            credit: "BigSoundBank \u{00B7} Long-eared owl (CC0)"
        ),
        AnimalCall(
            id: "owl_barn",
            name: "Barn owl",
            speciesId: "bird",
            category: "Bird",
            description: "Barn owl hiss/calls \u{2014} BigSoundBank CC0",
            packId: "uk_woodland_pack",
            remoteUrl: "https://bigsoundbank.com/UPLOAD/mp3/1400.mp3",
            isBuiltIn: true,
            filePath: nil,
            credit: "BigSoundBank \u{00B7} Barn owl (CC0)"
        ),
        AnimalCall(
            id: "owl_tawny",
            name: "Tawny owls",
            speciesId: "bird",
            category: "Bird",
            description: "Tawny owl chorus \u{2014} classic UK night sound",
            packId: "uk_woodland_pack",
            remoteUrl: "https://bigsoundbank.com/UPLOAD/mp3/0429.mp3",
            isBuiltIn: true,
            filePath: nil,
            credit: "BigSoundBank \u{00B7} Tawny owls (CC0)"
        )
    ]
    
    public static func speciesById(_ id: String) -> Species? {
        species.first { $0.id == id }
    }
    
    /// Species chips for the cull log (not the full catalog).
    /// Ordered for common UK cull / pest quarry — includes grey squirrel.
    public static let cullLogSpecies: [Species] = [
        "red_deer", "roe_deer", "fallow_deer", "sika_deer", "muntjac", "chinese_water_deer",
        "fox", "rabbit", "brown_hare", "grey_squirrel", "american_mink", "brown_rat",
        "pheasant", "woodpigeon", "carrion_crow", "magpie"
    ].compactMap { UkFieldCatalog.speciesById($0) }
    
    /// UK field names for male/female by species (BDS deer terms, drake/hen, cock/hen, etc.).
    public static func sexTermsFor(_ speciesId: String) -> (male: String, female: String) {
        return sexTerms[speciesId] ?? (male: "Male", female: "Female")
    }
    
    public static func maleTermFor(_ speciesId: String) -> String {
        sexTermsFor(speciesId).male
    }
    
    public static func femaleTermFor(_ speciesId: String) -> String {
        sexTermsFor(speciesId).female
    }
    
    private static let sexTerms: [String: (male: String, female: String)] = [
        // Deer — British Deer Society
        "red_deer": (male: "Stag", female: "Hind"),
        "sika_deer": (male: "Stag", female: "Hind"),
        "roe_deer": (male: "Buck", female: "Doe"),
        "fallow_deer": (male: "Buck", female: "Doe"),
        "muntjac": (male: "Buck", female: "Doe"),
        "chinese_water_deer": (male: "Buck", female: "Doe"),
        // Mammals
        "fox": (male: "Dog", female: "Vixen"),
        "rabbit": (male: "Buck", female: "Doe"),
        "brown_hare": (male: "Jack", female: "Jill"),
        "american_mink": (male: "Dog", female: "Bitch"),
        "grey_squirrel": (male: "Buck", female: "Doe"),
        "brown_rat": (male: "Buck", female: "Doe"),
        "black_rat": (male: "Buck", female: "Doe"),
        // Gamebirds
        "pheasant": (male: "Cock", female: "Hen"),
        "red_legged_partridge": (male: "Cock", female: "Hen"),
        "woodpigeon": (male: "Cock", female: "Hen"),
        // Wildfowl — ducks
        "mallard": (male: "Drake", female: "Hen"),
        "teal": (male: "Drake", female: "Hen"),
        "wigeon": (male: "Drake", female: "Hen"),
        "pintail": (male: "Drake", female: "Hen"),
        "shoveler": (male: "Drake", female: "Hen"),
        "gadwall": (male: "Drake", female: "Hen"),
        "goldeneye": (male: "Drake", female: "Hen"),
        "tufted_duck": (male: "Drake", female: "Hen"),
        "pochard": (male: "Drake", female: "Hen"),
        "scaup": (male: "Drake", female: "Hen"),
        // Geese
        "canada_goose": (male: "Gander", female: "Goose"),
        "greylag_goose": (male: "Gander", female: "Goose"),
        "pink_footed_goose": (male: "Gander", female: "Goose"),
        "white_fronted_goose": (male: "Gander", female: "Goose"),
        // Waders
        "common_snipe": (male: "Cock", female: "Hen"),
        "woodcock": (male: "Cock", female: "Hen"),
        // Corvids — no separate common names; sexes look alike in the field
        "carrion_crow": (male: "Male", female: "Female"),
        "hooded_crow": (male: "Male", female: "Female"),
        "rook": (male: "Male", female: "Female"),
        "jackdaw": (male: "Male", female: "Female"),
        "magpie": (male: "Male", female: "Female"),
        "jay": (male: "Male", female: "Female")
    ]
    
    public static func seasonsFor(_ speciesId: String, nation: UkNation? = nil) -> [LegalSeason] {
        seasons.filter { $0.speciesId == speciesId && (nation == nil || $0.nation == nation) }
    }
    
    public static func callsFor(_ speciesId: String? = nil) -> [AnimalCall] {
        guard let speciesId = speciesId else { return calls }
        return calls.filter { $0.speciesId == speciesId }
    }
    
    public static let gooseSpeciesIds: Set<String> = [
        "canada_goose",
        "greylag_goose",
        "pink_footed_goose",
        "white_fronted_goose"
    ]
    
    public static func isGooseSpecies(_ speciesId: String) -> Bool {
        gooseSpeciesIds.contains(speciesId)
    }
    
    public static let gooseMoreInformation = "Geese (Canada, Greylag, Pink-footed, European White-fronted): 1 Sep \u{2013} 31 Jan\n\nOpen season dates can vary slightly by foreshore vs inland in England and Wales. Always verify current law with GOV.UK or your regional authority before shooting.\n\nCanada and greylag occur UK-wide as feral or naturalised birds. Pink-footed geese winter in large skeins, especially Scotland and north-west England. European white-fronted geese use local winter sites such as the Severn Estuary and parts of Wales."
}
