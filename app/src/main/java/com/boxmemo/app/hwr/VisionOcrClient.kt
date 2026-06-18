package com.boxmemo.app.hwr

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI vision OCR via OpenRouter's OpenAI-compatible chat/completions
 * endpoint (R9's alternative to Onyx's built-in recognizer). The model
 * identifier is a constructor parameter, not hardcoded inline, so it can
 * be updated without a structural code change as OpenRouter's model
 * lineup changes over time (see plan Key Technical Decisions).
 */
class VisionOcrClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) {
    suspend fun recognizeText(bitmap: Bitmap): AiTextResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AiTextResult.Failure("No OpenRouter API key configured")

        val base64Image = bitmap.toBase64Jpeg()
        val requestBody = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put(
                                            "text",
                                            "Transcribe the handwritten text in this image exactly as written. " +
                                                "Plain text only, no commentary, no markdown formatting.",
                                        )
                                    },
                                )
                                .put(
                                    JSONObject().apply {
                                        put("type", "image_url")
                                        put(
                                            "image_url",
                                            JSONObject().put("url", "data:image/jpeg;base64,$base64Image"),
                                        )
                                    },
                                ),
                        )
                    },
                ),
            )
        }

        try {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }

            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader()
                .use { it.readText() }

            if (responseCode !in 200..299) {
                return@withContext AiTextResult.Failure("OpenRouter returned HTTP $responseCode: $responseBody")
            }

            val text = parseChatCompletionContent(responseBody)
            if (text.isNullOrBlank()) {
                AiTextResult.Failure("OpenRouter returned an empty or unrecognized response")
            } else {
                AiTextResult.Success(text.trim())
            }
        } catch (e: Exception) {
            AiTextResult.Failure("Network error calling OpenRouter: ${e.message}")
        }
    }

    private fun Bitmap.toBase64Jpeg(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        const val DEFAULT_MODEL = "google/gemini-2.0-flash-001"
    }
}
