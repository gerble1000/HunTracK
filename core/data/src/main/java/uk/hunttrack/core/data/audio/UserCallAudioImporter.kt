package uk.hunttrack.core.data.audio

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class UserCallAudioImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun importToLocalFile(sourceUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "user_calls").apply { mkdirs() }
            val ext = extensionFor(sourceUri)
            val dest = File(dir, "call_${System.currentTimeMillis()}.$ext")
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: error("Could not open selected audio file")
            input.use { inStream ->
                dest.outputStream().use { out -> inStream.copyTo(out) }
            }
            if (dest.length() < 256) error("Audio file looks empty or too small")
            dest
        }
    }

    private fun extensionFor(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        if (mime != null) {
            val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!fromMime.isNullOrBlank()) return fromMime.lowercase()
        }
        val name = uri.lastPathSegment.orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot > 0) {
            val ext = name.substring(dot + 1).lowercase()
            if (ext in SUPPORTED) return ext
        }
        return "m4a"
    }

    companion object {
        private val SUPPORTED = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "3gp", "amr", "mpeg", "mp4")
    }
}
