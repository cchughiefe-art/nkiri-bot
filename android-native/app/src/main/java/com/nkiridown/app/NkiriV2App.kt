package com.nkiridown.app

import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun NkiriV2App(
    viewModel: MainViewModel,
    onDownload: (SourceItem) -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownloadSeason: (Int) -> Unit,
    onResumePlayback: (PlaybackRecord) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.detailScreen != DetailScreen.NONE) {
        viewModel.back()
    }

    Scaffold(
        containerColor = NkiriBg,
        contentColor = NkiriText,
        bottomBar = {
            if (state.detailScreen == DetailScreen.NONE) {
                NavigationBar(
                    containerColor = Color(0xFF090C0D),
                    tonalElevation = 0.dp
                ) {
                    navItem("⌂", "Home", RootTab.HOME, state.rootTab, viewModel::selectTab)
                    navItem("♡", "Library", RootTab.FAVORITES, state.rootTab, viewModel::selectTab)
                    navItem("↓", "Downloads", RootTab.DOWNLOADS, state.rootTab, viewModel::selectTab)
                    navItem("↻", "History", RootTab.HISTORY, state.rootTab, viewModel::selectTab)
                    navItem("⚙", "Settings", RootTab.SETTINGS, state.rootTab, viewModel::selectTab)
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NkiriBg)
        ) {
            when (state.detailScreen) {
                DetailScreen.TITLE -> titleScreen(
                    state, viewModel::back, viewModel::toggleFavorite,
                    viewModel::openSeason, onPlay, onDownload,
                    onDownloadSeason, viewModel::openSearchItem, onOpenUrl
                )
                DetailScreen.EPISODES -> episodesScreen(
                    state, viewModel::back, viewModel::openEpisode, onDownloadSeason
                )
                DetailScreen.QUALITIES -> qualityScreen(
                    state, viewModel::back, onPlay, onDownload
                )
                DetailScreen.NONE -> when (state.rootTab) {
                    RootTab.HOME -> homeScreen(
                        state, viewModel::setQuery, { viewModel.search() },
                        viewModel::loadLatest, viewModel::openSearchItem, onResumePlayback
                    )
                    RootTab.FAVORITES -> libraryScreen(state.favorites, viewModel::openFavorite)
                    RootTab.DOWNLOADS -> downloadsScreen(state.downloads, viewModel::clearDownloads)
                    RootTab.HISTORY -> historyScreen(state.history, onResumePlayback, viewModel::clearHistory)
                    RootTab.SETTINGS -> settingsScreen(state, onOpenUrl, onShare)
                }
            }

            state.error?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    color = Color(0xFF3A1719),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(it, color = Color(0xFFFFC7C7), modifier = Modifier.padding(14.dp))
                }
            }

            if (state.loading) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NkiriAccent)
                }
            }
        }
    }
}

@Composable
private fun RowScope.navItem(
    symbol: String,
    label: String,
    tab: RootTab,
    selected: RootTab,
    onSelect: (RootTab) -> Unit
) {
    NavigationBarItem(
        selected = selected == tab,
        onClick = { onSelect(tab) },
        icon = { Text(symbol, color = if (selected == tab) NkiriAccent else NkiriMuted) },
        label = { Text(label, color = if (selected == tab) NkiriText else NkiriMuted) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = NkiriAccentSoft,
            selectedIconColor = NkiriAccent,
            selectedTextColor = NkiriText,
            unselectedIconColor = NkiriMuted,
            unselectedTextColor = NkiriMuted
        )
    )
}

