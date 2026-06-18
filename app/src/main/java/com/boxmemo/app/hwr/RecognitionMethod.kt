package com.boxmemo.app.hwr

/**
 * - [ONYX_BUILT_IN]: device's MyScript recognizer only.
 * - [AI_VISION]: OpenRouter vision model transcribes the capture image directly.
 * - [ONYX_THEN_AI_ENHANCE]: Onyx recognizes first, then OpenRouter cleans up
 *   the resulting text (no image sent) — a third, distinct path, not a
 *   replacement for the other two.
 */
enum class RecognitionMethod { ONYX_BUILT_IN, AI_VISION, ONYX_THEN_AI_ENHANCE }
