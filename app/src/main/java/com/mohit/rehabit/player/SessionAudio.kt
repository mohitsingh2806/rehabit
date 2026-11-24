package com.mohit.rehabit.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.mohit.rehabit.domain.Tone
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Voice (offline text-to-speech), beeps and vibration for the session player.
 * Every part fails soft: if TTS or audio isn't available the session still runs.
 */
class SessionAudio(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts: TextToSpeech = TextToSpeech(appContext, this)
    @Volatile private var ttsReady = false
    private var utterance = 0

    private val high: AudioTrack? = makeTone(1320.0, 140)
    private val low: AudioTrack? = makeTone(880.0, 110)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }

    var voiceOn = true
    var soundOn = true
    var vibrationOn = true

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.UK)
            }
            ttsReady = true
        }
    }

    fun speak(text: String) {
        if (!voiceOn || !ttsReady) return
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "rehabit-${utterance++}")
    }

    fun stopSpeaking() {
        if (ttsReady) tts.stop()
    }

    fun beep(tone: Tone) {
        if (!soundOn) return
        val track = if (tone == Tone.HIGH) high else low
        track ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun buzz() {
        if (!vibrationOn) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    fun release() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        high?.release()
        low?.release()
    }

    /** A short sine beep with soft edges (no clicks), played from a static buffer. */
    private fun makeTone(freq: Double, ms: Int): AudioTrack? = runCatching {
        val rate = 44_100
        val n = rate * ms / 1000
        val fade = rate * 0.008
        val pcm = ShortArray(n) { i ->
            val envelope = min(1.0, min(i, n - i) / fade)
            (sin(2 * PI * freq * i / rate) * envelope * 0.5 * Short.MAX_VALUE).toInt().toShort()
        }
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(pcm, 0, n) }
    }.getOrNull()
}
