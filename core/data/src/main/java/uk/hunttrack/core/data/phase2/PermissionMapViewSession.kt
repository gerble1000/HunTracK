package uk.hunttrack.core.data.phase2

import javax.inject.Inject
import javax.inject.Singleton
import uk.hunttrack.domain.model.PermissionMapFocusItem

@Singleton
class PermissionMapViewSession @Inject constructor() {
    @Volatile
    private var focus: List<PermissionMapFocusItem>? = null

    fun setFocus(items: List<PermissionMapFocusItem>) {
        focus = items
    }

    fun getFocus(): List<PermissionMapFocusItem>? = focus

    fun clear() {
        focus = null
    }
}
