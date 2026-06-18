package com.boxmemo.app.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boxmemo.app.BuildConfig
import com.boxmemo.app.hwr.OnyxHWREngine
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
 * Manual per-capture conversion (R8/R9): three buttons rather than one,
 * since [RecognitionMethod.ONYX_THEN_AI_ENHANCE] was added alongside the
 * original Onyx-only / AI-vision-only choice, not as a replacement.
 * Conversion failures never write anything to the note.
 */
@Composable
fun ConversionActions(
    date: LocalDate,
    scope: CaptureScope,
    strokes: List<StrokePath>,
    dailyNoteRepository: DailyNoteRepository,
    onConverted: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember(scope) { mutableStateOf<String?>(null) }

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

    Column(modifier = androidx.compose.ui.Modifier.padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = strokes.isNotEmpty(), onClick = {
                coroutineScope.launch {
                    statusMessage = "Recognizing (Onyx)..."
                    val bound = OnyxHWREngine.bindAndAwait(context)
                    if (!bound) {
                        statusMessage = "Built-in recognition unavailable on this device. Try AI vision instead."
                        return@launch
                    }
                    val text = OnyxHWREngine.recognizeStrokes(strokes, CAPTURE_WIDTH.toFloat(), CAPTURE_HEIGHT.toFloat())
                    if (text.isNullOrBlank()) {
                        statusMessage = "Nothing recognized."
                    } else {
                        writeBack(text)
                    }
                }
            }) { Text("Onyx") }

            Button(enabled = strokes.isNotEmpty(), onClick = {
                coroutineScope.launch {
                    statusMessage = "Recognizing (AI vision)..."
                    val bitmap = renderStrokesToBitmap(strokes, CAPTURE_WIDTH, CAPTURE_HEIGHT)
                    when (val result = VisionOcrClient(BuildConfig.OPENROUTER_API_KEY).recognizeText(bitmap)) {
                        is com.boxmemo.app.hwr.AiTextResult.Success -> writeBack(result.text)
                        is com.boxmemo.app.hwr.AiTextResult.Failure -> statusMessage = result.reason
                    }
                }
            }) { Text("AI vision") }

            Button(enabled = strokes.isNotEmpty(), onClick = {
                coroutineScope.launch {
                    statusMessage = "Recognizing (Onyx + AI enhance)..."
                    val bound = OnyxHWREngine.bindAndAwait(context)
                    if (!bound) {
                        statusMessage = "Built-in recognition unavailable on this device."
                        return@launch
                    }
                    val rawText = OnyxHWREngine.recognizeStrokes(strokes, CAPTURE_WIDTH.toFloat(), CAPTURE_HEIGHT.toFloat())
                    if (rawText.isNullOrBlank()) {
                        statusMessage = "Nothing recognized."
                        return@launch
                    }
                    when (val result = TextEnhancementClient(BuildConfig.OPENROUTER_API_KEY).enhance(rawText)) {
                        is com.boxmemo.app.hwr.AiTextResult.Success -> writeBack(result.text)
                        is com.boxmemo.app.hwr.AiTextResult.Failure -> statusMessage = result.reason
                    }
                }
            }) { Text("Onyx + AI enhance") }
        }

        statusMessage?.let { Text(it) }
    }
}
