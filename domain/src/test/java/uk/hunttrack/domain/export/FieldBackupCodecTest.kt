package uk.hunttrack.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hunttrack.domain.geo.BoundaryMath
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryPoint
import uk.hunttrack.domain.model.BoundaryZone
import uk.hunttrack.domain.model.BoundaryZoneKind
import uk.hunttrack.domain.model.BoundaryZoneType
import uk.hunttrack.domain.model.EmergencyContact
import uk.hunttrack.domain.model.EmergencyProfile
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.HuntingPermission
import uk.hunttrack.domain.model.MarkerCategory
import uk.hunttrack.domain.model.Route
import uk.hunttrack.domain.model.TrackPoint

class FieldBackupCodecTest {
    private val medical = EmergencyProfile(
        fullName = "Ada Field",
        bloodType = "O-",
        allergies = "penicillin",
        medicalInfo = "asthma",
        medication = "inhaler",
        notes = "private",
        contacts = listOf(EmergencyContact("Sam", "07000", "kin")),
        pinHash = "deadbeef",
        biometricEnabled = true,
    )

    @Test
    fun campsiteMarkerRoundTripsInTheBackup() {
        val backup = FieldBackupCodec.build(
            exportedAtEpochMs = 1,
            permissions = emptyList(),
            boundaries = emptyList(),
            markers = listOf(
                FieldMarker(
                    id = 4,
                    category = MarkerCategory.CAMPSITE,
                    name = "Camp",
                    latitude = 51.2,
                    longitude = -0.1,
                    createdAtEpochMs = 5,
                ),
            ),
            routes = emptyList(),
            trackPoints = emptyList(),
            medical = null,
            includeMedical = false,
        )
        val json = FieldBackupCodec.encode(backup)
        assertTrue(json.contains("CAMPSITE"))
        val decoded = FieldBackupCodec.materialize(FieldBackupCodec.decode(json), applyMedical = false)
        assertEquals(MarkerCategory.CAMPSITE, decoded.markers.single().category)
        assertEquals("Camp", decoded.markers.single().name)
    }

    @Test
    fun exportOmitsMedicalPinAndLocalFiles() {
        val json = FieldBackupCodec.encode(sample(includeMedical = false))
        assertFalse(json.contains("deadbeef"))
        assertFalse(json.contains("biometricEnabled"))
        assertFalse(json.contains("penicillin"))
        assertFalse(json.contains("content://secret"))
        assertFalse(json.contains("O-"))
        assertTrue(json.contains("\"includesMedicalProfile\": false"))
        val decoded = FieldBackupCodec.materialize(FieldBackupCodec.decode(json), applyMedical = true)
        assertNull(decoded.medical)
        assertNull(decoded.markers.single().photoUri)
        assertNull(decoded.permissions.single().attachmentUri)
    }

    @Test
    fun exportCanIncludeMedicalWithoutUnlockSecrets() {
        val backup = sample(includeMedical = true)
        val json = FieldBackupCodec.encode(backup)
        assertTrue(json.contains("penicillin"))
        assertFalse(json.contains("deadbeef"))
        assertFalse(json.contains("biometricEnabled"))
        val decoded = FieldBackupCodec.materialize(backup, applyMedical = true)
        assertEquals("O-", decoded.medical?.bloodType)
        assertNull(decoded.medical?.pinHash)
        assertFalse(decoded.medical?.biometricEnabled == true)
        assertNull(FieldBackupCodec.materialize(backup, applyMedical = false).medical)
    }

    @Test
    fun zonesRoundTripWithoutChangingTheBoundary() {
        val boundary = BoundaryArea(
            id = 7,
            name = "Beat",
            zoneType = BoundaryZoneType.HUNTING,
            notes = "keep me",
            points = listOf(
                BoundaryPoint(51.50, -1.20, 0),
                BoundaryPoint(51.50, -1.10, 1),
                BoundaryPoint(51.60, -1.10, 2),
                BoundaryPoint(51.60, -1.20, 3),
            ),
            areaSqMeters = 1234.0,
            zones = listOf(
                BoundaryZone(
                    id = 11,
                    boundaryId = 7,
                    name = "Quarry",
                    kind = BoundaryZoneKind.DANGER,
                    notes = "steep",
                    points = listOf(
                        BoundaryPoint(51.52, -1.18, 0),
                        BoundaryPoint(51.52, -1.16, 1),
                        BoundaryPoint(51.54, -1.16, 2),
                    ),
                ),
                BoundaryZone(
                    id = 12,
                    boundaryId = 7,
                    name = "Sold field",
                    kind = BoundaryZoneKind.SOLD,
                    notes = "farmer sold it",
                    points = listOf(
                        BoundaryPoint(51.58, -1.12, 0),
                        BoundaryPoint(51.58, -0.90, 1),
                        BoundaryPoint(51.70, -0.90, 2),
                        BoundaryPoint(51.70, -1.12, 3),
                    ),
                ),
            ),
        )
        val backup = FieldBackupCodec.build(
            exportedAtEpochMs = 50,
            permissions = emptyList(),
            boundaries = listOf(boundary),
            markers = emptyList(),
            routes = emptyList(),
            trackPoints = emptyList(),
            medical = null,
            includeMedical = false,
        )
        assertEquals(2, backup.version)
        val json = FieldBackupCodec.encode(backup)
        assertTrue(json.contains("\"kind\": \"DANGER\""))
        assertTrue(json.contains("\"kind\": \"SOLD\""))
        val decoded = FieldBackupCodec.materialize(FieldBackupCodec.decode(json), applyMedical = false)
        val again = decoded.boundaries.single()
        assertEquals(boundary.points, again.points)
        assertEquals(1234.0, again.areaSqMeters, 0.0001)
        assertEquals(BoundaryZoneType.HUNTING, again.zoneType)
        assertEquals("keep me", again.notes)
        assertEquals(2, again.zones.size)
        assertEquals(BoundaryZoneKind.DANGER, again.zones[0].kind)
        assertEquals("Quarry", again.zones[0].name)
        assertEquals("steep", again.zones[0].notes)
        assertEquals(-0.90, again.zones[1].points[1].longitude, 0.0001)
        assertEquals(7L, again.zones[1].boundaryId)
        val outsideBoundary = BoundaryPoint(51.65, -1.00, 0)
        assertFalse(BoundaryMath.contains(again.points, outsideBoundary.latitude, outsideBoundary.longitude))
        assertTrue(
            BoundaryMath.contains(
                again.zones[1].points,
                outsideBoundary.latitude,
                outsideBoundary.longitude,
            ),
        )
        val kml = GeoFormats.kml(backup)
        val geo = GeoFormats.geoJson(backup)
        val gpx = GeoFormats.gpx(backup)
        assertTrue(kml.contains("Sold field"))
        assertTrue(kml.contains("-0.9"))
        assertTrue(geo.contains("\"kind\":\"zone\""))
        assertTrue(geo.contains("SOLD"))
        assertTrue(gpx.contains("Zone: Sold field (SOLD)"))
        assertTrue(gpx.contains("lon=\"-0.9\""))
    }

