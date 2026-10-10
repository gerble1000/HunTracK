package uk.hunttrack.core.data.phase2

import javax.inject.Inject
import javax.inject.Singleton
import uk.hunttrack.domain.model.PendingBoundaryCreation

@Singleton
class BoundaryCreationSession @Inject constructor() {
    @Volatile
    private var pending: PendingBoundaryCreation? = null

    fun setPending(request: PendingBoundaryCreation) {
        pending = request
    }

    fun getPending(): PendingBoundaryCreation? = pending

    fun clear() {
        pending = null
    }
}
