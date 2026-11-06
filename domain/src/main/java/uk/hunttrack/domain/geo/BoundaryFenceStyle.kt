package uk.hunttrack.domain.geo

import uk.hunttrack.domain.model.HuntingPermission

/**
 * Fence line appearance shared by the map.
 * A fence being drawn, and a saved fence with no permission, is a thin blue line.
 * Once a permission is linked, the line is gold.
 * A boundary stays on the map when it has no permission, or when at least one
 * linked permission is still marked "show on map".
 */
object BoundaryFenceStyle {
    const val DRAW_COLOR = "#1A3A6E"
    const val LINKED_COLOR = "#E2B05C"
    const val DRAW_WIDTH = 1.75f
    const val LINKED_WIDTH = 2.25f

    val defaultNames = setOf(
        "",
        "Hunting area",
        "No-go zone",
        "Safe zone",
        "Field boundary",
        "Area",
    )

    fun isVisible(linkedPermissionIds: Collection<Long>, hiddenPermissionIds: Set<Long>): Boolean {
        if (linkedPermissionIds.isEmpty()) return true
        return linkedPermissionIds.any { it !in hiddenPermissionIds }
    }

    fun lineColor(linked: Boolean): String = if (linked) LINKED_COLOR else DRAW_COLOR

    fun lineWidth(linked: Boolean): Float = if (linked) LINKED_WIDTH else DRAW_WIDTH

    /** Keep a name the hunter typed. Replace only a blank or stock zone name. */
    fun nameAfterLink(currentName: String, permissionLabel: String): String {
        val label = permissionLabel.trim()
        if (label.isEmpty()) return currentName
        val current = currentName.trim()
        if (current.isEmpty() || current in defaultNames) return label
        return current
    }
}

/** Which permissions are linked, and which of those are hidden on the map. */
data class BoundaryVisibility(
    val permissions: List<HuntingPermission> = emptyList(),
    val hiddenPermissionIds: Set<Long> = emptySet(),
) {
    fun linkedIds(boundaryId: Long): List<Long> =
        permissions.mapNotNull { permission ->
            if (permission.boundaryAreaId == boundaryId) permission.id else null
        }

    fun isLinked(boundaryId: Long): Boolean = linkedIds(boundaryId).isNotEmpty()

    fun isVisible(boundaryId: Long): Boolean =
        BoundaryFenceStyle.isVisible(linkedIds(boundaryId), hiddenPermissionIds)
}
