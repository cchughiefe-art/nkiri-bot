package com.nkiridown.app.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nkiridown.app.EpisodeItem
import com.nkiridown.app.SearchItem
import com.nkiridown.app.SourceItem
import com.nkiridown.app.UiState
import com.nkiridown.app.ui.components.*
import com.nkiridown.app.ui.theme.*

@Composable
fun TitleDetailsScreen(
    state: UiState,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onSeason: (Int) -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit,
    onDownloadSeason: (Int) -> Unit,
    onOpenTitle: (SearchItem) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val title = state.title ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(440.dp)) {
                AsyncImage(
                    model = title.poster,
                    contentDescription = title.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.45f),
                                NkiriBackground
                            )
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(10.dp).align(Alignment.TopStart)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NkiriText
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(18.dp)
                ) {
                    Text(
                        title.title,
                        color = NkiriText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    MetadataLine(
                        listOfNotNull(
                            title.year?.toString(),
                            title.rating?.let { "★ $it" },
                            title.type.replaceFirstChar { it.uppercase() },
                            title.genre.takeIf { it.isNotBlank() },
                            title.country?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val firstDirect = state.sources.firstOrNull { !it.external && !it.url.isNullOrBlank() }
                    PremiumButton(
                        text = "Play",
                        onClick = { firstDirect?.let(onPlay) },
                        icon = Icons.Default.PlayArrow,
                        enabled = firstDirect != null || title.type == "series",
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onFavorite,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = NkiriShapes.medium,
                        border = BorderStroke(1.dp, NkiriOutline)
                    ) {
                        Icon(
                            if (state.isCurrentFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (state.isCurrentFavorite) NkiriViolet else NkiriText
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isCurrentFavorite) "Saved" else "Library", color = NkiriText)
                    }
                }

                title.trailer?.takeIf { it.isNotBlank() }?.let { trailer ->
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onOpenUrl(trailer) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, NkiriOutline),
                        shape = NkiriShapes.medium
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = NkiriViolet)
                        Spacer(Modifier.width(6.dp))
                        Text("Watch trailer", color = NkiriText)
                    }
                }

                if (title.description.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(title.description, color = NkiriMuted, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (title.type == "series") {
            item { SectionHeader("Seasons", "Choose an episode or save a full season") }

            if (state.loading && title.seasons.isEmpty()) {
                items(3) {
                    LoadingSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        height = 78
                    )
                }
            }
            items(title.seasons, key = { it.season }) { season ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = NkiriCard,
                    shape = NkiriShapes.medium,
                    border = BorderStroke(1.dp, NkiriOutline)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Season ${season.season}", color = NkiriText, fontWeight = FontWeight.Bold)
                                Text("${season.maxEp} episodes", color = NkiriMuted)
                            }
                            PremiumButton("Episodes", { onSeason(season.season) })
                        }
                        TextButton(onClick = { onDownloadSeason(season.season) }) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = NkiriViolet)
                            Spacer(Modifier.width(6.dp))
                            Text("Download season", color = NkiriViolet)
                        }
                    }
                }
            }
        } else {
            item { SectionHeader("Watch or download", "Choose a source and quality") }

            if (state.loading && state.sources.isEmpty()) {
                items(2) {
                    LoadingSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        height = 126
                    )
                }
            }

            items(state.sources.sortedByDescending { it.quality }) { source ->
                SourceCard(source, onPlay, onDownload)
            }
        }

        if (state.recommendations.isNotEmpty()) {
            item {
                MovieRail(
                    title = "More like this",
                    items = state.recommendations,
                    onOpen = onOpenTitle
                )
            }
        }
    }
}

@Composable
fun EpisodesScreen(
    state: UiState,
    onBack: () -> Unit,
    onEpisode: (EpisodeItem) -> Unit,
    onDownloadSeason: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NkiriText)
                }
                Column(Modifier.weight(1f)) {
                    Text("Episodes", color = NkiriText, style = MaterialTheme.typography.headlineSmall)
                    state.season?.let { Text("Season $it", color = NkiriMuted) }
                }
            }
        }

        state.season?.let { season ->
            item {
                OutlinedButton(
                    onClick = { onDownloadSeason(season) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, NkiriOutline)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = NkiriViolet)
                    Spacer(Modifier.width(8.dp))
                    Text("Download entire season", color = NkiriViolet)
                }
            }
        }

        if (state.loading && state.episodes.isEmpty()) {
            items(4) {
                LoadingSkeleton(
                    modifier = Modifier.fillMaxWidth(),
                    height = 72
                )
            }
        }

        items(state.episodes) { episode ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEpisode(episode) },
                color = NkiriCard,
                shape = NkiriShapes.medium,
                border = BorderStroke(1.dp, NkiriOutline)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NkiriViolet)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(episode.label, color = NkiriText, fontWeight = FontWeight.Bold)
                        Text("Choose quality", color = NkiriMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun QualityScreen(
    state: UiState,
    onBack: () -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NkiriText)
                }
                Column {
                    Text(
                        state.episode?.label ?: "Choose quality",
                        color = NkiriText,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text("Play now or save offline", color = NkiriMuted)
                }
            }
        }

        if (state.loading && state.sources.isEmpty()) {
            items(3) {
                LoadingSkeleton(
                    modifier = Modifier.fillMaxWidth(),
                    height = 126
                )
            }
        }

        items(state.sources.sortedByDescending { it.quality }) { source ->
            SourceCard(source, onPlay, onDownload)
        }
    }
}

@Composable
private fun SourceCard(
    source: SourceItem,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = NkiriCard,
        shape = NkiriShapes.medium,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                if (source.quality > 0) "${source.quality}p" else "Source",
                color = NkiriViolet,
                fontWeight = FontWeight.Black
            )
            MetadataLine(
                listOf(
                    source.format.uppercase().takeIf { it.isNotBlank() } ?: "",
                    source.sizeText.takeIf { it.isNotBlank() } ?: "",
                    if (source.external) "External provider page" else "Direct source"
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumButton(
                    text = "Play",
                    onClick = { onPlay(source) },
                    icon = Icons.Default.PlayArrow,
                    enabled = !source.external,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { onDownload(source) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, NkiriOutline),
                    shape = NkiriShapes.medium
                ) {
                    Icon(
                        if (source.external) Icons.Default.OpenInNew else Icons.Default.Download,
                        contentDescription = null,
                        tint = NkiriText
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (source.external) "Provider" else "Download", color = NkiriText)
                }
            }
        }
    }
}
