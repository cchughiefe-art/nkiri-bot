package com.nkiridown.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NkiriPrimary = Color(0xFFE11D48)
private val NkiriBackground = Color(0xFF09090B)
private val NkiriSurface = Color(0xFF15151B)
private val NkiriSurfaceVariant = Color(0xFF1C1C24)

@Composable
fun NkiriTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NkiriPrimary,
            background = NkiriBackground,
            surface = NkiriSurface,
            surfaceVariant = NkiriSurfaceVariant
        ),
        content = content
    )
}
