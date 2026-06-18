package com.boxmemo.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Default Material3 type sizes read too small on the Boox Go 10.3's e-ink
 * display at arm's length. Scaled up roughly 1.3-1.4x across the board.
 */
val BoxMemoTypography = Typography(
    displayLarge = TextStyle(fontSize = 50.sp),
    displayMedium = TextStyle(fontSize = 40.sp),
    displaySmall = TextStyle(fontSize = 32.sp),
    headlineLarge = TextStyle(fontSize = 28.sp),
    headlineMedium = TextStyle(fontSize = 24.sp),
    headlineSmall = TextStyle(fontSize = 22.sp),
    titleLarge = TextStyle(fontSize = 26.sp),
    titleMedium = TextStyle(fontSize = 22.sp),
    titleSmall = TextStyle(fontSize = 20.sp),
    bodyLarge = TextStyle(fontSize = 22.sp),
    bodyMedium = TextStyle(fontSize = 20.sp),
    bodySmall = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 20.sp),
    labelMedium = TextStyle(fontSize = 18.sp),
    labelSmall = TextStyle(fontSize = 16.sp),
)
