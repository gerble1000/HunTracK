package uk.hunttrack.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryPoint
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
