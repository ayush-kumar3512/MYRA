package com.example.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.example.data.DiagnosticLogger

class MyraVoiceSessionService : VoiceInteractionSessionService() {
    companion object {
        private const val TAG = "MyraVoiceSessionService"
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        DiagnosticLogger.i(TAG, "Creating new background MyraVoiceSession")
        return MyraVoiceSession(this)
    }
}
