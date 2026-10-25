package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.actions.DeviceActionManager
import com.example.audio.TtsFallback
import com.example.data.DiagnosticLogger
import com.example.live.GeminiRestFallback
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyraVoiceSession(context: Context) : VoiceInteractionSession(context) {
    companion object {
        private const val TAG = "MyraVoiceSession"
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val actionManager = DeviceActionManager(context)
    private val restFallback = GeminiRestFallback(actionManager)
    private val ttsFallback = TtsFallback(context)
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        // Do not display any intrusive UI window (user requested invisible background mic activation)
        setUiEnabled(false)
        initSpeechRecognizer()
        ttsFallback.setOnSpeechFinishedListener {
            mainHandler.postDelayed({
                finish()
            }, 600)
        }
        DiagnosticLogger.i(TAG, "MyraVoiceSession created (UI disabled for background execution)")
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        DiagnosticLogger.i(TAG, "MyraVoiceSession onShow triggered: Starting background voice listening")
        startListening()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        DiagnosticLogger.d(TAG, "Background SpeechRecognizer: Ready for speech (Green mic active)")
                    }

                    override fun onBeginningOfSpeech() {
                        DiagnosticLogger.d(TAG, "Background SpeechRecognizer: User speaking")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        DiagnosticLogger.d(TAG, "Background SpeechRecognizer: End of speech detected")
                    }

                    override fun onError(error: Int) {
                        DiagnosticLogger.w(TAG, "Background SpeechRecognizer error code: $error")
                        // If speech timed out without words, gently finish
                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                            finish()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedSpeech = matches[0]
                            DiagnosticLogger.i(TAG, "Background user command recognized: '$recognizedSpeech'")
                            processUserCommand(recognizedSpeech)
                        } else {
                            finish()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun startListening() {
        try {
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US", "hi-IN"))
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            speechRecognizer?.startListening(recognizerIntent)
            DiagnosticLogger.i(TAG, "Background speech recognizer listening started")
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error starting background speech recognizer", e)
            finish()
        }
    }

    private fun processUserCommand(command: String) {
        sessionScope.launch {
            try {
                val apiKey = AssistantViewModel.DEFAULT_LINKED_API_KEY
                val response = restFallback.generateResponse(apiKey, command)
                DiagnosticLogger.i(TAG, "Background command response: ${response.replyText}")
                ttsFallback.speak(response.replyText)
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Error processing background command", e)
                ttsFallback.speak("Main samajh nahi paayi, kripya dobara bolein.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        ttsFallback.release()
        sessionScope.cancel()
        DiagnosticLogger.i(TAG, "MyraVoiceSession destroyed")
    }
}
