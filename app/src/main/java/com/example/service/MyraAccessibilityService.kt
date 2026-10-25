package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.data.DiagnosticLogger

class MyraAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "MyraAccessibilityService"
        var isServiceRunning = false
            private set

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${MyraAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServicesSetting.contains(expectedComponentName)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        DiagnosticLogger.i(TAG, "MyraAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events monitored for background system state
    }

    override fun onInterrupt() {
        DiagnosticLogger.w(TAG, "MyraAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        DiagnosticLogger.i(TAG, "MyraAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }
}
