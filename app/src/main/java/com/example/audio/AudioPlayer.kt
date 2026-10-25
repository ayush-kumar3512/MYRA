package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.DiagnosticLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sin

class AudioPlayer(private val coroutineScope: CoroutineScope) {
    companion object {
        private const val TAG = "AudioPlayer"
        const val SAMPLE_RATE_GEMINI = 24000 // Gemini Live outputs 24kHz PCM
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val chunkQueue = ConcurrentLinkedQueue<ByteArray>()
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private var onPlaybackFinishedListener: (() -> Unit)? = null

    fun setOnPlaybackFinishedListener(listener: () -> Unit) {
        onPlaybackFinishedListener = listener
    }

    init {
        initAudioTrack()
    }

    @Synchronized
    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_GEMINI,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = minBufferSize.coerceAtLeast(8192) * 2

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_GEMINI)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            DiagnosticLogger.i(TAG, "AudioTrack initialized: sampleRate=$SAMPLE_RATE_GEMINI, bufferSize=$bufferSize, state=${audioTrack?.state}")
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    /**
     * Enqueues a PCM audio chunk for sequential playback.
     */
    fun enqueueAudioChunk(pcmChunk: ByteArray) {
        if (pcmChunk.isEmpty()) return
        DiagnosticLogger.d(TAG, "Enqueued audio chunk: ${pcmChunk.size} bytes. Current queue size: ${chunkQueue.size + 1}")
        chunkQueue.add(pcmChunk)
        startPlaybackLoopIfNeeded()
    }

    @Synchronized
    private fun startPlaybackLoopIfNeeded() {
        if (playbackJob?.isActive == true) return

        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            _isPlaying.value = true
            DiagnosticLogger.i(TAG, "Starting AudioTrack playback loop")

            try {
                if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    initAudioTrack()
                }

                audioTrack?.let { track ->
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                }

                var idleCount = 0
                while (isActive) {
                    val chunk = chunkQueue.poll()
                    if (chunk != null) {
                        idleCount = 0
                        // Calculate output amplitude for visualizer
                        calculateAndEmitAmplitude(chunk)

                        // Write PCM samples to AudioTrack
                        var bytesWritten = 0
                        while (bytesWritten < chunk.size && isActive) {
                            val track = audioTrack ?: break
                            val result = track.write(chunk, bytesWritten, chunk.size - bytesWritten)
                            if (result > 0) {
                                bytesWritten += result
                            } else {
                                DiagnosticLogger.w(TAG, "AudioTrack.write returned error code: $result")
                                break
                            }
                        }
                    } else {
                        // Queue empty, wait briefly before concluding playback
                        idleCount++
                        if (idleCount > 6) { // ~180ms of silence
                            break
                        }
                        delay(30)
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Error during audio playback loop", e)
            } finally {
                _outputAmplitude.value = 0f
                _isPlaying.value = false
                DiagnosticLogger.i(TAG, "Playback loop finished or paused")
                onPlaybackFinishedListener?.invoke()
            }
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
        val rms = kotlin.math.sqrt(sumSquares / sampleCount)
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _outputAmplitude.value = normalized
    }

    /**
     * Immediately stops playback, clears queue, flushes buffer (for interruption).
     */
    fun stopPlayback(reason: String = "User interruption") {
        DiagnosticLogger.i(TAG, "Stopping audio playback: $reason")
        chunkQueue.clear()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Error flushing AudioTrack", e)
        }
        _outputAmplitude.value = 0f
        _isPlaying.value = false
    }

    /**
     * Speaker Diagnostic Test: generates a 440Hz sine wave for 1.2s and plays it
     * through the AudioTrack to verify sound output hardware.
     */
    fun playSpeakerDiagnosticTone(onComplete: () -> Unit = {}) {
        coroutineScope.launch(Dispatchers.IO) {
            DiagnosticLogger.i(TAG, "Diagnostic: Generating 440Hz sine wave tone...")
            stopPlayback("Speaker test initiated")
            _isPlaying.value = true

            try {
                val sampleRate = SAMPLE_RATE_GEMINI
                val durationSeconds = 1.2
                val numSamples = (durationSeconds * sampleRate).toInt()
                val toneBytes = ByteArray(numSamples * 2)

                val frequency = 440.0 // 440 Hz standard A tone
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / frequency)
                    // Apply fade-in and fade-out envelope to prevent clicking
                    val envelope = when {
                        i < sampleRate * 0.05 -> i / (sampleRate * 0.05)
                        i > numSamples - sampleRate * 0.05 -> (numSamples - i) / (sampleRate * 0.05)
                        else -> 1.0
                    }
                    val sample = (sin(angle) * 28000 * envelope).toInt().toShort()
                    toneBytes[2 * i] = (sample.toInt() and 0x00FF).toByte()
                    toneBytes[2 * i + 1] = ((sample.toInt() and 0xFF00) shr 8).toByte()
                }

                enqueueAudioChunk(toneBytes)
                DiagnosticLogger.i(TAG, "Diagnostic: 440Hz tone queued to AudioTrack (${toneBytes.size} bytes)")
                delay(1300)
                DiagnosticLogger.i(TAG, "Diagnostic: 440Hz tone playback completed successfully!")
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Diagnostic: Failed to play 440Hz tone", e)
            } finally {
                _isPlaying.value = false
                onComplete()
            }
        }
    }

    fun release() {
        stopPlayback("Releasing AudioPlayer")
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error releasing AudioTrack", e)
        }
    }
}