    @Test
    fun versionOneBackupImportsWithNoZones() {
        val json = """
            {
              "format": "hunttrack-field-backup",
              "version": 1,
              "exportedAtEpochMs": 10,
              "includesMedicalProfile": false,
              "permissions": [],
              "boundaries": [{
                "id": 4,
                "name": "Old beat",
                "zoneType": "HUNTING",
                "notes": "",
                "areaSqMeters": 10.0,
                "points": [
                  {"latitude": 51.0, "longitude": -1.0, "orderIndex": 0},
                  {"latitude": 51.0, "longitude": -1.1, "orderIndex": 1},
                  {"latitude": 51.1, "longitude": -1.1, "orderIndex": 2}
                ]
              }],
              "markers": [],
              "routes": []
            }
        """.trimIndent()
        val decoded = FieldBackupCodec.materialize(FieldBackupCodec.decode(json), applyMedical = false)
        val area = decoded.boundaries.single()
        assertEquals("Old beat", area.name)
        assertEquals(3, area.points.size)
        assertTrue(area.zones.isEmpty())
        assertEquals(10.0, area.areaSqMeters, 0.0001)
    }

    @Test
    fun roundTripKeepsBoundaryLink() {
        val backup = sample(includeMedical = false)
        val decoded = FieldBackupCodec.materialize(FieldBackupCodec.decode(FieldBackupCodec.encode(backup)), false)
        assertEquals(7L, decoded.permissions.single().boundaryAreaId)
        assertEquals(51.5, decoded.boundaries.single().points.first().latitude, 0.0001)
        assertEquals(2, decoded.routes.single().second.size)
    }

    @Test
    fun geoFilesCarryCoordinatesAndZipRoundTrips() {
        val backup = sample(includeMedical = false)
        val gpx = GeoFormats.gpx(backup)
        val kml = GeoFormats.kml(backup)
        val geo = GeoFormats.geoJson(backup)
        assertTrue(gpx.contains("lat=\"51.5\""))
        assertTrue(gpx.contains("<trk>"))
        assertTrue(kml.contains("<Polygon>"))
        assertTrue(geo.contains("\"Polygon\""))
        assertTrue(geo.contains("-1.2"))
        val zip = FieldBackupBundle.zipBytes(backup)
        val again = FieldBackupBundle.backupFromZip(zip)
        assertEquals(backup.markers.single().name, again.markers.single().name)
        assertEquals(1, StoredZip.read(zip).size.coerceAtLeast(4).let { if (it >= 4) 1 else 0 })
        assertTrue(StoredZip.read(zip).containsKey(FieldBackupFormat.JSON_NAME))
    }

    private fun sample(includeMedical: Boolean) = FieldBackupCodec.build(
        exportedAtEpochMs = 1_700_000_000_000,
        permissions = listOf(
            HuntingPermission(
                id = 3,
                label = "Home farm",
                farmName = "Home farm",
                permissionType = "Deer",
                species = "Roe",
                validFromEpochMs = 1,
                validToEpochMs = 2,
                ownerName = "Pat",
                ownerContact = "pat@example.com",
                attachmentUri = "content://secret-pdf",
                boundaryAreaId = 7,
            ),
        ),
        boundaries = listOf(
            BoundaryArea(
                id = 7,
                name = "Beat",
                zoneType = BoundaryZoneType.HUNTING,
                points = listOf(
                    BoundaryPoint(51.5, -1.2, 0),
                    BoundaryPoint(51.5, -1.1, 1),
                    BoundaryPoint(51.6, -1.1, 2),
                ),
            ),
        ),
        markers = listOf(
            FieldMarker(
                id = 9,
                category = MarkerCategory.DEER,
                name = "Oak seat",
                latitude = 51.5,
                longitude = -1.2,
                createdAtEpochMs = 1_700_000_000_000,
                photoUri = "content://secret-photo",
            ),
        ),
        routes = listOf(Route(id = 4, name = "Walk", startedAtEpochMs = 10, distanceMeters = 12.0)),
        trackPoints = listOf(
            TrackPoint(routeId = 4, latitude = 51.5, longitude = -1.2, recordedAtEpochMs = 10),
            TrackPoint(routeId = 4, latitude = 51.51, longitude = -1.19, recordedAtEpochMs = 20),
        ),
        medical = medical,
        includeMedical = includeMedical,
    )
}
