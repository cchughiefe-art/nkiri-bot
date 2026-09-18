package com.nkiridown.app.ui.screens.you

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nkiridown.app.*
import com.nkiridown.app.ui.app.YouSection
import com.nkiridown.app.ui.components.EmptyState
import com.nkiridown.app.ui.components.MetadataLine
import com.nkiridown.app.ui.components.SectionHeader
import com.nkiridown.app.ui.theme.*

@Composable
fun YouScreen(
    state: UiState,
    section: YouSection,
    dataSaver: Boolean,
    onSection: (YouSection) -> Unit,
    onDataSaver: (Boolean) -> Unit,
    onOpenFavorite: (FavoriteItem) -> Unit,
    onResume: (PlaybackRecord) -> Unit,
    onClearHistory: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit
) {
    when (section) {
        YouSection.OVERVIEW ->
            Overview(state, dataSaver, onSection, onDataSaver, onOpenUrl, onShare)

        YouSection.LIBRARY ->
            Library(state.favorites, onSection, onOpenFavorite)

        YouSection.HISTORY ->
            History(state.history, onSection, onResume, onClearHistory)

        YouSection.SETTINGS ->
            Settings(state, dataSaver, onSection, onDataSaver, onOpenUrl, onShare)
    }
}

@Composable
private fun Overview(
    state: UiState,
    dataSaver: Boolean,
    onSection: (YouSection) -> Unit,
    onDataSaver: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("You", color = NkiriText, style = MaterialTheme.typography.headlineMedium)
            Text("Your library, history and app controls", color = NkiriMuted)
        }

        item { HubAction("My Library", "${state.favorites.size} saved", Icons.Default.Favorite) { onSection(YouSection.LIBRARY) } }
        item { HubAction("Watch History", "${state.history.size} watched", Icons.Default.History) { onSection(YouSection.HISTORY) } }

        if (state.continueWatching.isNotEmpty()) {
            item {
                HubAction(
                    "Continue Watching",
                    "${state.continueWatching.size} unfinished",
                    Icons.Default.PlayArrow
                ) { onSection(YouSection.HISTORY) }
            }
        }

        item {
            Surface(
                color = NkiriCard,
                shape = NkiriShapes.medium,
                border = BorderStroke(1.dp, NkiriOutline)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DataSaverOn, contentDescription = null, tint = NkiriViolet)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Data Saver", color = NkiriText, fontWeight = FontWeight.Bold)
                        Text("Reduce decorative network work and image preloading", color = NkiriMuted)
                    }
                    Switch(checked = dataSaver, onCheckedChange = onDataSaver)
                }
            }
        }

        item { HubAction("App settings", "Server, updates and privacy", Icons.Default.Settings) { onSection(YouSection.SETTINGS) } }
        item { HubAction("Telegram updates", "News and announcements", Icons.Default.Campaign) { onOpenUrl(state.channelUrl) } }
        item { HubAction("TheNkiri bot", "Open Telegram bot", Icons.Default.Send) { onOpenUrl(state.botUrl) } }
        item { HubAction("Share TheNkiri", "Send the app to someone", Icons.Default.Share) { onShare() } }
    }
}

@Composable
private fun Library(
    items: List<FavoriteItem>,
    onSection: (YouSection) -> Unit,
    onOpen: (FavoriteItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BackHeader("My Library") { onSection(YouSection.OVERVIEW) } }
        if (items.isEmpty()) {
            item { EmptyState("Your library is empty", "Save a movie or series and it will appear here.") }
        }
        items(items) { item ->
            HubAction(
                item.title,
                if (item.type == "series") "Series" else "Movie",
                Icons.Default.Movie
            ) { onOpen(item) }
        }
    }
}

@Composable
private fun History(
    items: List<PlaybackRecord>,
    onSection: (YouSection) -> Unit,
    onResume: (PlaybackRecord) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    BackHeader("Watch History") { onSection(YouSection.OVERVIEW) }
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear", color = NkiriMuted) }
                }
            }
        }

        if (items.isEmpty()) {
            item { EmptyState("No watch history", "Anything you watch appears here.") }
        }

        items(items) { item ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onResume(item) },
                color = NkiriCard,
                shape = NkiriShapes.medium,
                border = BorderStroke(1.dp, NkiriOutline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(item.title, color = NkiriText, fontWeight = FontWeight.Bold)
                    MetadataLine(
                        listOfNotNull(
                            item.episodeLabel,
                            if (item.completed) "Completed" else "Resume ${(item.progress * 100).toInt()}%"
                        )
                    )
                    if (!item.completed) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = NkiriViolet,
                            trackColor = NkiriElevated
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Settings(
    state: UiState,
    dataSaver: Boolean,
    onSection: (YouSection) -> Unit,
    onDataSaver: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BackHeader("Settings") { onSection(YouSection.OVERVIEW) } }

        item {
            InfoCard(
                "API status",
                state.apiStatus,
                if (state.apiStatus.contains("Online")) NkiriSuccess else NkiriDanger
            )
        }

        item {
            Surface(
                color = NkiriCard,
                shape = NkiriShapes.medium,
                border = BorderStroke(1.dp, NkiriOutline)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Data Saver", color = NkiriText, fontWeight = FontWeight.Bold)
                        Text("Stored locally on this device", color = NkiriMuted)
                    }
                    Switch(checked = dataSaver, onCheckedChange = onDataSaver)
                }
            }
        }

        if (state.updateAvailable && !state.updateUrl.isNullOrBlank()) {
            item {
                HubAction(
                    "Update TheNkiri",
                    "Version ${state.latestVersionName ?: "new"} is available",
                    Icons.Default.SystemUpdate
                ) { onOpenUrl(state.updateUrl!!) }
            }
        }

        state.remoteNotice?.takeIf { it.isNotBlank() }?.let { notice ->
            item { InfoCard("Notice", notice, NkiriViolet) }
        }

        item { InfoCard("App version", BuildConfig.VERSION_NAME, NkiriViolet) }
        item { HubAction("Telegram updates", "News and announcements", Icons.Default.Campaign) { onOpenUrl(state.channelUrl) } }
        item { HubAction("TheNkiri bot", "Telegram download bot", Icons.Default.Send) { onOpenUrl(state.botUrl) } }
        item { HubAction("Share app", "Send TheNkiri to someone", Icons.Default.Share) { onShare() } }

        state.lastCrash?.takeIf { it.isNotBlank() }?.let { crash ->
            item { InfoCard("Last local crash", crash.take(600), NkiriDanger) }
        }

        item {
            InfoCard(
                "Privacy",
                "TheNkiri stores favorites, watch history, searches, downloads and app preferences locally on your device unless a connected service explicitly says otherwise.",
                NkiriMuted
            )
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NkiriText)
        }
        Text(title, color = NkiriText, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun HubAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = NkiriCard,
        shape = NkiriShapes.medium,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = NkiriViolet)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = NkiriText, fontWeight = FontWeight.Bold)
                Text(subtitle, color = NkiriMuted)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NkiriMuted)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NkiriCard,
        shape = NkiriShapes.medium,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = NkiriMuted)
        }
    }
}
