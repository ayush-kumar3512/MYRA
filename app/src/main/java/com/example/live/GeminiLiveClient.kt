package com.example.live

import android.util.Base64
import com.example.actions.DeviceActionManager
import com.example.actions.DeviceActionResult
import com.example.data.DiagnosticLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val coroutineScope: CoroutineScope,
    private val actionManager: DeviceActionManager
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        const val LIVE_API_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val DEFAULT_MODEL = "models/gemini-2.0-flash-exp"
        const val DEFAULT_VOICE = "Aoede"

        const val MYRA_SYSTEM_INSTRUCTION =
            "You are MYRA, a young, confident, witty, playful, and emotionally responsive virtual assistant. " +
            "Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. " +
            "Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. " +
            "Automatically understand and respond in the language the user is speaking (English, Hindi, Hinglish, Marathi, Tamil, etc.). " +
            "Keep responses natural, engaging, and concise enough for real-time voice conversation. " +
            "You can execute safe supported device actions through available tools (openWhatsApp, openApp, openUrl, makeCall, callContact). " +
            "Never claim that an action was completed unless the application actually executed it. " +
            "Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality."
    }

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var onAudioChunkReceived: ((ByteArray) -> Unit)? = null
    private var onTranscriptReceived: ((text: String, isUser: Boolean) -> Unit)? = null
    private var onInterruptionDetected: (() -> Unit)? = null
    private var onToolExecuted: ((actionResult: DeviceActionResult) -> Unit)? = null
    private var onErrorOccurred: ((errorMessage: String) -> Unit)? = null

    fun setListeners(
        onAudio: (ByteArray) -> Unit,
        onTranscript: (String, Boolean) -> Unit,
        onInterruption: () -> Unit,
        onToolResult: (DeviceActionResult) -> Unit,
        onError: (String) -> Unit
    ) {
        onAudioChunkReceived = onAudio
        onTranscriptReceived = onTranscript
        onInterruptionDetected = onInterruption
        onToolExecuted = onToolResult
        onErrorOccurred = onError
    }

    fun connect(apiKey: String, voiceName: String = DEFAULT_VOICE) {
        if (apiKey.isBlank()) {
            DiagnosticLogger.e(TAG, "Cannot connect: API key is empty")
            onErrorOccurred?.invoke("Gemini API Key is missing. Please add your key in Settings.")
            return
        }

        disconnect("New connection initiated")
        DiagnosticLogger.i(TAG, "Connecting to Gemini Live WebSocket endpoint...")

        val request = Request.Builder()
            .url("$LIVE_API_URL?key=$apiKey")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                DiagnosticLogger.i(TAG, "WebSocket connected successfully! Response code: ${response.code}")
                _isConnected.value = true
                sendSetupMessage(ws, voiceName)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                DiagnosticLogger.i(TAG, "WebSocket closing: code=$code, reason='$reason'")
                _isConnected.value = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                DiagnosticLogger.i(TAG, "WebSocket closed: code=$code, reason='$reason'")
                _isConnected.value = false
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                DiagnosticLogger.e(TAG, "WebSocket failure: responseCode=${response?.code}", t)
                _isConnected.value = false
                val errorDetails = response?.message ?: t.message ?: "Connection error"
                onErrorOccurred?.invoke("Live session connection error: $errorDetails")
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket, voiceName: String) {
        DiagnosticLogger.i(TAG, "Sending setup message with MYRA system instruction and voice=$voiceName")

        try {
            val setupJson = JSONObject().apply {
                val setupObj = JSONObject().apply {
                    put("model", DEFAULT_MODEL)

                    // Generation config
                    val genConfig = JSONObject().apply {
                        val modalities = JSONArray().apply { put("AUDIO") }
                        put("responseModalities", modalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", voiceName)
                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", genConfig)

                    // System Instruction
                    val sysInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            val part = JSONObject().apply {
                                put("text", MYRA_SYSTEM_INSTRUCTION)
                            }
                            put(part)
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", sysInstruction)

                    // Tools Declarations
                    val tools = JSONArray().apply {
                        val toolWrapper = JSONObject().apply {
                            val declarations = JSONArray().apply {
                                // 1. openWhatsApp
                                put(JSONObject().apply {
                                    put("name", "openWhatsApp")
                                    put("description", "Opens WhatsApp application on the device. Call this when the user asks to open WhatsApp or says 'WhatsApp kholo'.")
                                })

                                // 2. openApp
                                put(JSONObject().apply {
                                    put("name", "openApp")
                                    put("description", "Opens a supported app on the device like YouTube, Instagram, Spotify, Google Maps, Camera, Calculator, Clock, or Settings.")
                                    val params = JSONObject().apply {
                                        put("type", "OBJECT")
                                        val props = JSONObject().apply {
                                            put("appName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Name of the app to open (e.g. YouTube, Instagram, Spotify, Maps, Camera, Calculator, Settings)")
                                            })
                                        }
                                        put("properties", props)
                                        put("required", JSONArray().apply { put("appName") })
                                    }
                                    put("parameters", params)
                                })

                                // 3. openUrl
                                put(JSONObject().apply {
                                    put("name", "openUrl")
                                    put("description", "Opens a valid web URL in the device browser.")
                                    val params = JSONObject().apply {
                                        put("type", "OBJECT")
                                        val props = JSONObject().apply {
                                            put("url", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The URL to open in the browser")
                                            })
                                        }
                                        put("properties", props)
                                        put("required", JSONArray().apply { put("url") })
                                    }
                                    put("parameters", params)
                                })

                                // 4. makeCall
                                put(JSONObject().apply {
                                    put("name", "makeCall")
                                    put("description", "Opens the phone dialer with a specific phone number.")
                                    val params = JSONObject().apply {
                                        put("type", "OBJECT")
                                        val props = JSONObject().apply {
                                            put("phoneNumber", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The phone number to dial")
                                            })
                                        }
                                        put("properties", props)
                                        put("required", JSONArray().apply { put("phoneNumber") })
                                    }
                                    put("parameters", params)
                                })

                                // 5. callContact
                                put(JSONObject().apply {
                                    put("name", "callContact")
                                    put("description", "Searches device contacts by name and initiates a call if found. Call when user says 'Call Mom', 'Rahul ko call karo', etc.")
                                    val params = JSONObject().apply {
                                        put("type", "OBJECT")
                                        val props = JSONObject().apply {
                                            put("contactName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The contact name to call")
                                            })
                                        }
                                        put("properties", props)
                                        put("required", JSONArray().apply { put("contactName") })
                                    }
                                    put("parameters", params)
                                })
                            }
                            put("functionDeclarations", declarations)
                        }
                        put(toolWrapper)
                    }
                    put("tools", tools)
                }
                put("setup", setupObj)
            }

            val sent = ws.send(setupJson.toString())
            DiagnosticLogger.i(TAG, "Setup message sent: success=$sent")
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error constructing setup JSON", e)
        }
    }

    /**
     * Sends a 16kHz 16-bit PCM mono audio chunk to Gemini Live.
     */
    fun sendAudioChunk(pcmBytes: ByteArray) {
        val ws = webSocket ?: return
        if (!_isConnected.value) return

        try {
            val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "audio/pcm")
                            put("data", base64Data)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            ws.send(json.toString())
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error sending audio chunk", e)
        }
    }

    /**
     * Sends user text input directly to the Live session.
     */
    fun sendTextMessage(text: String) {
        val ws = webSocket ?: return
        if (!_isConnected.value) return

        try {
            DiagnosticLogger.i(TAG, "Sending clientContent text: '$text'")
            val json = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    val turns = JSONArray().apply {
                        val turn = JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", text) })
                            }
                            put("parts", parts)
                        }
                        put(turn)
                    }
                    put("turns", turns)
                    put("turnComplete", true)
                }
                put("clientContent", clientContent)
            }
            ws.send(json.toString())
            onTranscriptReceived?.invoke(text, true)
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error sending clientContent text", e)
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            // 1. Check for serverContent
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Interruption check
                if (serverContent.optBoolean("interrupted", false)) {
                    DiagnosticLogger.i(TAG, "Interruption signal received from Gemini Live")
                    coroutineScope.launch(Dispatchers.Main) {
                        onInterruptionDetected?.invoke()
                    }
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Check for text transcript
                        if (part.has("text")) {
                            val transcriptText = part.getString("text")
                            DiagnosticLogger.d(TAG, "Received transcript: '$transcriptText'")
                            coroutineScope.launch(Dispatchers.Main) {
                                onTranscriptReceived?.invoke(transcriptText, false)
                            }
                        }

                        // Check for inlineData (audio bytes)
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val mimeType = inlineData.optString("mimeType", "")
                            val base64Data = inlineData.optString("data", "")

                            if (base64Data.isNotEmpty()) {
                                val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                DiagnosticLogger.d(TAG, "Received audio chunk: mimeType=$mimeType, decodedSize=${pcmBytes.size} bytes")
                                coroutineScope.launch(Dispatchers.Main) {
                                    onAudioChunkReceived?.invoke(pcmBytes)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Check for toolCall
            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()

                for (i in 0 until functionCalls.length()) {
                    val call = functionCalls.getJSONObject(i)
                    val callId = call.optString("id", "call_${System.currentTimeMillis()}")
                    val name = call.getString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()

                    DiagnosticLogger.i(TAG, "Gemini called tool '$name' (id=$callId) with args: $args")
                    executeToolCall(callId, name, args)
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error parsing incoming WebSocket message", e)
        }
    }

    private fun executeToolCall(callId: String, name: String, args: JSONObject) {
        coroutineScope.launch(Dispatchers.Main) {
            val result: DeviceActionResult = when (name) {
                "openWhatsApp" -> actionManager.openWhatsApp()
                "openApp" -> {
                    val appName = args.optString("appName", "")
                    actionManager.openApp(appName)
                }
                "openUrl" -> {
                    val url = args.optString("url", "")
                    actionManager.openUrl(url)
                }
                "makeCall" -> {
                    val phoneNumber = args.optString("phoneNumber", "")
                    actionManager.makeCall(phoneNumber)
                }
                "callContact" -> {
                    val contactName = args.optString("contactName", "")
                    actionManager.callContact(contactName)
                }
                else -> {
                    DeviceActionResult(
                        success = false,
                        action = name,
                        message = "Unknown function call: $name"
                    )
                }
            }

            onToolExecuted?.invoke(result)
            sendToolResponse(callId, name, result)
        }
    }

    private fun sendToolResponse(callId: String, name: String, result: DeviceActionResult) {
        val ws = webSocket ?: return
        if (!_isConnected.value) return

        try {
            DiagnosticLogger.i(TAG, "Sending tool response for '$name': success=${result.success}")
            val responseJson = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    val functionResponses = JSONArray().apply {
                        val funcResponse = JSONObject().apply {
                            put("id", callId)
                            val responseObj = JSONObject().apply {
                                val outputObj = JSONObject().apply {
                                    put("success", result.success)
                                    put("action", result.action)
                                    put("message", result.message)
                                    result.data.forEach { (k, v) -> put(k, v) }
                                }
                                put("output", outputObj)
                            }
                            put("response", responseObj)
                        }
                        put(funcResponse)
                    }
                    put("functionResponses", functionResponses)
                }
                put("toolResponse", toolResponse)
            }

            ws.send(responseJson.toString())
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error sending tool response", e)
        }
    }

    fun disconnect(reason: String = "User requested disconnect") {
        DiagnosticLogger.i(TAG, "Disconnecting Live session: $reason")
        try {
            webSocket?.close(1000, reason)
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Error closing WebSocket", e)
        } finally {
            webSocket = null
            _isConnected.value = false
        }
    }
}
