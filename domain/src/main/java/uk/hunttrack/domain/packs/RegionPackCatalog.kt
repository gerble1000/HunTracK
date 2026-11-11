package uk.hunttrack.domain.packs

import kotlin.math.PI
import kotlin.math.cos
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.LegalSeason

/**
 * Downloadable season packs and small offline map areas.
 *
 * The species directory is wider than the UK quarry catalog so a pack can list
 * mouflon, badger, chamois, wild boar and other country-specific animals.
 * A row stores a season only when it comes from the UK catalog or from the
 * sourced field reference. Everything else is an unverified placeholder with
 * no dates.
 */
object RegionPackCatalog {
    const val FORMAT = "hunttrack-region-packs"
    const val VERSION = 1
    const val UK_SEASON_PACK_ID = "uk-seasons"
    const val INTENDED_PRICE = "£1.00"
    const val RULE_SUPPRESSOR = "suppressor"
    const val RULE_NIGHT = "night_hunting"
    const val RULE_ORANGE = "blaze_orange"

    const val MAP_ATTRIBUTION =
        "Personal offline area only, about 15 square miles. " +
            "Tiles are OpenFreeMap Liberty using OpenStreetMap data. " +
            "© OpenStreetMap contributors. https://openfreemap.org · " +
            "https://www.openstreetmap.org/copyright. " +
            "Whole-country bulk downloads are not offered."

    const val PLACEHOLDER_NOTE =
        "Placeholder only. No open or close date is stored. " +
            "This row does not say the species may be hunted, is protected, or has a season. " +
            "Check the official source for this country."

    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    val speciesDirectory: List<PackSpeciesIdentity> = listOf(
        ident("red_deer", "Red deer", "Cervus elaphus"),
        ident("roe_deer", "Roe deer", "Capreolus capreolus"),
        ident("fallow_deer", "Fallow deer", "Dama dama"),
        ident("sika_deer", "Sika deer", "Cervus nippon"),
        ident("chinese_water_deer", "Chinese water deer", "Hydropotes inermis"),
        ident("muntjac", "Reeves's muntjac", "Muntiacus reevesi"),
        ident("mouflon", "Mouflon", "Ovis gmelini"),
        ident("chamois", "Chamois", "Rupicapra rupicapra"),
        ident("alpine_ibex", "Alpine ibex", "Capra ibex"),
        ident("wild_boar", "Wild boar", "Sus scrofa"),
        ident("badger", "Badger", "Meles meles"),
        ident("red_fox", "Red fox", "Vulpes vulpes"),
        ident("brown_hare", "Brown hare", "Lepus europaeus"),
        ident("rabbit", "Rabbit", "Oryctolagus cuniculus"),
        ident("moose", "Moose", "Alces alces"),
        ident("raccoon_dog", "Raccoon dog", "Nyctereutes procyonoides"),
        ident("beaver", "Eurasian beaver", "Castor fiber"),
        ident("pheasant", "Pheasant", "Phasianus colchicus"),
        ident("red_legged_partridge", "Red-legged partridge", "Alectoris rufa"),
        ident("grey_partridge", "Grey partridge", "Perdix perdix"),
        ident("partridge", "Partridge", ""),
        ident("woodcock", "Woodcock", "Scolopax rusticola"),
        ident("ducks", "Ducks", ""),
        ident("mallard", "Mallard", "Anas platyrhynchos"),
        ident("geese", "Geese", ""),
        ident("snipe", "Snipe", "Gallinago gallinago"),
        ident("woodpigeon", "Woodpigeon", "Columba palumbus"),
        ident("red_grouse", "Red grouse", "Lagopus lagopus scotica"),
        ident("teal", "Teal", "Anas crecca"),
        ident("gadwall", "Gadwall", "Mareca strepera"),
        ident("wigeon", "Wigeon", "Mareca penelope"),
        ident("shoveler", "Shoveler", "Spatula clypeata"),
        ident("tufted_duck", "Tufted duck", "Aythya fuligula"),
        ident("golden_plover", "Golden plover", "Pluvialis apricaria"),
        ident("jack_snipe", "Jack snipe", "Lymnocryptes minimus"),
        ident("ruddy_duck", "Ruddy duck", "Oxyura jamaicensis"),
        ident("canada_goose", "Canada goose", "Branta canadensis"),
        ident("greylag_goose", "Greylag goose", "Anser anser"),
        ident("hare", "Hare", "Lepus"),
        ident("iberian_hare", "Iberian hare", "Lepus granatensis"),
        ident("egyptian_mongoose", "Egyptian mongoose", "Herpestes ichneumon"),
        ident("rock_dove", "Rock dove", "Columba livia"),
        ident("stock_dove", "Stock dove", "Columba oenas"),
        ident("turtle_dove", "Turtle dove", "Streptopelia turtur"),
        ident("magpie", "Magpie", "Pica pica"),
        ident("carrion_crow", "Carrion crow", "Corvus corone"),
        ident("pintail", "Pintail", "Anas acuta"),
        ident("pochard", "Common pochard", "Aythya ferina"),
        ident("coot", "Eurasian coot", "Fulica atra"),
        ident("moorhen", "Moorhen", "Gallinula chloropus"),
        ident("quail", "Common quail", "Coturnix coturnix"),
        ident("fieldfare", "Fieldfare", "Turdus pilaris"),
        ident("song_thrush", "Song thrush", "Turdus philomelos"),
        ident("redwing", "Redwing", "Turdus iliacus"),
        ident("mistle_thrush", "Mistle thrush", "Turdus viscivorus"),
        ident("starling", "Common starling", "Sturnus vulgaris"),
    )

    private val directoryByKey = speciesDirectory.associateBy { it.speciesKey }

    val seasonPacks: List<SeasonPack> = listOf(
        ukPack(),
        irelandPack(),
        francePack(),
        germanyPack(),
        estoniaPack(),
        latviaPack(),
        lithuaniaPack(),
        polandPack(),
        romaniaPack(),
        spainPack(),
        portugalPack(),
        italyPack(),
    )

    val mapRegions: List<MapPackRegion> = listOf(
        region("uk-exmoor", "GB", "Exmoor sample", "About 15 square miles on Exmoor.", 51.14, -3.75),
        region("uk-new-forest", "GB", "New Forest sample", "About 15 square miles in the New Forest.", 50.87, -1.62),
        region("uk-cairngorms", "GB", "Cairngorms sample", "About 15 square miles in the Cairngorms, not the whole park.", 57.12, -3.68),
        region("uk-eryri", "GB", "Eryri sample", "About 15 square miles in Eryri (Snowdonia).", 53.07, -4.00),
        region("uk-peak", "GB", "Peak District sample", "About 15 square miles in the Peak District.", 53.35, -1.83),
        region("uk-broads", "GB", "Norfolk Broads sample", "About 15 square miles on the Broads.", 52.70, 1.50),
        region("uk-galloway", "GB", "Galloway sample", "About 15 square miles in Galloway Forest.", 55.08, -4.42),
        region("uk-mourne", "GB", "Mourne sample", "About 15 square miles in the Mournes.", 54.17, -6.05),
        region("ie-wicklow", "IE", "Wicklow sample", "About 15 square miles in the Wicklow Mountains.", 53.00, -6.35),
        region("fr-sologne", "FR", "Sologne sample", "About 15 square miles in the Sologne.", 47.60, 1.92),
        region("de-harz", "DE", "Harz sample", "About 15 square miles in the Harz.", 51.75, 10.65),
        region("ee-soomaa", "EE", "Soomaa sample", "About 15 square miles around Soomaa.", 58.42, 25.05),
        region("lv-kemeri", "LV", "Ķemeri sample", "About 15 square miles around Ķemeri.", 56.95, 23.50),
        region("lt-dzukija", "LT", "Dzūkija sample", "About 15 square miles in Dzūkija.", 54.10, 24.30),
        region("pl-bialowieza", "PL", "Białowieża sample", "About 15 square miles around Białowieża.", 52.70, 23.85),
        region("ro-apuseni", "RO", "Apuseni sample", "About 15 square miles in the Apuseni.", 46.55, 22.75),
        region("es-gredos", "ES", "Gredos sample", "About 15 square miles in the Sierra de Gredos, not the whole range.", 40.25, -5.15),
        region("pt-estrela", "PT", "Serra da Estrela sample", "About 15 square miles in the Serra da Estrela, not the whole range.", 40.32, -7.60),
        region("it-gran-sasso", "IT", "Gran Sasso sample", "About 15 square miles around Gran Sasso, not the whole massif.", 42.45, 13.55),
    )

