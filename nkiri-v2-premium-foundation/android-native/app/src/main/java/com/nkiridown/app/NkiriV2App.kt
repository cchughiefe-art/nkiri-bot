package com.nkiridown.app

import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val V2Bg = Color(0xFF07080A)
private val V2Card = Color(0xFF14161A)
private val V2Card2 = Color(0xFF1B1E24)
private val V2Red = Color(0xFFE50914)
private val V2Text2 = Color(0xFFB8BDC7)

@Composable
fun NkiriV2App(
    viewModel: MainViewModel,
    onDownload: (SourceItem) -> Unit,
    onPlay: (SourceItem) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.detailScreen != DetailScreen.NONE) { viewModel.back() }

    Scaffold(
        containerColor = V2Bg,
        topBar = { V2TopBar(state, viewModel::back) },
        bottomBar = {
            if (state.detailScreen == DetailScreen.NONE) {
                V2BottomBar(state.rootTab, viewModel::selectTab)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(V2Bg)) {
            when (state.detailScreen) {
                DetailScreen.TITLE -> V2TitleScreen(state, viewModel::openSeason, viewModel::toggleFavorite, onPlay, onDownload, onOpenUrl)
                DetailScreen.EPISODES -> V2EpisodesScreen(state, viewModel::openEpisode)
                DetailScreen.QUALITIES -> V2QualityScreen(state, onPlay, onDownload)
                DetailScreen.NONE -> when (state.rootTab) {
                    RootTab.HOME -> V2Home(state, viewModel::setQuery, { viewModel.search() }, viewModel::loadLatest, viewModel::openSearchItem)
                    RootTab.FAVORITES -> V2Library(state.favorites, viewModel::openFavorite)
                    RootTab.DOWNLOADS -> V2Downloads(state.downloads, viewModel::clearDownloads)
                    RootTab.SETTINGS -> V2Settings(state, onOpenUrl, onShare)
                }
            }

            state.error?.let { error ->
                Text(
                    error,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xFF451A1A)).padding(10.dp),
                    color = Color(0xFFFECACA)
                )
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = V2Red)
                }
            }
        }
    }
}

@Composable
private fun V2TopBar(state: UiState, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).background(V2Bg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.detailScreen != DetailScreen.NONE) {
            TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        } else {
            Box(Modifier.size(34.dp).background(V2Red, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("N", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            when (state.detailScreen) {
                DetailScreen.NONE -> "TheNkiri"
                DetailScreen.TITLE -> state.title?.title ?: "Title"
                DetailScreen.EPISODES -> "Episodes"
                DetailScreen.QUALITIES -> "Choose quality"
            },
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (state.apiStatus.contains("Online")) "● LIVE" else "● OFFLINE",
            color = if (state.apiStatus.contains("Online")) Color(0xFF45D483) else Color(0xFFF87171),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun V2Home(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLatest: (String, String) -> Unit,
    onOpenTitle: (SearchItem) -> Unit
) {
    val featured = state.results.firstOrNull()
    val remaining = state.results.drop(1)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search movies, series, K-Drama…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() })
                )
                LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { V2Chip("Discover") { onLatest("all", "Discover") } }
                    item { V2Chip("Movies") { onLatest("movie", "Movies") } }
                    item { V2Chip("Series") { onLatest("series", "Series") } }
                    item { V2Chip("K-Drama") { onLatest("drama", "K-Drama") } }
                }
            }
        }

        if (featured != null) {
            item {
                Box(Modifier.fillMaxWidth().height(330.dp).padding(top = 12.dp).clickable { onOpenTitle(featured) }) {
                    AsyncImage(featured.poster, featured.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, V2Bg.copy(alpha = 0.15f), V2Bg))))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text(featured.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2)
                        Text(listOfNotNull(featured.year?.toString(), featured.rating?.let { "★ $it" }, if (featured.type == "series") "Series" else "Movie").joinToString(" • "), color = V2Text2)
                        Button(onClick = { onOpenTitle(featured) }, colors = ButtonDefaults.buttonColors(containerColor = V2Red), modifier = Modifier.padding(top = 10.dp)) { Text("View details") }
                    }
                }
            }
        }

        item { Text(state.sectionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(14.dp)) }

        items(remaining.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> V2Poster(item, Modifier.weight(1f)) { onOpenTitle(item) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun V2Chip(text: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(text, fontWeight = FontWeight.SemiBold) })
}

