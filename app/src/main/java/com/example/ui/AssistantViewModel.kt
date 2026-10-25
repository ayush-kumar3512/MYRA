package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.actions.DeviceActionManager
import com.example.actions.DeviceActionResult
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.audio.TtsFallback
import com.example.data.DiagnosticLogger
import com.example.live.GeminiLiveClient
import com.example.live.GeminiRestFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "AssistantViewModel"
        private const val PREFS_NAME = "myra_prefs"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_VOICE_NAME = "voice_name"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Subsystems
    val actionManager = DeviceActionManager(application)
    val audioPlayer = AudioPlayer(viewModelScope)
    val audioRecorder = AudioRecorder(viewModelScope)
    val ttsFallback = TtsFallback(application)
    val liveClient = GeminiLiveClient(viewModelScope, actionManager)
    val restFallback = GeminiRestFallback(actionManager)

    // Speech Recognizer for native voice fallback
    private var speechRecognizer: SpeechRecognizer? = null

    // UI State
    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap the orb or mic to talk to MYRA")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _transcripts = MutableStateFlow<List<TranscriptItem>>(
        listOf(
            TranscriptItem(
                text = "Hey there! I'm MYRA, your real-time voice assistant. What can I do for you today?",
                isUser = false
            )
        )
    )
    val transcripts: StateFlow<List<TranscriptItem>> = _transcripts.asStateFlow()

    private val _settings = MutableStateFlow(
        MyraSettings(
            apiKey = loadApiKey(),
            selectedVoice = prefs.getString(KEY_VOICE_NAME, GeminiLiveClient.DEFAULT_VOICE) ?: GeminiLiveClient.DEFAULT_VOICE
        )
    )
    val settings: StateFlow<MyraSettings> = _settings.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Combined visualizer amplitude (reactive to mic when listening, to speaker when speaking)
    val visualizerAmplitude: StateFlow<Float> = MutableStateFlow(0f).apply {
        viewModelScope.launch {
            combine(
                audioRecorder.inputAmplitude,
                audioPlayer.outputAmplitude,
                _status
            ) { inAmp, outAmp, currentStatus ->
                when (currentStatus) {
                    AssistantStatus.LISTENING -> inAmp
                    AssistantStatus.SPEAKING -> outAmp
                    AssistantStatus.PROCESSING, AssistantStatus.CONNECTING -> 0.35f
                    else -> 0.05f
                }
            }.collect { value = it }
        }
    }

    init {
        setupListeners()
        initSpeechRecognizer()
        DiagnosticLogger.i(TAG, "MYRA AssistantViewModel initialized successfully")
    }

    private fun loadApiKey(): String {
        val customKey = prefs.getString(KEY_CUSTOM_API_KEY, "")
        if (!customKey.isNullOrBlank()) {
            return customKey
        }
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
    }

    fun updateApiKey(newKey: String) {
        prefs.edit().putString(KEY_CUSTOM_API_KEY, newKey.trim()).apply()
        _settings.value = _settings.value.copy(apiKey = newKey.trim())
        DiagnosticLogger.i(TAG, "API Key updated by user")
    }

    fun updateVoice(voiceName: String) {
        prefs.edit().putString(KEY_VOICE_NAME, voiceName).apply()
        _settings.value = _settings.value.copy(selectedVoice = voiceName)
        DiagnosticLogger.i(TAG, "Voice updated to $voiceName")
    }

    private fun setupListeners() {
        // Live client event callbacks
        liveClient.setListeners(
            onAudio = { pcmBytes ->
                _status.value = AssistantStatus.SPEAKING
                _statusMessage.value = "MYRA is speaking…"
                audioPlayer.enqueueAudioChunk(pcmBytes)
            },
            onTranscript = { text, isUser ->
                appendTranscript(text, isUser)
            },
            onInterruption = {
                handleInterruption()
            },
            onToolResult = { actionResult ->
                appendActionItem(actionResult)
            },
            onError = { errMsg ->
                DiagnosticLogger.e(TAG, "LiveClient error: $errMsg")
                _errorMessage.value = errMsg
                _status.value = AssistantStatus.ERROR
                _statusMessage.value = errMsg
            }
        )

        // Audio recorder chunk callback
        audioRecorder.setOnAudioChunkReadyListener { pcmBytes ->
            liveClient.sendAudioChunk(pcmBytes)
        }

        // Audio player finished callback
        audioPlayer.setOnPlaybackFinishedListener {
            if (_status.value == AssistantStatus.SPEAKING) {
                _status.value = AssistantStatus.LISTENING
                _statusMessage.value = "Listening… Speak now"
            }
        }

        // TTS fallback finished callback
        ttsFallback.setOnSpeechFinishedListener {
            if (_status.value == AssistantStatus.SPEAKING) {
                _status.value = AssistantStatus.LISTENING
                _statusMessage.value = "Listening… Speak now"
            }
        }
    }

    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(getApplication())) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {
                            DiagnosticLogger.d(TAG, "SpeechRecognizer: Beginning of speech")
                        }
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            DiagnosticLogger.d(TAG, "SpeechRecognizer: End of speech")
                        }
                        override fun onError(error: Int) {
                            DiagnosticLogger.w(TAG, "SpeechRecognizer error: $error")
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val spokenText = matches[0]
                                DiagnosticLogger.i(TAG, "SpeechRecognizer recognized: '$spokenText'")
                                handleUserSpokenInput(spokenText)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Could not initialize SpeechRecognizer", e)
        }
    }

    /**
     * Primary action: User taps the central Orb or Mic button.
     */
    fun onMicButtonClicked() {
        when (_status.value) {
            AssistantStatus.SPEAKING -> {
                // User interrupted MYRA!
                handleInterruption()
            }
            AssistantStatus.LISTENING -> {
                // User paused listening
                stopListening()
                _status.value = AssistantStatus.IDLE
                _statusMessage.value = "MYRA is on standby. Tap to talk."
            }
            else -> {
                startVoiceSession()
            }
        }
    }

    fun startVoiceSession() {
        val apiKey = _settings.value.apiKey
        _errorMessage.value = null

        // Stop any current playback
        audioPlayer.stopPlayback("Starting new voice session")
        ttsFallback.stop()

        _status.value = AssistantStatus.CONNECTING
        _statusMessage.value = "Connecting to MYRA…"

        // Connect to Gemini Live session if key is present
        if (apiKey.isNotBlank()) {
            liveClient.connect(apiKey, _settings.value.selectedVoice)
        }

        // Start capturing audio from microphone
        val micStarted = audioRecorder.startRecording()
        if (micStarted) {
            _status.value = AssistantStatus.LISTENING
            _statusMessage.value = "Listening… Speak in Hindi, English, Hinglish, etc."
            DiagnosticLogger.i(TAG, "Voice session started in LISTENING state")
        } else {
            // If raw AudioRecord couldn't start, try speech recognizer or report error
            _status.value = AssistantStatus.ERROR
            _statusMessage.value = "Microphone failed to start. Please check microphone permission."
        }
    }

    fun stopListening() {
        audioRecorder.stopRecording()
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Error stopping SpeechRecognizer", e)
        }
    }

    /**
     * Handles user interruption when MYRA is speaking.
     */
    fun handleInterruption() {
        DiagnosticLogger.i(TAG, "Handling user interruption: stopping active playback and resetting queue")
        audioPlayer.stopPlayback("Interruption triggered")
        ttsFallback.stop()
        _status.value = AssistantStatus.LISTENING
        _statusMessage.value = "Listening… Go ahead, I'm listening!"
    }

    /**
     * Handles text or transcribed speech input.
     */
    fun handleUserSpokenInput(text: String) {
        if (text.isBlank()) return
        appendTranscript(text, isUser = true)

        if (liveClient.isConnected.value) {
            liveClient.sendTextMessage(text)
        } else {
            // Execute via REST fallback
            viewModelScope.launch {
                _status.value = AssistantStatus.PROCESSING
                _statusMessage.value = "MYRA is thinking…"

                val apiKey = _settings.value.apiKey
                val history = _transcripts.value.map { it.text to it.isUser }
                val response = restFallback.generateResponse(apiKey, text, history)

                appendTranscript(response.replyText, isUser = false)
                if (response.toolResult != null) {
                    appendActionItem(response.toolResult)
                }

                _status.value = AssistantStatus.SPEAKING
                _statusMessage.value = "MYRA is speaking…"
                ttsFallback.speak(response.replyText)
            }
        }
    }

    /**
     * Triggered by user selecting a quick action prompt chip (e.g. "Open WhatsApp").
     */
    fun onQuickPromptSelected(prompt: String) {
        handleUserSpokenInput(prompt)
    }

    /**
     * Speaker Diagnostic Test: plays 440Hz tone as demanded by Sections 15 and 44.
     */
    fun testSpeaker() {
        DiagnosticLogger.i(TAG, "User triggered Test Speaker (440Hz tone)")
        _statusMessage.value = "Playing 440Hz speaker test tone…"
        audioPlayer.playSpeakerDiagnosticTone {
            viewModelScope.launch(Dispatchers.Main) {
                _statusMessage.value = "Speaker test completed! Sound hardware verified."
                appendTranscript("Speaker diagnostic test: 440Hz tone successfully played through phone speaker.", isUser = false)
            }
        }
    }

    private fun appendTranscript(text: String, isUser: Boolean) {
        val current = _transcripts.value.toMutableList()
        current.add(TranscriptItem(text = text, isUser = isUser))
        _transcripts.value = current
    }

    private fun appendActionItem(result: DeviceActionResult) {
        val current = _transcripts.value.toMutableList()
        current.add(
            TranscriptItem(
                text = result.message,
                isUser = false,
                actionResult = result
            )
        )
        _transcripts.value = current
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    fun dismissError() {
        _errorMessage.value = null
        if (_status.value == AssistantStatus.ERROR) {
            _status.value = AssistantStatus.IDLE
            _statusMessage.value = "Ready. Tap the mic to talk to MYRA."
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.release()
        ttsFallback.release()
        liveClient.disconnect("ViewModel cleared")
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Error destroying speech recognizer", e)
        }
    }
}
