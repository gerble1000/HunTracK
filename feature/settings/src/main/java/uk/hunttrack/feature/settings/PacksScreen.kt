package uk.hunttrack.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.offline.OfflineMapRepository
import uk.hunttrack.core.data.packs.PackBilling
import uk.hunttrack.core.data.packs.PackBillingState
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.HuntTextButton
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.model.OfflineMapRegion
import uk.hunttrack.domain.packs.HuntingRule
import uk.hunttrack.domain.packs.PackDataQuality
import uk.hunttrack.domain.packs.PackSpeciesSeason
import uk.hunttrack.domain.packs.RegionPackCatalog
import uk.hunttrack.domain.packs.RuleAnswer
import uk.hunttrack.domain.packs.SeasonPack

@HiltViewModel
class PacksViewModel @Inject constructor(
    private val billing: PackBilling,
    private val offlineMaps: OfflineMapRepository,
) : ViewModel() {
    val billingState: StateFlow<PackBillingState> = billing.state
    val regions: StateFlow<List<OfflineMapRegion>> = offlineMaps.observeRegions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unlocked(pack: SeasonPack): Boolean = billing.isUnlocked(pack)

    fun price(pack: SeasonPack): String = billing.priceLabel(pack)

    fun purchase(activity: Activity, pack: SeasonPack) = billing.purchase(activity, pack)

    fun restore() = billing.restore()

    fun download(pack: SeasonPack, regionId: String) {
        if (!billing.isUnlocked(pack)) return
        val region = RegionPackCatalog.mapRegions.firstOrNull { it.id == regionId } ?: return
        val bounds = region.bounds()
        viewModelScope.launch {
            runCatching {
                offlineMaps.createNamedRegion(region.name, bounds.north, bounds.south, bounds.east, bounds.west)
            }
        }
    }
}

@Composable
fun PacksRoute(viewModel: PacksViewModel = hiltViewModel()) {
    val billing by viewModel.billingState.collectAsStateWithLifecycle()
    val saved by viewModel.regions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf(RegionPackCatalog.UK_SEASON_PACK_ID) }
    PacksScreen(
        billing = billing,
        savedNames = saved.map { it.name }.toSet(),
        selectedId = selected,
        onSelect = { selected = it },
        unlocked = viewModel::unlocked,
        price = viewModel::price,
        onPurchase = { pack ->
            context.findActivity()?.let { viewModel.purchase(it, pack) }
        },
        onRestore = viewModel::restore,
        onDownload = viewModel::download,
    )
}

