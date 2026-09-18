package com.nkiridown.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object NkiriSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object NkiriShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
}

object NkiriMotion {
    const val PressMs = 180
    const val ScreenMs = 240
    const val ImageFadeMs = 220
    const val SearchDebounceMs = 450L
    const val PressScale = 0.97f
}
