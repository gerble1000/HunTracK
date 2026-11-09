package uk.hunttrack.domain.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Uncompressed zip so Android and iOS can both read the same bundle
 * without a third-party compression library on iOS.
 */
object StoredZip {
    fun write(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(ZipOutputStream.STORED)
            entries.forEach { (name, bytes) ->
                val crc = CRC32().apply { update(bytes) }
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): Map<String, ByteArray> {
        val found = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    found[entry.name.substringAfterLast('/')] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return found
    }
}

object FieldBackupBundle {
    fun zipBytes(backup: FieldBackup): ByteArray {
        val entries = linkedMapOf(
            FieldBackupFormat.JSON_NAME to FieldBackupCodec.encode(backup).toByteArray(Charsets.UTF_8),
            FieldBackupFormat.GPX_NAME to GeoFormats.gpx(backup).toByteArray(Charsets.UTF_8),
            FieldBackupFormat.KML_NAME to GeoFormats.kml(backup).toByteArray(Charsets.UTF_8),
            FieldBackupFormat.GEOJSON_NAME to GeoFormats.geoJson(backup).toByteArray(Charsets.UTF_8),
        )
        return StoredZip.write(entries)
    }

    fun backupFromZip(bytes: ByteArray): FieldBackup {
        val entries = StoredZip.read(bytes)
        val json = entries[FieldBackupFormat.JSON_NAME]
            ?: throw FieldBackupException("The zip has no ${FieldBackupFormat.JSON_NAME}.")
        return FieldBackupCodec.decode(json.toString(Charsets.UTF_8))
    }
}
