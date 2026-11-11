package uk.hunttrack.domain.packs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hunttrack.domain.catalog.UkFieldCatalog

class RegionPackCatalogTest {
    @Test
    fun everyCountryPackHasTheWideSpeciesListAndNoInventedDates() {
        val directoryKeys = RegionPackCatalog.speciesDirectory.map { it.speciesKey }.toSet()
        assertTrue(directoryKeys.containsAll(listOf("mouflon", "badger", "chamois", "wild_boar", "alpine_ibex")))
        RegionPackCatalog.seasonPacks.filter { it.countryCode != "GB" }.forEach { pack ->
            val keys = pack.species.map { it.speciesKey }.toSet()
            assertTrue("${pack.id} missing slots", keys.containsAll(directoryKeys))
            pack.species.filter { it.quality == PackDataQuality.PLACEHOLDER }.forEach { row ->
                assertNull("${pack.id} ${row.speciesKey} placeholder stored a date", row.openFromMonthDay)
                assertNull(row.openToMonthDay)
                assertEquals(SpeciesLegalStatus.UNVERIFIED, row.legalStatus)
            }
            pack.species.filter { it.legalStatus == SpeciesLegalStatus.OPEN_SEASON }.forEach { row ->
                assertTrue(row.openFromMonthDay?.matches(Regex("""\d{2}-\d{2}""")) == true)
                assertTrue(row.openToMonthDay?.matches(Regex("""\d{2}-\d{2}""")) == true)
                assertEquals(PackDataQuality.REFERENCE, row.quality)
            }
        }
        val redStag = RegionPackCatalog.pack("ie-seasons")!!.species.single {
            it.speciesKey == "red_deer" && it.sexLabel == "Male"
        }
        assertEquals("01-08", redStag.openFromMonthDay)
        assertEquals("30-04", redStag.openToMonthDay)
        assertTrue(redStag.notes.contains("Kerry"))
    }

    @Test
    fun ukCatalogSeasonsStayAndBadgerIsProtectedWithoutASeason() {
        val uk = RegionPackCatalog.pack(RegionPackCatalog.UK_SEASON_PACK_ID)!!
        val cited = uk.species.filter { it.quality == PackDataQuality.CATALOG }
        assertEquals(UkFieldCatalog.seasons.size, cited.size)
        val badger = uk.species.single { it.speciesKey == "badger" && it.legalStatus == SpeciesLegalStatus.PROTECTED }
        assertNull(badger.openFromMonthDay)
        assertTrue(badger.sourceUrl!!.contains("ukpga/1992/51"))
        val mouflon = uk.species.single { it.speciesKey == "mouflon" }
        assertEquals(SpeciesLegalStatus.UNVERIFIED, mouflon.legalStatus)
        assertNull(mouflon.openFromMonthDay)
    }

