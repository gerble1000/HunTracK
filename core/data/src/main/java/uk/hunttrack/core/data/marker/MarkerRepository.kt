package uk.hunttrack.core.data.marker

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.hunttrack.core.data.db.MarkerDao
import uk.hunttrack.core.data.db.MarkerEntity
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.MarkerCategory

@Singleton
class MarkerRepository @Inject constructor(
    private val dao: MarkerDao,
) {
    fun observeMarkers(): Flow<List<FieldMarker>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getMarker(id: Long): FieldMarker? = dao.getById(id)?.toDomain()

    suspend fun save(marker: FieldMarker): Long {
        return dao.upsert(marker.toEntity())
    }

    /** One active homing target at a time — replace previous. */
    suspend fun setHoming(
        latitude: Double,
        longitude: Double,
        elevationMeters: Double? = null,
        name: String = "Downed prey",
        notes: String = "",
    ): Long {
        dao.deleteByCategory(MarkerCategory.HOMING.name)
        return dao.upsert(
            FieldMarker(
                category = MarkerCategory.HOMING,
                name = name,
                notes = notes,
                latitude = latitude,
                longitude = longitude,
                elevationMeters = elevationMeters,
                createdAtEpochMs = System.currentTimeMillis(),
                isPrivate = true,
            ).toEntity(),
        )
    }

    suspend fun clearHoming() = dao.deleteByCategory(MarkerCategory.HOMING.name)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()
}

private fun MarkerEntity.toDomain() = FieldMarker(
    id = id,
    category = runCatching { MarkerCategory.valueOf(category) }.getOrDefault(MarkerCategory.OTHER_WILDLIFE),
    name = name,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    elevationMeters = elevationMeters,
    createdAtEpochMs = createdAtEpochMs,
    weatherSnapshotJson = weatherSnapshotJson,
    photoUri = photoUri,
    isPrivate = isPrivate,
    speciesId = speciesId,
)

private fun FieldMarker.toEntity() = MarkerEntity(
    id = id,
    category = category.name,
    name = name,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    elevationMeters = elevationMeters,
    createdAtEpochMs = createdAtEpochMs,
    weatherSnapshotJson = weatherSnapshotJson,
    photoUri = photoUri,
    isPrivate = isPrivate,
    speciesId = speciesId,
)
