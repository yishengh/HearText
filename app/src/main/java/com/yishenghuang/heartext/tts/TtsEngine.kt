package com.yishenghuang.heartext.tts

interface TtsEngine {
    val name: String
    suspend fun speak(text: String, voiceId: String? = null)
    fun stop()
    fun pause()
    fun resume()
    fun isSpeaking(): Boolean
    fun shutdown()
    /** 0f..1f playback gain for ducking. Default no-op for engines without volume control. */
    fun setVolume(volume: Float) {}
    /** Speech rate multiplier, typically 0.5–2.5. Default no-op. */
    fun setSpeechRate(rate: Float) {}
}

enum class TtsPlaybackState {
    Idle, Speaking, Paused, Error
}
