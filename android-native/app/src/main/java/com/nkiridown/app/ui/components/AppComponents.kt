package com.nkiridown.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nkiridown.app.ui.theme.*

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) NkiriMotion.PressScale else 1f,
        animationSpec = tween(NkiriMotion.PressMs),
        label = "buttonScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        shape = NkiriShapes.medium,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(listOf(NkiriPurple, NkiriViolet))
                    } else {
                        Brush.horizontalGradient(listOf(NkiriCard, NkiriCard))
                    }
                )
                .padding(horizontal = 18.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = NkiriText)
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = NkiriText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = NkiriText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = NkiriMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, color = NkiriViolet)
            }
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = NkiriText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = NkiriMuted)
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            PremiumButton("Retry", onRetry)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = NkiriSurface,
        shape = NkiriShapes.large,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(title, color = NkiriText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = NkiriMuted)
        }
    }
}

@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    height: Int = 180
) {
    Surface(
        modifier = modifier.height(height.dp),
        color = NkiriCard,
        shape = NkiriShapes.large
    ) {}
}

@Composable
fun MetadataLine(parts: List<String>) {
    val cleaned = parts.filter { it.isNotBlank() }
    if (cleaned.isNotEmpty()) {
        Text(
            cleaned.joinToString(" • "),
            color = NkiriMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