@Composable
private fun homeScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLatest: (String, String) -> Unit,
    onOpenTitle: (SearchItem) -> Unit,
    onResume: (PlaybackRecord) -> Unit
) {
    val featured = state.results.firstOrNull()
    val rest = if (featured != null) state.results.drop(1) else state.results

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TheNkiri", color = NkiriText, style = MaterialTheme.typography.headlineMedium)
                        Text("Download less. Watch more.", color = NkiriMuted)
                    }
                    Surface(
                        color = if (state.apiStatus.contains("Online")) Color(0xFF103A2E) else Color(0xFF3A1719),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            if (state.apiStatus.contains("Online")) "● LIVE" else "● OFFLINE",
                            color = if (state.apiStatus.contains("Online")) NkiriAccent else NkiriDanger,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search movies, series, K-Drama…", color = NkiriMuted) },
                    leadingIcon = { Text("⌕", color = NkiriMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NkiriText,
                        unfocusedTextColor = NkiriText,
                        cursorColor = NkiriAccent,
                        focusedBorderColor = NkiriAccent,
                        unfocusedBorderColor = NkiriOutline,
                        focusedContainerColor = NkiriSurface,
                        unfocusedContainerColor = NkiriSurface
                    )
                )

                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { browseChip("Discover") { onLatest("all", "Discover") } }
                    item { browseChip("Movies") { onLatest("movie", "Movies") } }
                    item { browseChip("Series") { onLatest("series", "Series") } }
                    item { browseChip("K-Drama") { onLatest("drama", "K-Drama") } }
                }
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item {
                sectionHeader("Continue Watching", "Pick up where you stopped")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.continueWatching.take(10)) { item ->
                        continueCard(item) { onResume(item) }
                    }
                }
            }
        }

        if (featured != null) {
            item { heroCard(featured) { onOpenTitle(featured) } }
        }

        item { sectionHeader(state.sectionTitle.ifBlank { "Discover" }, "${state.results.size} titles") }

        items(rest.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { item ->
                    posterCard(item, Modifier.weight(1f)) { onOpenTitle(item) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (state.results.isEmpty() && !state.loading) {
            item { emptyState("Nothing here yet", "Try another search or category.") }
        }
    }
}

@Composable
private fun browseChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = NkiriSurfaceHigh,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Text(
            label,
            color = NkiriText,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun heroCard(item: SearchItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(CardShape)
            .clickable(onClick = onClick)
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
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), NkiriBg)
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
        ) {
            Surface(color = NkiriAccent, shape = RoundedCornerShape(50)) {
                Text(
                    if (item.type == "series") "SERIES" else "FEATURED",
                    color = Color(0xFF002117),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                item.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    item.year?.toString(),
                    item.rating?.let { "★ $it" },
                    item.genre.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                color = Color(0xFFD6DDDA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NkiriAccent,
                    contentColor = Color(0xFF002117)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("View details", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun continueCard(item: PlaybackRecord, onClick: () -> Unit) {
    Column(
        Modifier
            .width(210.dp)
            .clip(CardShape)
            .background(NkiriSurface)
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().height(118.dp),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.align(Alignment.Center).size(44.dp),
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("▶", color = NkiriText) }
            }
        }
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth(),
            color = NkiriAccent,
            trackColor = NkiriSurfaceHigh
        )
        Text(
            item.title,
            color = NkiriText,
            modifier = Modifier.padding(12.dp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun posterCard(item: SearchItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(CardShape)
            .background(NkiriSurface)
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
                contentScale = ContentScale.Crop
            )
            item.rating?.let {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "★ $it",
                        color = Color(0xFFFFD66B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(11.dp)) {
            Text(
                item.title,
                color = NkiriText,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(item.year?.toString(), if (item.type == "series") "Series" else "Movie")
                    .joinToString(" • "),
                color = NkiriMuted
            )
        }
    }
}

@Composable
private fun titleScreen(
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(430.dp)) {
                AsyncImage(
                    model = title.poster,
                    contentDescription = title.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.42f), NkiriBg)
                        )
                    )
                )
                Surface(
                    modifier = Modifier.padding(14.dp).size(46.dp).clickable(onClick = onBack),
                    color = Color.Black.copy(alpha = 0.66f),
                    shape = RoundedCornerShape(50)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = NkiriText, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(title.title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        listOfNotNull(
                            title.year?.toString(),
                            title.rating?.let { "★ $it" },
                            title.genre.takeIf { it.isNotBlank() },
                            title.country
                        ).joinToString(" • "),
                        color = Color(0xFFD7DFDC)
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onFavorite,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isCurrentFavorite) NkiriAccentSoft else NkiriSurfaceHigh,
                            contentColor = if (state.isCurrentFavorite) NkiriAccent else NkiriText
                        )
                    ) {
                        Text(if (state.isCurrentFavorite) "♥ Saved" else "♡ Save")
                    }
                    title.trailer?.takeIf { it.isNotBlank() }?.let { trailer ->
                        OutlinedButton(
                            onClick = { onOpenUrl(trailer) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NkiriOutline)
                        ) {
                            Text("Trailer", color = NkiriText)
                        }
                    }
                }

                if (title.description.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(title.description, color = NkiriMuted, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (title.type == "series") {
            item { sectionHeader("Seasons", "Choose what to watch") }
            items(title.seasons, key = { it.season }) { season ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                    color = NkiriSurface,
                    shape = CardShape,
                    border = BorderStroke(1.dp, NkiriOutline)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Season ${season.season}", color = NkiriText, fontWeight = FontWeight.Bold)
                                Text("${season.maxEp} episodes", color = NkiriMuted)
                            }
                            Button(
                                onClick = { onSeason(season.season) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NkiriAccent,
                                    contentColor = Color(0xFF002117)
                                )
                            ) { Text("Episodes") }
                        }
                        TextButton(onClick = { onDownloadSeason(season.season) }) {
                            Text("↓ Download full season", color = NkiriAccent)
                        }
                    }
                }
            }
        } else {
            item { sectionHeader("Watch or download", "Choose your quality") }
            items(state.sources.sortedByDescending { it.quality }) { source ->
                sourceCard(source, { onPlay(source) }, { onDownload(source) })
            }
        }

        if (state.recommendations.isNotEmpty()) {
            item {
                sectionHeader("More like this", "You may also like")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.recommendations, key = { it.id }) { item ->
                        posterCard(item, Modifier.width(160.dp)) { onOpenTitle(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun episodesScreen(
    state: UiState,
    onBack: () -> Unit,
    onEpisode: (EpisodeItem) -> Unit,
    onDownloadSeason: (Int) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { screenHeader("Episodes", state.season?.let { "Season $it" }, onBack) }

        state.season?.let { season ->
            item {
                OutlinedButton(
                    onClick = { onDownloadSeason(season) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, NkiriOutline)
                ) {
                    Text("↓ Download entire season", color = NkiriAccent)
                }
            }
        }

        items(state.episodes) { episode ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEpisode(episode) },
                color = NkiriSurface,
                shape = CardShape,
                border = BorderStroke(1.dp, NkiriOutline)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = NkiriAccentSoft, shape = RoundedCornerShape(12.dp)) {
                        Text("▶", color = NkiriAccent, modifier = Modifier.padding(12.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(episode.label, color = NkiriText, fontWeight = FontWeight.Bold)
                        Text("Choose quality", color = NkiriMuted)
                    }
                    Text("›", color = NkiriMuted)
                }
            }
        }
    }
}

@Composable
private fun qualityScreen(
    state: UiState,
    onBack: () -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            screenHeader(
                state.episode?.label ?: "Choose quality",
                "Play now or save offline",
                onBack
            )
        }
        items(state.sources.sortedByDescending { it.quality }) { source ->
            sourceCard(source, { onPlay(source) }, { onDownload(source) })
        }
    }
}

