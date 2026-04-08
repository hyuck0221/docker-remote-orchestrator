package com.orchestrator.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AADF4),
    onPrimary = Color(0xFF1A1B26),
    primaryContainer = Color(0xFF2A3A5C),
    onPrimaryContainer = Color(0xFFD0DCFA),
    secondary = Color(0xFF7DD3C0),
    onSecondary = Color(0xFF1A1B26),
    background = Color(0xFF0F1019),
    onBackground = Color(0xFFDCE0EC),
    surface = Color(0xFF16171F),
    onSurface = Color(0xFFDCE0EC),
    surfaceVariant = Color(0xFF1C1D2B),
    onSurfaceVariant = Color(0xFF9399B0),
    error = Color(0xFFED8796),
    onError = Color.White,
    outline = Color(0xFF2A2C3E),
    outlineVariant = Color(0xFF363849)
)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    titleSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, letterSpacing = 0.3.sp)
)

// Status colors - soft, easy on the eyes
val StatusRunning = Color(0xFF8BD5CA)
val StatusExited = Color(0xFFED8796)
val StatusPaused = Color(0xFFF5BF73)
val StatusOther = Color(0xFF6E738D)

// Surface levels
val Surface0 = Color(0xFF0F1019)
val Surface1 = Color(0xFF16171F)
val Surface2 = Color(0xFF1C1D2B)
val Surface3 = Color(0xFF232536)

// Accents
val AccentBlue = Color(0xFF8AADF4)
val AccentTeal = Color(0xFF7DD3C0)
val AccentMauve = Color(0xFFC6A0F6)
val TextMuted = Color(0xFF6E738D)
val TextSubtle = Color(0xFF9399B0)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
