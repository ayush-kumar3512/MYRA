package com.example.service

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import com.example.data.DiagnosticLogger

class MyraRecognitionService : RecognitionService() {
    companion object {
        private const val TAG = "MyraRecognitionService"
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        DiagnosticLogger.d(TAG, "MyraRecognitionService: onStartListening")
        listener?.readyForSpeech(Bundle())
    }

    override fun onCancel(listener: Callback?) {
        DiagnosticLogger.d(TAG, "MyraRecognitionService: onCancel")
    }

    override fun onStopListening(listener: Callback?) {
        DiagnosticLogger.d(TAG, "MyraRecognitionService: onStopListening")
        listener?.endOfSpeech()
    }
}
