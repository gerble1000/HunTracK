package uk.hunttrack.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MarkerEntity::class,
        RouteEntity::class,
        TrackPointEntity::class,
        OfflineMapRegionEntity::class,
        WeatherCacheEntity::class,
        CallJournalEntity::class,
        UserCallEntity::class,
        AudioPackEntity::class,
        HuntingPermissionEntity::class,
        BoundaryAreaEntity::class,
        BoundaryPointEntity::class,
        EmergencyProfileEntity::class,
        CalendarDayEntryEntity::class,
        TideStationCacheEntity::class,
        TideEventsCacheEntity::class,
        CullRecordEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class HunTracKDatabase : RoomDatabase() {
    abstract fun markerDao(): MarkerDao
    abstract fun routeDao(): RouteDao
    abstract fun offlineMapDao(): OfflineMapDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun callJournalDao(): CallJournalDao
    abstract fun userCallDao(): UserCallDao
    abstract fun audioPackDao(): AudioPackDao
    abstract fun huntingPermissionDao(): HuntingPermissionDao
    abstract fun boundaryDao(): BoundaryDao
    abstract fun emergencyDao(): EmergencyDao
    abstract fun calendarDayDao(): CalendarDayDao
    abstract fun tideCacheDao(): TideCacheDao
    abstract fun cullDao(): CullDao
}
