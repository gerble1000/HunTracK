package uk.hunttrack.core.data.phase2

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.hunttrack.core.data.db.CalendarDayDao
import uk.hunttrack.core.data.db.CalendarDayEntryEntity
import uk.hunttrack.domain.model.CalendarDayEntry

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDayDao: CalendarDayDao,
) {
    fun observeEntries(): Flow<List<CalendarDayEntry>> =
        calendarDayDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveEntry(entry: CalendarDayEntry): Long =
        calendarDayDao.upsert(entry.toEntity())

    suspend fun deleteEntry(dayStartEpochMs: Long) =
        calendarDayDao.deleteForDay(dayStartEpochMs)
}

private fun CalendarDayEntryEntity.toDomain() = CalendarDayEntry(
    id = id,
    dayStartEpochMs = dayStartEpochMs,
    title = title,
    notes = notes,
    speciesId = speciesId,
    location = location,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun CalendarDayEntry.toEntity() = CalendarDayEntryEntity(
    id = id,
    dayStartEpochMs = dayStartEpochMs,
    title = title,
    notes = notes,
    speciesId = speciesId,
    location = location,
    updatedAtEpochMs = updatedAtEpochMs,
)
