package com.nkiridown.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@Composable
fun NkiriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}

@Composable
fun NkiriApp(
    viewModel: MainViewModel,
    onDownload: (SourceItem, String, String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                onBack = viewModel::back
            )

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .clickable { viewModel.clearError() }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (state.screen) {
                    Screen.SEARCH -> SearchScreen(
                        state = state,
                        onQueryChange = viewModel::setQuery,
                        onSearch = viewModel::search,
                        onOpenTitle = viewModel::openTitle
                    )

                    Screen.TITLE -> TitleScreen(
                        state = state,
                        onSeason = viewModel::openSeason,
                        onDownload = onDownload
                    )

                    Screen.EPISODES -> EpisodesScreen(
                        state = state,
                        onEpisode = viewModel::openEpisode
                    )

                    Screen.QUALITIES -> QualitiesScreen(
                        state = state,
                        onDownload = onDownload
                    )
                }

                if (state.loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: UiState,
    onBack: () -> Unit
) {
    val title = when (state.screen) {
        Screen.SEARCH -> "TheNkiri"
        Screen.TITLE -> state.title?.title ?: "Title"
        Screen.EPISODES -> "${state.title?.title ?: "Series"} • S${state.season ?: ""}"
        Screen.QUALITIES -> state.episode?.label ?: "Quality"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.screen != Screen.SEARCH) {
            TextButton(onClick = onBack) {
                Text("‹ Back")
            }
        } else {
            Spacer(Modifier.width(72.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = state.apiName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(88.dp)
        )
    }
}

@Composable
private fun SearchScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenTitle: (SearchItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        Text(
            "Movies, series & K-Drama",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )

        Text(
            "Search. Choose a title. Pick quality. Download.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search") },
            placeholder = { Text("Toy Story, Breaking Bad…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )

        Button(
            onClick = onSearch,
            enabled = state.query.trim().length >= 2 && !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Text("Search")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = state.results,
                key = { "${it.id}-${it.type}" }
            ) { item ->
                SearchCard(
                    item = item,
                    onClick = { onOpenTitle(item) }
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SearchCard(
    item: SearchItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.title,
            modifier = Modifier
                .size(width = 76.dp, height = 108.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(10.dp)
                ),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                listOfNotNull(
                    item.year?.toString(),
                    if (item.type == "series") "Series" else "Movie",
                    item.rating?.let { "★ $it" }
                ).joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (item.genre.isNotBlank()) {
                Text(
                    item.genre,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TitleScreen(
    state: UiState,
    onSeason: (Int) -> Unit,
    onDownload: (SourceItem, String, String?) -> Unit
) {
    val title = state.title ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))

            AsyncImage(
                model = title.poster,
                contentDescription = title.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(18.dp)
                    ),
                contentScale = ContentScale.Crop
            )

            Text(
                title.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                listOfNotNull(
                    title.year?.toString(),
                    title.rating?.let { "★ $it" },
                    title.country
                ).joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (title.genre.isNotBlank()) {
                Text(
                    title.genre,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (title.description.isNotBlank()) {
                Text(
                    title.description,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            Text(
                if (title.type == "series") "Seasons" else "Choose quality",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp)
            )
        }

        if (title.type == "series") {
            items(title.seasons, key = { it.season }) { season ->
                Button(
                    onClick = { onSeason(season.season) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Season ${season.season} • ${season.maxEp} episodes")
                }
            }
        } else {
            items(
                state.sources.sortedByDescending { it.quality },
                key = { "${it.quality}-${it.size}" }
            ) { source ->
                Button(
                    onClick = {
                        onDownload(
                            source,
                            title.title,
                            null
                        )
                    },
                    enabled = source.url != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(sourceLabel(source))
                }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EpisodesScreen(
    state: UiState,
    onEpisode: (EpisodeItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        gridItems(
            items = state.episodes,
            key = { it.label }
        ) { episode ->
            Button(
                onClick = { onEpisode(episode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(episode.label)
            }
        }
    }
}

@Composable
private fun QualitiesScreen(
    state: UiState,
    onDownload: (SourceItem, String, String?) -> Unit
) {
    val title = state.title ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                state.episode?.label ?: "Episode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose download quality",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        items(
            state.sources.sortedByDescending { it.quality },
            key = { "${it.quality}-${it.size}" }
        ) { source ->
            Button(
                onClick = {
                    onDownload(
                        source,
                        title.title,
                        state.episode?.label
                    )
                },
                enabled = source.url != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(sourceLabel(source))
            }
        }
    }
}

private fun sourceLabel(source: SourceItem): String =
    buildString {
        append(if (source.quality > 0) "${source.quality}p" else "Download")
        if (source.sizeText.isNotBlank()) {
            append(" • ")
            append(source.sizeText)
        }
    }
