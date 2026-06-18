package com.boxmemo.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

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
