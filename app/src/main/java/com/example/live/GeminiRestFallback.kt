package com.example.live

import com.example.actions.DeviceActionManager
import com.example.actions.DeviceActionResult
import com.example.data.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RestAiResponse(
    val replyText: String,
    val toolResult: DeviceActionResult? = null
)

class GeminiRestFallback(
    private val actionManager: DeviceActionManager
) {
    companion object {
        private const val TAG = "GeminiRestFallback"
        private const val MODEL_NAME = "gemini-2.5-flash"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(
        apiKey: String,
        userPrompt: String,
        conversationHistory: List<Pair<String, Boolean>> = emptyList()
    ): RestAiResponse = withContext(Dispatchers.IO) {
        DiagnosticLogger.i(TAG, "Executing REST fallback for query: '$userPrompt'")

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

            val requestJson = JSONObject().apply {
                // System Instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", GeminiLiveClient.MYRA_SYSTEM_INSTRUCTION)
                        })
                    })
                })

                // Contents
                val contents = JSONArray().apply {
                    // History (last 4 turns)
                    conversationHistory.takeLast(4).forEach { (msg, isUser) ->
                        put(JSONObject().apply {
                            put("role", if (isUser) "user" else "model")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", msg) })
                            })
                        })
                    }
                    // Current prompt
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userPrompt) })
                        })
                    })
                }
                put("contents", contents)

                // Tools
                val tools = JSONArray().apply {
                    val toolObj = JSONObject().apply {
                        val funcs = JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "openWhatsApp")
                                put("description", "Opens WhatsApp application on the device.")
                            })
                            put(JSONObject().apply {
                                put("name", "openApp")
                                put("description", "Opens a supported app on the device (e.g. YouTube, Instagram, Spotify, Maps, Camera, Calculator, Clock, Settings).")
                                val params = JSONObject().apply {
                                    put("type", "OBJECT")
                                    val props = JSONObject().apply {
                                        put("appName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Name of the app")
                                        })
                                    }
                                    put("properties", props)
                                    put("required", JSONArray().apply { put("appName") })
                                }
                                put("parameters", params)
                            })
                            put(JSONObject().apply {
                                put("name", "openUrl")
                                put("description", "Opens a URL in the browser.")
                                val params = JSONObject().apply {
                                    put("type", "OBJECT")
                                    val props = JSONObject().apply {
                                        put("url", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Target URL")
                                        })
                                    }
                                    put("properties", props)
                                    put("required", JSONArray().apply { put("url") })
                                }
                                put("parameters", params)
                            })
                            put(JSONObject().apply {
                                put("name", "makeCall")
                                put("description", "Opens the phone dialer with a phone number.")
                                val params = JSONObject().apply {
                                    put("type", "OBJECT")
                                    val props = JSONObject().apply {
                                        put("phoneNumber", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Phone number")
                                        })
                                    }
                                    put("properties", props)
                                    put("required", JSONArray().apply { put("phoneNumber") })
                                }
                                put("parameters", params)
                            })
                            put(JSONObject().apply {
                                put("name", "callContact")
                                put("description", "Searches device contacts and dials if found.")
                                val params = JSONObject().apply {
                                    put("type", "OBJECT")
                                    val props = JSONObject().apply {
                                        put("contactName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Contact name")
                                        })
                                    }
                                    put("properties", props)
                                    put("required", JSONArray().apply { put("contactName") })
                                }
                                put("parameters", params)
                            })
                        }
                        put("functionDeclarations", funcs)
                    }
                    put(toolObj)
                }
                put("tools", tools)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                DiagnosticLogger.e(TAG, "REST API call failed: code=${response.code}, body=$responseBody")
                return@withContext RestAiResponse("Sorry, I had trouble connecting. Error code: ${response.code}")
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates") ?: JSONArray()
            if (candidates.length() == 0) {
                return@withContext RestAiResponse("MYRA didn't get a response. Try asking again!")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()

            var replyText = ""
            var actionResult: DeviceActionResult? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    replyText += part.getString("text") + " "
                }
                if (part.has("functionCall")) {
                    val fnCall = part.getJSONObject("functionCall")
                    val fnName = fnCall.getString("name")
                    val args = fnCall.optJSONObject("args") ?: JSONObject()

                    DiagnosticLogger.i(TAG, "REST: Model requested function call '$fnName' with args: $args")
                    actionResult = when (fnName) {
                        "openWhatsApp" -> actionManager.openWhatsApp()
                        "openApp" -> actionManager.openApp(args.optString("appName", ""))
                        "openUrl" -> actionManager.openUrl(args.optString("url", ""))
                        "makeCall" -> actionManager.makeCall(args.optString("phoneNumber", ""))
                        "callContact" -> actionManager.callContact(args.optString("contactName", ""))
                        else -> null
                    }
                }
            }

            if (replyText.isBlank()) {
                replyText = when {
                    actionResult != null && actionResult.success -> actionResult.message
                    actionResult != null -> actionResult.message
                    else -> "Got it! How else can I help?"
                }
            }

            RestAiResponse(replyText.trim(), actionResult)
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error in REST fallback", e)
            RestAiResponse("Something went wrong while speaking with MYRA: ${e.message}")
        }
    }
}
