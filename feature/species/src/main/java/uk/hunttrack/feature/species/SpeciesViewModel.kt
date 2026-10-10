package uk.hunttrack.feature.species

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.LegalSeason
import uk.hunttrack.domain.model.Species
import uk.hunttrack.domain.model.SpeciesGroup
import uk.hunttrack.domain.model.UkNation

data class SpeciesUiState(
    val query: String = "",
    val groupFilter: SpeciesGroup? = null,
    val nation: UkNation = UkNation.ENGLAND,
    val species: List<Species> = UkFieldCatalog.species,
    val selected: Species? = null,
    val seasons: List<LegalSeason> = emptyList(),
    val disclaimer: String = UkFieldCatalog.legalDisclaimer,
)

@HiltViewModel
class SpeciesViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(SpeciesUiState())
    val state: StateFlow<SpeciesUiState> = _state.asStateFlow()

    fun setQuery(query: String) {
        _state.update { it.copy(query = query).withFiltered() }
    }

    fun setGroup(group: SpeciesGroup?) {
        _state.update { it.copy(groupFilter = group).withFiltered() }
    }

    fun setNation(nation: UkNation) {
        _state.update {
            val seasons = it.selected?.let { s -> UkFieldCatalog.seasonsFor(s.id, nation) }.orEmpty()
            it.copy(nation = nation, seasons = seasons)
        }
    }

    fun select(species: Species?) {
        _state.update {
            it.copy(
                selected = species,
                seasons = species?.let { s -> UkFieldCatalog.seasonsFor(s.id, it.nation) }.orEmpty(),
            )
        }
    }

    private fun SpeciesUiState.withFiltered(): SpeciesUiState {
        val filtered = UkFieldCatalog.species.filter { s ->
            (groupFilter == null || s.group == groupFilter) &&
                (query.isBlank() ||
                    s.commonName.contains(query, true) ||
                    s.scientificName.contains(query, true))
        }
        return copy(species = filtered)
    }
}
