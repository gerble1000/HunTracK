package uk.hunttrack.core.designsystem.species

import androidx.annotation.DrawableRes
import uk.hunttrack.core.designsystem.R
import uk.hunttrack.core.designsystem.markers.MarkerIconResolver
import uk.hunttrack.domain.catalog.UkFieldCatalog

/** Bundled male/female reference photos for the species ID guide (drawable-nodpi). */
object SpeciesImageResolver {

    @DrawableRes
    fun maleImage(speciesId: String): Int = imageFor(speciesId, isMale = true)

    @DrawableRes
    fun femaleImage(speciesId: String): Int = imageFor(speciesId, isMale = false)

    @DrawableRes
    private fun imageFor(speciesId: String, isMale: Boolean): Int {
        val key = "${speciesId}_${if (isMale) "male" else "female"}"
        return drawableByKey[key] ?: fallbackFor(speciesId)
    }

    @DrawableRes
    private fun fallbackFor(speciesId: String): Int {
        val species = UkFieldCatalog.speciesById(speciesId) ?: return R.drawable.marker_ic_other
        return MarkerIconResolver.iconForSpecies(species)
    }

    fun hasPhoto(speciesId: String, isMale: Boolean): Boolean {
        val key = "${speciesId}_${if (isMale) "male" else "female"}"
        return drawableByKey.containsKey(key)
    }

    private val drawableByKey: Map<String, Int> = mapOf(
        "american_mink_female" to R.drawable.species_american_mink_female,
        "american_mink_male" to R.drawable.species_american_mink_male,
        "black_rat_female" to R.drawable.species_black_rat_female,
        "black_rat_male" to R.drawable.species_black_rat_male,
        "brown_hare_female" to R.drawable.species_brown_hare_female,
        "brown_hare_male" to R.drawable.species_brown_hare_male,
        "brown_rat_female" to R.drawable.species_brown_rat_female,
        "brown_rat_male" to R.drawable.species_brown_rat_male,
        "canada_goose_female" to R.drawable.species_canada_goose_female,
        "canada_goose_male" to R.drawable.species_canada_goose_male,
        "carrion_crow_female" to R.drawable.species_carrion_crow_female,
        "carrion_crow_male" to R.drawable.species_carrion_crow_male,
        "chinese_water_deer_female" to R.drawable.species_chinese_water_deer_female,
        "chinese_water_deer_male" to R.drawable.species_chinese_water_deer_male,
        "common_snipe_female" to R.drawable.species_common_snipe_female,
        "common_snipe_male" to R.drawable.species_common_snipe_male,
        "fallow_deer_female" to R.drawable.species_fallow_deer_female,
        "fallow_deer_male" to R.drawable.species_fallow_deer_male,
        "fox_female" to R.drawable.species_fox_female,
        "fox_male" to R.drawable.species_fox_male,
        "gadwall_female" to R.drawable.species_gadwall_female,
        "gadwall_male" to R.drawable.species_gadwall_male,
        "goldeneye_female" to R.drawable.species_goldeneye_female,
        "goldeneye_male" to R.drawable.species_goldeneye_male,
        "grey_squirrel_female" to R.drawable.species_grey_squirrel_female,
        "grey_squirrel_male" to R.drawable.species_grey_squirrel_male,
        "greylag_goose_female" to R.drawable.species_greylag_goose_female,
        "greylag_goose_male" to R.drawable.species_greylag_goose_male,
        "hooded_crow_female" to R.drawable.species_hooded_crow_female,
        "hooded_crow_male" to R.drawable.species_hooded_crow_male,
        "jackdaw_female" to R.drawable.species_jackdaw_female,
        "jackdaw_male" to R.drawable.species_jackdaw_male,
        "jay_female" to R.drawable.species_jay_female,
        "jay_male" to R.drawable.species_jay_male,
        "magpie_female" to R.drawable.species_magpie_female,
        "magpie_male" to R.drawable.species_magpie_male,
        "mallard_female" to R.drawable.species_mallard_female,
        "mallard_male" to R.drawable.species_mallard_male,
        "muntjac_female" to R.drawable.species_muntjac_female,
        "muntjac_male" to R.drawable.species_muntjac_male,
        "pheasant_female" to R.drawable.species_pheasant_female,
        "pheasant_male" to R.drawable.species_pheasant_male,
        "pink_footed_goose_female" to R.drawable.species_pink_footed_goose_female,
        "pink_footed_goose_male" to R.drawable.species_pink_footed_goose_male,
        "pintail_female" to R.drawable.species_pintail_female,
        "pintail_male" to R.drawable.species_pintail_male,
        "pochard_female" to R.drawable.species_pochard_female,
        "pochard_male" to R.drawable.species_pochard_male,
        "rabbit_female" to R.drawable.species_rabbit_female,
        "rabbit_male" to R.drawable.species_rabbit_male,
        "red_deer_female" to R.drawable.species_red_deer_female,
        "red_deer_male" to R.drawable.species_red_deer_male,
        "red_legged_partridge_female" to R.drawable.species_red_legged_partridge_female,
        "red_legged_partridge_male" to R.drawable.species_red_legged_partridge_male,
        "roe_deer_female" to R.drawable.species_roe_deer_female,
        "roe_deer_male" to R.drawable.species_roe_deer_male,
        "rook_female" to R.drawable.species_rook_female,
        "rook_male" to R.drawable.species_rook_male,
        "scaup_female" to R.drawable.species_scaup_female,
        "scaup_male" to R.drawable.species_scaup_male,
        "shoveler_female" to R.drawable.species_shoveler_female,
        "shoveler_male" to R.drawable.species_shoveler_male,
        "sika_deer_female" to R.drawable.species_sika_deer_female,
        "sika_deer_male" to R.drawable.species_sika_deer_male,
        "teal_female" to R.drawable.species_teal_female,
        "teal_male" to R.drawable.species_teal_male,
        "tufted_duck_female" to R.drawable.species_tufted_duck_female,
        "tufted_duck_male" to R.drawable.species_tufted_duck_male,
        "white_fronted_goose_female" to R.drawable.species_white_fronted_goose_female,
        "white_fronted_goose_male" to R.drawable.species_white_fronted_goose_male,
        "wigeon_female" to R.drawable.species_wigeon_female,
        "wigeon_male" to R.drawable.species_wigeon_male,
        "woodcock_female" to R.drawable.species_woodcock_female,
        "woodcock_male" to R.drawable.species_woodcock_male,
        "woodpigeon_female" to R.drawable.species_woodpigeon_female,
        "woodpigeon_male" to R.drawable.species_woodpigeon_male,
    )
}
