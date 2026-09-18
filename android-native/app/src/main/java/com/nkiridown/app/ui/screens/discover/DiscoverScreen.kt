package com.nkiridown.app.ui.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nkiridown.app.SearchItem
import com.nkiridown.app.UiState
import com.nkiridown.app.ui.components.EmptyState
import com.nkiridown.app.ui.components.MoviePoster
import com.nkiridown.app.ui.components.SectionHeader
import com.nkiridown.app.ui.theme.*

private val filters =
    listOf(
        Triple("All", "all", "Discover"),
        Triple("Movies", "movie", "Movies"),
        Triple("Series", "series", "Series"),
        Triple("K-Drama", "drama", "K-Drama"),
        Triple("Recently Added", "all", "Recently Added")
    )

@Composable
fun DiscoverScreen(
    state: UiState,
    onFilter: (String, String) -> Unit,
    onOpenTitle: (SearchItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(top = 18.dp)) {
                SectionHeader("Discover", "Browse the catalog your way")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { (label, type, section) ->
                        Surface(
                            modifier = Modifier.clickable { onFilter(type, section) },
                            color = if (state.sectionTitle == section) NkiriPurple else NkiriCard,
                            shape = NkiriShapes.pill,
                            border = BorderStroke(1.dp, NkiriOutline)
                        ) {
                            Text(
                                label,
                                color = NkiriText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.results.isEmpty() && !state.loading) {
            item {
                EmptyState("No titles found", "Try a different category.")
            }
        }

        items(state.results.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { item ->
                    MoviePoster(
                        item = item,
                        onClick = { onOpenTitle(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
