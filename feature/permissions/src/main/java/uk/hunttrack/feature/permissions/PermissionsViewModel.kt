package uk.hunttrack.feature.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.phase2.BoundaryCreationSession
import uk.hunttrack.core.data.phase2.PermissionMapViewSession
import uk.hunttrack.core.data.phase2.PermissionsRepository
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryCaptureMode
import uk.hunttrack.domain.model.BoundaryPoint
import uk.hunttrack.domain.model.BoundaryZoneType
import uk.hunttrack.domain.model.HuntingPermission
import uk.hunttrack.domain.model.PendingBoundaryCreation
import uk.hunttrack.domain.model.PermissionDocType
import uk.hunttrack.domain.model.PermissionMapFocusItem
import uk.hunttrack.domain.model.displayLabel

data class PendingPermissionBoundaryLink(
    val permissionId: Long,
    val suggestedBoundaryName: String,
)

data class PermissionsUiState(
    val permissions: List<HuntingPermission> = emptyList(),
    val boundaries: List<BoundaryArea> = emptyList(),
    val tab: PermTab = PermTab.WALLET,
    val pendingBoundaryLink: PendingPermissionBoundaryLink? = null,
    val selectedPermissionIds: Set<Long> = emptySet(),
)

enum class PermTab { WALLET, BOUNDARIES }

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val repository: PermissionsRepository,
    private val locationRepository: LocationRepository,
    private val boundaryCreationSession: BoundaryCreationSession,
    private val permissionMapViewSession: PermissionMapViewSession,
) : ViewModel() {

    private val tab = MutableStateFlow(PermTab.WALLET)
    private val pendingBoundaryLink = MutableStateFlow<PendingPermissionBoundaryLink?>(null)
    private val selectedPermissionIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<PermissionsUiState> = combine(
        repository.observePermissions(),
        repository.observeBoundaries(),
        tab,
        pendingBoundaryLink,
        selectedPermissionIds,
    ) { permissions, boundaries, t, pending, selected ->
        PermissionsUiState(permissions, boundaries, t, pending, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PermissionsUiState())

    fun setTab(value: PermTab) {
        tab.value = value
    }

    fun savePermission(
        label: String,
        farm: String,
        type: String,
        species: String,
        owner: String,
        contact: String,
        notes: String,
        validDays: Int,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = repository.savePermission(
                HuntingPermission(
                    label = label.trim(),
                    farmName = farm,
                    permissionType = type.ifBlank { "Stalking" },
                    species = species,
                    validFromEpochMs = now,
                    validToEpochMs = now + validDays * 24L * 60 * 60 * 1000,
                    ownerName = owner,
                    ownerContact = contact,
                    notes = notes,
                    docType = PermissionDocType.NOTE,
                ),
            )
            pendingBoundaryLink.value = PendingPermissionBoundaryLink(
                permissionId = id,
                suggestedBoundaryName = label.trim().ifBlank { farm.ifBlank { "Hunting area" } },
            )
        }
    }

    fun dismissBoundaryLink() {
        pendingBoundaryLink.value = null
    }

    fun linkExistingBoundary(boundaryId: Long) {
        viewModelScope.launch {
            val pending = pendingBoundaryLink.value ?: return@launch
            repository.linkPermissionToBoundary(pending.permissionId, boundaryId)
            pendingBoundaryLink.value = null
        }
    }

    fun togglePermissionSelection(id: Long) {
        selectedPermissionIds.value = selectedPermissionIds.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAllWithBoundaries() {
        viewModelScope.launch {
            val permissions = repository.observePermissions().first()
            selectedPermissionIds.value = permissions
                .filter { it.boundaryAreaId != null }
                .map { it.id }
                .toSet()
        }
    }

    fun clearPermissionSelection() {
        selectedPermissionIds.value = emptySet()
    }

    fun showPermissionsOnMap(permissionIds: List<Long>) {
        viewModelScope.launch {
            val focus = buildMapFocusItems(permissionIds)
            if (focus.isEmpty()) return@launch
            permissionMapViewSession.setFocus(focus)
            selectedPermissionIds.value = emptySet()
        }
    }

    fun showSelectedOnMap() {
        showPermissionsOnMap(selectedPermissionIds.value.toList())
    }

    private suspend fun buildMapFocusItems(permissionIds: List<Long>): List<PermissionMapFocusItem> {
        val permissions = repository.observePermissions().first()
        return permissionIds.mapNotNull { id ->
            val permission = permissions.find { it.id == id } ?: return@mapNotNull null
            val boundaryId = permission.boundaryAreaId ?: return@mapNotNull null
            PermissionMapFocusItem(
                permissionId = permission.id,
                label = permission.displayLabel(),
                boundaryAreaId = boundaryId,
            )
        }
    }

    fun deletePermission(id: Long) {
        viewModelScope.launch {
            repository.deletePermission(id)
            selectedPermissionIds.value = selectedPermissionIds.value - id
        }
    }

    fun startBoundaryOnMap(
        mode: BoundaryCaptureMode,
        name: String,
        zone: BoundaryZoneType,
        permissionId: Long? = null,
    ) {
        boundaryCreationSession.setPending(
            PendingBoundaryCreation(
                mode = mode,
                name = name.ifBlank { "Field boundary" },
                zoneType = zone,
                permissionId = permissionId,
            ),
        )
    }

    fun startBoundaryOnMapForPending(mode: BoundaryCaptureMode, name: String, zone: BoundaryZoneType) {
        val pending = pendingBoundaryLink.value ?: return
        startBoundaryOnMap(mode, name, zone, permissionId = pending.permissionId)
        pendingBoundaryLink.value = null
    }

    fun createSquareBoundary(name: String, zone: BoundaryZoneType, sizeMeters: Double = 500.0) {
        viewModelScope.launch {
            saveSquareBoundary(name, zone, sizeMeters)
        }
    }

    fun createSquareBoundaryForPending(name: String, zone: BoundaryZoneType, sizeMeters: Double = 500.0) {
        viewModelScope.launch {
            val pending = pendingBoundaryLink.value ?: return@launch
            val boundaryId = saveSquareBoundary(name, zone, sizeMeters)
            repository.linkPermissionToBoundary(pending.permissionId, boundaryId)
            pendingBoundaryLink.value = null
        }
    }

    private suspend fun saveSquareBoundary(
        name: String,
        zone: BoundaryZoneType,
        sizeMeters: Double,
    ): Long {
        val fix = locationRepository.lastKnown() ?: locationRepository.fallbackFix()
        val dLat = (sizeMeters / 2) / 111_000.0
        val dLng = (sizeMeters / 2) / (111_000.0 * kotlin.math.cos(Math.toRadians(fix.latitude)))
        val points = listOf(
            BoundaryPoint(fix.latitude - dLat, fix.longitude - dLng, 0),
            BoundaryPoint(fix.latitude - dLat, fix.longitude + dLng, 1),
            BoundaryPoint(fix.latitude + dLat, fix.longitude + dLng, 2),
            BoundaryPoint(fix.latitude + dLat, fix.longitude - dLng, 3),
        )
        return repository.saveBoundary(
            BoundaryArea(
                name = name.ifBlank { "Area" },
                zoneType = zone,
                notes = "Created from GPS",
                points = points,
            ),
        )
    }

    fun deleteBoundary(id: Long) {
        viewModelScope.launch { repository.deleteBoundary(id) }
    }
}
