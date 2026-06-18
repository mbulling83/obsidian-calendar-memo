package com.boxmemo.app.hwr

import org.json.JSONObject

sealed interface AiTextResult {
    data class Success(val text: String) : AiTextResult
    data class Failure(val reason: String) : AiTextResult
}

/**
 * Pulls the assistant's text content out of an OpenAI-compatible
 * chat/completions JSON response body. Pure JSON parsing, no Android
 * dependency, so it's covered by a JVM unit test rather than androidTest.
 * Shared by [VisionOcrClient] and [TextEnhancementClient] since both call
 * the same OpenRouter response shape.
 */
internal fun parseChatCompletionContent(responseBody: String): String? = try {
    JSONObject(responseBody)
        .optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content")
} catch (e: Exception) {
    null
}
