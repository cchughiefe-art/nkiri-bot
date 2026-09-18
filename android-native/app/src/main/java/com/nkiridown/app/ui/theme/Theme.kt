package com.nkiridown.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NkiriColorScheme =
    darkColorScheme(
        primary = NkiriPurple,
        secondary = NkiriViolet,
        background = NkiriBackground,
        surface = NkiriSurface,
        surfaceVariant = NkiriCard,
        onPrimary = NkiriText,
        onSecondary = NkiriText,
        onBackground = NkiriText,
        onSurface = NkiriText,
        outline = NkiriOutline,
        error = NkiriDanger
    )

@Composable
fun PremiumNkiriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NkiriColorScheme,
        content = content
    )
}
