package com.boxmemo.app.hwr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cleans up raw Onyx-recognized text via OpenRouter (text-only completion,
 * no image) — fixes obvious recognition errors and punctuation while
 * preserving the original wording and meaning as closely as possible.
 * This is the "Onyx OCR, then AI enhance" pipeline: a distinct path from
 * [VisionOcrClient]'s image-based recognition, not a replacement for it.
 */
class TextEnhancementClient(
    private val apiKey: String,
    private val model: String = VisionOcrClient.DEFAULT_MODEL,
) {
    suspend fun enhance(rawText: String): AiTextResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AiTextResult.Failure("No OpenRouter API key configured")
        if (rawText.isBlank()) return@withContext AiTextResult.Success("")

        val requestBody = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            "The following text was transcribed by handwriting recognition and may contain " +
                                "recognition errors. Correct obvious errors and punctuation, but preserve the " +
                                "original wording and meaning as closely as possible. Output only the corrected " +
                                "text, no commentary, no markdown formatting.\n\n$rawText",
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

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}
