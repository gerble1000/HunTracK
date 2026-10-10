package uk.hunttrack.core.designsystem.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.hunttrack.core.designsystem.R

enum class HuntSound {
    CLICK,
    GPS_LOCK,
    MARKER_PLACE,
    SUCCESS,
    ARRIVAL,
    APP_LOADED,
}

class SoundManager(
    private val context: Context,
) {
    private val _soundsEnabled = MutableStateFlow(true)
    val soundsEnabled: StateFlow<Boolean> = _soundsEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(true)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
    private var player: MediaPlayer? = null

    /** Alarm usage so field alerts still buzz when media is muted. */
    private val vibrationAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun setSoundsEnabled(enabled: Boolean) {
        _soundsEnabled.value = enabled
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
    }

    /** App sounds off, or device silent / vibrate / media muted. */
    private fun shouldPlayAudio(): Boolean {
        if (!_soundsEnabled.value) return false
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT,
            AudioManager.RINGER_MODE_VIBRATE,
            -> false
            else -> audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        }
    }

    fun play(sound: HuntSound) {
        vibrateFor(sound)
        if (!shouldPlayAudio()) return
        val resId = when (sound) {
            HuntSound.CLICK -> R.raw.sfx_bird_chirp
            HuntSound.MARKER_PLACE -> R.raw.sfx_bird_chirp
            HuntSound.GPS_LOCK -> R.raw.sfx_bird_trill
            HuntSound.SUCCESS -> R.raw.sfx_bird_trill
            HuntSound.ARRIVAL -> R.raw.sfx_bird_trill
            HuntSound.APP_LOADED -> R.raw.sfx_stag_roar
        }
        val volume = when (sound) {
            HuntSound.APP_LOADED -> 0.85f
            HuntSound.ARRIVAL -> 0.95f
            else -> 0.7f
        }
        if (!playRaw(resId, volume)) {
            try {
                when (sound) {
                    HuntSound.CLICK, HuntSound.MARKER_PLACE ->
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                    HuntSound.GPS_LOCK, HuntSound.SUCCESS ->
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    HuntSound.ARRIVAL ->
                        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    HuntSound.APP_LOADED ->
                        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 280)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun vibrateFor(sound: HuntSound) {
        if (!_vibrationEnabled.value) return
        runCatching {
            val vibrator = defaultVibrator() ?: return
            if (!vibrator.hasVibrator()) return
            val effect = when (sound) {
                HuntSound.CLICK, HuntSound.MARKER_PLACE ->
                    VibrationEffect.createOneShot(105, 255)
                HuntSound.GPS_LOCK, HuntSound.SUCCESS ->
                    VibrationEffect.createOneShot(300, 255)
                HuntSound.ARRIVAL -> {
                    val timings = longArrayOf(0, 420, 90, 420, 90, 960)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                HuntSound.APP_LOADED -> {
                    val timings = longArrayOf(0, 240, 50, 360)
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, vibrationAttributes)
            } else {
                vibrator.vibrate(effect)
            }
        }
    }

    private fun defaultVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun playRaw(resId: Int, volume: Float): Boolean {
        return try {
            player?.let {
                runCatching {
                    it.stop()
                    it.release()
                }
            }
            player = null
            val mp = MediaPlayer.create(context, resId) ?: return false
            // USAGE_MEDIA follows the media volume / silent choice (sonification often ignores silent).
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener { completed ->
                completed.release()
                if (player === completed) player = null
            }
            player = mp
            mp.start()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        tone.release()
    }
}
