package com.yishenghuang.heartext.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Requests media audio focus and maps system focus changes to pause / duck / resume.
 */
class AudioFocusHelper(
    context: Context,
    private val onFocusChanged: (FocusChange) -> Unit
) {
    enum class FocusChange {
        Gained,
        Lost,
        LostTransient,
        Duck
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null
    private var hasFocus = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onFocusChanged(FocusChange.Gained)
            AudioManager.AUDIOFOCUS_LOSS -> onFocusChanged(FocusChange.Lost)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusChanged(FocusChange.LostTransient)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocusChanged(FocusChange.Duck)
        }
    }

    fun requestFocus(): Boolean {
        if (hasFocus) return true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .build()
            request = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        request = null
        hasFocus = false
    }
}
