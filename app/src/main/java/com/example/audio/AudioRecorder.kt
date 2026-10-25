package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.data.DiagnosticLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioRecorder(private val coroutineScope: CoroutineScope) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE_IN = 16000 // 16kHz required by Gemini Live API
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE_BYTES = 2048 // 1024 samples (64ms at 16kHz)
    }

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private var onAudioChunkReady: ((ByteArray) -> Unit)? = null

    fun setOnAudioChunkReadyListener(listener: (ByteArray) -> Unit) {
        onAudioChunkReady = listener
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_isRecording.value) {
            DiagnosticLogger.d(TAG, "Audio recording is already active")
            return true
        }

        DiagnosticLogger.i(TAG, "Starting AudioRecord: 16kHz 16-bit Mono PCM")

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_IN,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = minBufferSize.coerceAtLeast(CHUNK_SIZE_BYTES * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_IN,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                DiagnosticLogger.e(TAG, "AudioRecord failed to initialize")
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            DiagnosticLogger.i(TAG, "AudioRecord started successfully, bufferSize=$bufferSize")

            recordJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                var chunksCount = 0

                while (isActive && _isRecording.value) {
                    val record = audioRecord ?: break
                    val bytesRead = record.read(buffer, 0, buffer.size)

                    if (bytesRead > 0) {
                        chunksCount++
                        val chunkCopy = buffer.copyOf(bytesRead)
                        calculateAndEmitAmplitude(chunkCopy)
                        onAudioChunkReady?.invoke(chunkCopy)

                        if (chunksCount % 30 == 0) {
                            DiagnosticLogger.d(TAG, "Recorded $chunksCount audio chunks (sent ~${chunksCount * CHUNK_SIZE_BYTES} bytes)")
                        }
                    } else {
                        DiagnosticLogger.w(TAG, "AudioRecord.read returned: $bytesRead")
                    }
                }
            }

            return true
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error starting AudioRecord", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        if (!_isRecording.value && recordJob == null) return

        DiagnosticLogger.i(TAG, "Stopping AudioRecord")
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            _inputAmplitude.value = 0f
        }
    }

    private fun calculateAndEmitAmplitude(pcmBytes: ByteArray) {
        if (pcmBytes.size < 2) return
        var sumSquares = 0.0
        val sampleCount = pcmBytes.size / 2
        for (i in 0 until sampleCount) {
            val sample = (pcmBytes[i * 2].toInt() and 0xFF) or (pcmBytes[i * 2 + 1].toInt() shl 8)
            val shortVal = sample.toShort()
            sumSquares += shortVal * shortVal
        }
        val rms = sqrt(sumSquares / sampleCount)
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _inputAmplitude.value = normalized
    }
}
