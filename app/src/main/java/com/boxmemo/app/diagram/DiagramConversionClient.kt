package com.boxmemo.app.diagram

import android.graphics.Bitmap
import android.util.Base64
import com.boxmemo.app.hwr.AiTextResult
import com.boxmemo.app.hwr.parseChatCompletionContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed interface AiImageResult {
    data class Success(val imageBytes: ByteArray) : AiImageResult
    data class Failure(val reason: String) : AiImageResult
}

/**
 * Recreates a hand-drawn diagram via OpenRouter, either as a clean
 * raster image or as Mermaid diagram code — the user's choice per
 * diagram (R12/R13), not a fixed setting. Model identifiers are
 * constructor parameters per the plan's Key Technical Decisions; the
 * image-generation response shape (`message.images[].image_url.url` as a
 * base64 data URL) follows OpenRouter's documented convention for
 * image-output models as of this writing — re-verify if it stops working,
 * since OpenRouter's image-output support is newer and more likely to
 * shift than the plain-text chat completion shape.
 */
class DiagramConversionClient(
    private val apiKey: String,
    private val textModel: String = "google/gemini-2.0-flash-001",
    private val imageModel: String = "google/gemini-2.5-flash-image-preview",
) {
    suspend fun generateMermaidCode(bitmap: Bitmap): AiTextResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AiTextResult.Failure("No OpenRouter API key configured")

        val requestBody = chatRequestBody(
            model = textModel,
            promptText = "Recreate the hand-drawn diagram in this image as Mermaid diagram syntax. " +
                "Output only the Mermaid code (no code fences, no commentary, no markdown).",
            base64Image = bitmap.toBase64Jpeg(),
        )

        try {
            val (responseCode, responseBody) = postJson(ENDPOINT, requestBody)
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

    suspend fun generateImage(bitmap: Bitmap): AiImageResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AiImageResult.Failure("No OpenRouter API key configured")

        val requestBody = chatRequestBody(
            model = imageModel,
            promptText = "Recreate this hand-drawn diagram as a clean, legible digital diagram image, " +
                "preserving its structure and labels.",
            base64Image = bitmap.toBase64Jpeg(),
            modalities = JSONArray().put("image").put("text"),
        )

        try {
            val (responseCode, responseBody) = postJson(ENDPOINT, requestBody)
            if (responseCode !in 200..299) {
                return@withContext AiImageResult.Failure("OpenRouter returned HTTP $responseCode: $responseBody")
            }
            val imageBytes = parseImageBytes(responseBody)
            if (imageBytes == null) {
                AiImageResult.Failure("OpenRouter did not return image data")
            } else {
                AiImageResult.Success(imageBytes)
            }
        } catch (e: Exception) {
            AiImageResult.Failure("Network error calling OpenRouter: ${e.message}")
        }
    }

    private fun chatRequestBody(
        model: String,
        promptText: String,
        base64Image: String,
        modalities: JSONArray? = null,
    ): JSONObject = JSONObject().apply {
        put("model", model)
        modalities?.let { put("modalities", it) }
        put(
            "messages",
            JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "content",
                        JSONArray()
                            .put(JSONObject().apply { put("type", "text"); put("text", promptText) })
                            .put(
                                JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Image"))
                                },
                            ),
                    )
                },
            ),
        )
    }

    private fun postJson(endpoint: String, body: JSONObject): Pair<Int, String> {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            .bufferedReader()
            .use { it.readText() }
        return responseCode to responseBody
    }

    private fun parseImageBytes(responseBody: String): ByteArray? = try {
        val message = JSONObject(responseBody)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
        val dataUrl = message
            ?.optJSONArray("images")
            ?.optJSONObject(0)
            ?.optJSONObject("image_url")
            ?.optString("url")
        val base64Part = dataUrl?.substringAfter("base64,", missingDelimiterValue = "")
        if (base64Part.isNullOrBlank()) null else Base64.decode(base64Part, Base64.DEFAULT)
    } catch (e: Exception) {
        null
    }

    private fun Bitmap.toBase64Jpeg(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}
