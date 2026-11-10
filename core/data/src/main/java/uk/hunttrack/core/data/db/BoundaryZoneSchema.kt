package uk.hunttrack.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 8 → 9 adds zone tables. Existing boundary rows are left in place.
 * The SQL matches the tables Room creates for [BoundaryZoneEntity] and
 * [BoundaryZonePointEntity] so the post-migration schema check succeeds.
 */
object BoundaryZoneSchema {
    const val CREATE_ZONES =
        "CREATE TABLE IF NOT EXISTS `boundary_zones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`boundaryId` INTEGER NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
            "FOREIGN KEY(`boundaryId`) REFERENCES `boundary_areas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_ZONES_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_boundary_zones_boundaryId` ON `boundary_zones` (`boundaryId`)"

    const val CREATE_ZONE_POINTS =
        "CREATE TABLE IF NOT EXISTS `boundary_zone_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`zoneId` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
            "`orderIndex` INTEGER NOT NULL, FOREIGN KEY(`zoneId`) REFERENCES `boundary_zones`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_ZONE_POINTS_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_boundary_zone_points_zoneId` ON `boundary_zone_points` (`zoneId`)"

    val statements: List<String> = listOf(
        CREATE_ZONES,
        CREATE_ZONES_INDEX,
        CREATE_ZONE_POINTS,
        CREATE_ZONE_POINTS_INDEX,
    )
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        BoundaryZoneSchema.statements.forEach { db.execSQL(it) }
    }
}
