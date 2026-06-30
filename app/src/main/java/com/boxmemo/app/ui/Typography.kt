package com.boxmemo.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Material3's baseline scheme tints surfaces and outlines with low-saturation
 * purple/lavender that collapses to faint mid-greys on a grayscale e-ink panel
 * — borders and resize grab-bars (which use [ColorScheme.outline]) nearly
 * vanish. We start from [lightColorScheme] (never the system scheme — dark mode
 * ghosts badly on e-ink) and darken only `outline` toward a DKGRAY anchor so
 * those affordances read crisply, leaving every other token at its tested
 * default. The selected-day vs today container fills still want on-device
 * tuning (they map to near-identical greys) — see CalendarView.DayCell, which
 * adds a structural black border for the selected cell as a panel-safe cue.
 */
val BoxMemoColorScheme: ColorScheme = lightColorScheme(
    outline = Color(0xFF444444),
)

/**
 * Default Material3 type sizes read too small on the Boox Go 10.3's e-ink
 * display at arm's length. Scaled up roughly 1.3-1.4x across the board.
 */
val BoxMemoTypography = Typography(
    displayLarge = TextStyle(fontSize = 62.sp),
    displayMedium = TextStyle(fontSize = 50.sp),
    displaySmall = TextStyle(fontSize = 40.sp),
    headlineLarge = TextStyle(fontSize = 35.sp),
    headlineMedium = TextStyle(fontSize = 30.sp),
    headlineSmall = TextStyle(fontSize = 27.sp),
    titleLarge = TextStyle(fontSize = 32.sp),
    titleMedium = TextStyle(fontSize = 27.sp),
    titleSmall = TextStyle(fontSize = 25.sp),
    bodyLarge = TextStyle(fontSize = 27.sp),
    bodyMedium = TextStyle(fontSize = 25.sp),
    bodySmall = TextStyle(fontSize = 22.sp),
    labelLarge = TextStyle(fontSize = 25.sp),
    labelMedium = TextStyle(fontSize = 22.sp),
    labelSmall = TextStyle(fontSize = 20.sp),
)
