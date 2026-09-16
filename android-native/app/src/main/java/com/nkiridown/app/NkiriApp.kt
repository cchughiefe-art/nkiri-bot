package com.nkiridown.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

private val NkiriRed =
    Color(0xFFE11D48)

private val NkiriDark =
    Color(0xFF09090B)

private val NkiriSurface =
    Color(0xFF15151B)

private val NkiriCard =
    Color(0xFF1C1C24)

@Composable
fun NkiriTheme(
    content:
        @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary =
                    NkiriRed,
                background =
                    NkiriDark,
                surface =
                    NkiriSurface,
                surfaceVariant =
                    NkiriCard
            ),
        content =
            content
    )
}

@Composable
fun NkiriApp(
    viewModel: MainViewModel,
    onDownload:
        (SourceItem) -> Unit,
    onOpenDownloads:
        () -> Unit,
    onOpenUrl:
        (String) -> Unit,
    onShare:
        () -> Unit
) {
    val state
        by viewModel.state
            .collectAsStateWithLifecycle()

    BackHandler(
        enabled =
            state.detailScreen !=
            DetailScreen.NONE
    ) {
        viewModel.back()
    }

    Surface(
        modifier =
            Modifier.fillMaxSize(),
        color =
            MaterialTheme
                .colorScheme
                .background
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
        ) {
            TopBar(
                state =
                    state,
                onBack =
                    viewModel::back
            )

            if (
                state.updateAvailable
            ) {
                UpdateBanner(
                    state =
                        state,
                    onUpdate = {
                        state.updateUrl
                            ?.let(
                                onOpenUrl
                            )
                    }
                )
            }

            state.remoteNotice
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    NoticeBanner(it)
                }

            state.error?.let {
                ErrorBanner(
                    text = it,
                    onDismiss =
                        viewModel::clearError
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
            ) {
                when (
                    state.detailScreen
                ) {
                    DetailScreen.TITLE ->
                        TitleScreen(
                            state =
                                state,
                            onSeason =
                                viewModel::openSeason,
                            onFavorite =
                                viewModel::toggleFavorite,
                            onDownload =
                                onDownload,
                            onOpenUrl =
                                onOpenUrl
                        )

                    DetailScreen.EPISODES ->
                        EpisodesScreen(
                            state =
                                state,
                            onEpisode =
                                viewModel::openEpisode
                        )

                    DetailScreen.QUALITIES ->
                        QualitiesScreen(
                            state =
                                state,
                            onDownload =
                                onDownload
                        )

                    DetailScreen.NONE ->
                        RootContent(
                            state =
                                state,
                            viewModel =
                                viewModel,
                            onOpenDownloads =
                                onOpenDownloads,
                            onOpenUrl =
                                onOpenUrl,
                            onShare =
                                onShare
                        )
                }

                if (
                    state.loading
                ) {
                    LoadingOverlay()
                }
            }

            if (
                state.detailScreen ==
                DetailScreen.NONE
            ) {
                BottomTabs(
                    selected =
                        state.rootTab,
                    onSelect =
                        viewModel::selectTab
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    state: UiState,
    onBack: () -> Unit
) {
    val title =
        when (
            state.detailScreen
        ) {
            DetailScreen.NONE ->
                "TheNkiri"

            DetailScreen.TITLE ->
                state.title?.title
                    ?: "Title"

            DetailScreen.EPISODES ->
                "${state.title?.title ?: "Series"} • Season ${state.season ?: ""}"

            DetailScreen.QUALITIES ->
                state.episode?.label
                    ?: "Quality"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(
                    horizontal =
                        10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        if (
            state.detailScreen !=
            DetailScreen.NONE
        ) {
            TextButton(
                onClick =
                    onBack
            ) {
                Text(
                    "‹ Back"
                )
            }
        } else {
            Text(
                "N",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Black,
                modifier =
                    Modifier
                        .background(
                            NkiriRed,
                            RoundedCornerShape(
                                9.dp
                            )
                        )
                        .padding(
                            horizontal =
                                12.dp,
                            vertical =
                                7.dp
                        )
            )

            Spacer(
                Modifier.width(
                    10.dp
                )
            )
        }

        Text(
            text =
                title,
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
            fontWeight =
                FontWeight.Black,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            modifier =
                Modifier.weight(
                    1f
                )
        )

        Text(
            text =
                if (
                    state.apiStatus
                        .contains(
                            "Online"
                        )
                ) {
                    "ONLINE"
                } else {
                    "OFFLINE"
                },
            color =
                if (
                    state.apiStatus
                        .contains(
                            "Online"
                        )
                ) {
                    Color(
                        0xFF4ADE80
                    )
                } else {
                    Color(
                        0xFFF87171
                    )
                },
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun UpdateBanner(
    state: UiState,
    onUpdate: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        12.dp,
                    vertical =
                        4.dp
                )
                .background(
                    NkiriRed.copy(
                        alpha =
                            0.16f
                    ),
                    RoundedCornerShape(
                        12.dp
                    )
                )
                .padding(
                    12.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(
                    1f
                )
        ) {
            Text(
                if (
                    state.forceUpdate
                ) {
                    "Update required"
                } else {
                    "New version available"
                },
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "v${state.latestVersionName ?: "new"}",
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        if (
            state.updateUrl != null
        ) {
            Button(
                onClick =
                    onUpdate
            ) {
                Text("Update")
            }
        }
    }
}

@Composable
private fun NoticeBanner(
    text: String
) {
    Text(
        text,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        12.dp,
                    vertical =
                        4.dp
                )
                .background(
                    NkiriCard,
                    RoundedCornerShape(
                        12.dp
                    )
                )
                .padding(
                    10.dp
                ),
        color =
            MaterialTheme
                .colorScheme
                .onSurfaceVariant
    )
}

@Composable
private fun ErrorBanner(
    text: String,
    onDismiss: () -> Unit
) {
    Text(
        text =
            "$text\nTap to dismiss.",
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        12.dp,
                    vertical =
                        4.dp
                )
                .background(
                    Color(
                        0xFF451A1A
                    ),
                    RoundedCornerShape(
                        12.dp
                    )
                )
                .clickable(
                    onClick =
                        onDismiss
                )
                .padding(
                    12.dp
                ),
        color =
            Color(
                0xFFFECACA
            )
    )
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    NkiriDark.copy(
                        alpha =
                            0.70f
                    )
                ),
        contentAlignment =
            Alignment.Center
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color =
                    NkiriRed
            )

            Text(
                "Loading…",
                modifier =
                    Modifier.padding(
                        top =
                            10.dp
                    ),
                color =
                    Color.White
            )
        }
    }
}

@Composable
private fun RootContent(
    state: UiState,
    viewModel: MainViewModel,
    onOpenDownloads:
        () -> Unit,
    onOpenUrl:
        (String) -> Unit,
    onShare:
        () -> Unit
) {
    when (
        state.rootTab
    ) {
        RootTab.HOME ->
            HomeScreen(
                state =
                    state,
                onQueryChange =
                    viewModel::setQuery,
                onSearch = {
                    viewModel.search()
                },
                onRecentSearch = {
                    viewModel.search(it)
                },
                onLatest = {
                    type,
                    label ->
                    viewModel.loadLatest(
                        type,
                        label
                    )
                },
                onOpenTitle =
                    viewModel::openSearchItem
            )

        RootTab.FAVORITES ->
            FavoritesScreen(
                favorites =
                    state.favorites,
                onOpen =
                    viewModel::openFavorite
            )

        RootTab.DOWNLOADS ->
            DownloadsScreen(
                downloads =
                    state.downloads,
                onOpenDownloads =
                    onOpenDownloads,
                onClear =
                    viewModel::clearDownloads
            )

        RootTab.SETTINGS ->
            SettingsScreen(
                state =
                    state,
                onSaveApi =
                    viewModel::saveApiBaseUrl,
                onCheckApi =
                    viewModel::checkApi,
                onOpenUrl =
                    onOpenUrl,
                onShare =
                    onShare,
                onClearRecent =
                    viewModel::clearRecentSearches,
                onClearCrash =
                    viewModel::clearCrashLog
            )
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onQueryChange:
        (String) -> Unit,
    onSearch:
        () -> Unit,
    onRecentSearch:
        (String) -> Unit,
    onLatest:
        (String, String) -> Unit,
    onOpenTitle:
        (SearchItem) -> Unit
) {
    val featured =
        state.results
            .firstOrNull()

    val remaining =
        state.results
            .drop(
                if (
                    featured != null
                ) {
                    1
                } else {
                    0
                }
            )

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                bottom =
                    28.dp
            )
    ) {
        item {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal =
                            14.dp
                    )
            ) {
                OutlinedTextField(
                    value =
                        state.query,
                    onValueChange =
                        onQueryChange,
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Search movies, series, K-Drama"
                        )
                    },
                    singleLine =
                        true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction =
                                ImeAction.Search
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = {
                                onSearch()
                            }
                        )
                )

                Button(
                    onClick =
                        onSearch,
                    enabled =
                        state.query
                            .trim()
                            .length >= 2,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top =
                                    8.dp
                            )
                ) {
                    Text(
                        "Search"
                    )
                }

                LazyRow(
                    modifier =
                        Modifier.padding(
                            top =
                                10.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    item {
                        CategoryChip(
                            "Discover"
                        ) {
                            onLatest(
                                "all",
                                "Discover"
                            )
                        }
                    }

                    item {
                        CategoryChip(
                            "Movies"
                        ) {
                            onLatest(
                                "movie",
                                "Movies"
                            )
                        }
                    }

                    item {
                        CategoryChip(
                            "Series"
                        ) {
                            onLatest(
                                "series",
                                "Series"
                            )
                        }
                    }

                    item {
                        CategoryChip(
                            "K-Drama"
                        ) {
                            onLatest(
                                "drama",
                                "K-Drama"
                            )
                        }
                    }
                }

                if (
                    state.recentSearches
                        .isNotEmpty()
                ) {
                    LazyRow(
                        modifier =
                            Modifier.padding(
                                top =
                                    8.dp
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        items(
                            state.recentSearches
                                .take(6)
                        ) {
                            recent ->
                            AssistChip(
                                onClick = {
                                    onRecentSearch(
                                        recent
                                    )
                                },
                                label = {
                                    Text(
                                        recent,
                                        maxLines =
                                            1
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (
            featured != null
        ) {
            item {
                FeaturedHero(
                    item =
                        featured,
                    onClick = {
                        onOpenTitle(
                            featured
                        )
                    }
                )
            }
        }

        item {
            Text(
                state.sectionTitle,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Black,
                modifier =
                    Modifier.padding(
                        horizontal =
                            14.dp,
                        vertical =
                            12.dp
                    )
            )
        }

        val rows =
            remaining
                .chunked(2)

        items(
            rows.size
        ) {
            index ->
            val row =
                rows[index]

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal =
                                10.dp,
                            vertical =
                                5.dp
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                row.forEach {
                    item ->
                    PosterCard(
                        item =
                            item,
                        modifier =
                            Modifier.weight(
                                1f
                            ),
                        onClick = {
                            onOpenTitle(
                                item
                            )
                        }
                    )
                }

                if (
                    row.size == 1
                ) {
                    Spacer(
                        Modifier.weight(
                            1f
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick =
            onClick,
        label = {
            Text(
                label,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    )
}

@Composable
private fun FeaturedHero(
    item: SearchItem,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    300.dp
                )
                .padding(
                    top =
                        14.dp
                )
                .clickable(
                    onClick =
                        onClick
                )
    ) {
        AsyncImage(
            model =
                item.poster,
            contentDescription =
                item.title,
            contentScale =
                ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                NkiriDark.copy(
                                    alpha =
                                        0.25f
                                ),
                                NkiriDark
                            )
                        )
                    )
        )

        Column(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomStart
                    )
                    .padding(
                        18.dp
                    )
        ) {
            Text(
                item.title,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Black,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis
            )

            Text(
                listOfNotNull(
                    item.year
                        ?.toString(),
                    if (
                        item.type ==
                        "series"
                    ) {
                        "Series"
                    } else {
                        "Movie"
                    },
                    item.rating
                        ?.let {
                            "★ $it"
                        }
                )
                    .joinToString(
                        " • "
                    ),
                color =
                    Color(
                        0xFFE4E4E7
                    ),
                modifier =
                    Modifier.padding(
                        top =
                            4.dp
                    )
            )

            Button(
                onClick =
                    onClick,
                modifier =
                    Modifier.padding(
                        top =
                            10.dp
                    )
            ) {
                Text(
                    "View"
                )
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: SearchItem,
    modifier:
        Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier =
            modifier
                .clickable(
                    onClick =
                        onClick
                )
    ) {
        AsyncImage(
            model =
                item.poster,
            contentDescription =
                item.title,
            contentScale =
                ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        0.68f
                    )
                    .clip(
                        RoundedCornerShape(
                            12.dp
                        )
                    )
                    .background(
                        NkiriCard
                    )
        )

        Text(
            item.title,
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(
                    top =
                        7.dp
                )
        )

        Text(
            listOfNotNull(
                item.year
                    ?.toString(),
                item.rating
                    ?.let {
                        "★ $it"
                    }
            )
                .joinToString(
                    " • "
                ),
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun TitleScreen(
    state: UiState,
    onSeason:
        (Int) -> Unit,
    onFavorite:
        () -> Unit,
    onDownload:
        (SourceItem) -> Unit,
    onOpenUrl:
        (String) -> Unit
) {
    val title =
        state.title ?: return

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                bottom =
                    30.dp
            )
    ) {
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            430.dp
                        )
            ) {
                AsyncImage(
                    model =
                        title.poster,
                    contentDescription =
                        title.title,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    contentScale =
                        ContentScale.Crop
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        NkiriDark.copy(
                                            alpha =
                                                0.28f
                                        ),
                                        NkiriDark
                                    )
                                )
                            )
                )
            }
        }

        item {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal =
                            16.dp
                    )
            ) {
                Text(
                    title.title,
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,
                    fontWeight =
                        FontWeight.Black
                )

                Text(
                    listOfNotNull(
                        title.year
                            ?.toString(),
                        title.rating
                            ?.let {
                                "★ $it"
                            },
                        title.country,
                        if (
                            title.type ==
                            "series"
                        ) {
                            "Series"
                        } else {
                            "Movie"
                        }
                    )
                        .joinToString(
                            " • "
                        ),
                    color =
                        Color(
                            0xFFD4D4D8
                        ),
                    modifier =
                        Modifier.padding(
                            top =
                                5.dp
                        )
                )

                if (
                    title.genre
                        .isNotBlank()
                ) {
                    Text(
                        title.genre,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                top =
                                    5.dp
                            )
                    )
                }

                if (
                    title.description
                        .isNotBlank()
                ) {
                    Text(
                        title.description,
                        modifier =
                            Modifier.padding(
                                top =
                                    14.dp
                            )
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top =
                                    14.dp
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    OutlinedButton(
                        onClick =
                            onFavorite,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            if (
                                state.isCurrentFavorite
                            ) {
                                "★ Saved"
                            } else {
                                "☆ Save"
                            }
                        )
                    }

                    if (
                        title.trailer != null
                    ) {
                        OutlinedButton(
                            onClick = {
                                onOpenUrl(
                                    title.trailer
                                )
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Trailer"
                            )
                        }
                    }
                }

                Text(
                    if (
                        title.type ==
                        "series"
                    ) {
                        "Episodes"
                    } else {
                        "Download"
                    },
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight =
                        FontWeight.Black,
                    modifier =
                        Modifier.padding(
                            top =
                                18.dp,
                            bottom =
                                8.dp
                        )
                )
            }
        }

        if (
            title.type ==
            "series"
        ) {
            items(
                items =
                    title.seasons,
                key = {
                    it.season
                }
            ) {
                season ->
                Button(
                    onClick = {
                        onSeason(
                            season.season
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    16.dp,
                                vertical =
                                    4.dp
                            )
                ) {
                    Text(
                        "Season ${season.season} • ${season.maxEp} episodes"
                    )
                }
            }
        } else {
            items(
                items =
                    state.sources
                        .sortedByDescending {
                            it.quality
                        },
                key = {
                    "${it.quality}:${it.size}:${it.url.hashCode()}"
                }
            ) {
                source ->
                QualityButton(
                    source =
                        source,
                    onClick = {
                        onDownload(
                            source
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodesScreen(
    state: UiState,
    onEpisode:
        (EpisodeItem) -> Unit
) {
    LazyVerticalGrid(
        columns =
            GridCells.Adaptive(
                minSize =
                    112.dp
            ),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    14.dp
                ),
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {
        gridItems(
            items =
                state.episodes,
            key = {
                "${it.season}:${it.episode}"
            }
        ) {
            episode ->
            Button(
                onClick = {
                    onEpisode(
                        episode
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    episode.label
                )
            }
        }
    }
}

@Composable
private fun QualitiesScreen(
    state: UiState,
    onDownload:
        (SourceItem) -> Unit
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    16.dp
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {
        item {
            Text(
                state.episode
                    ?.label
                    ?: "Episode",
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Black
            )

            Text(
                "Choose quality",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        top = 4.dp,
                        bottom =
                            8.dp
                    )
            )
        }

        items(
            items =
                state.sources
                    .sortedByDescending {
                        it.quality
                    },
            key = {
                "${it.quality}:${it.size}:${it.url.hashCode()}"
            }
        ) {
            source ->
            QualityButton(
                source =
                    source,
                onClick = {
                    onDownload(
                        source
                    )
                }
            )
        }
    }
}

@Composable
private fun QualityButton(
    source: SourceItem,
    onClick: () -> Unit
) {
    Button(
        onClick =
            onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        16.dp,
                    vertical =
                        3.dp
                )
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {
            Text(
                if (
                    source.quality > 0
                ) {
                    "${source.quality}p"
                } else {
                    "Download"
                },
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                source.sizeText,
                color =
                    Color(
                        0xFFF4F4F5
                    )
            )
        }
    }
}

@Composable
private fun FavoritesScreen(
    favorites:
        List<FavoriteItem>,
    onOpen:
        (FavoriteItem) -> Unit
) {
    if (
        favorites.isEmpty()
    ) {
        EmptyState(
            title =
                "Nothing saved yet",
            body =
                "Save movies and series to build your library."
        )
        return
    }

    val rows =
        favorites.chunked(2)

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                10.dp
            )
    ) {
        item {
            Text(
                "My Library",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Black,
                modifier =
                    Modifier.padding(
                        4.dp,
                        8.dp
                    )
            )
        }

        items(
            rows.size
        ) {
            index ->
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                rows[index]
                    .forEach {
                        favorite ->
                        PosterCard(
                            item =
                                SearchItem(
                                    id =
                                        favorite.id,
                                    title =
                                        favorite.title,
                                    displayTitle =
                                        favorite.title,
                                    year = null,
                                    type =
                                        favorite.type,
                                    poster =
                                        favorite.poster,
                                    rating = null,
                                    genre = "",
                                    provider =
                                        favorite.provider
                                ),
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            onClick = {
                                onOpen(
                                    favorite
                                )
                            }
                        )
                    }

                if (
                    rows[index].size ==
                    1
                ) {
                    Spacer(
                        Modifier.weight(
                            1f
                        )
                    )
                }
            }

            Spacer(
                Modifier.height(
                    12.dp
                )
            )
        }
    }
}

@Composable
private fun DownloadsScreen(
    downloads:
        List<DownloadRecord>,
    onOpenDownloads:
        () -> Unit,
    onClear:
        () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    14.dp
                )
    ) {
        Text(
            "Downloads",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium,
            fontWeight =
                FontWeight.Black
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 10.dp,
                        bottom =
                            10.dp
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            Button(
                onClick =
                    onOpenDownloads,
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {
                Text(
                    "Open Files"
                )
            }

            OutlinedButton(
                onClick =
                    onClear,
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {
                Text(
                    "Clear History"
                )
            }
        }

        if (
            downloads.isEmpty()
        ) {
            EmptyState(
                title =
                    "No downloads yet",
                body =
                    "Your download history will appear here."
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier.weight(
                        1f
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                items(
                    downloads,
                    key = {
                        "${it.downloadId}:${it.createdAt}"
                    }
                ) {
                    record ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    NkiriCard,
                                    RoundedCornerShape(
                                        14.dp
                                    )
                                )
                                .padding(
                                    14.dp
                                )
                    ) {
                        Text(
                            record.title,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            listOfNotNull(
                                record.episodeLabel,
                                record.quality
                                    .takeIf {
                                        it > 0
                                    }
                                    ?.let {
                                        "${it}p"
                                    },
                                record.sizeText
                                    .takeIf {
                                        it.isNotBlank()
                                    }
                            )
                                .joinToString(
                                    " • "
                                ),
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            modifier =
                                Modifier.padding(
                                    top =
                                        4.dp
                                )
                        )

                        Text(
                            DateFormat
                                .getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT
                                )
                                .format(
                                    Date(
                                        record.createdAt
                                    )
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            modifier =
                                Modifier.padding(
                                    top =
                                        6.dp
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    onSaveApi:
        (String) -> Unit,
    onCheckApi:
        () -> Unit,
    onOpenUrl:
        (String) -> Unit,
    onShare:
        () -> Unit,
    onClearRecent:
        () -> Unit,
    onClearCrash:
        () -> Unit
) {
    var apiUrl
        by remember(
            state.apiBaseUrl
        ) {
            mutableStateOf(
                state.apiBaseUrl
            )
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    14.dp
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {
        item {
            Text(
                "Settings",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Black
            )
        }

        item {
            SettingsSection(
                title =
                    "Connection"
            ) {
                Text(
                    state.apiStatus,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                OutlinedTextField(
                    value =
                        apiUrl,
                    onValueChange = {
                        apiUrl = it
                    },
                    label = {
                        Text(
                            "API base URL"
                        )
                    },
                    singleLine =
                        true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top =
                                    8.dp
                            )
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top =
                                    8.dp
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    Button(
                        onClick = {
                            onSaveApi(
                                apiUrl
                            )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text("Save")
                    }

                    OutlinedButton(
                        onClick =
                            onCheckApi,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text("Test")
                    }
                }
            }
        }

        item {
            SettingsSection(
                title =
                    "TheNkiri"
            ) {
                SettingButton(
                    "Updates Channel"
                ) {
                    onOpenUrl(
                        state.channelUrl
                    )
                }

                SettingButton(
                    "Open Telegram Bot"
                ) {
                    onOpenUrl(
                        state.botUrl
                    )
                }

                SettingButton(
                    "Share TheNkiri"
                ) {
                    onShare()
                }

                SettingButton(
                    "Install VLC"
                ) {
                    onOpenUrl(
                        "https://play.google.com/store/apps/details?id=org.videolan.vlc"
                    )
                }
            }
        }

        item {
            SettingsSection(
                title =
                    "Storage & Privacy"
            ) {
                SettingButton(
                    "Clear Recent Searches"
                ) {
                    onClearRecent()
                }

                if (
                    state.lastCrash != null
                ) {
                    Text(
                        "A local crash log is available. It stays on this device and is not uploaded automatically.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    SettingButton(
                        "Clear Crash Log"
                    ) {
                        onClearCrash()
                    }
                }

                Text(
                    "Favorites, recent searches and download history stay on this phone. Search and source requests go to the configured API. Only download content you are authorized to access.",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }

        item {
            Text(
                "TheNkiri Android ${BuildConfig.VERSION_NAME}",
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                modifier =
                    Modifier.padding(
                        top = 12.dp,
                        bottom =
                            28.dp
                    )
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content:
        @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    NkiriCard,
                    RoundedCornerShape(
                        16.dp
                    )
                )
                .padding(
                    14.dp
                )
    ) {
        Text(
            title,
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            fontWeight =
                FontWeight.Bold,
            modifier =
                Modifier.padding(
                    bottom =
                        8.dp
                )
        )

        content()
    }
}

@Composable
private fun SettingButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick =
            onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        3.dp
                )
    ) {
        Text(label)
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top =
                        48.dp,
                    start =
                        24.dp,
                    end =
                        24.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
            fontWeight =
                FontWeight.Black
        )

        Text(
            body,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.padding(
                    top =
                        6.dp
                )
        )
    }
}

@Composable
private fun BottomTabs(
    selected: RootTab,
    onSelect:
        (RootTab) -> Unit
) {
    NavigationBar(
        containerColor =
            NkiriSurface
    ) {
        NavigationBarItem(
            selected =
                selected ==
                RootTab.HOME,
            onClick = {
                onSelect(
                    RootTab.HOME
                )
            },
            icon = {
                Text("⌂")
            },
            label = {
                Text("Home")
            }
        )

        NavigationBarItem(
            selected =
                selected ==
                RootTab.FAVORITES,
            onClick = {
                onSelect(
                    RootTab.FAVORITES
                )
            },
            icon = {
                Text("★")
            },
            label = {
                Text("Library")
            }
        )

        NavigationBarItem(
            selected =
                selected ==
                RootTab.DOWNLOADS,
            onClick = {
                onSelect(
                    RootTab.DOWNLOADS
                )
            },
            icon = {
                Text("↓")
            },
            label = {
                Text("Downloads")
            }
        )

        NavigationBarItem(
            selected =
                selected ==
                RootTab.SETTINGS,
            onClick = {
                onSelect(
                    RootTab.SETTINGS
                )
            },
            icon = {
                Text("⚙")
            },
            label = {
                Text("Settings")
            }
        )
    }
}