@Composable
private fun V2Poster(item: SearchItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(V2Card).clickable(onClick = onClick).padding(bottom = 10.dp)) {
        AsyncImage(item.poster, item.title, Modifier.fillMaxWidth().aspectRatio(0.68f), contentScale = ContentScale.Crop)
        Text(item.title, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(item.year?.toString(), item.rating?.let { "★ $it" }).joinToString(" • "), Modifier.padding(horizontal = 10.dp), color = V2Text2, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun V2TitleScreen(
    state: UiState,
    onSeason: (Int) -> Unit,
    onFavorite: () -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val title = state.title ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(390.dp)) {
                AsyncImage(title.poster, title.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, V2Bg.copy(alpha = 0.2f), V2Bg))))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(title.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(listOfNotNull(title.year?.toString(), title.rating?.let { "★ $it" }, title.country, if (title.type == "series") "Series" else "Movie").joinToString(" • "), color = V2Text2, modifier = Modifier.padding(top = 5.dp))
                if (title.description.isNotBlank()) Text(title.description, Modifier.padding(top = 14.dp), color = Color(0xFFE5E7EB))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onFavorite, modifier = Modifier.weight(1f)) { Text(if (state.isCurrentFavorite) "★ Saved" else "☆ Save") }
                    title.trailer?.let { trailer -> OutlinedButton(onClick = { onOpenUrl(trailer) }, modifier = Modifier.weight(1f)) { Text("Trailer") } }
                }
                Text(if (title.type == "series") "Seasons" else "Play & Download", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            }
        }

        if (title.type == "series") {
            items(title.seasons, key = { it.season }) { season ->
                Button(onClick = { onSeason(season.season) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = V2Card2)) {
                    Text("Season ${season.season} • ${season.maxEp} episodes")
                }
            }
        } else {
            items(state.sources.sortedByDescending { it.quality }, key = { "${it.quality}:${it.url}:${it.size}" }) { source ->
                V2SourceRow(source, { onPlay(source) }, { onDownload(source) })
            }
        }
    }
}

@Composable
private fun V2EpisodesScreen(state: UiState, onEpisode: (EpisodeItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.episodes) { episode ->
            Row(Modifier.fillMaxWidth().background(V2Card, RoundedCornerShape(14.dp)).clickable { onEpisode(episode) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(episode.label, fontWeight = FontWeight.Bold)
                    Text("Tap to choose quality", color = V2Text2, style = MaterialTheme.typography.labelMedium)
                }
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun V2QualityScreen(state: UiState, onPlay: (SourceItem) -> Unit, onDownload: (SourceItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(state.episode?.label ?: "Episode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        items(state.sources.sortedByDescending { it.quality }) { source -> V2SourceRow(source, { onPlay(source) }, { onDownload(source) }) }
    }
}

@Composable
private fun V2SourceRow(source: SourceItem, onPlay: () -> Unit, onDownload: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp).background(V2Card, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (source.quality > 0) "${source.quality}p" else "Source", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(listOfNotNull(source.sizeText.takeIf { it.isNotBlank() }, source.format.uppercase().takeIf { it.isNotBlank() }).joinToString(" • "), color = V2Text2)
            }
            if (source.external) AssistChip(onClick = {}, label = { Text("External") })
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = V2Red)) { Text("▶ Play") }
            OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f)) { Text("↓ Download") }
        }
    }
}

