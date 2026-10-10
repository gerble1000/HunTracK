package uk.hunttrack.core.data.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.AudioPack

@Singleton
class AudioPackDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun packDir(packId: String): File =
        File(context.filesDir, "audio_packs/$packId").apply { mkdirs() }

    fun localPath(packId: String, fileName: String): File = File(packDir(packId), fileName)

    fun isPackComplete(pack: AudioPack): Boolean =
        pack.tracks.isNotEmpty() && pack.tracks.all { localPath(pack.id, it.fileName).exists() }

    fun resolveCallFile(callId: String): File? {
        val call = UkFieldCatalog.calls.find { it.id == callId } ?: return null
        val pack = UkFieldCatalog.audioPacks.find { it.id == call.packId } ?: return null
        val track = pack.tracks.find { it.callId == callId } ?: return null
        val file = localPath(pack.id, track.fileName)
        return file.takeIf { it.exists() }
    }

    suspend fun downloadPack(packId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val pack = UkFieldCatalog.audioPacks.find { it.id == packId }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown pack"))
        runCatching {
            val total = pack.tracks.size.coerceAtLeast(1)
            pack.tracks.forEachIndexed { index, track ->
                val out = localPath(pack.id, track.fileName)
                if (!out.exists() || out.length() < 1024) {
                    downloadFile(track.remoteUrl, out)
                }
                _progress.value = _progress.value + (packId to ((index + 1f) / total))
            }
            _progress.value = _progress.value + (packId to 1f)
        }
    }

    private fun downloadFile(remoteUrl: String, out: File) {
        val tmp = File(out.parentFile, out.name + ".part")
        val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "HunTracK/1.1 (Android; field companion)")
        }
        connection.inputStream.use { input ->
            FileOutputStream(tmp).use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()
        if (tmp.length() < 512) {
            tmp.delete()
            error("Download too small for $remoteUrl")
        }
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
    }
}