    fun pack(id: String): SeasonPack? = seasonPacks.firstOrNull { it.id == id }

    fun regionsFor(countryCode: String): List<MapPackRegion> =
        mapRegions.filter { it.countryCode == countryCode }

    fun encode(): String = json.encodeToString(snapshot())

    fun decode(text: String): RegionPackFile = json.decodeFromString(text)

    fun snapshot(): RegionPackFile = RegionPackFile(
        format = FORMAT,
        version = VERSION,
        mapAttribution = MAP_ATTRIBUTION,
        speciesDirectory = speciesDirectory,
        seasonPacks = seasonPacks,
        mapRegions = mapRegions,
    )

    private fun ukPack(): SeasonPack {
        val speciesById = UkFieldCatalog.species.associateBy { it.id }
        val rows = UkFieldCatalog.seasons.map { season -> season.toPackRow(speciesById) }.toMutableList()
        rows += protected(
            key = "badger",
            regionLabel = "UNITED_KINGDOM",
            notes = "Protected under the Protection of Badgers Act 1992. " +
                "No hunting season is stored. Licences are a separate regime and are not described here.",
            sourceUrl = "https://www.legislation.gov.uk/ukpga/1992/51",
            sourceTitle = "Protection of Badgers Act 1992",
        )
        val present = rows.map { it.speciesKey }.toSet()
        speciesDirectory.filter { it.speciesKey !in present }.forEach { identity ->
            rows += PackSpeciesSeason(
                speciesKey = identity.speciesKey,
                commonName = identity.commonName,
                scientificName = identity.scientificName,
                legalStatus = SpeciesLegalStatus.UNVERIFIED,
                quality = PackDataQuality.PLACEHOLDER,
                precision = SeasonPrecision.UNSPECIFIED,
                notes = PLACEHOLDER_NOTE,
                sourceUrl = "https://www.legislation.gov.uk/ukpga/1991/54/schedule/1",
                sourceTitle = "Deer Act 1991, Schedule 1",
            )
        }
        return SeasonPack(
            id = UK_SEASON_PACK_ID,
            countryCode = "GB",
            countryName = "United Kingdom",
            title = "UK seasons",
            summary = "Full UK quarry table already shipped in HunTracK, badger cited as protected, " +
                "and empty slots for other European species with no season. Indicative — verify before you go out. " +
                "Included with the app. The store product ID is ready for when a £1.00 product is created.",
            sourceTitle = "HunTracK UK field catalog; Deer Act 1991; Protection of Badgers Act 1992",
            sourceUrl = "https://www.legislation.gov.uk/ukpga/1991/54/schedule/1",
            disclaimer = UkFieldCatalog.legalDisclaimer,
            species = rows,
            rules = ukRules(),
            productId = productId("GB"),
            intendedPriceLabel = INTENDED_PRICE,
            bundledWithApp = true,
        )
    }

    fun productId(countryCode: String): String = "hunttrack_pack_${countryCode.lowercase()}"

    fun rulesFor(countryCode: String): List<HuntingRule> = when (countryCode) {
        "GB" -> ukRules()
        "FR" -> franceRules()
        "IE" -> irelandRules()
        "DE" -> germanyRules()
        "ES" -> spainRules()
        "PT" -> portugalRules()
        "IT" -> italyRules()
        else -> unverifiedRules(countryCode)
    }

    private fun irelandPack(): SeasonPack = european(
        id = "ie-seasons",
        countryCode = "IE",
        countryName = "Ireland",
        title = "Ireland seasons",
        sourceTitle = "NPWS Open Seasons Order",
        sourceUrl = "https://www.npws.ie/legislation/irish-law/open-seasons-order",
        summary = "Mammal and bird open seasons copied from the NPWS Open Seasons Order page, " +
            "read on 9 November 2026. Deer hunters also need a deer hunting licence. " +
            "Species not in those tables have no season stored.",
    ) {
        val npws = "Copied from the NPWS Open Seasons Order page read on 9 November 2026. " +
            "Shooting with firearms. Wildlife (Wild Mammals) (Open Seasons) (Amendment) Order 2024 (S.I. No. 66 of 2024) " +
            "is the mammal order named on that page."
        cited("fallow_deer", "Male", "01-08", "30-04", "$npws Throughout the State.")
        cited("fallow_deer", "Female / antlerless", "01-11", "31-03", "$npws Throughout the State.")
        cited("sika_deer", "Male", "01-08", "30-04", "$npws Throughout the State.")
        cited("sika_deer", "Female / antlerless", "01-11", "31-03", "$npws Throughout the State.")
        cited("red_deer", "Male", "01-08", "30-04", "$npws Throughout the State except County Kerry.")
        cited("red_deer", "Female / antlerless", "01-11", "31-03", "$npws Throughout the State except County Kerry.")
        cited("muntjac", "Either", "01-09", "31-08", "$npws Throughout the State.")
        cited(
            "hare",
            "Either",
            "26-09",
            "28-02",
            "$npws Also coursing at regulated matches, and hunting with packs of beagles and harriers. " +
                "Excludes the townlands of North East Slob, North West Slob, Big Island, Beggerin Island and The Raven, County Wexford.",
        )
        val birds = "Copied from the NPWS wild-bird open-season table read on 9 November 2026. " +
            "Wildlife (Wild Birds) (Open Seasons) (Amendment) Order 2023 (S.I. No. 421 of 2023)."
        cited("red_grouse", "Either", "01-09", "30-09", "$birds Throughout the State.")
        listOf("mallard", "teal", "gadwall", "wigeon", "shoveler", "tufted_duck", "golden_plover", "snipe", "jack_snipe").forEach { key ->
            cited(key, "Either", "01-09", "31-01", "$birds Throughout the State. Grouped together on the NPWS table.")
        }
        cited("red_legged_partridge", "Either", "01-11", "31-01", "$birds Throughout the State.")
        cited("pheasant", "Cock", "01-11", "31-01", "$birds Throughout the State. Grouped with woodcock on the NPWS table.")
        cited("woodcock", "Either", "01-11", "31-01", "$birds Throughout the State. Grouped with cock pheasant on the NPWS table.")
        cited("woodpigeon", "Either", "01-11", "31-01", "$birds Throughout the State.")
        cited("canada_goose", "Either", "01-09", "15-10", "$birds First window: throughout the State.")
        cited(
            "canada_goose",
            "Either",
            "16-10",
            "31-01",
            "$birds Second window: counties of Cavan (excluding the townlands of Eonish Island, Rinn and Deramfield) " +
                "and Leitrim (excluding the River Shannon).",
        )
        cited("greylag_goose", "Either", "01-09", "15-10", "$birds First window: throughout the State.")
        cited(
            "greylag_goose",
            "Either",
            "16-10",
            "31-01",
            "$birds Second window: Lady's Island, County Wexford, and Gearagh East and Gearagh West, County Cork.",
        )
        cited(
            "ruddy_duck",
            "Either",
            "01-09",
            "31-01",
            "$birds The places cell on the fetched page was cut off after \"Throughout the Stat\". Confirm the places column on the NPWS page.",
        )
        notListed(
            "roe_deer",
            "Not listed in the mammal or bird tables on the NPWS Open Seasons Order page read on 9 November 2026. No open season is stored.",
        )
        notListed(
            "wild_boar",
            "Not listed in the mammal or bird tables on the NPWS Open Seasons Order page read on 9 November 2026. No open season is stored.",
        )
    }

