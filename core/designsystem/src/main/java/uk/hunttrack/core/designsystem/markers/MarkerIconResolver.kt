package uk.hunttrack.core.designsystem.markers

import androidx.annotation.DrawableRes
import uk.hunttrack.core.designsystem.R
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.MarkerCategory
import uk.hunttrack.domain.model.Species
import uk.hunttrack.domain.model.SpeciesGroup

/** Resolves map / sheet icons for field markers and UK species sightings. */
object MarkerIconResolver {

    @DrawableRes
    fun iconFor(category: MarkerCategory, speciesId: String? = null): Int {
        speciesId?.let { id ->
            UkFieldCatalog.speciesById(id)?.let { return iconForSpecies(it) }
        }
        return iconForCategory(category)
    }

    @DrawableRes
    fun iconForSpecies(species: Species): Int = when (species.id) {
        "fox" -> R.drawable.marker_ic_fox
        "rabbit" -> R.drawable.marker_ic_rabbit
        "brown_hare" -> R.drawable.marker_ic_hare
        "american_mink", "grey_squirrel" -> R.drawable.marker_ic_pest
        "brown_rat", "black_rat" -> R.drawable.marker_ic_rat
        "carrion_crow", "hooded_crow", "rook", "jackdaw", "magpie", "jay" -> R.drawable.marker_ic_crow
        "common_snipe" -> R.drawable.marker_ic_snipe
        "woodcock" -> R.drawable.marker_ic_woodcock
        "canada_goose", "greylag_goose", "pink_footed_goose", "white_fronted_goose" -> R.drawable.marker_ic_goose
        "mallard", "teal", "wigeon", "pintail", "shoveler", "gadwall",
        "goldeneye", "tufted_duck", "pochard", "scaup",
        -> R.drawable.marker_ic_duck
        else -> when (species.group) {
            SpeciesGroup.DEER -> R.drawable.marker_ic_deer
            SpeciesGroup.OTHER_QUARRY -> R.drawable.marker_ic_fox
            SpeciesGroup.BIRD -> R.drawable.marker_ic_bird
            SpeciesGroup.WATERFOWL -> R.drawable.marker_ic_duck
            SpeciesGroup.CORVID -> R.drawable.marker_ic_crow
            SpeciesGroup.PEST -> R.drawable.marker_ic_pest
        }
    }

    @DrawableRes
    fun iconForCategory(category: MarkerCategory): Int = when (category) {
        MarkerCategory.DEER -> R.drawable.marker_ic_deer
        MarkerCategory.FOX -> R.drawable.marker_ic_fox
        MarkerCategory.RABBIT -> R.drawable.marker_ic_rabbit
        MarkerCategory.BIRD -> R.drawable.marker_ic_bird
        MarkerCategory.BADGER -> R.drawable.marker_ic_badger
        MarkerCategory.OTHER_WILDLIFE -> R.drawable.marker_ic_other
        MarkerCategory.HIGH_SEAT -> R.drawable.marker_ic_high_seat
        MarkerCategory.HIDE -> R.drawable.marker_ic_hide
        MarkerCategory.FEEDING_AREA -> R.drawable.marker_ic_feed
        MarkerCategory.TRAIL_CAMERA -> R.drawable.marker_ic_camera
        MarkerCategory.CALLER -> R.drawable.marker_ic_caller
        MarkerCategory.WATER_SOURCE -> R.drawable.marker_ic_water
        MarkerCategory.SALT_LICK -> R.drawable.marker_ic_salt
        MarkerCategory.TREE_STAND -> R.drawable.marker_ic_tree_stand
        MarkerCategory.PARKING -> R.drawable.marker_ic_parking
        MarkerCategory.GATE -> R.drawable.marker_ic_gate
        MarkerCategory.FENCE -> R.drawable.marker_ic_fence
        MarkerCategory.BOUNDARY -> R.drawable.marker_ic_boundary
        MarkerCategory.DANGER_AREA -> R.drawable.marker_ic_danger
        MarkerCategory.SHELTER -> R.drawable.marker_ic_shelter
        MarkerCategory.HOMING -> R.drawable.marker_ic_sighting
        MarkerCategory.CULL -> R.drawable.marker_ic_skull
        MarkerCategory.FOOTPRINT -> R.drawable.marker_ic_footprint
        MarkerCategory.BLOOD -> R.drawable.marker_ic_blood
        MarkerCategory.HAIR -> R.drawable.marker_ic_hair
        MarkerCategory.DROPPINGS -> R.drawable.marker_ic_droppings
        MarkerCategory.BEDDING_AREA -> R.drawable.marker_ic_bedding
        MarkerCategory.LAST_SIGHTING -> R.drawable.marker_ic_sighting
    }

    fun categoryForSpecies(species: Species): MarkerCategory = when (species.id) {
        "fox" -> MarkerCategory.FOX
        "rabbit" -> MarkerCategory.RABBIT
        "brown_hare" -> MarkerCategory.RABBIT
        else -> when (species.group) {
            SpeciesGroup.DEER -> MarkerCategory.DEER
            SpeciesGroup.BIRD, SpeciesGroup.WATERFOWL, SpeciesGroup.CORVID -> MarkerCategory.BIRD
            SpeciesGroup.PEST -> MarkerCategory.OTHER_WILDLIFE
            SpeciesGroup.OTHER_QUARRY -> MarkerCategory.FOX
        }
    }
}
