package uk.hunttrack.core.data.cull

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.hunttrack.core.data.db.CullDao
import uk.hunttrack.core.data.db.CullRecordEntity
import uk.hunttrack.domain.model.CarcassTestStatus
import uk.hunttrack.domain.model.CullAgeClass
import uk.hunttrack.domain.model.CullRecord
import uk.hunttrack.domain.model.CullSex

@Singleton
class CullRepository @Inject constructor(
    private val dao: CullDao,
) {
    fun observeAll(): Flow<List<CullRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): CullRecord? = dao.getById(id)?.toDomain()

    suspend fun save(record: CullRecord): Long = dao.upsert(record.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)
}

private fun CullRecordEntity.toDomain() = CullRecord(
    id = id,
    culledAtEpochMs = culledAtEpochMs,
    speciesId = speciesId,
    speciesLabel = speciesLabel,
    sex = runCatching { CullSex.valueOf(sex) }.getOrDefault(CullSex.UNKNOWN),
    ageClass = runCatching { CullAgeClass.valueOf(ageClass) }.getOrDefault(CullAgeClass.UNKNOWN),
    count = count,
    latitude = latitude,
    longitude = longitude,
    osGridRef = osGridRef,
    locationName = locationName,
    carcassTestStatus = runCatching { CarcassTestStatus.valueOf(carcassTestStatus) }
        .getOrDefault(CarcassTestStatus.NOT_REQUIRED),
    notes = notes,
    linkedMarkerId = linkedMarkerId,
)

private fun CullRecord.toEntity() = CullRecordEntity(
    id = id,
    culledAtEpochMs = culledAtEpochMs,
    speciesId = speciesId,
    speciesLabel = speciesLabel,
    sex = sex.name,
    ageClass = ageClass.name,
    count = count,
    latitude = latitude,
    longitude = longitude,
    osGridRef = osGridRef,
    locationName = locationName,
    carcassTestStatus = carcassTestStatus.name,
    notes = notes,
    linkedMarkerId = linkedMarkerId,
)