@Composable
private fun sourceCard(source: SourceItem, onPlay: () -> Unit, onDownload: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        color = NkiriSurface,
        shape = CardShape,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = NkiriAccentSoft, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (source.quality > 0) "${source.quality}p" else "Source",
                        color = NkiriAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Black
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        listOfNotNull(
                            source.format.uppercase().takeIf { it.isNotBlank() },
                            source.sizeText.takeIf { it.isNotBlank() }
                        ).joinToString(" • ").ifBlank { "Ready" },
                        color = NkiriText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(if (source.external) "Provider page" else "Direct source", color = NkiriMuted)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    enabled = !source.external,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NkiriAccent,
                        contentColor = Color(0xFF002117)
                    )
                ) {
                    Text("▶ Play", fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, NkiriOutline)
                ) {
                    Text("↓ Download", color = NkiriText)
                }
            }
        }
    }
}

@Composable
private fun libraryScreen(items: List<FavoriteItem>, onOpen: (FavoriteItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
        item { pageTitle("My Library", "Saved movies and series") }
        if (items.isEmpty()) item { emptyState("Your library is empty", "Save titles and they will appear here.") }
        items(items) { item ->
            mediaListCard(
                item.poster,
                item.title,
                if (item.type == "series") "Series" else "Movie"
            ) { onOpen(item) }
        }
    }
}

@Composable
private fun downloadsScreen(downloads: List<DownloadRecord>, onClear: () -> Unit) {
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf<Map<Long, DownloadSnapshot>>(emptyMap()) }

    LaunchedEffect(downloads) {
        while (true) {
            snapshots = downloads.mapNotNull { record ->
                queryDownloadSnapshot(context, record.downloadId)?.let { record.downloadId to it }
            }.toMap()
            delay(1000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    pageTitle("Downloads", "Watch offline inside TheNkiri")
                }
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear", color = NkiriMuted) }
                }
            }
        }

        if (downloads.isEmpty()) {
            item { emptyState("No downloads yet", "Downloaded movies and episodes will appear here.") }
        }

        items(downloads, key = { it.downloadId }) { record ->
            val snap = snapshots[record.downloadId]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NkiriSurface,
                shape = CardShape,
                border = BorderStroke(1.dp, NkiriOutline)
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text(record.title, color = NkiriText, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            record.episodeLabel,
                            record.quality.takeIf { it > 0 }?.let { "${it}p" },
                            record.sizeText.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        color = NkiriMuted
                    )

                    snap?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            it.label,
                            color = when (it.status) {
                                DownloadManager.STATUS_SUCCESSFUL -> NkiriAccent
                                DownloadManager.STATUS_FAILED -> NkiriDanger
                                else -> NkiriMuted
                            },
                            fontWeight = FontWeight.Bold
                        )

                        if (it.status == DownloadManager.STATUS_RUNNING ||
                            it.status == DownloadManager.STATUS_PENDING
                        ) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { it.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = NkiriAccent,
                                trackColor = NkiriSurfaceHigh
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        if (it.status == DownloadManager.STATUS_SUCCESSFUL) {
                            Button(
                                onClick = {
                                    runCatching { openCompletedDownload(context, record) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NkiriAccent,
                                    contentColor = Color(0xFF002117)
                                )
                            ) {
                                Text("▶ Watch in TheNkiri", fontWeight = FontWeight.Black)
                            }
                        } else if (
                            it.status == DownloadManager.STATUS_RUNNING ||
                            it.status == DownloadManager.STATUS_PENDING ||
                            it.status == DownloadManager.STATUS_PAUSED
                        ) {
                            OutlinedButton(
                                onClick = { cancelDownload(context, record.downloadId) },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, NkiriOutline)
                            ) {
                                Text("Cancel download", color = NkiriDanger)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun historyScreen(
    history: List<PlaybackRecord>,
    onResume: (PlaybackRecord) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { pageTitle("History", "Recently watched") }
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear", color = NkiriMuted) }
                }
            }
        }
        if (history.isEmpty()) item { emptyState("No watch history", "Anything you watch will appear here.") }
        items(history) { item ->
            mediaListCard(
                item.poster,
                item.title,
                listOfNotNull(
                    item.episodeLabel,
                    if (item.completed) "Completed" else "Resume ${(item.progress * 100).toInt()}%"
                ).joinToString(" • "),
                item.progress.takeIf { !item.completed }
            ) { onResume(item) }
        }
    }
}

