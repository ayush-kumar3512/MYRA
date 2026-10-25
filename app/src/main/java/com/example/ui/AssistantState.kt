package com.example.ui

import com.example.actions.DeviceActionResult

enum class AssistantStatus {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    PROCESSING,
    ERROR
}

data class TranscriptItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionResult: DeviceActionResult? = null
)

data class MyraSettings(
    val selectedVoice: String = "Aoede", // Aoede (female), Puck, Charon, Kore, Fenrir
    val isSpeakerMuted: Boolean = false,
    val languagePreference: String = "Auto Detect (Hindi / English / Hinglish)"
)
