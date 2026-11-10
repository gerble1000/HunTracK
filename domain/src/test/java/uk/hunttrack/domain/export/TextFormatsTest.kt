package uk.hunttrack.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

class TextFormatsTest {
    @Test
    fun backupZipStaysTheOriginalFourFiles() {
        val names = StoredZip.read(FieldBackupBundle.zipBytes(sample(includeMedical = false))).keys
        assertEquals(
            setOf(
                FieldBackupFormat.JSON_NAME,
                FieldBackupFormat.GPX_NAME,
                FieldBackupFormat.KML_NAME,
                FieldBackupFormat.GEOJSON_NAME,
            ),
            names,
        )
        assertFalse(names.contains(TextFormats.PERMISSIONS_CSV))
        assertFalse(names.contains(TextFormats.XML_NAME))
        assertFalse(names.contains(TextFormats.PDF_NAME))
    }

    @Test
    fun csvQuotesSpecialCharactersAndOmitsSecrets() {
        val files = TextFormats.csvFiles(sample(includeMedical = false))
        assertNull(files[TextFormats.MEDICAL_CSV])
        val permissions = files.getValue(TextFormats.PERMISSIONS_CSV)
        assertTrue(permissions.startsWith("\uFEFF"))
        assertTrue(permissions.contains("id,label,farmName,permissionType,species,validFrom,validTo"))
        assertTrue(permissions.contains("\"He said \"\"ok\"\", then left"))
        assertTrue(permissions.contains("and <crossed> & more"))
        assertTrue(permissions.contains("1970-01-01"))
        assertFalse(permissions.contains("deadbeef"))
        assertFalse(permissions.contains("penicillin"))

        val boundaries = files.getValue(TextFormats.BOUNDARIES_CSV).removePrefix("\uFEFF")
        assertTrue(boundaries.lines().first().startsWith("id,name,zoneType,areaSqMeters"))
        assertTrue(boundaries.contains("Beat,HUNTING"))
        assertTrue(boundaries.contains("51.500000 -1.200000"))

        val markers = files.getValue(TextFormats.MARKERS_CSV)
        assertTrue(markers.contains("Oak seat"))
        assertTrue(markers.contains("51.500000"))
        assertTrue(markers.contains("true"))
        assertFalse(markers.contains("content://"))

        val tracks = files.getValue(TextFormats.TRACKS_CSV)
        assertTrue(tracks.contains("Walk"))
        assertTrue(tracks.contains(",2\n") || tracks.contains(",2\r\n"))
    }

    @Test
    fun csvZipHasOneFilePerRecordTypeAndAnEmptyZonesSheet() {
        val entries = StoredZip.read(TextFormats.csvZipBytes(sample(includeMedical = true)))
        assertEquals(
            setOf(
                TextFormats.PERMISSIONS_CSV,
                TextFormats.BOUNDARIES_CSV,
                TextFormats.MARKERS_CSV,
                TextFormats.ZONES_CSV,
                TextFormats.TRACKS_CSV,
                TextFormats.MEDICAL_CSV,
            ),
            entries.keys,
        )
        val zones = entries.getValue(TextFormats.ZONES_CSV).toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        assertEquals(
            "id,boundaryId,boundaryName,name,kind,notes,vertexCount,outline",
            zones,
        )
        val medical = entries.getValue(TextFormats.MEDICAL_CSV).toString(Charsets.UTF_8)
        assertTrue(medical.contains("penicillin"))
        assertTrue(medical.contains("Sam 07000 kin"))
        assertFalse(medical.contains("deadbeef"))
        assertFalse(medical.contains("biometric"))
    }