    private fun francePack(): SeasonPack = european(
        id = "fr-seasons",
        countryCode = "FR",
        countryName = "France",
        title = "France seasons",
        sourceTitle = "Service-Public hunting seasons by département",
        sourceUrl = "https://www.service-public.gouv.fr/particuliers/vosdroits/R59991",
        summary = "No single national calendar is bundled. Dates are set by département arrêté. " +
            "Extra quarry such as mouflon, chamois and badger are listed without dates.",
    ) {
        unstated("roe_deer", "Field reference: roe often from June. The département arrêté sets the date. No date is stored.")
        unstated("red_deer", "Field reference: red deer often from September. No date is stored.")
        unstated("wild_boar", "Field reference: boar often about June to May, and rules vary. No date is stored.")
        unstated("pheasant", "Field reference: from the general open; the close varies. No date is stored.")
        unstated("partridge", "Field reference: from the general open; the close varies. No date is stored.")
        place(
            "mouflon", "chamois", "alpine_ibex", "badger", "fallow_deer", "sika_deer",
            "brown_hare", "red_fox", "grey_partridge", "woodcock", "mallard",
        )
    }

    private fun germanyPack(): SeasonPack = european(
        id = "de-seasons",
        countryCode = "DE",
        countryName = "Germany",
        title = "Germany seasons",
        sourceTitle = "Federal typical hunting seasons (Länder differ)",
        sourceUrl = "https://schonzeiten.de/jagdzeiten-deutschland-bundesweit-jagd/",
        summary = "Approximate federal pattern from the field reference. Each Bundesland sets its own seasons. " +
            "Mouflon, chamois, ibex and badger are placeholders with no dates.",
    ) {
        approx("red_deer", "Either", "01-08", "31-01", "Federal typical pattern. The Bundesland is authoritative.")
        approx("roe_deer", "Buck", "01-05", "15-10", "Federal typical pattern. The Bundesland is authoritative.")
        approx("roe_deer", "Doe / kid", "01-09", "31-01", "Federal typical pattern. The Bundesland is authoritative.")
        approx("fallow_deer", "Either", "01-09", "31-01", "Grouped with sika in the field reference.")
        approx("sika_deer", "Either", "01-09", "31-01", "Grouped with fallow in the field reference.")
        yearRound("wild_boar", "Often year-round. Confirm the Bundesland. Not a guarantee.")
        approx("pheasant", "Either", "01-10", "15-01", "Federal typical pattern. The Bundesland is authoritative.")
        approx("woodcock", "Either", "16-10", "15-01", "Federal typical pattern. The Bundesland is authoritative.")
        place(
            "mouflon", "chamois", "alpine_ibex", "badger", "brown_hare", "red_fox",
            "rabbit", "grey_partridge", "mallard", "moose",
        )
    }

    private fun estoniaPack(): SeasonPack = european(
        id = "ee-seasons",
        countryCode = "EE",
        countryName = "Estonia",
        title = "Estonia seasons",
        sourceTitle = "Jahieeskiri (Riigi Teataja)",
        sourceUrl = "https://www.riigiteataja.ee/akt/125012019008",
        summary = "Approximate windows from the field reference of the hunting rules. " +
            "Moose, raccoon dog, beaver, mouflon and badger have no dates stored.",
    ) {
        approx("red_deer", "Stag", "01-09", "15-02", "Field reference of the hunting rules. Verify the current text.")
        approx("red_deer", "Hind / calf", "15-08", "31-01", "Field reference of the hunting rules. Verify the current text.")
        approx("roe_deer", "Buck", "01-06", "31-01", "Field reference of the hunting rules. Verify the current text.")
        approx("roe_deer", "Doe / fawn", "01-09", "31-01", "Field reference of the hunting rules. Verify the current text.")
        yearRound("wild_boar", "Field reference: year-round. Verify the current text.")
        approx("pheasant", "Either", "01-10", "28-02", "Field reference said through the end of February.")
        approx("woodcock", "Either", "01-08", "30-11", "Field reference of the hunting rules. Verify the current text.")
        approx("ducks", "Either", "20-08", "31-10", "Field reference of the hunting rules. Verify the current text.")
        place("moose", "raccoon_dog", "beaver", "badger", "mouflon", "chamois", "brown_hare", "red_fox")
    }

    private fun latviaPack(): SeasonPack = european(
        id = "lv-seasons",
        countryCode = "LV",
        countryName = "Latvia",
        title = "Latvia seasons",
        sourceTitle = "Latvian Hunters' Association hunting calendar",
        sourceUrl = "https://www.latma.lv/medibas_latvija/medibu-kalendars/",
        summary = "Field reference of the LatMA calendar. Duck closing date was not stated. " +
            "Moose, raccoon dog, mouflon, chamois and badger have no dates stored.",
    ) {
        dated("red_deer", "Stag", "01-09", "15-02", "Field reference of the LatMA calendar. Verify the current calendar.")
        dated("red_deer", "Cow", "15-07", "31-01", "Field reference of the LatMA calendar. Verify the current calendar.")
        dated("red_deer", "Calf", "15-07", "31-03", "Field reference of the LatMA calendar. Verify the current calendar.")
        dated("roe_deer", "Buck", "01-06", "30-11", "Field reference of the LatMA calendar. Verify the current calendar.")
        dated("roe_deer", "Doe / kid", "15-08", "30-11", "Field reference of the LatMA calendar. Verify the current calendar.")
        yearRound("wild_boar", "Field reference: year-round.")
        yearRound("fallow_deer", "Field reference: invasive, year-round. Verify the current calendar.")
        yearRound("sika_deer", "Field reference: invasive, year-round. Verify the current calendar.")
        dated("pheasant", "Either", "01-08", "31-03", "Field reference of the LatMA calendar. Verify the current calendar.")
        dated("woodcock", "Either", "01-09", "15-12", "Field reference of the LatMA calendar. Verify the current calendar.")
        partial("ducks", "Either", "20-08", "Field reference: from about 20 August. The end date was not stated.")
        place("moose", "raccoon_dog", "badger", "mouflon", "chamois", "brown_hare", "red_fox", "beaver")
    }

    private fun lithuaniaPack(): SeasonPack = european(
        id = "lt-seasons",
        countryCode = "LT",
        countryName = "Lithuania",
        title = "Lithuania seasons",
        sourceTitle = "AAD hunting periods",
        sourceUrl = "https://aad.lrv.lt/lt/konsultacijos/atmintines/medziokle/medziojimo-laikotarpiai/",
        summary = "Field reference of the AAD hunting-period note. Duck dates were marked approximate. " +
            "Moose, raccoon dog, mouflon and badger have no dates stored.",
    ) {
        dated("red_deer", "Male", "15-08", "28-02", "Field reference of the AAD note. Verify the current note.")
        dated("red_deer", "Female / calves", "01-09", "15-01", "Field reference of the AAD note. Verify the current note.")
        dated("fallow_deer", "Male", "01-10", "31-03", "Field reference of the AAD note. Verify the current note.")
        dated("fallow_deer", "Female / calves", "01-10", "31-01", "Field reference of the AAD note. Verify the current note.")
        dated("roe_deer", "Buck", "01-05", "31-10", "Field reference of the AAD note. Verify the current note.")
        dated("roe_deer", "Doe / kid", "01-10", "31-12", "Field reference of the AAD note. Verify the current note.")
        yearRound("wild_boar", "Field reference: year-round.")
        dated("pheasant", "Either", "01-09", "30-04", "Field reference of the AAD note. Verify the current note.")
        approx("ducks", "Either", "15-08", "15-12", "Marked approximate in the field reference.")
        dated("woodcock", "Either", "15-08", "30-11", "Grouped with snipe in the field reference.")
        dated("snipe", "Either", "15-08", "30-11", "Grouped with woodcock in the field reference.")
        place("moose", "raccoon_dog", "badger", "mouflon", "chamois", "brown_hare", "red_fox", "beaver")
    }

