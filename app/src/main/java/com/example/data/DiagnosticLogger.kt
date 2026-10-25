package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

object DiagnosticLogger {
    private const val MAX_LOGS = 300
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private fun sanitize(text: String): String {
        return text.replace(Regex("key=[^&\\s]+"), "key=PROTECTED_KEY")
            .replace(Regex("AQ\\.[a-zA-Z0-9_-]+"), "PROTECTED_KEY")
    }

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO, throwable: Throwable? = null) {
        val time = timeFormat.format(Date())
        val rawMessage = if (throwable != null) "$message: ${throwable.message}" else message
        val safeMessage = sanitize(rawMessage)

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, safeMessage, throwable)
            LogLevel.INFO -> Log.i(tag, safeMessage, throwable)
            LogLevel.WARN -> Log.w(tag, safeMessage, throwable)
            LogLevel.ERROR -> Log.e(tag, safeMessage, throwable)
        }

        val entry = LogEntry(timestamp = time, tag = tag, message = safeMessage, level = level)
        val currentList = _logs.value.toMutableList()
        if (currentList.size >= MAX_LOGS) {
            currentList.removeAt(0)
        }
        currentList.add(entry)
        _logs.value = currentList
    }

    fun d(tag: String, message: String) = log(tag, message, LogLevel.DEBUG)
    fun i(tag: String, message: String) = log(tag, message, LogLevel.INFO)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(tag, message, LogLevel.WARN, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(tag, message, LogLevel.ERROR, throwable)

    fun clear() {
        _logs.value = emptyList()
    }
}