    @Test
    fun zonesOnABoundaryAreWrittenWithoutBeingPassedIn() {
        val backup = FieldBackupCodec.build(
            exportedAtEpochMs = 1_700_000_000_000,
            permissions = emptyList(),
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
                    zones = listOf(
                        BoundaryZone(
                            id = 5,
                            boundaryId = 7,
                            name = "Quarry",
                            kind = BoundaryZoneKind.DANGER,
                            notes = "Steep",
                            points = listOf(
                                BoundaryPoint(51.51, -1.15, 0),
                                BoundaryPoint(51.52, -1.14, 1),
                                BoundaryPoint(51.50, -1.13, 2),
                            ),
                        ),
                    ),
                ),
            ),
            markers = emptyList(),
            routes = emptyList(),
            trackPoints = emptyList(),
            medical = null,
            includeMedical = false,
        )
        val zone = TextFormats.zonesOf(backup).single()
        assertEquals(5L, zone.id)
        assertEquals(7L, zone.boundaryId)
        assertEquals("Beat", zone.boundaryName)
        assertEquals("DANGER", zone.kind)
        val csv = TextFormats.csvFiles(backup).getValue(TextFormats.ZONES_CSV)
        assertTrue(csv.contains("5,7,Beat,Quarry,DANGER,Steep,3,"))
        assertTrue(csv.contains("51.510000 -1.150000"))
        val xml = TextFormats.xml(backup)
        assertTrue(xml.contains("<zone id=\"5\" boundaryId=\"7\" kind=\"DANGER\" vertexCount=\"3\">"))
        assertTrue(xml.contains("<name>Quarry</name>"))
        val pdf = String(TextFormats.pdf(backup), Charsets.US_ASCII)
        assertTrue(pdf.contains("Quarry"))
        assertTrue(pdf.contains("Danger / No-go"))
        assertFalse(pdf.contains("None in this backup"))
    }

    @Test
    fun zonesSectionIsReadyForSchemaV2() {
        assertTrue(TextFormats.zonesOf(sample(includeMedical = false)).isEmpty())
        val zone = OverlayZone(
            id = 5,
            boundaryId = 7,
            boundaryName = "Beat",
            name = "Quarry",
            kind = "DANGER",
            notes = "Steep",
            points = listOf(
                BackupPoint(51.51, -1.15, 0),
                BackupPoint(51.52, -1.14, 1),
                BackupPoint(51.50, -1.13, 2),
            ),
        )
        val csv = TextFormats.csvFiles(sample(includeMedical = false), zones = listOf(zone))
            .getValue(TextFormats.ZONES_CSV)
        assertTrue(csv.contains("5,7,Beat,Quarry,DANGER,Steep,3,"))
        assertTrue(csv.contains("51.510000 -1.150000"))

        val xml = TextFormats.xml(sample(includeMedical = false), zones = listOf(zone))
        assertTrue(xml.contains("<zone id=\"5\" boundaryId=\"7\" kind=\"DANGER\" vertexCount=\"3\">"))
        assertTrue(xml.contains("<name>Quarry</name>"))
        assertTrue(xml.contains("<boundaryName>Beat</boundaryName>"))

        val pdf = String(TextFormats.pdf(sample(includeMedical = false), zones = listOf(zone)), Charsets.US_ASCII)
        assertTrue(pdf.contains("Quarry"))
        assertTrue(pdf.contains("Danger / No-go"))
    }

    @Test
    fun xmlEscapesTextAndLeavesMedicalOutUntilAsked() {
        val plain = TextFormats.xml(sample(includeMedical = false))
        assertTrue(plain.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(plain.contains("backupVersion=\"2\""))
        assertTrue(plain.contains("<zones/>"))
        assertTrue(plain.contains("a &lt; b &amp; c"))
        assertFalse(plain.contains("a < b"))
        assertFalse(plain.contains("<medical>"))
        assertFalse(plain.contains("penicillin"))
        assertFalse(plain.contains("deadbeef"))
        assertTrue(plain.contains("<name>Beat</name>"))
        assertTrue(plain.contains("lat=\"51.500000\""))
        assertTrue(plain.contains("<track id=\"4\""))

        val withMedical = TextFormats.xml(sample(includeMedical = true))
        assertTrue(withMedical.contains("<medical>"))
        assertTrue(withMedical.contains("<bloodType>O-</bloodType>"))
        assertTrue(withMedical.contains("<phone>07000</phone>"))
        assertFalse(withMedical.contains("deadbeef"))
    }

    @Test
    fun pdfIsAPrintSummaryWithASketchAndNoUnlockSecrets() {
        val bytes = TextFormats.pdf(sample(includeMedical = false))
        val text = String(bytes, Charsets.US_ASCII)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("HunTracK field summary"))
        assertTrue(text.contains("Home farm"))
        assertTrue(text.contains("Hunting area"))
        assertTrue(text.contains("Oak seat"))
        assertTrue(text.contains("Walk"))
        assertTrue(text.contains("None in this backup"))
        assertTrue(text.contains("print summary, not the import file"))
        assertTrue(text.contains(" m\n") || text.contains(" m"))
        assertFalse(text.contains("penicillin"))
        assertFalse(text.contains("deadbeef"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
    }

    @Test
    fun pdfIncludesMedicalOnlyWhenRequestedAndCanRunToASecondPage() {
        val withMedical = String(TextFormats.pdf(sample(includeMedical = true)), Charsets.US_ASCII)
        assertTrue(withMedical.contains("penicillin"))
        assertTrue(withMedical.contains("O-"))
        assertFalse(withMedical.contains("deadbeef"))

        val longNotes = (1..40).map { index ->
            HuntingPermission(
                id = index.toLong(),
                label = "Permission $index",
                farmName = "",
                permissionType = "",
                species = "",
                validFromEpochMs = 0,
                validToEpochMs = 0,
                ownerName = "",
                ownerContact = "",
                notes = "A long note about the gate, the keeper and the evening flight $index.",
            )
        }
        val backup = FieldBackupCodec.build(
            exportedAtEpochMs = 1_700_000_000_000,
            permissions = longNotes,
            boundaries = emptyList(),
            markers = emptyList(),
            routes = emptyList(),
            trackPoints = emptyList(),
            medical = null,
            includeMedical = false,
        )
        val text = String(TextFormats.pdf(backup), Charsets.US_ASCII)
        assertTrue(text.contains("/Count 2") || text.contains("/Count 3"))
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
                notes = "He said \"ok\", then left\nand <crossed> & more",
                attachmentUri = "content://secret-pdf",
                boundaryAreaId = 7,
            ),
        ),
        boundaries = listOf(
            BoundaryArea(
                id = 7,
                name = "Beat",
                zoneType = BoundaryZoneType.HUNTING,
                notes = "a < b & c",
                points = listOf(
                    BoundaryPoint(51.5, -1.2, 0),
                    BoundaryPoint(51.5, -1.1, 1),
                    BoundaryPoint(51.6, -1.1, 2),
                ),
                areaSqMeters = 125_000.0,
            ),
        ),
        markers = listOf(
            FieldMarker(
                id = 9,
                category = MarkerCategory.DEER,
                name = "Oak seat",
                notes = "PERM-NOTE",
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
        medical = EmergencyProfile(
            fullName = "Ada Field",
            bloodType = "O-",
            allergies = "penicillin",
            medicalInfo = "asthma",
            medication = "inhaler",
            notes = "private",
            contacts = listOf(EmergencyContact("Sam", "07000", "kin")),
            pinHash = "deadbeef",
            biometricEnabled = true,
        ),
        includeMedical = includeMedical,
    )
}
