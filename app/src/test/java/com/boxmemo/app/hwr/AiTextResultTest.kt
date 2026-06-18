package com.boxmemo.app.hwr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTextResultTest {

    @Test
    fun `extracts assistant text from a well-formed chat completion response`() {
        val response = """
            {"choices":[{"message":{"role":"assistant","content":"Hello world"}}]}
        """.trimIndent()

        assertEquals("Hello world", parseChatCompletionContent(response))
    }

    @Test
    fun `returns null when choices array is missing`() {
        val response = """{"error":"something went wrong"}"""

        assertNull(parseChatCompletionContent(response))
    }

    @Test
    fun `returns null for malformed JSON`() {
        assertNull(parseChatCompletionContent("not json at all"))
    }

    @Test
    fun `returns empty string when content is present but blank`() {
        val response = """
            {"choices":[{"message":{"role":"assistant","content":""}}]}
        """.trimIndent()

        assertEquals("", parseChatCompletionContent(response))
    }
}
