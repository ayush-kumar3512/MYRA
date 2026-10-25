package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.actions.DeviceActionManager
import com.example.audio.TtsFallback
import com.example.data.DiagnosticLogger
import com.example.live.GeminiRestFallback
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyraWakeWordService : Service() {
    companion object {
        private const val TAG = "MyraWakeWordService"
        private const val CHANNEL_ID = "myra_wake_word_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val WAKE_KEYWORDS = listOf(
            "hey myra", "hay myra", "hi myra", "hello myra",
            "ok myra", "okay myra", "namaste myra", "open myra",
            "myra suno", "myra"
        )

        fun start(context: Context) {
            val intent = Intent(context, MyraWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MyraWakeWordService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var actionManager: DeviceActionManager
    private lateinit var restFallback: GeminiRestFallback
    private lateinit var ttsFallback: TtsFallback
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isProcessingCommand = false

    override fun onCreate() {
        super.onCreate()
        actionManager = DeviceActionManager(this)
        restFallback = GeminiRestFallback(actionManager)
        ttsFallback = TtsFallback(this)

        createNotificationChannel()
        startForegroundNotification()

        initSpeechRecognizer()
        _isServiceRunning.value = true
        DiagnosticLogger.i(TAG, "MyraWakeWordService started (listening for 'Hey Myra')")

        // Start initial keyword listening
        startListening()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Voice Trigger",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background wake word detection for 'Hey Myra'"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val appIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Voice Trigger Active")
            .setContentText("Listening for 'Hey Myra'… Speak commands anytime")
            .setSmallIcon(R.drawable.myra_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        DiagnosticLogger.d(TAG, "Wake word recognizer status code: $error")
                        if (!isProcessingCommand) {
                            // Seamlessly re-arm speech recognition loop
                            mainHandler.postDelayed({
                                startListening()
                            }, 350)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0].lowercase().trim()
                            DiagnosticLogger.d(TAG, "Wake word detected speech: '$recognizedText'")
                            handleRecognizedText(recognizedText)
                        } else {
                            if (!isProcessingCommand) {
                                startListening()
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partialText = matches[0].lowercase().trim()
                            if (containsWakeWord(partialText) && !isProcessingCommand) {
                                handleRecognizedText(partialText)
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        return WAKE_KEYWORDS.any { keyword -> text.contains(keyword) }
    }

    private fun handleRecognizedText(text: String) {
        val matchedKeyword = WAKE_KEYWORDS.firstOrNull { text.contains(it) }

        if (matchedKeyword != null && !isProcessingCommand) {
            isProcessingCommand = true
            DiagnosticLogger.i(TAG, "WAKE WORD TRIGGERED: '$matchedKeyword' in '$text'")
            vibrateAlert()

            // Extract command after wake word (e.g. "hey myra torch jalao" -> "torch jalao")
            val command = text.substringAfter(matchedKeyword).trim()

            if (command.isNotBlank()) {
                executeVoiceCommand(command)
            } else {
                // Prompt user and listen for command
                ttsFallback.speak("Haan ji, boliye!")
                ttsFallback.setOnSpeechFinishedListener {
                    isProcessingCommand = false
                    startListening()
                }
            }
        } else {
            // Not wake word, re-listen
            if (!isProcessingCommand) {
                startListening()
            }
        }
    }

    private fun executeVoiceCommand(command: String) {
        serviceScope.launch {
            try {
                DiagnosticLogger.i(TAG, "Executing background command via Gemini: '$command'")
                val apiKey = AssistantViewModel.DEFAULT_LINKED_API_KEY
                val response = restFallback.generateResponse(apiKey, command)
                DiagnosticLogger.i(TAG, "Response: ${response.replyText}")

                ttsFallback.speak(response.replyText)
                ttsFallback.setOnSpeechFinishedListener {
                    serviceScope.launch {
                        delay(500)
                        isProcessingCommand = false
                        startListening()
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Error executing command in wake service", e)
                ttsFallback.speak("Main samajh nahi paayi.")
                isProcessingCommand = false
                startListening()
            }
        }
    }

    private fun startListening() {
        if (isProcessingCommand) return
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US", "hi-IN"))
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                try {
                    audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_MUTE, 0)
                    audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
                } catch (e: Exception) {}

                speechRecognizer?.startListening(intent)

                mainHandler.postDelayed({
                    try {
                        audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                        audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    } catch (e: Exception) {}
                }, 400)
            } catch (e: Exception) {
                DiagnosticLogger.w(TAG, "Error re-starting wake word recognizer", e)
            }
        }
    }

    private fun vibrateAlert() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (e: Exception) {
            DiagnosticLogger.d(TAG, "Vibration not available: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        speechRecognizer?.destroy()
        ttsFallback.release()
        serviceScope.cancel()
        DiagnosticLogger.i(TAG, "MyraWakeWordService destroyed")
    }
}