@Composable
private fun V2Library(favorites: List<FavoriteItem>, onOpen: (FavoriteItem) -> Unit) {
    if (favorites.isEmpty()) { V2Empty("Your library is empty", "Save movies and series and they will appear here."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item { Text("My Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(4.dp, 8.dp)) }
        items(favorites) { favorite ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(V2Card, RoundedCornerShape(14.dp)).clickable { onOpen(favorite) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(favorite.poster, favorite.title, Modifier.width(66.dp).height(96.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(favorite.title, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(if (favorite.type == "series") "Series" else "Movie", color = V2Text2)
                }
                Text("›")
            }
        }
    }
}

@Composable
private fun V2Downloads(downloads: List<DownloadRecord>, onClear: () -> Unit) {
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf<Map<Long, DownloadSnapshot>>(emptyMap()) }

    LaunchedEffect(downloads) {
        while (true) {
            snapshots = downloads.mapNotNull { r -> queryDownloadSnapshot(context, r.downloadId)?.let { r.downloadId to it } }.toMap()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Managed inside TheNkiri", color = V2Text2)
            }
            if (downloads.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear history") }
        }

        if (downloads.isEmpty()) {
            V2Empty("Nothing downloading", "Your offline movies and episodes will show here.")
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(downloads, key = { it.downloadId }) { record ->
                    val snap = snapshots[record.downloadId]
                    Column(Modifier.fillMaxWidth().background(V2Card, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Text(record.title, fontWeight = FontWeight.Black)
                        Text(listOfNotNull(record.episodeLabel, record.quality.takeIf { it > 0 }?.let { "${it}p" }, record.sizeText.takeIf { it.isNotBlank() }).joinToString(" • "), color = V2Text2, modifier = Modifier.padding(top = 3.dp))
                        if (snap != null) {
                            Text(snap.label, color = if (snap.status == DownloadManager.STATUS_FAILED) Color(0xFFF87171) else Color(0xFF86EFAC), modifier = Modifier.padding(top = 8.dp))
                            if (snap.status == DownloadManager.STATUS_RUNNING || snap.status == DownloadManager.STATUS_PENDING) {
                                LinearProgressIndicator(progress = { snap.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                Text("${formatBytes(snap.downloadedBytes)} / ${formatBytes(snap.totalBytes)}", color = V2Text2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                            }
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (snap.status == DownloadManager.STATUS_SUCCESSFUL) {
                                    Button(onClick = { runCatching { openCompletedDownload(context, record.downloadId) } }, modifier = Modifier.weight(1f)) { Text("▶ Play") }
                                } else if (snap.status == DownloadManager.STATUS_RUNNING || snap.status == DownloadManager.STATUS_PENDING || snap.status == DownloadManager.STATUS_PAUSED) {
                                    OutlinedButton(onClick = { cancelDownload(context, record.downloadId) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                }
                            }
                        } else {
                            Text("Status unavailable", color = V2Text2, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V2Settings(state: UiState, onOpenUrl: (String) -> Unit, onShare: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
        item { V2SettingCard("Connection") { Text(state.apiStatus, color = V2Text2); Text("Connected to TheNkiri cloud API", color = V2Text2, style = MaterialTheme.typography.labelMedium) } }
        item {
            V2SettingCard("TheNkiri") {
                V2SettingButton("Updates channel") { onOpenUrl(state.channelUrl) }
                V2SettingButton("Telegram bot") { onOpenUrl(state.botUrl) }
                V2SettingButton("Share TheNkiri") { onShare() }
            }
        }
        item { V2SettingCard("Playback") { Text("Built-in player • Picture-in-Picture • offline playback", color = V2Text2) } }
        item { Text("TheNkiri ${BuildConfig.VERSION_NAME}", color = V2Text2, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun V2SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(V2Card, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(title, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun V2SettingButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(text) }
}

@Composable
private fun V2BottomBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0D0F12)) {
        NavigationBarItem(selected == RootTab.HOME, { onSelect(RootTab.HOME) }, { Text("⌂") }, label = { Text("Home") })
        NavigationBarItem(selected == RootTab.FAVORITES, { onSelect(RootTab.FAVORITES) }, { Text("★") }, label = { Text("Library") })
        NavigationBarItem(selected == RootTab.DOWNLOADS, { onSelect(RootTab.DOWNLOADS) }, { Text("↓") }, label = { Text("Downloads") })
        NavigationBarItem(selected == RootTab.SETTINGS, { onSelect(RootTab.SETTINGS) }, { Text("⚙") }, label = { Text("Settings") })
    }
}

@Composable
private fun V2Empty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(body, color = V2Text2, modifier = Modifier.padding(top = 7.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
}
