package uk.hunttrack.core.data.db

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs the version 8 → 9 SQL against a database that already has a boundary.
 * Zones are added without rewriting the boundary row, and deleting the boundary
 * removes its zones.
 */
class BoundaryZoneMigrationTest {
    @Test
    fun migrationPreservesBoundariesAndDeletesZonesWithThem() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute(
                    """
                    CREATE TABLE boundary_areas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        zoneType TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        areaSqMeters REAL NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE boundary_points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        areaId INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        orderIndex INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    "INSERT INTO boundary_areas (id, name, zoneType, notes, areaSqMeters) " +
                        "VALUES (7, 'Beat', 'HUNTING', 'keep', 1200.0)",
                )
                statement.execute(
                    "INSERT INTO boundary_points (areaId, latitude, longitude, orderIndex) " +
                        "VALUES (7, 51.5, -1.2, 0)",
                )
                BoundaryZoneSchema.statements.forEach { statement.execute(it) }
            }

            assertEquals("Beat", queryString(connection, "SELECT name FROM boundary_areas WHERE id = 7"))
            assertEquals(1200.0, queryDouble(connection, "SELECT areaSqMeters FROM boundary_areas WHERE id = 7"), 0.001)
            assertEquals(-1.2, queryDouble(connection, "SELECT longitude FROM boundary_points WHERE areaId = 7"), 0.001)

            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO boundary_zones (id, boundaryId, name, kind, notes) " +
                        "VALUES (3, 7, 'Quarry', 'DANGER', 'steep')",
                )
                statement.execute(
                    "INSERT INTO boundary_zone_points (zoneId, latitude, longitude, orderIndex) " +
                        "VALUES (3, 51.52, -1.18, 0)",
                )
                statement.execute("DELETE FROM boundary_areas WHERE id = 7")
            }

            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM boundary_zones"))
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM boundary_zone_points"))
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM boundary_areas"))
        }
    }

    private fun queryString(connection: java.sql.Connection, sql: String): String =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                rs.getString(1)
            }
        }

    private fun queryDouble(connection: java.sql.Connection, sql: String): Double =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                rs.getDouble(1)
            }
        }

    private fun queryInt(connection: java.sql.Connection, sql: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
}