@Composable
fun PacksScreen(
    billing: PackBillingState,
    savedNames: Set<String>,
    selectedId: String,
    onSelect: (String) -> Unit,
    unlocked: (SeasonPack) -> Boolean,
    price: (SeasonPack) -> String,
    onPurchase: (SeasonPack) -> Unit,
    onRestore: () -> Unit,
    onDownload: (SeasonPack, String) -> Unit,
) {
    val selected = RegionPackCatalog.pack(selectedId) ?: RegionPackCatalog.seasonPacks.first()
    val open = unlocked(selected)
    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Packs", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text(
                    "One pack per country, intended price £1.00. " +
                        "Map downloads are small areas, not a whole country. " +
                        "OpenFreeMap tiles, © OpenStreetMap contributors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Countries")
                    RegionPackCatalog.seasonPacks.forEach { pack ->
                        val label = if (unlocked(pack)) {
                            if (pack.bundledWithApp) "Included" else "Purchased"
                        } else {
                            price(pack)
                        }
                        HuntTextButton(onClick = { onSelect(pack.id) }, text = "${pack.countryName} · $label")
                    }
                    HuntButton(onClick = onRestore, text = "Restore purchases", modifier = Modifier.fillMaxWidth())
                    billing.message?.let {
                        Text(it, color = HuntColors.Gold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(selected.title, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                    Text(selected.summary, color = HuntColors.Mist, style = MaterialTheme.typography.bodySmall)
                    Text(selected.disclaimer, color = HuntColors.Mist, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Product ID ${selected.productId} · ${price(selected)}",
                        color = HuntColors.BronzeBright,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (!open) {
                        HuntButton(
                            onClick = { onPurchase(selected) },
                            text = "Buy ${selected.countryName}",
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            "Species seasons stay locked until Play acknowledges this product. " +
                                "The product is not in the store yet, so the button will not charge you.",
                            color = HuntColors.Mist,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Hunting rules")
                    selected.rules.forEach { rule -> RuleBlock(rule) }
                }
            }
            if (open) {
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionLabel("Map areas")
                        Text(
                            "About 15 square miles each. Streets and Outdoor can work offline after the download. Satellite does not.",
                            color = HuntColors.Mist,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        RegionPackCatalog.regionsFor(selected.countryCode).forEach { region ->
                            val saved = region.name in savedNames
                            Text(region.name, color = HuntColors.Cream, modifier = Modifier.padding(top = 8.dp))
                            Text(region.summary, color = HuntColors.Mist, style = MaterialTheme.typography.bodySmall)
                            HuntTextButton(
                                onClick = { if (!saved) onDownload(selected, region.id) },
                                text = if (saved) "Saved" else "Download map area",
                            )
                        }
                    }
                }
                val cited = selected.species.filter { it.quality != PackDataQuality.PLACEHOLDER }
                val slots = selected.species.filter { it.quality == PackDataQuality.PLACEHOLDER }
                val spotlight = listOf("mouflon", "badger", "chamois", "wild_boar", "alpine_ibex")
                val spotlightRows = spotlight.mapNotNull { key -> slots.firstOrNull { it.speciesKey == key } }
                if (spotlightRows.isNotEmpty()) {
                    item { SectionLabel("Wider species slots") }
                    items(spotlightRows, key = { "s-${it.speciesKey}" }) { row ->
                        SpeciesRow(row)
                    }
                }
                if (cited.isNotEmpty()) {
                    item { SectionLabel("Cited seasons") }
                    items(cited, key = { "c-${it.speciesKey}-${it.sexLabel}-${it.regionLabel}-${it.notes.hashCode()}" }) { row ->
                        SpeciesRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleBlock(rule: HuntingRule) {
    Text(rule.title, color = HuntColors.Cream, modifier = Modifier.padding(top = 8.dp))
    Text(rule.answer.shortLabel(), color = HuntColors.BronzeBright, style = MaterialTheme.typography.labelLarge)
    Text(rule.notes, color = HuntColors.Mist, style = MaterialTheme.typography.bodySmall)
    val verified = rule.lastVerified?.let { " · read $it" }.orEmpty()
    Text(
        "${rule.sourceTitle}$verified",
        color = HuntColors.Gold,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SpeciesRow(row: PackSpeciesSeason) {
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            listOf(row.commonName, row.sexLabel, row.regionLabel).filter { it.isNotBlank() }.joinToString(" · "),
            color = HuntColors.Cream,
        )
        if (row.scientificName.isNotBlank()) {
            Text(row.scientificName, color = HuntColors.Mist, style = MaterialTheme.typography.bodySmall)
        }
        Text(row.statusLabel(), color = HuntColors.BronzeBright, style = MaterialTheme.typography.labelLarge)
        Text(row.seasonText(), color = HuntColors.Cream, style = MaterialTheme.typography.bodyMedium)
        if (row.notes.isNotBlank()) {
            Text(row.notes, color = HuntColors.Mist, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun RuleAnswer.shortLabel(): String = when (this) {
    RuleAnswer.ALLOWED -> "Allowed"
    RuleAnswer.PROHIBITED -> "Not allowed"
    RuleAnswer.CONDITIONAL -> "Conditional"
    RuleAnswer.NOT_REQUIRED -> "Not required"
    RuleAnswer.UNVERIFIED -> "Unverified"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
