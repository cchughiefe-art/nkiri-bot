package com.nkiridown.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nkiridown.app.PlaybackRecord
import com.nkiridown.app.SearchItem
import com.nkiridown.app.ui.theme.*

@Composable
fun MoviePoster(
    item: SearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clip(NkiriShapes.medium)
            .background(NkiriCard)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
            contentScale = ContentScale.Crop,
            alpha = 1f
        )
        Column(Modifier.padding(10.dp)) {
            Text(
                item.title,
                color = NkiriText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MetadataLine(
                listOfNotNull(
                    item.year?.toString(),
                    if (item.type == "series") "Series" else "Movie",
                    item.rating?.let { "★ $it" }
                )
            )
        }
    }
}

@Composable
fun MovieRail(
    title: String,
    subtitle: String? = null,
    items: List<SearchItem>,
    onOpen: (SearchItem) -> Unit
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title, subtitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { "${title}:${it.id}" }) { item ->
                MoviePoster(item = item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
fun CinematicHero(
    item: SearchItem,
    onOpen: () -> Unit,
    onLibrary: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .padding(horizontal = 12.dp)
            .clip(NkiriShapes.large)
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.30f),
                        NkiriBackground.copy(alpha = 0.98f)
                    )
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
        ) {
            Surface(
                color = NkiriPurple.copy(alpha = 0.92f),
                shape = NkiriShapes.pill
            ) {
                Text(
                    if (item.type == "series") "FEATURED SERIES" else "FEATURED",
                    color = NkiriText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                item.title,
                color = NkiriText,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MetadataLine(
                listOfNotNull(
                    item.year?.toString(),
                    item.rating?.let { "★ $it" },
                    item.genre.takeIf { it.isNotBlank() }
                )
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumButton(
                    text = "View",
                    onClick = onOpen,
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onLibrary,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, NkiriOutline),
                    shape = NkiriShapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NkiriText)
                    Spacer(Modifier.width(6.dp))
                    Text("Library", color = NkiriText)
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: PlaybackRecord,
    onResume: () -> Unit
) {
    Column(
        Modifier
            .width(220.dp)
            .clip(NkiriShapes.medium)
            .background(NkiriCard)
            .clickable(onClick = onResume)
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.title,
            modifier = Modifier.fillMaxWidth().height(124.dp),
            contentScale = ContentScale.Crop
        )
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth(),
            color = NkiriViolet,
            trackColor = NkiriElevated
        )
        Column(Modifier.padding(12.dp)) {
            Text(
                item.title,
                color = NkiriText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val remaining =
                if (item.durationMs > item.positionMs && item.durationMs > 0L) {
                    ((item.durationMs - item.positionMs) / 60_000L).coerceAtLeast(1L)
                } else null
            Text(
                remaining?.let { "$it min remaining" } ?: "Resume",
                color = NkiriMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
