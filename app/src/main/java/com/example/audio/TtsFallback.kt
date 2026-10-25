package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsFallback(context: Context) {
    companion object {
        private const val TAG = "TtsFallback"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onSpeechFinishedListener: (() -> Unit)? = null

    fun setOnSpeechFinishedListener(listener: () -> Unit) {
        onSpeechFinishedListener = listener
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale("hi", "IN") // Supports Hindi & English
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.15f) // Slightly higher, youthful & vibrant tone matching MYRA!
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                        DiagnosticLogger.i(TAG, "TTS playback started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        DiagnosticLogger.i(TAG, "TTS playback completed: $utteranceId")
                        onSpeechFinishedListener?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        DiagnosticLogger.e(TAG, "TTS playback error on utterance: $utteranceId")
                        onSpeechFinishedListener?.invoke()
                    }
                })
                DiagnosticLogger.i(TAG, "TextToSpeech initialized successfully")
            } else {
                DiagnosticLogger.e(TAG, "TextToSpeech initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String, utteranceId: String = "myra_tts_${System.currentTimeMillis()}") {
        if (!isInitialized || text.isBlank()) {
            DiagnosticLogger.w(TAG, "TTS cannot speak: initialized=$isInitialized, text='$text'")
            return
        }

        DiagnosticLogger.i(TAG, "TTS speaking: $text")
        _isSpeaking.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
            _isSpeaking.value = false
        }
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
