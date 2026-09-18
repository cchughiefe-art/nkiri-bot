package com.nkiridown.app.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nkiridown.app.SearchItem
import com.nkiridown.app.UiState
import com.nkiridown.app.ui.components.EmptyState
import com.nkiridown.app.ui.components.LoadingSkeleton
import com.nkiridown.app.ui.components.MetadataLine
import com.nkiridown.app.ui.components.SectionHeader
import com.nkiridown.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String?) -> Unit,
    onClearRecent: () -> Unit,
    onOpenTitle: (SearchItem) -> Unit
) {
    var localQuery by remember(state.query) { mutableStateOf(state.query) }
    var lastSubmitted by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }

    val filteredResults =
        remember(state.results, filter) {
            when (filter) {
                "movies" -> state.results.filter { it.type != "series" }
                "series" -> state.results.filter { it.type == "series" }
                "kdrama" -> state.results.filter {
                    it.genre.contains("korean", ignoreCase = true) ||
                    it.genre.contains("k-drama", ignoreCase = true) ||
                    it.title.contains("korean", ignoreCase = true)
                }
                else -> state.results
            }
        }

    LaunchedEffect(localQuery) {
        val q = localQuery.trim()
        if (q.length >= 2 && q != lastSubmitted) {
            delay(NkiriMotion.SearchDebounceMs)
            if (q == localQuery.trim()) {
                lastSubmitted = q
                onSearch(q)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Search",
                    color = NkiriText,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = localQuery,
                    onValueChange = {
                        localQuery = it
                        onQueryChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search movies, series and K-Drama", color = NkiriMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (localQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    localQuery = ""
                                    onQueryChange("")
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = NkiriShapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val q = localQuery.trim()
                            if (q.length >= 2) {
                                lastSubmitted = q
                                onSearch(q)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NkiriPurple,
                        unfocusedBorderColor = NkiriOutline,
                        focusedTextColor = NkiriText,
                        unfocusedTextColor = NkiriText,
                        focusedContainerColor = NkiriSurface,
                        unfocusedContainerColor = NkiriSurface
                    )
                )
            }
        }

        if (localQuery.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all" to "All",
                        "movies" to "Movies",
                        "series" to "Series",
                        "kdrama" to "K-Drama"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = filter == key,
                            onClick = { filter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (localQuery.isBlank() && state.recentSearches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent searches",
                    action = "Clear",
                    onAction = onClearRecent
                )
            }
            items(state.recentSearches) { recent ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable {
                            localQuery = recent
                            lastSubmitted = recent
                            onQueryChange(recent)
                            onSearch(recent)
                        },
                    color = NkiriSurface,
                    shape = NkiriShapes.medium,
                    border = BorderStroke(1.dp, NkiriOutline)
                ) {
                    Text(recent, color = NkiriText, modifier = Modifier.padding(16.dp))
                }
            }
        }

        if (state.loading && localQuery.length >= 2) {
            items(3) {
                LoadingSkeleton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    height = 92
                )
            }
        } else if (
            localQuery.length >= 2 &&
            state.sectionTitle == "Search results" &&
            state.results.isEmpty()
        ) {
            item {
                EmptyState("No results", "Try another title or spelling.")
            }
        }

        if (state.sectionTitle == "Search results") {
            items(filteredResults, key = { it.id }) { item ->
                SearchResultCard(item = item, onClick = { onOpenTitle(item) })
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SearchItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        color = NkiriCard,
        shape = NkiriShapes.medium,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, color = NkiriText, style = MaterialTheme.typography.titleMedium)
                MetadataLine(
                    listOfNotNull(
                        item.year?.toString(),
                        if (item.type == "series") "Series" else "Movie",
                        item.rating?.let { "★ $it" },
                        item.genre.takeIf { it.isNotBlank() },
                        item.provider.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }
}
