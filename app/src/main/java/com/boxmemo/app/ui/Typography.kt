package com.boxmemo.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * "Paper & ink" light scheme designed to work on BOTH grayscale e-ink and
 * colour panels (always light — dark mode ghosts badly on e-ink).
 *
 * The rule for every hue here is that its grayscale luminance is chosen
 * first, the hue second:
 *  - Content stays near-black (#1B1A17) on warm paper white (#FDFBF7).
 *  - Strong accents (primary/tertiary/error) sit at ~20-30% luminance, so on
 *    a B&W panel they collapse to the near-black a filled button already
 *    needs, while colour panels see ink blue / forest green / deep red.
 *  - Container fills sit at ~87-89% luminance — the light grey wash the
 *    grayscale UI already uses for highlights — so black text on them keeps
 *    full contrast on both panel types.
 *  - Meaning is never carried by hue alone: on B&W the selected-day cell is
 *    disambiguated from "today" by a structural border (CalendarView.DayCell)
 *    and the banners by their 2dp outline, with colour as a bonus layer.
 *
 * Hue semantics: ink blue = selection/primary actions, warm amber = today,
 * forest green = update/good news, deep red = vault-health warnings.
 * `outline` is darkened toward DKGRAY so borders and resize grab-bars read
 * crisply on e-ink (Material's lavender-grey default nearly vanishes).
 */
val BoxMemoColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F3A5F),            // ink blue, ~22% luminance
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E4F2),   // pale blue, ~89%
    onPrimaryContainer = Color(0xFF16283F),
    inversePrimary = Color(0xFFA9C4E8),
    secondary = Color(0xFF6E4A14),          // amber-brown, ~31%
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2DFB9), // pale amber, ~88%
    onSecondaryContainer = Color(0xFF453310),
    tertiary = Color(0xFF3D5A3C),           // forest green, ~31%
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE9D8),  // pale green, ~88%
    onTertiaryContainer = Color(0xFF243620),
    error = Color(0xFF8C2B2B),              // deep red, ~26%
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF4DAD6),     // pale red, ~88%
    onErrorContainer = Color(0xFF5B1A1A),
    background = Color(0xFFFDFBF7),         // warm paper white
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFDFBF7),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFEDE9E1),
    onSurfaceVariant = Color(0xFF45413A),
    surfaceBright = Color(0xFFFDFBF7),
    surfaceDim = Color(0xFFE0DBD0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F4ED),
    surfaceContainer = Color(0xFFF2EEE5),
    surfaceContainerHigh = Color(0xFFECE7DC),
    surfaceContainerHighest = Color(0xFFE6E1D4),
    inverseSurface = Color(0xFF32302B),
    inverseOnSurface = Color(0xFFF5F1E9),
    outline = Color(0xFF444444),            // DKGRAY anchor — e-ink-tested
    outlineVariant = Color(0xFF756F64),
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