    private fun polandPack(): SeasonPack = european(
        id = "pl-seasons",
        countryCode = "PL",
        countryName = "Poland",
        title = "Poland seasons",
        sourceTitle = "Ministry hunting periods",
        sourceUrl = "https://www.gov.pl/web/klimat/okresy-polowan-i-przepisy-lowieckie",
        summary = "Field reference of the ministry hunting-period pages. Some windows were approximate or incomplete. " +
            "Mouflon, moose, chamois and badger have no dates stored.",
    ) {
        dated(
            "red_deer",
            "Stag",
            "21-08",
            "28-02",
            "To the end of February. Some voivodeships open earlier for young stags.",
        )
        approx("red_deer", "Hind", "01-09", "15-01", "Marked approximate in the field reference.")
        approx("red_deer", "Calf", "01-09", "28-02", "To the end of February. Marked approximate in the field reference.")
        dated("roe_deer", "Buck", "11-05", "30-09", "Field reference. Verify the current regulation.")
        dated("roe_deer", "Doe / kid", "01-10", "15-01", "Field reference. Verify the current regulation.")
        approx("fallow_deer", "Stag", "01-09", "28-02", "To the end of February. Marked approximate in the field reference.")
        unstated(
            "fallow_deer",
            "Field reference: does to 15 January. A separate start date was not stated, so no window is stored.",
            sex = "Doe",
        )
        yearRound("wild_boar", "Field reference: year-round.")
        yearRound("pheasant", "Field reference: cock year-round.", sex = "Cock")
        unstated("pheasant", "Field reference: hens restricted. Dates were not stated.", sex = "Hen")
        place(
            "mouflon", "moose", "chamois", "badger", "brown_hare", "red_fox",
            "raccoon_dog", "grey_partridge", "beaver",
        )
    }

