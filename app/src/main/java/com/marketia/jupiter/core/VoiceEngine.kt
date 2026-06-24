package com.marketia.jupiter.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.marketia.jupiter.core.tts.TtsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Natural default voice: slightly slower and lower than TTS default
    override var speed: Float = 0.87f
        private set
    override var pitch: Float = 0.93f
        private set
    override val isReady: Boolean get() = ttsReady

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try es-US first (often richer voice on modern Android), then es-ES, then device default
                val locales = listOf(Locale("es", "US"), Locale("es", "ES"), Locale("es", "MX"))
                ttsReady = false
                for (locale in locales) {
                    val r = tts?.setLanguage(locale)
                    if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                        ttsReady = true; break
                    }
                }
                if (!ttsReady) { tts?.setLanguage(Locale.getDefault()); ttsReady = true }
                tts?.setSpeechRate(speed)
                tts?.setPitch(pitch)
            }
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun setSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speed)
    }

    override fun setPitch(newPitch: Float) {
        pitch = newPitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitch)
    }

    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle) {}

            override fun onResults(results: Bundle) {
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { onFinalResult(it) }
            }

            override fun onPartialResults(results: Bundle) {
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { onPartialResult(it) }
            }

            override fun onError(error: Int) { onError(error) }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() { recognizer?.stopListening() }

    override fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jupiter_${System.currentTimeMillis()}")
        }
    }

    override fun stop() { tts?.stop() }

    // Keep legacy name for existing callers
    fun stopSpeaking() = stop()

    fun destroy() {
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
    }
}
