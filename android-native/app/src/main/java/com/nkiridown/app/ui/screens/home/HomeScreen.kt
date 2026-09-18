package com.nkiridown.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nkiridown.app.PlaybackRecord
import com.nkiridown.app.SearchItem
import com.nkiridown.app.UiState
import com.nkiridown.app.ui.components.*
import com.nkiridown.app.ui.theme.*

@Composable
fun HomeScreen(
    state: UiState,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTitle: (SearchItem) -> Unit,
    onResume: (PlaybackRecord) -> Unit,
    onLibrary: (SearchItem) -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "TheNkiri",
                        color = NkiriText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text("What are you watching tonight?", color = NkiriMuted)
                }
                if (state.updateAvailable || !state.remoteNotice.isNullOrBlank()) {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Notifications, contentDescription = "Updates", tint = NkiriViolet)
                    }
                }
                IconButton(onClick = onOpenProfile) {
                    Icon(Icons.Default.Person, contentDescription = "You", tint = NkiriText)
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 54.dp)
                    .clickable(onClick = onOpenSearch),
                color = NkiriSurface,
                shape = NkiriShapes.medium,
                border = BorderStroke(1.dp, NkiriOutline)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = NkiriMuted)
                    Spacer(Modifier.width(10.dp))
                    Text("Search movies, series and K-Drama", color = NkiriMuted)
                }
            }
        }

        if (state.loading && state.homeSections.isEmpty()) {
            item {
                LoadingSkeleton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    height = 380
                )
            }
        }

        if (state.apiStatus.contains("offline", ignoreCase = true) && state.homeSections.isEmpty()) {
            item {
                ErrorState(
                    "You're offline",
                    "TheNkiri will reconnect automatically. Downloads already saved remain available.",
                    onRetry
                )
            }
        }

        val featured =
            state.homeSections.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
                ?: state.results.firstOrNull()

        if (featured != null) {
            item {
                CinematicHero(
                    item = featured,
                    onOpen = { onOpenTitle(featured) },
                    onLibrary = { onLibrary(featured) }
                )
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Continue Watching", "Pick up where you stopped")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.continueWatching.take(12)) { record ->
                            ContinueWatchingCard(record) { onResume(record) }
                        }
                    }
                }
            }
        }

        if (state.homeSections.isNotEmpty()) {
            state.homeSections.forEach { section ->
                if (section.items.isNotEmpty()) {
                    item {
                        MovieRail(
                            title = section.title,
                            subtitle = section.subtitle,
                            items = section.items,
                            onOpen = onOpenTitle
                        )
                    }
                }
            }
        } else if (!state.loading && state.error == null && featured == null) {
            item {
                EmptyState(
                    "Nothing to show yet",
                    "Try again when you have a connection."
                )
            }
        }

        state.error?.takeIf { state.homeSections.isEmpty() }?.let { message ->
            item {
                ErrorState("Catalog unavailable", message, onRetry)
            }
        }
    }
}