    private fun romaniaPack(): SeasonPack = european(
        id = "ro-seasons",
        countryCode = "RO",
        countryName = "Romania",
        title = "Romania seasons",
        sourceTitle = "Hunting legislation (Law 407/2006 annex, verify current)",
        sourceUrl = "https://mmediu.ro/domenii/mediu/legislatie-vanatoare/",
        summary = "Field reference of the Law 407/2006 annex. Pheasant was marked approximate. " +
            "Mouflon, chamois and badger have no dates stored. Verify the current annex.",
    ) {
        dated("red_deer", "Trophy male", "10-09", "15-11", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("red_deer", "Selection male", "01-09", "15-12", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("red_deer", "Female / calf", "01-09", "15-02", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("fallow_deer", "Trophy male", "10-10", "01-12", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("fallow_deer", "Female / calf", "01-09", "15-02", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("roe_deer", "Buck", "15-05", "15-10", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("roe_deer", "Doe", "01-09", "15-02", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("wild_boar", "Either", "01-08", "15-02", "Law 407/2006 annex as summarised. Verify the current annex.")
        approx("pheasant", "Either", "01-10", "15-03", "Marked approximate in the field reference.")
        dated("partridge", "Either", "15-09", "31-12", "Law 407/2006 annex as summarised. Verify the current annex.")
        dated("ducks", "Either", "01-09", "28-02", "Grouped with geese in the field reference.")
        dated("geese", "Either", "01-09", "28-02", "Grouped with ducks in the field reference.")
        dated("woodcock", "Either", "15-09", "15-04", "Law 407/2006 annex as summarised. Verify the current annex.")
        place("mouflon", "chamois", "alpine_ibex", "badger", "brown_hare", "red_fox", "moose")
    }

    private fun spainPack(): SeasonPack = european(
        id = "es-seasons",
        countryCode = "ES",
        countryName = "Spain",
        title = "Spain seasons",
        sourceTitle = "Reglamento de Armas (BOE)",
        sourceUrl = "https://www.boe.es/buscar/act.php?id=BOE-A-1993-6202",
        summary = "No national open-season calendar is stored. Hunting periods in Spain are set by each autonomous community. " +
            "Species rows stay unverified until an official orden de vedas is copied. " +
            "The silencer rule is taken from the national weapons regulation, read on 9 November 2026.",
    ) {
        listOf(
            "red_deer", "roe_deer", "fallow_deer", "wild_boar", "mouflon", "chamois", "alpine_ibex",
            "rabbit", "iberian_hare", "red_legged_partridge", "red_fox",
        ).forEach { key ->
            notListed(
                key,
                "Named here only so the slot is not a blank placeholder. " +
                    "No national season was copied. Each comunidad autónoma publishes its own orden de vedas.",
            )
        }
    }

    private fun portugalPack(): SeasonPack = european(
        id = "pt-seasons",
        countryCode = "PT",
        countryName = "Portugal",
        title = "Portugal seasons",
        sourceTitle = "Calendário venatório 2024-2027",
        sourceUrl = "https://diariodarepublica.pt/dr/legislacao-consolidada/portaria/2024-898587591",
        summary = "Fixed dates below are copied from the consolidated hunting calendar for the 2024-2025, 2025-2026 and 2026-2027 seasons " +
            "(Portaria n.º 67/2024, as shown on diariodarepublica.pt on 9 November 2026). " +
            "They are the ordered-land window unless the note says otherwise. " +
            "A period that starts on a numbered Sunday is kept in words, not converted to a day. " +
            "A blank period cell was not given the date from the row above. " +
            "Fallow deer, red deer, roe deer and mouflon are named in that table with empty period cells, so no season is stored for them.",
    ) {
        val src = "Copied from the consolidated Portuguese hunting calendar for 2024-2025, 2025-2026 and 2026-2027, read on 9 November 2026."
        cited(
            "rabbit",
            "Either",
            "01-09",
            "31-12",
            "$src Ordered land. Unordered land is the first Sunday of October to the last Sunday of November. " +
                "Daily bag on the same row: (3) on ordered land and 1 on unordered land.",
        )
        cited(
            "red_fox",
            "Either",
            "01-10",
            "28-02",
            "$src Ordered land. Unordered land is the first Sunday of October to the last Sunday of December. " +
                "Daily bag on unordered land on that row: 1.",
        )
        cited(
            "red_legged_partridge",
            "Either",
            "01-10",
            "31-01",
            "$src Ordered land. The unordered-land cell on the Portaria n.º 309/2024 extract of this table is a dash. Daily bag on unordered land: 1.",
        )
        cited(
            "gadwall",
            "Either",
            "01-10",
            "20-01",
            "$src Ordered land, on the gadwall row. The unordered-land cell is a dash. Daily bag on ordered land on that row: 1. " +
                "Later duck rows with a blank period cell are not given this date.",
        )
        cited(
            "golden_plover",
            "Either",
            "01-11",
            "20-01",
            "$src On the golden-plover row. Daily bag on that row: 5.",
        )
        cited(
            "woodcock",
            "Either",
            "01-11",
            "10-02",
            "$src On the woodcock row. Daily bag on that row: 3.",
        )
        cited(
            "quail",
            "Either",
            "01-09",
            "30-11",
            "$src On the quail row. Daily bag on that row: 10.",
        )
        cited(
            "wild_boar",
            "Either",
            "01-06",
            "31-05",
            "$src On the wild-boar row. The window runs across the year. Daily bag cell on that row: (3).",
        )
        notListed(
            "iberian_hare",
            "$src The Iberian hare is named. The period cells on that row were blank, so no season is stored. Daily bag on unordered land on that row: 1.",
        )
        notListed(
            "egyptian_mongoose",
            "$src The Egyptian mongoose is named. The period cells on that row were blank, so no season is stored. Daily bag on unordered land on that row: 1.",
        )
        notListed(
            "pheasant",
            "$src The pheasant row has a blank ordered-land period and a dash for unordered land. No season is stored.",
        )
        notListed(
            "rock_dove",
            "$src Ordered land: from the third Sunday of August to 31 December. " +
                "Unordered land: first Sunday of October to the last Sunday of December. " +
                "Daily bag 25 ordered and 15 unordered. Not stored as day-month dates because the start is a Sunday.",
        )
        notListed(
            "magpie",
            "$src Ordered land: from the third Sunday of August to 28 February. Daily bag on that row: (3) ordered and 1 unordered. " +
                "Not stored as day-month dates because the start is a Sunday.",
        )
        notListed(
            "carrion_crow",
            "$src Named on the row under magpie. The period cells were blank, so the magpie window was not copied onto this row.",
        )
        listOf("shoveler", "pochard", "tufted_duck", "teal", "pintail", "wigeon").forEach { key ->
            notListed(
                key,
                "$src Named in the duck block under gadwall. The period cell on this row was blank, so 1 October–20 January was not copied here.",
            )
        }
        notListed(
            "mallard",
            "$src The mallard row states from the third Sunday of August to 20 January. Not stored as day-month dates because the start is a Sunday.",
        )
        notListed("coot", "$src Named under mallard. The period cell on this row was blank, so no season is stored.")
        notListed("moorhen", "$src Named under mallard. The period cell on this row was blank. A daily-bag figure of 5 appears on that row.")
        notListed(
            "snipe",
            "$src One extract put 1 November–20 February on the common-snipe row and another left the period cell blank under golden plover. No date is stored.",
        )
        notListed("jack_snipe", "$src Named under common snipe. The period cell was blank, so no season is stored.")
        notListed(
            "turtle_dove",
            "$src The turtle-dove row states the 3rd and 4th Sundays of August and the 1st and 2nd Sundays of September. Daily bag on that row: 4. " +
                "Not stored as day-month dates.",
        )
        notListed(
            "stock_dove",
            "$src From the third Sunday of August to 20 February. Daily bag on that row: 50. Not stored as day-month dates because the start is a Sunday.",
        )
        notListed("woodpigeon", "$src Named under stock dove. The period cell was blank, so the stock-dove window was not copied here.")
        notListed(
            "fieldfare",
            "$src The fieldfare row states 1 November to 20 February in one extract, with a daily bag of 40. " +
                "The same block's later thrush rows are blank. Because the extracts do not agree on which row owns that window, no date is stored.",
        )
        listOf("song_thrush", "redwing", "mistle_thrush", "starling").forEach { key ->
            notListed(key, "$src Named in the thrush block. The period cell on this row was blank, so no season is stored.")
        }
        listOf("fallow_deer", "red_deer", "roe_deer", "mouflon").forEach { key ->
            notListed(
                key,
                "$src Named at the end of the table. The period cells were empty, so no season is stored.",
            )
        }
    }

    private fun italyPack(): SeasonPack = european(
        id = "it-seasons",
        countryCode = "IT",
        countryName = "Italy",
        title = "Italy seasons",
        sourceTitle = "Legge 11 febbraio 1992, n. 157",
        sourceUrl = "https://www.normattiva.it/atto/caricaDettaglioAtto?atto.articolo.numero=21&atto.codiceRedazionale=092G0211&atto.dataPubblicazioneGazzetta=1992-02-25",
        summary = "No species dates are stored. Regional hunting calendars set the seasons, and those calendars were not copied. " +
            "Articles 13 and 21 of Law 157/1992 were read on Normattiva on 9 November 2026 for the rules below.",
    ) {
        listOf(
            "red_deer", "roe_deer", "fallow_deer", "wild_boar", "mouflon", "chamois", "alpine_ibex",
            "brown_hare", "pheasant", "red_legged_partridge", "grey_partridge",
        ).forEach { key ->
            notListed(
                key,
                "No national season was copied. The regions publish the hunting calendar. " +
                    "Articles 13 and 21 of Law 157/1992, read on 9 November 2026, do not set this species' dates.",
            )
        }
    }

    private fun spainRules(): List<HuntingRule> = listOf(
        HuntingRule(
            id = RULE_SUPPRESSOR,
            title = "Sound moderator / silencer",
            answer = RuleAnswer.PROHIBITED,
            notes = "Reglamento de Armas, article 5.1.k, consolidated text on the BOE and restated by Real Decreto 726/2020: " +
                "advertising, sale, possession and use of silencers adaptable to firearms is prohibited, " +
                "except by specially authorised officials and under the rules that apply to them. " +
                "Article 5.4 says those items may be sold by authorised dealers only to the bodies those officials belong to.",
            sourceTitle = "Reglamento de Armas, artículo 5",
            sourceUrl = "https://www.boe.es/buscar/act.php?id=BOE-A-1993-6202",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_NIGHT,
            title = "Night hunting",
            answer = RuleAnswer.UNVERIFIED,
            notes = "Not confirmed in a national statute read for this pack. Night hunting is commonly set in each autonomous community's orden de vedas. No yes or no is stored.",
            sourceTitle = "No national night-hunting article copied",
            sourceUrl = "https://www.boe.es/buscar/act.php?id=BOE-A-1993-6202",
            lastVerified = null,
        ),
        HuntingRule(
            id = RULE_ORANGE,
            title = "Blaze orange / high-vis clothing",
            answer = RuleAnswer.UNVERIFIED,
            notes = "Not confirmed in a national statute read for this pack. The weapons regulation read on 9 November 2026 does not mention hunting clothing. No yes or no is stored.",
            sourceTitle = "No national high-vis article copied",
            sourceUrl = "https://www.boe.es/buscar/act.php?id=BOE-A-1993-6202",
            lastVerified = null,
        ),
    )

    private fun portugalRules(): List<HuntingRule> = listOf(
        HuntingRule(
            id = RULE_SUPPRESSOR,
            title = "Sound moderator / silencer",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Lei n.º 5/2006, consolidated text read on 9 November 2026. " +
                "A silencer is defined as an accessory fitted to the muzzle to eliminate or reduce the noise of the shot. " +
                "A sound moderator is a homologated accessory that removes up to 50 dB. " +
                "Silencers, and sound moderators that are not homologated or that reduce the sound by more than 50 dB, are class A. " +
                "Homologated sound moderators with a maximum reduction of 50 dB are class C. " +
                "Class C weapons and accessories need prior authorisation from the national director of the PSP. " +
                "Using a sound moderator on a firearm that is not class C is an offence (fine of €400 to €4,000).",
            sourceTitle = "Lei n.º 5/2006 (consolidated)",
            sourceUrl = "https://diariodarepublica.pt/dr/legislacao-consolidada/lei/2006-34574575",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_NIGHT,
            title = "Night hunting",
            answer = RuleAnswer.UNVERIFIED,
            notes = "The consolidated hunting calendar read on 9 November 2026 lists species periods and does not state a night-hunting rule. No yes or no is stored.",
            sourceTitle = "Calendário venatório 2024-2027",
            sourceUrl = "https://diariodarepublica.pt/dr/legislacao-consolidada/portaria/2024-898587591",
            lastVerified = null,
        ),
        HuntingRule(
            id = RULE_ORANGE,
            title = "Blaze orange / high-vis clothing",
            answer = RuleAnswer.UNVERIFIED,
            notes = "The consolidated hunting calendar read on 9 November 2026 does not mention blaze-orange or high-vis clothing. No yes or no is stored.",
            sourceTitle = "Calendário venatório 2024-2027",
            sourceUrl = "https://diariodarepublica.pt/dr/legislacao-consolidada/portaria/2024-898587591",
            lastVerified = null,
        ),
    )

    private fun italyRules(): List<HuntingRule> = listOf(
        HuntingRule(
            id = RULE_SUPPRESSOR,
            title = "Sound moderator / silencer",
            answer = RuleAnswer.PROHIBITED,
            notes = "Law 157/1992, article 21(1)(u), text in force on Normattiva read on 9 November 2026: " +
                "it is forbidden for anyone to use firearms fitted with a silencer. " +
                "The same letter also forbids shot on ungulates and several other means. " +
                "Article 13(5) forbids every hunting weapon and means that article 13 does not explicitly allow. Silencers are not in that list.",
            sourceTitle = "Legge 157/1992, articolo 21",
            sourceUrl = "https://www.normattiva.it/atto/caricaDettaglioAtto?atto.articolo.numero=21&atto.codiceRedazionale=092G0211&atto.dataPubblicazioneGazzetta=1992-02-25",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_NIGHT,
            title = "Night hunting",
            answer = RuleAnswer.UNVERIFIED,
            notes = "Articles 13 and 21, read on 9 November 2026, do not state a general night-hunting ban, so no yes or no is stored. " +
                "Article 13(2-ter) does allow night-vision aiming devices, including digital ones, for selective wild-boar culling in the forms the law provides, " +
                "except devices that are military materiel under Law 185/1990, article 2. That is an aiming-device rule, not a season.",
            sourceTitle = "Legge 157/1992, articolo 13",
            sourceUrl = "https://www.normattiva.it/atto/caricaDettaglioAtto?atto.articolo.numero=13&atto.codiceRedazionale=092G0211&atto.dataPubblicazioneGazzetta=1992-02-25",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_ORANGE,
            title = "Blaze orange / high-vis clothing",
            answer = RuleAnswer.UNVERIFIED,
            notes = "Articles 13 and 21, read on 9 November 2026, do not mention blaze-orange or high-vis clothing. No yes or no is stored.",
            sourceTitle = "Legge 157/1992, articolo 21",
            sourceUrl = "https://www.normattiva.it/atto/caricaDettaglioAtto?atto.articolo.numero=21&atto.codiceRedazionale=092G0211&atto.dataPubblicazioneGazzetta=1992-02-25",
            lastVerified = null,
        ),
    )

    private fun ukRules(): List<HuntingRule> = listOf(
        HuntingRule(
            id = RULE_SUPPRESSOR,
            title = "Sound moderator / silencer",
            answer = RuleAnswer.CONDITIONAL,
            notes = "England, Wales and Scotland: from 29 June 2026, section 44 of the Crime and Policing Act 2026 " +
                "makes it an offence to possess a sound moderator for a section 1 firearm unless the person holds a " +
                "firearm certificate or a shotgun certificate. A certificate variation is no longer required for that " +
                "accessory. The section does not extend to Northern Ireland. Shotgun and air-weapon moderators are a " +
                "separate case.",
            sourceTitle = "Crime and Policing Act 2026, section 44",
            sourceUrl = "https://www.legislation.gov.uk/ukpga/2026/20/section/44",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_NIGHT,
            title = "Night hunting",
            answer = RuleAnswer.CONDITIONAL,
            notes = "England and Wales: Deer Act 1991 section 3 makes it an offence to take or intentionally kill any " +
                "deer between the end of the first hour after sunset and the start of the last hour before sunrise, " +
                "subject to the exceptions in sections 6 and 8. This section does not state the law in Scotland or " +
                "Northern Ireland. The GOV.UK class licence CL55 said it could be used until 31 July 2026, so it is " +
                "not treated here as a current permission to shoot deer at night.",
            sourceTitle = "Deer Act 1991, section 3",
            sourceUrl = "https://www.legislation.gov.uk/ukpga/1991/54/section/3",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_ORANGE,
            title = "Blaze orange / high-vis clothing",
            answer = RuleAnswer.UNVERIFIED,
            notes = "No statute requiring blaze-orange hats or vests for hunting was confirmed for this pack. " +
                "The Deer Act 1991 does not mention high-visibility clothing. That is not proof that no other rule does.",
            sourceTitle = "Deer Act 1991",
            sourceUrl = "https://www.legislation.gov.uk/ukpga/1991/54",
            lastVerified = "2026-11-09",
        ),
    )

    private fun franceRules(): List<HuntingRule> = listOf(
        HuntingRule(
            id = RULE_SUPPRESSOR,
            title = "Sound moderator / silencer",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Two official texts, both read on 9 November 2026, cover different parts of this. " +
                "Classification: décret n° 2018-542 of 29 June 2018 rewrote article R. 311-1 of the Code de la sécurité intérieure. " +
                "Section IV says a sound moderator is not a weapon when it is an add-on part that does not change how the firearm works. " +
                "Acquisition: the same decree added article R. 312-45-2. Nobody may acquire a sound moderator without showing " +
                "one of the titles in article R. 312-53 (a validated French or foreign hunting permit, a delegated shooting-federation licence, " +
                "or a collector card) and the detention title for the matching firearm. " +
                "Use while hunting: the consolidated arrêté of 1 August 1986, in force on 9 November 2026, article 2, " +
                "no longer lists a ban on a device that quiets the shot. That line is in older copies of article 2, including the text " +
                "shown as in force on 3 September 2008, and it is not in the current article 2. " +
                "This is not a statement that every firearm authority or département allows a moderator. Check the current code article " +
                "and the firearm's own papers before buying or fitting one.",
            sourceTitle = "Décret n° 2018-542 (Code de la sécurité intérieure R. 311-1 and R. 312-45-2); arrêté of 1 August 1986, article 2",
            sourceUrl = "https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000037129603",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_NIGHT,
            title = "Night hunting",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Arrêté of 1 August 1986, in force on 9 November 2026: article 2 bans lights and mirrors used to make capture or killing easier. " +
                "Article 11 bis bans searching for or pursuing game with lights, except counts and captures authorised for science or restocking. " +
                "The national hunters' federation, which is not the statute, describes daytime as from one hour before sunrise until one hour after sunset, " +
                "with waterfowl flight times and registered night hides as exceptions. Those clock times are not copied here as a code article. " +
                "Confirm the Code de l'environnement before relying on them.",
            sourceTitle = "Arrêté du 1er août 1986, articles 2 and 11 bis",
            sourceUrl = "https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000862758",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = "ungulate_shot",
            title = "How named hoofed quarry may be shot",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Arrêté of 1 August 1986, article 4, in force on 9 November 2026, names red deer, fallow deer, mouflon, " +
                "chamois or isard, roe deer and wild boar. They may be shot only with a bullet, or with a hunting bow under the bow-hunting rules. " +
                "A prefect may allow roe deer to be shot with shot on part of a department. Buckshot for wild boar in a driven hunt needs a ministerial order. " +
                "This article does not set the open season. Département dates are not stored in this pack.",
            sourceTitle = "Arrêté du 1er août 1986, article 4",
            sourceUrl = "https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000862758",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_ORANGE,
            title = "Blaze orange / high-vis clothing",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Arrêté of 5 October 2020, article 1: every participant in a collective driven big-game shoot, " +
                "armed or not, must wear the fluorescent vest in article L. 424-15 so it stays visible the whole time. " +
                "The vest may be part of a bright T-shirt, jacket or cape. The order does not say solitary stalking, " +
                "or an orange hat on its own, is covered.",
            sourceTitle = "Arrêté du 5 octobre 2020, article 1",
            sourceUrl = "https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000042423887",
            lastVerified = "2026-11-09",
        ),
    )

    private fun irelandRules(): List<HuntingRule> = listOf(
        HuntingRule(
            id = RULE_SUPPRESSOR,
            title = "Sound moderator / silencer",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Firearms and Offensive Weapons Act 1990, section 7, as enacted: possession, sale or transfer of a silencer " +
                "is an offence unless a superintendent of the Garda Síochána for the district where the person lives authorises it in writing. " +
                "The superintendent must be satisfied that the person holds a firearm certificate for a firearm the silencer can fit, " +
                "that the possession will not endanger public safety or the peace, and that the person has a special need sufficient to justify the authorisation. " +
                "Conditions may be attached. The authorisation may last no longer than one year and may be revoked. " +
                "This is the enacted section. Later amendments were not re-read for this pack.",
            sourceTitle = "Firearms and Offensive Weapons Act 1990, section 7",
            sourceUrl = "https://www.irishstatutebook.ie/eli/1990/act/12/section/7/enacted/en/html",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_NIGHT,
            title = "Night hunting",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Wildlife Act 1976, section 37, as enacted: subject to section 42, a person shall not hunt a woodcock between sunset and sunrise, " +
                "or any other protected wild bird other than a wild duck or wild goose, or any protected wild animal, " +
                "from one hour after sunset until one hour before sunrise. " +
                "Subsection (2) excepts hunting under stated ministerial licences. " +
                "This is the enacted section. Later amendments were not re-read for this pack.",
            sourceTitle = "Wildlife Act 1976, section 37",
            sourceUrl = "https://www.irishstatutebook.ie/eli/1976/act/39/section/37/enacted/en/index.html",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_ORANGE,
            title = "Blaze orange / high-vis clothing",
            answer = RuleAnswer.UNVERIFIED,
            notes = "No Irish statute requiring blaze-orange hats or vests for hunting was confirmed for this pack.",
            sourceTitle = "NPWS Open Seasons Order (no clothing rule found on the page)",
            sourceUrl = "https://www.npws.ie/legislation/irish-law/open-seasons-order",
            lastVerified = "2026-11-09",
        ),
    )

    private fun germanyRules(): List<HuntingRule> = listOf(
        HuntingRule(
            id = RULE_SUPPRESSOR,
            title = "Sound moderator / silencer",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Weapons Act (Waffengesetz) section 13(9), on gesetze-im-internet.de: the earlier paragraphs of that section apply to sound moderators as well. " +
                "Sound moderators covered by that sentence may be used only with long guns that are permitted for hunting, with centre-fire ammunition, " +
                "and only for hunting and hunting practice. The licensing paragraphs were not copied in full here.",
            sourceTitle = "Waffengesetz § 13",
            sourceUrl = "https://www.gesetze-im-internet.de/waffg_2002/__13.html",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_NIGHT,
            title = "Night hunting",
            answer = RuleAnswer.CONDITIONAL,
            notes = "Federal Hunting Act (Bundesjagdgesetz) section 19(1) number 4: it is forbidden to kill cloven-hoofed game, except wild boar, " +
                "and feathered game at night. Night runs from one and a half hours after sunset until one and a half hours before sunrise. " +
                "The ban does not cover gulls, woodcock, capercaillie, black grouse or rackelhahn. " +
                "The Länder can change hunting times under section 22. Species dates are not stored in this pack.",
            sourceTitle = "Bundesjagdgesetz § 19",
            sourceUrl = "https://www.gesetze-im-internet.de/bjagdg/__19.html",
            lastVerified = "2026-11-09",
        ),
        HuntingRule(
            id = RULE_ORANGE,
            title = "Blaze orange / high-vis clothing",
            answer = RuleAnswer.UNVERIFIED,
            notes = "Sections 19 and 22 of the Federal Hunting Act, read on 9 November 2026, do not mention blaze-orange hats or vests. " +
                "That is not proof that a Land has no clothing rule.",
            sourceTitle = "Bundesjagdgesetz §§ 19 and 22",
            sourceUrl = "https://www.gesetze-im-internet.de/bjagdg/__22.html",
            lastVerified = "2026-11-09",
        ),
    )

    private fun unverifiedRules(countryCode: String): List<HuntingRule> {
        val sources = mapOf(
            "IE" to ("NPWS Open Seasons Order" to "https://www.npws.ie/legislation/irish-law/open-seasons-order"),
            "DE" to ("Federal hunting-season summary" to "https://schonzeiten.de/jagdzeiten-deutschland-bundesweit-jagd/"),
            "EE" to ("Jahieeskiri" to "https://www.riigiteataja.ee/akt/125012019008"),
            "LV" to ("Latvian Hunters' Association calendar" to "https://www.latma.lv/medibas_latvija/medibu-kalendars/"),
            "LT" to ("AAD hunting periods" to "https://aad.lrv.lt/lt/konsultacijos/atmintines/medziokle/medziojimo-laikotarpiai/"),
            "PL" to ("Ministry hunting periods" to "https://www.gov.pl/web/klimat/okresy-polowan-i-przepisy-lowieckie"),
            "RO" to ("Ministry hunting legislation" to "https://mmediu.ro/domenii/mediu/legislatie-vanatoare/"),
        )
        val (title, url) = sources[countryCode]
            ?: ("Official hunting authority" to "https://www.gov.uk/")
        val note = "Not confirmed against the statute for this pack. No yes or no is stored."
        return listOf(RULE_SUPPRESSOR, RULE_NIGHT, RULE_ORANGE).map { id ->
            HuntingRule(
                id = id,
                title = when (id) {
                    RULE_SUPPRESSOR -> "Sound moderator / silencer"
                    RULE_NIGHT -> "Night hunting"
                    else -> "Blaze orange / high-vis clothing"
                },
                answer = RuleAnswer.UNVERIFIED,
                notes = note,
                sourceTitle = title,
                sourceUrl = url,
                lastVerified = null,
            )
        }
    }

}

private const val REFERENCE_DISCLAIMER =
    "A season date is shown only when it comes from the UK catalog already in HunTracK or from a law cited on that row. " +
        "Unverified rows are species slots, not a statement that the animal is legal quarry and not a shooting season. " +
        "Pocket notes are labelled unverified and are not stored as open or close dates. " +
        "This is not legal advice."

private fun european(
    id: String,
    countryCode: String,
    countryName: String,
    title: String,
    sourceTitle: String,
    sourceUrl: String,
    summary: String,
    fill: PackRows.() -> Unit,
): SeasonPack {
    val rows = PackRows(sourceUrl, sourceTitle).apply {
        fill()
        place(*RegionPackCatalog.speciesDirectory.map { it.speciesKey }.toTypedArray())
    }.rows
    return SeasonPack(
        id = id,
        countryCode = countryCode,
        countryName = countryName,
        title = title,
        summary = summary,
        sourceTitle = sourceTitle,
        sourceUrl = sourceUrl,
        disclaimer = REFERENCE_DISCLAIMER,
        species = rows,
        rules = RegionPackCatalog.rulesFor(countryCode),
        productId = RegionPackCatalog.productId(countryCode),
        intendedPriceLabel = RegionPackCatalog.INTENDED_PRICE,
        bundledWithApp = false,
    )
}

private class PackRows(
    private val sourceUrl: String,
    private val sourceTitle: String,
) {
    val rows = mutableListOf<PackSpeciesSeason>()

    fun dated(key: String, sex: String, from: String, to: String, notes: String) {
        fieldNote(key, sex, "Unverified field note, not a legal season: ${formatDayMonth(from)} – ${formatDayMonth(to)}. $notes")
    }

    fun approx(key: String, sex: String, from: String, to: String, notes: String) {
        fieldNote(key, sex, "Unverified field note, not a legal season: about ${formatDayMonth(from)} – ${formatDayMonth(to)}. $notes")
    }

    fun partial(key: String, sex: String, from: String, notes: String) {
        fieldNote(key, sex, "Unverified field note, not a legal season: about from ${formatDayMonth(from)}. $notes")
    }

    fun yearRound(key: String, notes: String, sex: String = "Either") {
        fieldNote(key, sex, "Unverified field note, not a legal season: described as year-round. $notes")
    }

    fun closed(key: String, notes: String) {
        fieldNote(key, "Either", "Unverified field note, not a legal season: described as no open season. $notes")
    }

    fun unstated(key: String, notes: String, sex: String = "Either") {
        fieldNote(key, sex, "Unverified field note, not a legal season. $notes")
    }

    fun cited(key: String, sex: String, from: String, to: String, notes: String) {
        add(key, sex, SpeciesLegalStatus.OPEN_SEASON, SeasonPrecision.DATED, from, to, notes, PackDataQuality.REFERENCE)
    }

    fun notListed(key: String, notes: String) {
        add(
            key,
            "Either",
            SpeciesLegalStatus.NOT_STATED,
            SeasonPrecision.UNSPECIFIED,
            null,
            null,
            notes,
            PackDataQuality.REFERENCE,
        )
    }

    private fun fieldNote(key: String, sex: String, notes: String) {
        add(key, sex, SpeciesLegalStatus.UNVERIFIED, SeasonPrecision.UNSPECIFIED, null, null, notes, PackDataQuality.PLACEHOLDER)
    }

    fun place(vararg keys: String) {
        keys.forEach { key ->
            if (rows.any { it.speciesKey == key }) return@forEach
            val identity = requireNotNull(RegionPackCatalog.speciesDirectory.firstOrNull { it.speciesKey == key }) {
                "Missing species directory entry $key"
            }
            rows += PackSpeciesSeason(
                speciesKey = identity.speciesKey,
                commonName = identity.commonName,
                scientificName = identity.scientificName,
                sexLabel = "Either",
                regionLabel = "",
                legalStatus = SpeciesLegalStatus.UNVERIFIED,
                quality = PackDataQuality.PLACEHOLDER,
                precision = SeasonPrecision.UNSPECIFIED,
                openFromMonthDay = null,
                openToMonthDay = null,
                notes = RegionPackCatalog.PLACEHOLDER_NOTE,
                sourceUrl = sourceUrl,
                sourceTitle = sourceTitle,
            )
        }
    }

    private fun add(
        key: String,
        sex: String,
        status: SpeciesLegalStatus,
        precision: SeasonPrecision,
        from: String?,
        to: String?,
        notes: String,
        quality: PackDataQuality = PackDataQuality.REFERENCE,
    ) {
        val identity = requireNotNull(RegionPackCatalog.speciesDirectory.firstOrNull { it.speciesKey == key }) {
            "Missing species directory entry $key"
        }
        rows += PackSpeciesSeason(
            speciesKey = identity.speciesKey,
            commonName = identity.commonName,
            scientificName = identity.scientificName,
            sexLabel = sex,
            regionLabel = "",
            legalStatus = status,
            quality = quality,
            precision = precision,
            openFromMonthDay = from,
            openToMonthDay = to,
            notes = notes,
            sourceUrl = sourceUrl,
            sourceTitle = sourceTitle,
        )
    }
}

private fun protected(
    key: String,
    regionLabel: String,
    notes: String,
    sourceUrl: String,
    sourceTitle: String,
): PackSpeciesSeason {
    val identity = requireNotNull(RegionPackCatalog.speciesDirectory.firstOrNull { it.speciesKey == key })
    return PackSpeciesSeason(
        speciesKey = identity.speciesKey,
        commonName = identity.commonName,
        scientificName = identity.scientificName,
        sexLabel = "Either",
        regionLabel = regionLabel,
        legalStatus = SpeciesLegalStatus.PROTECTED,
        quality = PackDataQuality.REFERENCE,
        precision = SeasonPrecision.UNSPECIFIED,
        openFromMonthDay = null,
        openToMonthDay = null,
        notes = notes,
        sourceUrl = sourceUrl,
        sourceTitle = sourceTitle,
    )
}

private fun LegalSeason.toPackRow(speciesById: Map<String, uk.hunttrack.domain.model.Species>): PackSpeciesSeason {
    val species = speciesById[speciesId]
    val dated = openFromMonthDay.matches(DAY_MONTH) && openToMonthDay.matches(DAY_MONTH)
    return PackSpeciesSeason(
        speciesKey = speciesId,
        commonName = species?.commonName ?: speciesId,
        scientificName = species?.scientificName.orEmpty(),
        sexLabel = sexLabel,
        regionLabel = nation.name,
        legalStatus = if (dated) SpeciesLegalStatus.OPEN_SEASON else SpeciesLegalStatus.NOT_STATED,
        quality = PackDataQuality.CATALOG,
        precision = if (dated) SeasonPrecision.DATED else SeasonPrecision.UNSPECIFIED,
        openFromMonthDay = openFromMonthDay.takeIf { dated },
        openToMonthDay = openToMonthDay.takeIf { dated },
        notes = notes.ifBlank { species?.seasonsNote.orEmpty() },
        sourceUrl = null,
        sourceTitle = null,
    )
}

private fun ident(key: String, common: String, scientific: String) =
    PackSpeciesIdentity(speciesKey = key, commonName = common, scientificName = scientific)

private fun region(
    id: String,
    countryCode: String,
    name: String,
    summary: String,
    latitude: Double,
    longitude: Double,
) = MapPackRegion(
    id = id,
    countryCode = countryCode,
    name = name,
    summary = summary,
    centerLatitude = latitude,
    centerLongitude = longitude,
    sideKilometres = PERSONAL_SIDE_KM,
)

private val DAY_MONTH = Regex("""\d{2}-\d{2}""")
private const val PERSONAL_SIDE_KM = 6.2

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

@Serializable
data class RegionPackFile(
    val format: String,
    val version: Int,
    val mapAttribution: String,
    val speciesDirectory: List<PackSpeciesIdentity>,
    val seasonPacks: List<SeasonPack>,
    val mapRegions: List<MapPackRegion>,
)

@Serializable
data class PackSpeciesIdentity(
    val speciesKey: String,
    val commonName: String,
    val scientificName: String,
)

@Serializable
enum class SpeciesLegalStatus {
    OPEN_SEASON,
    CLOSED_NO_SEASON,
    YEAR_ROUND,
    NOT_STATED,
    UNVERIFIED,
    PROTECTED,
}

@Serializable
enum class PackDataQuality {
    CATALOG,
    REFERENCE,
    PLACEHOLDER,
}

@Serializable
enum class SeasonPrecision {
    DATED,
    APPROXIMATE,
    UNSPECIFIED,
}

@Serializable
enum class RuleAnswer {
    ALLOWED,
    PROHIBITED,
    CONDITIONAL,
    NOT_REQUIRED,
    UNVERIFIED,
}

@Serializable
data class HuntingRule(
    val id: String,
    val title: String,
    val answer: RuleAnswer,
    val notes: String,
    val sourceTitle: String,
    val sourceUrl: String,
    /** ISO date the source was read, or null when the answer was not checked. */
    val lastVerified: String? = null,
)

@Serializable
data class SeasonPack(
    val id: String,
    val countryCode: String,
    val countryName: String,
    val title: String,
    val summary: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val disclaimer: String,
    val species: List<PackSpeciesSeason>,
    val rules: List<HuntingRule> = emptyList(),
    /** Placeholder non-consumable id. Not created in Play Console or App Store Connect. */
    val productId: String,
    val intendedPriceLabel: String = RegionPackCatalog.INTENDED_PRICE,
    /** UK ships inside the app. Other countries stay locked until a store purchase. */
    val bundledWithApp: Boolean = false,
) {
    val referenceRows: List<PackSpeciesSeason>
        get() = species.filter { it.quality != PackDataQuality.PLACEHOLDER }

    val placeholderRows: List<PackSpeciesSeason>
        get() = species.filter { it.quality == PackDataQuality.PLACEHOLDER }
}

@Serializable
data class PackSpeciesSeason(
    val speciesKey: String,
    val commonName: String,
    val scientificName: String = "",
    val sexLabel: String = "Either",
    val regionLabel: String = "",
    val legalStatus: SpeciesLegalStatus,
    val quality: PackDataQuality,
    val precision: SeasonPrecision,
    val openFromMonthDay: String? = null,
    val openToMonthDay: String? = null,
    val notes: String = "",
    val sourceUrl: String? = null,
    val sourceTitle: String? = null,
) {
    fun seasonText(): String = when (legalStatus) {
        SpeciesLegalStatus.YEAR_ROUND -> "Year-round in the reference"
        SpeciesLegalStatus.CLOSED_NO_SEASON -> "No open season in the reference"
        SpeciesLegalStatus.PROTECTED -> "Protected in the cited law — no season stored"
        SpeciesLegalStatus.UNVERIFIED -> "Unverified — no date stored"
        SpeciesLegalStatus.NOT_STATED -> "No date stored"
        SpeciesLegalStatus.OPEN_SEASON -> when {
            openFromMonthDay != null && openToMonthDay != null ->
                "${formatDayMonth(openFromMonthDay)} – ${formatDayMonth(openToMonthDay)}"
            openFromMonthDay != null -> "From ${formatDayMonth(openFromMonthDay)} — end date not stored"
            else -> "No date stored"
        }
    }

    fun statusLabel(): String = when (legalStatus) {
        SpeciesLegalStatus.OPEN_SEASON -> when {
            quality == PackDataQuality.CATALOG -> "Open season in the UK catalog"
            precision == SeasonPrecision.APPROXIMATE -> "Approximate open season"
            else -> "Open season in the cited source"
        }
        SpeciesLegalStatus.CLOSED_NO_SEASON -> "No open season"
        SpeciesLegalStatus.YEAR_ROUND -> "Year-round in the reference"
        SpeciesLegalStatus.NOT_STATED -> "Season not stated"
        SpeciesLegalStatus.UNVERIFIED -> "Unverified placeholder"
        SpeciesLegalStatus.PROTECTED -> "Protected — no season stored"
    }
}

@Serializable
data class MapPackRegion(
    val id: String,
    val countryCode: String,
    val name: String,
    val summary: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val sideKilometres: Double,
) {
    fun bounds(): MapPackBounds {
        val halfLat = (sideKilometres / 2.0) / 111.32
        val cosLat = cos(centerLatitude * PI / 180.0).coerceAtLeast(0.01)
        val halfLon = (sideKilometres / 2.0) / (111.32 * cosLat)
        return MapPackBounds(
            north = centerLatitude + halfLat,
            south = centerLatitude - halfLat,
            east = centerLongitude + halfLon,
            west = centerLongitude - halfLon,
        )
    }
}

data class MapPackBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
) {
    fun requirePersonalScale() {
        val latSpan = north - south
        val lonSpan = east - west
        require(latSpan > 0.0 && lonSpan > 0.0) { "Map pack bounds are empty." }
        require(latSpan <= 0.20 && lonSpan <= 0.35) {
            "Map pack is too large for a personal OpenFreeMap download."
        }
    }
}

fun formatDayMonth(dayMonth: String): String {
    val parts = dayMonth.split("-")
    if (parts.size != 2) return dayMonth
    val day = parts[0].toIntOrNull() ?: return dayMonth
    val month = parts[1].toIntOrNull() ?: return dayMonth
    if (month !in 1..12) return dayMonth
    return "$day ${MONTHS[month - 1]}"
}