    @Test
    fun rulesAreStructuredAndOnlyCitedAnswersAreUsed() {
        RegionPackCatalog.seasonPacks.forEach { pack ->
            val ruleIds = pack.rules.map { it.id }.toSet()
            assertEquals(pack.rules.size, ruleIds.size)
            assertTrue(ruleIds.containsAll(listOf(
                RegionPackCatalog.RULE_SUPPRESSOR,
                RegionPackCatalog.RULE_NIGHT,
                RegionPackCatalog.RULE_ORANGE,
            )))
        }
        val uk = RegionPackCatalog.pack(RegionPackCatalog.UK_SEASON_PACK_ID)!!
        val suppressor = uk.rules.single { it.id == RegionPackCatalog.RULE_SUPPRESSOR }
        assertEquals(RuleAnswer.CONDITIONAL, suppressor.answer)
        assertEquals("2026-11-09", suppressor.lastVerified)
        assertTrue(suppressor.sourceUrl.contains("ukpga/2026/20/section/44"))
        val orange = uk.rules.single { it.id == RegionPackCatalog.RULE_ORANGE }
        assertEquals(RuleAnswer.UNVERIFIED, orange.answer)
        val france = RegionPackCatalog.pack("fr-seasons")!!
        val vest = france.rules.single { it.id == RegionPackCatalog.RULE_ORANGE }
        assertEquals(RuleAnswer.CONDITIONAL, vest.answer)
        assertTrue(vest.sourceUrl.contains("legifrance.gouv.fr"))
        val frenchModerator = france.rules.single { it.id == RegionPackCatalog.RULE_SUPPRESSOR }
        assertEquals(RuleAnswer.CONDITIONAL, frenchModerator.answer)
        assertTrue(frenchModerator.notes.contains("R. 312-45-2"))
        assertTrue(frenchModerator.notes.contains("not a weapon"))
        val ireland = RegionPackCatalog.pack("ie-seasons")!!
        assertEquals(RuleAnswer.CONDITIONAL, ireland.rules.single { it.id == RegionPackCatalog.RULE_SUPPRESSOR }.answer)
        assertEquals(RuleAnswer.UNVERIFIED, ireland.rules.single { it.id == RegionPackCatalog.RULE_ORANGE }.answer)
        val germany = RegionPackCatalog.pack("de-seasons")!!
        assertEquals(RuleAnswer.CONDITIONAL, germany.rules.single { it.id == RegionPackCatalog.RULE_NIGHT }.answer)
        assertEquals(RuleAnswer.UNVERIFIED, germany.rules.single { it.id == RegionPackCatalog.RULE_ORANGE }.answer)
        assertTrue(RegionPackCatalog.pack("pl-seasons")!!.rules.all { it.answer == RuleAnswer.UNVERIFIED })
        val spain = RegionPackCatalog.pack("es-seasons")!!
        assertEquals(RuleAnswer.PROHIBITED, spain.rules.single { it.id == RegionPackCatalog.RULE_SUPPRESSOR }.answer)
        assertEquals(RuleAnswer.UNVERIFIED, spain.rules.single { it.id == RegionPackCatalog.RULE_NIGHT }.answer)
        assertTrue(spain.species.none { it.legalStatus == SpeciesLegalStatus.OPEN_SEASON })
        val portugal = RegionPackCatalog.pack("pt-seasons")!!
        val rabbit = portugal.species.single { it.speciesKey == "rabbit" }
        assertEquals("01-09", rabbit.openFromMonthDay)
        assertEquals("31-12", rabbit.openToMonthDay)
        assertEquals(RuleAnswer.CONDITIONAL, portugal.rules.single { it.id == RegionPackCatalog.RULE_SUPPRESSOR }.answer)
        assertTrue(portugal.species.single { it.speciesKey == "red_deer" }.openFromMonthDay == null)
        val italy = RegionPackCatalog.pack("it-seasons")!!
        assertEquals(RuleAnswer.PROHIBITED, italy.rules.single { it.id == RegionPackCatalog.RULE_SUPPRESSOR }.answer)
        assertTrue(italy.rules.single { it.id == RegionPackCatalog.RULE_NIGHT }.notes.contains("13(2-ter)"))
        assertTrue(italy.species.none { it.openFromMonthDay != null })
    }

    @Test
    fun productsAreOnePerCountryAndOnlyUkIsBundled() {
        val ids = RegionPackCatalog.seasonPacks.map { it.productId }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf("gb", "ie", "fr", "de", "ee", "lv", "lt", "pl", "ro", "es", "pt", "it").map { "hunttrack_pack_$it" },
            ids,
        )
        assertEquals(setOf("GB"), RegionPackCatalog.seasonPacks.filter { it.bundledWithApp }.map { it.countryCode }.toSet())
        assertTrue(RegionPackCatalog.seasonPacks.all { it.intendedPriceLabel == "£1.00" })
    }

    @Test
    fun mapAreasStaySmallEnoughForPersonalTiles() {
        assertTrue(RegionPackCatalog.mapRegions.size >= 9)
        RegionPackCatalog.mapRegions.forEach { region ->
            region.bounds().requirePersonalScale()
            assertTrue(region.sideKilometres <= 8.0)
        }
    }

    @Test
    fun committedPackJsonMatchesTheCatalog() {
        val encoded = RegionPackCatalog.encode()
        val root = repoRoot()
        val copies = listOf(
            File(root, "core/data/src/main/assets/region-packs.json"),
            File(root, "ios/HunTracK/HunTracK/Resources/region-packs.json"),
        )
        if (System.getenv("UPDATE_PACKS") == "1") {
            copies.forEach { file ->
                file.parentFile?.mkdirs()
                file.writeText(encoded)
            }
        }
        copies.forEach { file ->
            assertTrue("Missing ${file.path}. Run with UPDATE_PACKS=1", file.isFile)
            assertEquals(file.path, encoded, file.readText().orEmpty())
        }
        val decoded = RegionPackCatalog.decode(encoded)
        assertEquals(RegionPackCatalog.seasonPacks.size, decoded.seasonPacks.size)
        assertTrue(decoded.speciesDirectory.any { it.speciesKey == "mouflon" })
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return dir
        }
        return File(System.getProperty("user.dir"))
    }
}
