package uk.hunttrack.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Query("SELECT * FROM markers ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<MarkerEntity>>

    @Query("SELECT * FROM markers WHERE id = :id")
    suspend fun getById(id: Long): MarkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MarkerEntity): Long

    @Update
    suspend fun update(entity: MarkerEntity)

    @Query("DELETE FROM markers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM markers WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("DELETE FROM markers")
    suspend fun deleteAll()
}

@Dao
interface RouteDao {
    @Insert
    suspend fun insertRoute(entity: RouteEntity): Long

    @Update
    suspend fun updateRoute(entity: RouteEntity)

    @Query("SELECT * FROM routes ORDER BY startedAtEpochMs DESC")
    fun observeRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getRoute(id: Long): RouteEntity?

    @Insert
    suspend fun insertTrackPoint(entity: TrackPointEntity): Long

    @Query("SELECT * FROM track_points WHERE routeId = :routeId ORDER BY recordedAtEpochMs ASC")
    fun observeTrackPoints(routeId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE routeId = :routeId ORDER BY recordedAtEpochMs ASC")
    suspend fun getTrackPoints(routeId: Long): List<TrackPointEntity>
}

@Dao
interface OfflineMapDao {
    @Query("SELECT * FROM offline_map_regions ORDER BY name ASC")
    fun observeAll(): Flow<List<OfflineMapRegionEntity>>

    @Query("SELECT * FROM offline_map_regions ORDER BY name ASC")
    suspend fun getAll(): List<OfflineMapRegionEntity>

    @Query("SELECT COUNT(*) FROM offline_map_regions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineMapRegionEntity): Long

    @Update
    suspend fun update(entity: OfflineMapRegionEntity)

    @Query("DELETE FROM offline_map_regions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM offline_map_regions WHERE id = :id")
    suspend fun getById(id: Long): OfflineMapRegionEntity?
}

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE gridKey = :key LIMIT 1")
    suspend fun get(key: String): WeatherCacheEntity?

    @Query("SELECT * FROM weather_cache ORDER BY fetchedAtEpochMs DESC LIMIT 1")
    suspend fun getLatest(): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache")
    suspend fun clear()
}

@Dao
interface CallJournalDao {
    @Query("SELECT * FROM call_journal ORDER BY recordedAtEpochMs DESC")
    fun observeAll(): Flow<List<CallJournalEntity>>

    @Insert
    suspend fun insert(entity: CallJournalEntity): Long

    @Query("DELETE FROM call_journal WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface UserCallDao {
    @Query("SELECT * FROM user_calls ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<UserCallEntity>>

    @Insert
    suspend fun insert(entity: UserCallEntity): Long

    @Query("DELETE FROM user_calls WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AudioPackDao {
    @Query("SELECT * FROM audio_packs")
    fun observeAll(): Flow<List<AudioPackEntity>>

    @Query("SELECT * FROM audio_packs WHERE id = :id")
    suspend fun get(id: String): AudioPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AudioPackEntity)
}

@Dao
interface HuntingPermissionDao {
    @Query("SELECT * FROM hunting_permissions ORDER BY validToEpochMs ASC")
    fun observeAll(): Flow<List<HuntingPermissionEntity>>

    @Query("SELECT * FROM hunting_permissions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HuntingPermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HuntingPermissionEntity): Long

    @Query("UPDATE hunting_permissions SET boundaryAreaId = NULL WHERE boundaryAreaId = :boundaryId")
    suspend fun clearBoundaryLink(boundaryId: Long)

    @Query("DELETE FROM hunting_permissions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BoundaryDao {
    @Query("SELECT * FROM boundary_areas ORDER BY name ASC")
    fun observeAreas(): Flow<List<BoundaryAreaEntity>>

    @Query("SELECT * FROM boundary_points WHERE areaId = :areaId ORDER BY orderIndex ASC")
    suspend fun pointsFor(areaId: Long): List<BoundaryPointEntity>

    @Query("SELECT * FROM boundary_points ORDER BY areaId ASC, orderIndex ASC")
    fun observeAllPoints(): Flow<List<BoundaryPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArea(entity: BoundaryAreaEntity): Long

    @Insert
    suspend fun insertPoints(points: List<BoundaryPointEntity>)

    @Query("DELETE FROM boundary_points WHERE areaId = :areaId")
    suspend fun deletePoints(areaId: Long)

    @Query("DELETE FROM boundary_areas WHERE id = :id")
    suspend fun deleteArea(id: Long)
}

@Dao
interface TideCacheDao {
    @Query("SELECT * FROM tide_stations_cache WHERE id = 1 LIMIT 1")
    suspend fun getStationsCache(): TideStationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStationsCache(entity: TideStationCacheEntity)

    @Query("SELECT * FROM tide_events_cache WHERE stationId = :stationId LIMIT 1")
    suspend fun getEventsCache(stationId: String): TideEventsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEventsCache(entity: TideEventsCacheEntity)

    @Query("DELETE FROM tide_events_cache")
    suspend fun clearEventsCache()

    @Query("DELETE FROM tide_stations_cache")
    suspend fun clearStationsCache()
}

@Dao
interface CalendarDayDao {
    @Query("SELECT * FROM calendar_day_entries ORDER BY dayStartEpochMs ASC")
    fun observeAll(): Flow<List<CalendarDayEntryEntity>>

    @Query("SELECT * FROM calendar_day_entries WHERE dayStartEpochMs = :dayStart LIMIT 1")
    suspend fun getForDay(dayStart: Long): CalendarDayEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalendarDayEntryEntity): Long

    @Query("DELETE FROM calendar_day_entries WHERE dayStartEpochMs = :dayStart")
    suspend fun deleteForDay(dayStart: Long)
}

@Dao
interface EmergencyDao {
    @Query("SELECT * FROM emergency_profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<EmergencyProfileEntity?>

    @Query("SELECT * FROM emergency_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): EmergencyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmergencyProfileEntity)
}

@Dao
interface CullDao {
    @Query("SELECT * FROM cull_records ORDER BY culledAtEpochMs DESC")
    fun observeAll(): Flow<List<CullRecordEntity>>

    @Query("SELECT * FROM cull_records WHERE id = :id")
    suspend fun getById(id: Long): CullRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CullRecordEntity): Long

    @Query("DELETE FROM cull_records WHERE id = :id")
    suspend fun delete(id: Long)
}
