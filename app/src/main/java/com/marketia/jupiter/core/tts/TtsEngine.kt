package com.marketia.jupiter.core.tts

interface TtsEngine {
    val speed: Float
    val pitch: Float
    val isReady: Boolean
    fun speak(text: String)
    fun stop()
    fun setSpeed(speed: Float)
    fun setPitch(pitch: Float)
}
