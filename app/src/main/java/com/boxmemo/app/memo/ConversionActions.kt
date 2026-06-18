package com.boxmemo.app.memo

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boxmemo.app.BuildConfig
import com.boxmemo.app.hwr.AiTextResult
import com.boxmemo.app.hwr.OnyxHWREngine
import com.boxmemo.app.hwr.RecognitionMethod
import com.boxmemo.app.hwr.RecognitionMethodPreference
import com.boxmemo.app.hwr.TextEnhancementClient
import com.boxmemo.app.hwr.VisionOcrClient
import com.boxmemo.app.hwr.formatAsMeetingDetailLines
import com.boxmemo.app.hwr.formatAsNoteLines
import com.boxmemo.app.vault.DailyNoteRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val CAPTURE_WIDTH = 1000
private const val CAPTURE_HEIGHT = 600

/**
 * Single manual conversion action (R8) using whichever method is
 * currently set on the Settings page (R9) — diagram conversion is
 * deprioritized for now per user request, so this only handles text.
 */
@Composable
fun ConversionActions(
    date: LocalDate,
    scope: CaptureScope,
    strokes: List<StrokePath>,
    dailyNoteRepository: DailyNoteRepository,
    recognitionMethodPreference: RecognitionMethodPreference,
    onConverted: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember(scope) { mutableStateOf<String?>(null) }
    val method by recognitionMethodPreference.lastUsedMethod.collectAsState(initial = RecognitionMethod.ONYX_BUILT_IN)

    fun writeBack(text: String) {
        val written = when (scope) {
            is CaptureScope.Meeting ->
                dailyNoteRepository.addMeetingDetailBullets(date, scope.startTime, formatAsMeetingDetailLines(text))
            else ->
                dailyNoteRepository.addNoteLines(date, formatAsNoteLines(text))
        }
        statusMessage = if (written) "Converted and saved." else "Converted, but couldn't write to the note."
        if (written) onConverted()
    }

    FilterChip(
        selected = false,
        enabled = strokes.isNotEmpty(),
        onClick = {
            coroutineScope.launch {
                when (method) {
                    RecognitionMethod.ONYX_BUILT_IN -> {
                        statusMessage = "Recognizing…"
                        if (!OnyxHWREngine.bindAndAwait(context)) {
                            statusMessage = "Onyx HWR unavailable."
                            return@launch
                        }
                        val text = OnyxHWREngine.recognizeStrokes(strokes, CAPTURE_WIDTH.toFloat(), CAPTURE_HEIGHT.toFloat())
                        if (text.isNullOrBlank()) statusMessage = "Nothing recognized." else writeBack(text)
                    }
                    RecognitionMethod.AI_VISION -> {
                        statusMessage = "Recognizing…"
                        val bitmap = renderStrokesToBitmap(strokes, CAPTURE_WIDTH, CAPTURE_HEIGHT)
                        when (val result = VisionOcrClient(BuildConfig.OPENROUTER_API_KEY).recognizeText(bitmap)) {
                            is AiTextResult.Success -> writeBack(result.text)
                            is AiTextResult.Failure -> statusMessage = result.reason
                        }
                    }
                    RecognitionMethod.ONYX_THEN_AI_ENHANCE -> {
                        statusMessage = "Recognizing…"
                        if (!OnyxHWREngine.bindAndAwait(context)) {
                            statusMessage = "Onyx HWR unavailable."
                            return@launch
                        }
                        val rawText = OnyxHWREngine.recognizeStrokes(strokes, CAPTURE_WIDTH.toFloat(), CAPTURE_HEIGHT.toFloat())
                        if (rawText.isNullOrBlank()) {
                            statusMessage = "Nothing recognized."
                            return@launch
                        }
                        when (val result = TextEnhancementClient(BuildConfig.OPENROUTER_API_KEY).enhance(rawText)) {
                            is AiTextResult.Success -> writeBack(result.text)
                            is AiTextResult.Failure -> statusMessage = result.reason
                        }
                    }
                }
            }
        },
        label = { Text("Convert") },
    )

    statusMessage?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            modifier = androidx.compose.ui.Modifier.padding(start = 6.dp),
        )
    }
}
