package com.example.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates and plays a crisp, pleasant dual-tone confirmation chime when a task is completed,
 * strictly respecting device ringer mode, notification/system volume, mute state, and Do Not Disturb.
 */
object CompletionSoundPlayer {

    private const val SAMPLE_RATE = 44_100
    private const val DURATION_MS = 145
    private val soundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun shouldPlayBeep(context: Context, preferenceEnabled: Boolean): Boolean {
        if (!preferenceEnabled) return false

        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        val notifVolume = runCatching {
            audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        }.getOrDefault(0)
        val systemVolume = runCatching {
            audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
        }.getOrDefault(0)
        val isNotifMuted = runCatching {
            audioManager.isStreamMute(AudioManager.STREAM_NOTIFICATION)
        }.getOrDefault(false)
        val nm = context.getSystemService(NotificationManager::class.java)
        val interruptionFilter = nm?.currentInterruptionFilter

        return shouldPlayBeepForState(
            preferenceEnabled = preferenceEnabled,
            ringerMode = audioManager.ringerMode,
            notifVolume = notifVolume,
            systemVolume = systemVolume,
            isNotifMuted = isNotifMuted,
            interruptionFilter = interruptionFilter
        )
    }

    internal fun shouldPlayBeepForState(
        preferenceEnabled: Boolean,
        ringerMode: Int,
        notifVolume: Int,
        systemVolume: Int,
        isNotifMuted: Boolean,
        interruptionFilter: Int?
    ): Boolean {
        if (!preferenceEnabled) return false
        if (ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        if (notifVolume <= 0 && systemVolume <= 0) return false
        if (isNotifMuted && systemVolume <= 0) return false
        if (interruptionFilter != null &&
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        ) {
            return false
        }
        return true
    }

    fun playTaskCompletedBeep(context: Context, preferenceEnabled: Boolean = true) {
        val appContext = context.applicationContext
        if (!shouldPlayBeep(appContext, preferenceEnabled)) return

        soundScope.launch {
            runCatching {
                val samples = synthesizeCompletionChimePcm()
                val byteSize = samples.size * 2
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(byteSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                delay(DURATION_MS + 60L)
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    internal fun synthesizeCompletionChimePcm(): ShortArray {
        val totalSamples = (SAMPLE_RATE * DURATION_MS) / 1000
        val splitSample = (totalSamples * 0.42).toInt()
        val pcm = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val (freq, localT) = if (i < splitSample) {
                880.0 to t // A5
            } else {
                1318.51 to ((i - splitSample).toDouble() / SAMPLE_RATE) // E6
            }
            val attack = minOf(1.0, localT / 0.008)
            val decay = exp(-localT * 22.0)
            val wave = 0.85 * sin(2.0 * PI * freq * localT) +
                0.15 * sin(2.0 * PI * (freq * 2.0) * localT)
            val sample = (wave * attack * decay * 0.45 * Short.MAX_VALUE).toInt()
            pcm[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }
}
