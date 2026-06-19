package com.boxmemo.app.memo

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// Inline spans, in precedence order: wiki links, bold, strikethrough, inline
// code, then italic. Bold (`**`/`__`) is matched before italic (`*`/`_`) so a
// double marker isn't mistaken for two single ones. Each match contributes one
// non-nested style — adequate for the bullet detail lines and file lines shown.
private val INLINE_MD = Regex(
    """\[\[([^\]]+)]]""" +      // 1: [[wiki link]] / [[target|alias]]
        """|\*\*([^*]+)\*\*""" + // 2: **bold**
        """|__([^_]+)__""" +     // 3: __bold__
        """|~~([^~]+)~~""" +     // 4: ~~strikethrough~~
        """|`([^`]+)`""" +       // 5: `code`
        """|\*([^*]+)\*""" +     // 6: *italic*
        """|_([^_]+)_""",        // 7: _italic_
)

/**
 * Renders the common inline Markdown found in notes — bold, italic,
 * strikethrough, inline code and `[[wiki links]]` — into a styled
 * [AnnotatedString], with the markers themselves hidden. Anything unmatched is
 * passed through verbatim.
 */
fun renderInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_MD.findAll(text)) {
        if (match.range.first < cursor) continue // overlaps a span already emitted
        append(text, cursor, match.range.first)

        val token = match.value
        when {
            token.startsWith("[[") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                append(match.groupValues[1].substringAfter('|'))
            }
            token.startsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groupValues[2])
            }
            token.startsWith("__") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groupValues[3])
            }
            token.startsWith("~~") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(match.groupValues[4])
            }
            token.startsWith("`") -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                append(match.groupValues[5])
            }
            token.startsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(match.groupValues[6])
            }
            else -> { // _italic_
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(match.groupValues[7])
            }
        }
        pop()
        cursor = match.range.last + 1
    }
    append(text, cursor, text.length)
}
