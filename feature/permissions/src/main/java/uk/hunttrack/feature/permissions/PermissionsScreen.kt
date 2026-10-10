package uk.hunttrack.feature.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.hunttrack.core.common.formatBoundaryArea
import uk.hunttrack.core.common.formatBoundaryPerimeter
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.MetricRow
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.geo.BoundaryMath
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryCaptureMode
import uk.hunttrack.domain.model.BoundaryZoneType
import uk.hunttrack.domain.model.displayLabel
import uk.hunttrack.domain.model.displayName
import uk.hunttrack.domain.model.summary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PermissionsRoute(
    viewModel: PermissionsViewModel = hiltViewModel(),
    onOpenMapForBoundary: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PermissionsScreen(
        state = state,
        onTab = viewModel::setTab,
        onSavePermission = viewModel::savePermission,
        onDeletePermission = viewModel::deletePermission,
        onDismissBoundaryLink = viewModel::dismissBoundaryLink,
        onLinkExistingBoundary = viewModel::linkExistingBoundary,
        onDrawBoundaryOnMap = { name, zone ->
            viewModel.startBoundaryOnMap(BoundaryCaptureMode.DRAW, name, zone)
            onOpenMapForBoundary()
        },
        onWalkBoundaryOnMap = { name, zone ->
            viewModel.startBoundaryOnMap(BoundaryCaptureMode.WALK, name, zone)
            onOpenMapForBoundary()
        },
        onDrawBoundaryForPending = { name, zone ->
            viewModel.startBoundaryOnMapForPending(BoundaryCaptureMode.DRAW, name, zone)
            onOpenMapForBoundary()
        },
        onWalkBoundaryForPending = { name, zone ->
            viewModel.startBoundaryOnMapForPending(BoundaryCaptureMode.WALK, name, zone)
            onOpenMapForBoundary()
        },
        onCreateBoundary = viewModel::createSquareBoundary,
        onCreateBoundaryForPending = viewModel::createSquareBoundaryForPending,
        onDeleteBoundary = viewModel::deleteBoundary,
        onTogglePermissionSelection = viewModel::togglePermissionSelection,
        onSelectAllWithBoundaries = viewModel::selectAllWithBoundaries,
        onClearPermissionSelection = viewModel::clearPermissionSelection,
        onShowPermissionOnMap = { viewModel.showPermissionsOnMap(listOf(it)) },
        onShowSelectedOnMap = viewModel::showSelectedOnMap,
        onOpenMap = onOpenMapForBoundary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    state: PermissionsUiState,
    onTab: (PermTab) -> Unit,
    onSavePermission: (label: String, farm: String, type: String, species: String, owner: String, contact: String, notes: String, validDays: Int) -> Unit,
    onDeletePermission: (Long) -> Unit,
    onDismissBoundaryLink: () -> Unit,
    onLinkExistingBoundary: (Long) -> Unit,
    onDrawBoundaryOnMap: (name: String, zone: BoundaryZoneType) -> Unit,
    onWalkBoundaryOnMap: (name: String, zone: BoundaryZoneType) -> Unit,
    onDrawBoundaryForPending: (name: String, zone: BoundaryZoneType) -> Unit,
    onWalkBoundaryForPending: (name: String, zone: BoundaryZoneType) -> Unit,
    onCreateBoundary: (name: String, zone: BoundaryZoneType, sizeMeters: Double) -> Unit,
    onCreateBoundaryForPending: (name: String, zone: BoundaryZoneType, sizeMeters: Double) -> Unit,
    onDeleteBoundary: (Long) -> Unit,
    onTogglePermissionSelection: (Long) -> Unit,
    onSelectAllWithBoundaries: () -> Unit,
    onClearPermissionSelection: () -> Unit,
    onShowPermissionOnMap: (Long) -> Unit,
    onShowSelectedOnMap: () -> Unit,
    onOpenMap: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var farm by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Stalking") }
    var species by remember { mutableStateOf("Red deer") }
    var owner by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var boundaryName by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(BoundaryZoneType.HUNTING) }

    var hadPendingLink by remember { mutableStateOf(false) }
    LaunchedEffect(state.pendingBoundaryLink) {
        if (hadPendingLink && state.pendingBoundaryLink == null) {
            label = ""
            farm = ""
            notes = ""
        }
        hadPendingLink = state.pendingBoundaryLink != null
    }

    val pendingLink = state.pendingBoundaryLink
    if (pendingLink != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissBoundaryLink,
            sheetState = sheetState,
            containerColor = HuntColors.ForestDeep,
        ) {
            PermissionBoundaryLinkSheet(
                pending = pendingLink,
                boundaries = state.boundaries,
                onSkip = onDismissBoundaryLink,
                onLinkExisting = onLinkExistingBoundary,
                onDrawOnMap = onDrawBoundaryForPending,
                onWalkOnMap = onWalkBoundaryForPending,
                onQuickSquare = onCreateBoundaryForPending,
            )
        }
    }

    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Permissions", style = MaterialTheme.typography.headlineLarge)
                Text("Wallet & boundary management", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.tab == PermTab.WALLET, onClick = { onTab(PermTab.WALLET) }, label = { Text("Wallet") })
                    FilterChip(selected = state.tab == PermTab.BOUNDARIES, onClick = { onTab(PermTab.BOUNDARIES) }, label = { Text("Boundaries") })
                }
            }
            if (state.tab == PermTab.WALLET) {
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionLabel("Add permission")
                        OutlinedTextField(
                            label,
                            { label = it },
                            label = { Text("Label / nickname") },
                            placeholder = { Text("e.g. Bob's farm, The estate") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(farm, { farm = it }, label = { Text("Farm / estate") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(type, { type = it }, label = { Text("Permission type") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(species, { species = it }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(contact, { contact = it }, label = { Text("Contact") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                onSavePermission(label, farm, type, species, owner, contact, notes, 365)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save (1 year)") }
                        Text(
                            "After saving you can link a hunting boundary — existing or new.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Mist,
                        )
                        Text("Attach PDF/photo via system picker in a later polish pass — notes & contacts work offline now.", style = MaterialTheme.typography.bodySmall, color = HuntColors.Mist)
                    }
                }
                if (state.permissions.any { it.boundaryAreaId != null }) {
                    item {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            SectionLabel("Show on map")
                            Text(
                                "Select one or more permissions with linked boundaries, then open the map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onSelectAllWithBoundaries) { Text("Select all linked") }
                                if (state.selectedPermissionIds.isNotEmpty()) {
                                    TextButton(onClick = onClearPermissionSelection) { Text("Clear") }
                                }
                            }
                            if (state.selectedPermissionIds.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        onShowSelectedOnMap()
                                        onOpenMap()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Show ${state.selectedPermissionIds.size} on map")
                                    }
                                }
                            }
                        }
                    }
                }
                items(state.permissions) { p ->
                    val linkedBoundary = p.boundaryAreaId?.let { id -> state.boundaries.find { it.id == id } }
                    val canShowOnMap = p.boundaryAreaId != null && linkedBoundary != null
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (canShowOnMap) {
                                Checkbox(
                                    checked = p.id in state.selectedPermissionIds,
                                    onCheckedChange = { onTogglePermissionSelection(p.id) },
                                )
                            } else {
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(p.displayLabel(), style = MaterialTheme.typography.titleLarge)
                                if (p.label.isNotBlank() && p.farmName.isNotBlank() && p.label != p.farmName) {
                                    Text(
                                        p.farmName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HuntColors.Mist,
                                    )
                                }
                            }
                        }
                        MetricRow("Type", p.permissionType)
                        MetricRow("Species", p.species)
                        MetricRow("Owner", p.ownerName)
                        MetricRow("Contact", p.ownerContact)
                        MetricRow("Valid", "${fmt(p.validFromEpochMs)} – ${fmt(p.validToEpochMs)}")
                        if (linkedBoundary != null) {
                            Spacer(Modifier.height(8.dp))
                            LinkedBoundarySummary(boundary = linkedBoundary)
                        } else if (p.boundaryAreaId != null) {
                            MetricRow("Boundary", "Linked area removed")
                        }
                        if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onShowPermissionOnMap(p.id)
                                    onOpenMap()
                                },
                                enabled = canShowOnMap,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Show on map")
                            }
                            TextButton(onClick = { onDeletePermission(p.id) }) { Text("Delete") }
                        }
                        if (!canShowOnMap) {
                            Text(
                                "Link a boundary when saving to show this permission on the map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                            )
                        }
                    }
                }
            } else {
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(
                            "Mark areas on your map so you know where you may hunt, must avoid, or can retreat to.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HuntColors.Cream,
                        )
                        Text(
                            "Choose a type below, give it a name, then draw or walk the fence on the map. " +
                                "Saved areas appear on the Map tab in matching colours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Mist,
                        )
                    }
                }
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionLabel("Create boundary")
                        OutlinedTextField(boundaryName, { boundaryName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        SectionLabel("Area type")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BoundaryZoneType.entries.forEach { z ->
                                ZoneTypeOption(
                                    zone = z,
                                    selected = zone == z,
                                    onClick = { zone = z },
                                )
                            }
                        }
                        Text(
                            "Draw or walk the fence on the map — same tools as the Map tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Mist,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onDrawBoundaryOnMap(boundaryName, zone) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Draw fence on map")
                                }
                            }
                            Button(
                                onClick = { onWalkBoundaryOnMap(boundaryName, zone) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Walk fence on map")
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                onCreateBoundary(boundaryName, zone, 500.0)
                                boundaryName = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Quick 500 m square around me") }
                    }
                }
                items(state.boundaries) { b ->
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(b.name, style = MaterialTheme.typography.titleLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (b.zoneType) {
                                            BoundaryZoneType.NO_GO -> HuntColors.Danger
                                            BoundaryZoneType.SAFE -> HuntColors.Success
                                            BoundaryZoneType.HUNTING -> HuntColors.Bronze
                                        },
                                    ),
                            )
                            Text(
                                b.zoneType.displayName(),
                                color = when (b.zoneType) {
                                    BoundaryZoneType.NO_GO -> HuntColors.Danger
                                    BoundaryZoneType.SAFE -> HuntColors.Success
                                    BoundaryZoneType.HUNTING -> HuntColors.Bronze
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            b.zoneType.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Mist,
                        )
                        MetricRow("Points", "${b.points.size}")
                        MetricRow("Perimeter", formatBoundaryPerimeter(BoundaryMath.perimeterMeters(b.points)))
                        MetricRow("Area", formatBoundaryArea(b.areaSqMeters))
                        if (b.notes.isNotBlank()) Text(b.notes, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onDeleteBoundary(b.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBoundaryLinkSheet(
    pending: PendingPermissionBoundaryLink,
    boundaries: List<BoundaryArea>,
    onSkip: () -> Unit,
    onLinkExisting: (Long) -> Unit,
    onDrawOnMap: (name: String, zone: BoundaryZoneType) -> Unit,
    onWalkOnMap: (name: String, zone: BoundaryZoneType) -> Unit,
    onQuickSquare: (name: String, zone: BoundaryZoneType, sizeMeters: Double) -> Unit,
) {
    var boundaryName by remember(pending) { mutableStateOf(pending.suggestedBoundaryName) }
    var zone by remember { mutableStateOf(BoundaryZoneType.HUNTING) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Link hunting area", style = MaterialTheme.typography.headlineSmall, color = HuntColors.Cream)
        Text(
            "Optionally tie this permission to a map boundary — choose a saved area or create a new one.",
            style = MaterialTheme.typography.bodyMedium,
            color = HuntColors.Mist,
        )
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
        }

        HorizontalDivider(color = HuntColors.GlassBorder)

        SectionLabel("Choose existing")
        if (boundaries.isEmpty()) {
            Text(
                "No saved boundaries yet — create one below.",
                style = MaterialTheme.typography.bodySmall,
                color = HuntColors.Mist,
            )
        } else {
            boundaries.forEach { b ->
                ExistingBoundaryPickRow(
                    boundary = b,
                    onClick = { onLinkExisting(b.id) },
                )
            }
        }

        HorizontalDivider(color = HuntColors.GlassBorder)

        SectionLabel("Create new boundary")
        OutlinedTextField(
            value = boundaryName,
            onValueChange = { boundaryName = it },
            label = { Text("Boundary name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoundaryZoneType.entries.forEach { z ->
                ZoneTypeOption(
                    zone = z,
                    selected = zone == z,
                    onClick = { zone = z },
                )
            }
        }
        Button(
            onClick = { onDrawOnMap(boundaryName, zone) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Draw fence on map")
            }
        }
        Button(
            onClick = { onWalkOnMap(boundaryName, zone) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Walk fence on map")
            }
        }
        TextButton(
            onClick = { onQuickSquare(boundaryName, zone, 500.0) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Quick 500 m square around me") }
    }
}

@Composable
private fun ExistingBoundaryPickRow(
    boundary: BoundaryArea,
    onClick: () -> Unit,
) {
    val accent = when (boundary.zoneType) {
        BoundaryZoneType.NO_GO -> HuntColors.Danger
        BoundaryZoneType.SAFE -> HuntColors.Success
        BoundaryZoneType.HUNTING -> HuntColors.Bronze
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, HuntColors.GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(boundary.name, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
        Text(
            boundary.zoneType.displayName(),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
        )
        Text(
            formatBoundaryArea(boundary.areaSqMeters),
            style = MaterialTheme.typography.bodySmall,
            color = HuntColors.Mist,
        )
    }
}

@Composable
private fun LinkedBoundarySummary(boundary: BoundaryArea) {
    val accent = when (boundary.zoneType) {
        BoundaryZoneType.NO_GO -> HuntColors.Danger
        BoundaryZoneType.SAFE -> HuntColors.Success
        BoundaryZoneType.HUNTING -> HuntColors.Bronze
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HuntColors.ForestGreen.copy(alpha = 0.35f))
            .padding(10.dp),
    ) {
        Text("Linked boundary", style = MaterialTheme.typography.labelMedium, color = HuntColors.Mist)
        Text(boundary.name, style = MaterialTheme.typography.titleSmall, color = HuntColors.Cream)
        Text(
            "${boundary.zoneType.displayName()} · ${formatBoundaryArea(boundary.areaSqMeters)}",
            style = MaterialTheme.typography.bodySmall,
            color = accent,
        )
    }
}

private fun fmt(epoch: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.UK).format(Date(epoch))

@Composable
private fun ZoneTypeOption(
    zone: BoundaryZoneType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = when (zone) {
        BoundaryZoneType.NO_GO -> HuntColors.Danger
        BoundaryZoneType.SAFE -> HuntColors.Success
        BoundaryZoneType.HUNTING -> HuntColors.Bronze
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else HuntColors.GlassBorder,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                zone.displayName(),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) accent else HuntColors.Cream,
            )
        }
        Text(
            zone.summary(),
            style = MaterialTheme.typography.bodySmall,
            color = HuntColors.Mist,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
