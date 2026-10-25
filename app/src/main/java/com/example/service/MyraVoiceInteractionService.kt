package com.example.service

import android.service.voice.VoiceInteractionService
import com.example.data.DiagnosticLogger

class MyraVoiceInteractionService : VoiceInteractionService() {
    companion object {
        private const val TAG = "MyraVoiceInteractionService"
    }

    override fun onReady() {
        super.onReady()
        DiagnosticLogger.i(TAG, "MyraVoiceInteractionService is ready as system assistant")
    }

    override fun onShutdown() {
        super.onShutdown()
        DiagnosticLogger.i(TAG, "MyraVoiceInteractionService shutdown")
    }
}
