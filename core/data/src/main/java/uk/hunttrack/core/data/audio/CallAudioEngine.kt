package uk.hunttrack.core.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plays bundled synthetic animal-call previews and user recordings.
 * Built-in library uses generated tones (offline, no large assets) until offline packs
 * supply real WAV/MP3 files.
 */
@Singleton
class CallAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var recorder: MediaRecorder? = null

    @Volatile var isPlaying: Boolean = false
        private set

    suspend fun playTone(profile: String, loop: Boolean = false) = withContext(Dispatchers.Default) {
        stop()
        val sampleRate = 22050
        val durationSec = if (loop) 1.2 else 1.8
        val samples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(samples)
        val gain = CallPlaybackGain
        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val envelope = sin(PI * (i.toDouble() / samples)).coerceIn(0.0, 1.0)
            val sample = when (profile) {
                "deer_low" -> sin(2 * PI * 110 * t) * 0.95 + sin(2 * PI * 55 * t) * 0.55
                "deer_mid" -> sin(2 * PI * 180 * t) * 0.9 + sin(2 * PI * 90 * t) * 0.5
                "deer_soft" -> sin(2 * PI * 420 * t) * 0.75
                "fox_high" -> sin(2 * PI * (900 + 200 * sin(2 * PI * 3 * t)) * t) * 0.85
                "fox_chatter" -> if ((i / 800) % 2 == 0) sin(2 * PI * 1200 * t) * 0.8 else 0.0
                "duck" -> sin(2 * PI * (350 + 40 * sin(2 * PI * 8 * t)) * t) * 0.8
                "goose" -> sin(2 * PI * 220 * t) * 0.9 + sin(2 * PI * 440 * t) * 0.35
                else -> sin(2 * PI * 440 * t) * 0.85
            }
            val amplified = (sample * envelope * gain).coerceIn(-1.0, 1.0)
            buffer[i] = (amplified * Short.MAX_VALUE).toInt().toShort()
        }
        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        at.write(buffer, 0, buffer.size)
        if (loop) at.setLoopPoints(0, buffer.size, -1)
        at.setVolume(1f)
        at.play()
        track = at
        isPlaying = true
    }

    fun playFile(path: String, loop: Boolean = false) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(path)
            isLooping = loop
            setOnCompletionListener { this@CallAudioEngine.isPlaying = false }
            prepare()
            // Full MediaPlayer gain — device media volume still applies on top
            setVolume(1f, 1f)
            start()
        }
        isPlaying = true
    }

    fun stop() {
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        isPlaying = false
    }

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startRecording(outputFile: File): Result<Unit> {
        if (!hasRecordPermission()) {
            return Result.failure(SecurityException("Microphone permission not granted"))
        }
        stopRecording()
        outputFile.parentFile?.mkdirs()
        return runCatching {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
        }
    }

    fun stopRecording() {
        val active = recorder ?: return
        recorder = null
        try {
            active.stop()
        } catch (_: Exception) {
            // Can happen if recording never started or was too short.
        }
        try {
            active.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Extra headroom on synthesised previews (still clamped to avoid harsh clipping). */
        private const val CallPlaybackGain = 1.35
    }
}