@Composable
private fun settingsScreen(state: UiState, onOpenUrl: (String) -> Unit, onShare: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { pageTitle("Settings", "TheNkiri ${BuildConfig.VERSION_NAME}") }

        if (state.updateAvailable && !state.updateUrl.isNullOrBlank()) {
            item {
                Surface(color = NkiriAccentSoft, shape = CardShape) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Update available", color = NkiriText, fontWeight = FontWeight.Bold)
                        Text("Version ${state.latestVersionName ?: "new"} is ready.", color = NkiriMuted)
                        Button(
                            onClick = { onOpenUrl(state.updateUrl!!) },
                            modifier = Modifier.padding(top = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NkiriAccent,
                                contentColor = Color(0xFF002117)
                            )
                        ) { Text("Update TheNkiri") }
                    }
                }
            }
        }

        state.remoteNotice?.takeIf { it.isNotBlank() }?.let { notice ->
            item { infoCard("Notice", notice, NkiriAccent) }
        }

        item {
            infoCard(
                "Server",
                state.apiStatus,
                if (state.apiStatus.contains("Online")) NkiriAccent else NkiriDanger
            )
        }

        item { settingsAction("Telegram updates", "News and announcements") { onOpenUrl(state.channelUrl) } }
        item { settingsAction("TheNkiri bot", "Open the Telegram bot") { onOpenUrl(state.botUrl) } }
        item { settingsAction("Share TheNkiri", "Send the app to someone") { onShare() } }
    }
}

@Composable
private fun settingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = NkiriSurface,
        shape = CardShape,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = NkiriText, fontWeight = FontWeight.Bold)
                Text(subtitle, color = NkiriMuted)
            }
            Text("›", color = NkiriMuted)
        }
    }
}

@Composable
private fun infoCard(title: String, body: String, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NkiriSurface,
        shape = CardShape,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(50)).background(accent)
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = NkiriText, fontWeight = FontWeight.Bold)
                Text(body, color = NkiriMuted)
            }
        }
    }
}

@Composable
private fun mediaListCard(
    poster: String?,
    title: String,
    subtitle: String,
    progress: Float? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        color = NkiriSurface,
        shape = CardShape,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = poster,
                contentDescription = title,
                modifier = Modifier.width(66.dp).height(92.dp).clip(RoundedCornerShape(13.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(title, color = NkiriText, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(subtitle, color = NkiriMuted)
                progress?.let {
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth(),
                        color = NkiriAccent,
                        trackColor = NkiriSurfaceHigh
                    )
                }
            }
            Text("›", color = NkiriMuted)
        }
    }
}

@Composable
private fun sectionHeader(title: String, subtitle: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
    ) {
        Text(title, color = NkiriText, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, color = NkiriMuted) }
    }
}

@Composable
private fun pageTitle(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(title, color = NkiriText, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = NkiriMuted)
    }
}

@Composable
private fun screenHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp).clickable(onClick = onBack),
            color = NkiriSurfaceHigh,
            shape = RoundedCornerShape(50)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", color = NkiriText, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = NkiriText, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, color = NkiriMuted) }
        }
    }
}

@Composable
private fun emptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            color = NkiriSurfaceHigh,
            shape = RoundedCornerShape(50)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("N", color = NkiriAccent, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = NkiriText, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = NkiriMuted, modifier = Modifier.padding(top = 5.dp))
    }
}
