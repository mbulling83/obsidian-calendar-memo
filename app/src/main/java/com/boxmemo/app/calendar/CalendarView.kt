package com.boxmemo.app.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ROW_HEIGHT = 36.dp

/**
 * Month grid mirroring the native Boox Calendar+Memo layout: month nav
 * arrows, day cells, today/selected highlighting.
 */
@Composable
fun CalendarView(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.height(ROW_HEIGHT)) {
                Text("<")
            }
            Text(
                text = "${yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${yearMonth.year}",
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.height(ROW_HEIGHT)) {
                Text(">")
            }
        }

        val firstOfMonth = yearMonth.atDay(1)
        // Monday-first week, matching the user's existing daily-note convention.
        val leadingBlanks = (firstOfMonth.dayOfWeek.value - 1).coerceAtLeast(0)
        val daysInMonth = yearMonth.lengthOfMonth()
        val totalCells = leadingBlanks + daysInMonth
        val weeks = (totalCells + 6) / 7

        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                for (dayOfWeek in 0 until 7) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNumber = cellIndex - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayNumber)
                            DayCell(
                                day = dayNumber,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                onClick = { onDaySelected(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = day.toString(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}
