package com.nkiridown.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NkiriBg = Color(0xFF050708)
val NkiriSurface = Color(0xFF0E1214)
val NkiriSurfaceHigh = Color(0xFF171C1F)
val NkiriAccent = Color(0xFF25E6A7)
val NkiriAccentSoft = Color(0xFF0F3D31)
val NkiriText = Color(0xFFF7FAF9)
val NkiriMuted = Color(0xFFA8B1AE)
val NkiriOutline = Color(0xFF2A3230)
val NkiriDanger = Color(0xFFFF6B6B)

private val NkiriColors = darkColorScheme(
    primary = NkiriAccent,
    onPrimary = Color(0xFF002117),
    primaryContainer = NkiriAccentSoft,
    onPrimaryContainer = Color(0xFFB8FFE5),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF001F2A),
    background = NkiriBg,
    onBackground = NkiriText,
    surface = NkiriSurface,
    onSurface = NkiriText,
    surfaceVariant = NkiriSurfaceHigh,
    onSurfaceVariant = NkiriMuted,
    outline = NkiriOutline,
    error = NkiriDanger,
    onError = Color.Black
)

private val NkiriTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
)

@Composable
fun NkiriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NkiriColors, typography = NkiriTypography, content = content)
}
