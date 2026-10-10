package uk.hunttrack.core.data.phase2

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import uk.hunttrack.core.data.audio.AudioPackDownloader
import uk.hunttrack.core.data.audio.UserCallAudioImporter
import uk.hunttrack.core.data.db.AudioPackDao
import uk.hunttrack.core.data.db.AudioPackEntity
import uk.hunttrack.core.data.db.CallJournalDao
import uk.hunttrack.core.data.db.CallJournalEntity
import uk.hunttrack.core.data.db.UserCallDao
import uk.hunttrack.core.data.db.UserCallEntity
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.AnimalCall
import uk.hunttrack.domain.model.AudioPack
import uk.hunttrack.domain.model.CallJournalEntry

@Singleton
class CallsRepository @Inject constructor(
    private val journalDao: CallJournalDao,
    private val userCallDao: UserCallDao,
    private val audioPackDao: AudioPackDao,
    private val downloader: AudioPackDownloader,
    private val audioImporter: UserCallAudioImporter,
) {
    fun observeJournal(): Flow<List<CallJournalEntry>> =
        journalDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeUserCalls(): Flow<List<AnimalCall>> =
        userCallDao.observeAll().map { list ->
            list.map {
                AnimalCall(
                    id = "user_${it.id}",
                    name = it.name,
                    speciesId = it.speciesId,
                    category = it.category,
                    description = it.description,
                    packId = "user",
                    remoteUrl = "",
                    isBuiltIn = false,
                    filePath = it.filePath,
                )
            }
        }

    fun observeBuiltInCalls(): Flow<List<AnimalCall>> =
        observePacks().map { packs ->
            val downloaded = packs.filter { it.downloaded }.map { it.id }.toSet()
            UkFieldCatalog.calls.map { call ->
                val local = if (call.packId in downloaded) downloader.resolveCallFile(call.id) else null
                call.copy(filePath = local?.absolutePath)
            }
        }

    fun observePacks(): Flow<List<AudioPack>> =
        combine(audioPackDao.observeAll(), downloader.progress) { saved, progress ->
            val map = saved.associateBy { it.id }
            UkFieldCatalog.audioPacks.map { pack ->
                val complete = downloader.isPackComplete(pack) || map[pack.id]?.downloaded == true
                pack.copy(
                    downloaded = complete,
                    downloadProgress = progress[pack.id] ?: if (complete) 1f else 0f,
                )
            }
        }

    suspend fun addJournal(entry: CallJournalEntry): Long =
        journalDao.insert(
            CallJournalEntity(
                callId = entry.callId,
                speciesId = entry.speciesId,
                notes = entry.notes,
                response = entry.response,
                distanceEstimateMeters = entry.distanceEstimateMeters,
                latitude = entry.latitude,
                longitude = entry.longitude,
                weatherJson = entry.weatherJson,
                recordedAtEpochMs = entry.recordedAtEpochMs,
            ),
        )

    suspend fun deleteJournal(id: Long) = journalDao.delete(id)

    suspend fun addUserCall(
        name: String,
        speciesId: String,
        category: String,
        description: String,
        filePath: String,
        latitude: Double?,
        longitude: Double?,
        weatherJson: String?,
        notes: String,
    ): Long = userCallDao.insert(
        UserCallEntity(
            name = name,
            speciesId = speciesId,
            category = category,
            description = description,
            filePath = filePath,
            createdAtEpochMs = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            weatherJson = weatherJson,
            notes = notes,
        ),
    )

    suspend fun deleteUserCall(id: Long) = userCallDao.delete(id)

    suspend fun importUserCall(
        sourceUri: Uri,
        name: String,
        speciesId: String,
        description: String,
        latitude: Double?,
        longitude: Double?,
        weatherJson: String?,
        notes: String,
    ): Result<Long> {
        val fileResult = audioImporter.importToLocalFile(sourceUri)
        if (fileResult.isFailure) return Result.failure(fileResult.exceptionOrNull()!!)
        val file = fileResult.getOrThrow()
        return runCatching {
            addUserCall(
                name = name,
                speciesId = speciesId,
                category = "Uploaded",
                description = description,
                filePath = file.absolutePath,
                latitude = latitude,
                longitude = longitude,
                weatherJson = weatherJson,
                notes = notes,
            )
        }
    }

    suspend fun downloadPack(id: String): Result<Unit> {
        val result = downloader.downloadPack(id)
        if (result.isSuccess) {
            audioPackDao.upsert(
                AudioPackEntity(
                    id = id,
                    downloaded = true,
                    downloadedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        return result
    }
}

private fun CallJournalEntity.toDomain() = CallJournalEntry(
    id, callId, speciesId, notes, response, distanceEstimateMeters,
    latitude, longitude, weatherJson, recordedAtEpochMs,
)
