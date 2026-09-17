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

private val Bg = Color(0xFF07080A)
private val Card = Color(0xFF14161A)
private val Card2 = Color(0xFF1B1E24)
private val Accent = Color(0xFFE50914)
private val Muted = Color(0xFFB8BDC7)

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

    BackHandler(
        enabled =
            state.detailScreen !=
            DetailScreen.NONE
    ) {
        viewModel.back()
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            AppTopBar(
                state,
                viewModel::back
            )
        },
        bottomBar = {
            if (
                state.detailScreen ==
                DetailScreen.NONE
            ) {
                AppBottomBar(
                    state.rootTab,
                    viewModel::selectTab
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Bg)
        ) {
            when (
                state.detailScreen
            ) {
                DetailScreen.TITLE ->
                    TitleScreen(
                        state,
                        viewModel::openSeason,
                        viewModel::toggleFavorite,
                        onPlay,
                        onDownload,
                        onDownloadSeason,
                        viewModel::openSearchItem,
                        onOpenUrl
                    )

                DetailScreen.EPISODES ->
                    EpisodesScreen(
                        state,
                        viewModel::openEpisode,
                        onDownloadSeason
                    )

                DetailScreen.QUALITIES ->
                    QualityScreen(
                        state,
                        onPlay,
                        onDownload
                    )

                DetailScreen.NONE ->
                    when (
                        state.rootTab
                    ) {
                        RootTab.HOME ->
                            HomeScreen(
                                state,
                                viewModel::setQuery,
                                { viewModel.search() },
                                viewModel::loadLatest,
                                viewModel::openSearchItem,
                                onResumePlayback
                            )

                        RootTab.FAVORITES ->
                            LibraryScreen(
                                state.favorites,
                                viewModel::openFavorite
                            )

                        RootTab.DOWNLOADS ->
                            DownloadsScreen(
                                state.downloads,
                                viewModel::clearDownloads
                            )

                        RootTab.HISTORY ->
                            HistoryScreen(
                                state.history,
                                onResumePlayback,
                                viewModel::clearHistory
                            )

                        RootTab.SETTINGS ->
                            SettingsScreen(
                                state,
                                onOpenUrl,
                                onShare
                            )
                    }
            }

            state.error?.let {
                Text(
                    it,
                    modifier =
                        Modifier
                            .align(
                                Alignment.TopCenter
                            )
                            .fillMaxWidth()
                            .background(
                                Color(0xFF451A1A)
                            )
                            .padding(10.dp),
                    color =
                        Color(0xFFFECACA)
                )
            }

            if (state.loading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = 0.55f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Accent
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(
    state: UiState,
    onBack: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Bg)
            .padding(horizontal = 14.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        if (
            state.detailScreen !=
            DetailScreen.NONE
        ) {
            TextButton(
                onClick = onBack
            ) {
                Text("‹")
            }
        } else {
            Box(
                Modifier
                    .size(34.dp)
                    .background(
                        Accent,
                        RoundedCornerShape(
                            10.dp
                        )
                    ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    "N",
                    fontWeight =
                        FontWeight.Black
                )
            }
            Spacer(
                Modifier.width(10.dp)
            )
        }

        Text(
            when (
                state.detailScreen
            ) {
                DetailScreen.NONE ->
                    "TheNkiri"
                DetailScreen.TITLE ->
                    state.title?.title
                        ?: "Title"
                DetailScreen.EPISODES ->
                    "Episodes"
                DetailScreen.QUALITIES ->
                    "Choose quality"
            },
            modifier =
                Modifier.weight(1f),
            fontWeight =
                FontWeight.Black,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis
        )

        Text(
            if (
                state.apiStatus
                    .contains("Online")
            ) "● LIVE"
            else "● OFFLINE",
            color =
                if (
                    state.apiStatus
                        .contains("Online")
                ) Color(0xFF45D483)
                else Color(0xFFF87171),
            style =
                MaterialTheme
                    .typography
                    .labelSmall
        )
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLatest:
        (String, String) -> Unit,
    onOpenTitle:
        (SearchItem) -> Unit,
    onResume:
        (PlaybackRecord) -> Unit
) {
    val featured =
        state.results
            .firstOrNull()

    val remaining =
        state.results
            .drop(1)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                bottom = 24.dp
            )
    ) {
        item {
            Column(
                Modifier.padding(
                    horizontal = 14.dp
                )
            ) {
                OutlinedTextField(
                    value =
                        state.query,
                    onValueChange =
                        onQueryChange,
                    modifier =
                        Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Search movies, series, K-Drama…"
                        )
                    },
                    singleLine = true,
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

                LazyRow(
                    Modifier.padding(
                        top = 10.dp
                    ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    item {
                        AppChip(
                            "Discover"
                        ) {
                            onLatest(
                                "all",
                                "Discover"
                            )
                        }
                    }
                    item {
                        AppChip(
                            "Movies"
                        ) {
                            onLatest(
                                "movie",
                                "Movies"
                            )
                        }
                    }
                    item {
                        AppChip(
                            "Series"
                        ) {
                            onLatest(
                                "series",
                                "Series"
                            )
                        }
                    }
                    item {
                        AppChip(
                            "K-Drama"
                        ) {
                            onLatest(
                                "drama",
                                "K-Drama"
                            )
                        }
                    }
                }
            }
        }

        if (
            state.continueWatching
                .isNotEmpty()
        ) {
            item {
                SectionTitle(
                    "Continue Watching"
                )

                LazyRow(
                    contentPadding =
                        PaddingValues(
                            horizontal = 14.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    items(
                        state.continueWatching
                            .take(10),
                        key = {
                            "${it.mediaId}:${it.season}:${it.episode}"
                        }
                    ) {
                        ContinueCard(
                            it
                        ) {
                            onResume(it)
                        }
                    }
                }
            }
        }

        if (featured != null) {
            item {
                HeroCard(
                    featured
                ) {
                    onOpenTitle(
                        featured
                    )
                }
            }
        }

        item {
            SectionTitle(
                state.sectionTitle
            )
        }

        items(
            remaining.chunked(2)
        ) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 6.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                row.forEach {
                    PosterCard(
                        it,
                        Modifier.weight(1f)
                    ) {
                        onOpenTitle(it)
                    }
                }

                if (row.size == 1) {
                    Spacer(
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    item: SearchItem,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(330.dp)
            .padding(top = 12.dp)
            .clickable(
                onClick = onClick
            )
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription =
                item.title,
            modifier =
                Modifier.fillMaxSize(),
            contentScale =
                ContentScale.Crop
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Bg.copy(
                                alpha = 0.15f
                            ),
                            Bg
                        )
                    )
                )
        )

        Column(
            Modifier
                .align(
                    Alignment.BottomStart
                )
                .padding(18.dp)
        ) {
            Text(
                item.title,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Black,
                maxLines = 2
            )

            Text(
                listOfNotNull(
                    item.year
                        ?.toString(),
                    item.rating
                        ?.let {
                            "★ $it"
                        },
                    if (
                        item.type ==
                        "series"
                    ) "Series"
                    else "Movie"
                ).joinToString(
                    " • "
                ),
                color = Muted
            )

            Button(
                onClick = onClick,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                Accent
                        ),
                modifier =
                    Modifier.padding(
                        top = 10.dp
                    )
            ) {
                Text("View details")
            }
        }
    }
}

@Composable
private fun ContinueCard(
    item: PlaybackRecord,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(180.dp)
            .clip(
                RoundedCornerShape(
                    14.dp
                )
            )
            .background(Card)
            .clickable(
                onClick = onClick
            )
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription =
                item.title,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(105.dp),
            contentScale =
                ContentScale.Crop
        )

        LinearProgressIndicator(
            progress = {
                item.progress
            },
            modifier =
                Modifier.fillMaxWidth()
        )

        Text(
            item.title,
            modifier =
                Modifier.padding(
                    10.dp,
                    8.dp,
                    10.dp,
                    2.dp
                ),
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis
        )

        Text(
            item.episodeLabel
                ?: "Resume",
            color = Muted,
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            modifier =
                Modifier.padding(
                    10.dp,
                    0.dp,
                    10.dp,
                    10.dp
                )
        )
    }
}

@Composable
private fun AppChip(
    text: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(text)
        }
    )
}

@Composable
private fun SectionTitle(
    text: String
) {
    Text(
        text,
        style =
            MaterialTheme
                .typography
                .titleLarge,
        fontWeight =
            FontWeight.Black,
        modifier =
            Modifier.padding(14.dp)
    )
}

@Composable
private fun PosterCard(
    item: SearchItem,
    modifier: Modifier =
        Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(
                RoundedCornerShape(
                    14.dp
                )
            )
            .background(Card)
            .clickable(
                onClick = onClick
            )
            .padding(
                bottom = 10.dp
            )
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription =
                item.title,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        0.68f
                    ),
            contentScale =
                ContentScale.Crop
        )

        Text(
            item.title,
            Modifier.padding(
                horizontal = 10.dp,
                vertical = 8.dp
            ),
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis
        )

        Text(
            listOfNotNull(
                item.year
                    ?.toString(),
                item.rating
                    ?.let {
                        "★ $it"
                    }
            ).joinToString(
                " • "
            ),
            Modifier.padding(
                horizontal = 10.dp
            ),
            color = Muted,
            style =
                MaterialTheme
                    .typography
                    .labelMedium
        )
    }
}

@Composable
private fun TitleScreen(
    state: UiState,
    onSeason: (Int) -> Unit,
    onFavorite: () -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownload:
        (SourceItem) -> Unit,
    onDownloadSeason:
        (Int) -> Unit,
    onOpenTitle:
        (SearchItem) -> Unit,
    onOpenUrl:
        (String) -> Unit
) {
    val title =
        state.title
            ?: return

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                bottom = 28.dp
            )
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(390.dp)
            ) {
                AsyncImage(
                    model =
                        title.poster,
                    contentDescription =
                        title.title,
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Bg.copy(
                                        alpha = 0.2f
                                    ),
                                    Bg
                                )
                            )
                        )
                )
            }
        }

        item {
            Column(
                Modifier.padding(
                    horizontal = 16.dp
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
                        ) "Series"
                        else "Movie"
                    ).joinToString(
                        " • "
                    ),
                    color = Muted
                )

                if (
                    title.description
                        .isNotBlank()
                ) {
                    Text(
                        title.description,
                        modifier =
                            Modifier.padding(
                                top = 14.dp
                            )
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 14.dp
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
                            ) "★ Saved"
                            else "☆ Save"
                        )
                    }

                    title.trailer
                        ?.let { trailer ->
                            OutlinedButton(
                                onClick = {
                                    onOpenUrl(
                                        trailer
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
            }
        }

        if (
            title.type ==
            "series"
        ) {
            item {
                SectionTitle(
                    "Seasons"
                )
            }

            items(
                title.seasons,
                key = {
                    it.season
                }
            ) { season ->
                Column(
                    Modifier.padding(
                        horizontal =
                            16.dp,
                        vertical =
                            4.dp
                    )
                ) {
                    Button(
                        onClick = {
                            onSeason(
                                season.season
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        Card2
                                )
                    ) {
                        Text(
                            "Season ${season.season} • ${season.maxEp} episodes"
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onDownloadSeason(
                                season.season
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "↓ Download Season ${season.season}"
                        )
                    }
                }
            }
        } else {
            item {
                SectionTitle(
                    "Play & Download"
                )
            }

            items(
                state.sources
                    .sortedByDescending {
                        it.quality
                    }
            ) { source ->
                SourceRow(
                    source,
                    {
                        onPlay(
                            source
                        )
                    },
                    {
                        onDownload(
                            source
                        )
                    }
                )
            }
        }

        if (
            state.recommendations
                .isNotEmpty()
        ) {
            item {
                SectionTitle(
                    "You may also like"
                )
            }

            item {
                LazyRow(
                    contentPadding =
                        PaddingValues(
                            horizontal =
                                14.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    items(
                        state.recommendations,
                        key = {
                            it.id
                        }
                    ) {
                        Box(
                            Modifier.width(
                                150.dp
                            )
                        ) {
                            PosterCard(it) {
                                onOpenTitle(
                                    it
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesScreen(
    state: UiState,
    onEpisode:
        (EpisodeItem) -> Unit,
    onDownloadSeason:
        (Int) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(14.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {
        state.season?.let {
            item {
                OutlinedButton(
                    onClick = {
                        onDownloadSeason(
                            it
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "↓ Download full season"
                    )
                }
            }
        }

        items(
            state.episodes
        ) { episode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Card,
                        RoundedCornerShape(
                            14.dp
                        )
                    )
                    .clickable {
                        onEpisode(
                            episode
                        )
                    }
                    .padding(16.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(
                        1f
                    )
                ) {
                    Text(
                        episode.label,
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        "Tap to choose quality",
                        color = Muted
                    )
                }

                Text("›")
            }
        }
    }
}

@Composable
private fun QualityScreen(
    state: UiState,
    onPlay:
        (SourceItem) -> Unit,
    onDownload:
        (SourceItem) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
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
        }

        items(
            state.sources
                .sortedByDescending {
                    it.quality
                }
        ) {
            SourceRow(
                it,
                {
                    onPlay(it)
                },
                {
                    onDownload(it)
                }
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceItem,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 14.dp,
                vertical = 5.dp
            )
            .background(
                Card,
                RoundedCornerShape(
                    16.dp
                )
            )
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    if (
                        source.quality >
                        0
                    ) "${source.quality}p"
                    else "Source",
                    fontWeight =
                        FontWeight.Black
                )

                Text(
                    listOfNotNull(
                        source.sizeText
                            .takeIf {
                                it.isNotBlank()
                            },
                        source.format
                            .uppercase()
                            .takeIf {
                                it.isNotBlank()
                            }
                    ).joinToString(
                        " • "
                    ),
                    color = Muted
                )
            }

            if (
                source.external
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "External"
                        )
                    }
                )
            }
        }

        Row(
            Modifier.padding(
                top = 10.dp
            ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            Button(
                onClick = onPlay,
                modifier =
                    Modifier.weight(1f),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                Accent
                        )
            ) {
                Text("▶ Play")
            }

            OutlinedButton(
                onClick = onDownload,
                modifier =
                    Modifier.weight(1f)
            ) {
                Text("↓ Download")
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    items:
        List<FavoriteItem>,
    onOpen:
        (FavoriteItem) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState(
            "Your library is empty",
            "Save movies and series and they will appear here."
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(12.dp)
    ) {
        item {
            SectionTitle(
                "My Library"
            )
        }

        items(items) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 5.dp
                    )
                    .background(
                        Card,
                        RoundedCornerShape(
                            14.dp
                        )
                    )
                    .clickable {
                        onOpen(item)
                    }
                    .padding(12.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                AsyncImage(
                    model =
                        item.poster,
                    contentDescription =
                        item.title,
                    modifier =
                        Modifier
                            .width(66.dp)
                            .height(96.dp)
                            .clip(
                                RoundedCornerShape(
                                    10.dp
                                )
                            ),
                    contentScale =
                        ContentScale.Crop
                )

                Column(
                    Modifier
                        .weight(1f)
                        .padding(
                            start = 12.dp
                        )
                ) {
                    Text(
                        item.title,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        if (
                            item.type ==
                            "series"
                        ) "Series"
                        else "Movie",
                        color = Muted
                    )
                }

                Text("›")
            }
        }
    }
}

@Composable
private fun DownloadsScreen(
    downloads:
        List<DownloadRecord>,
    onClear: () -> Unit
) {
    val context =
        LocalContext.current

    var snapshots by remember {
        mutableStateOf<
            Map<
                Long,
                DownloadSnapshot
            >
        >(
            emptyMap()
        )
    }

    LaunchedEffect(
        downloads
    ) {
        while (true) {
            snapshots =
                downloads
                    .mapNotNull {
                        record ->

                        queryDownloadSnapshot(
                            context,
                            record.downloadId
                        )?.let {
                            record.downloadId
                                to it
                        }
                    }
                    .toMap()

            delay(1000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(14.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            )
    ) {
        item {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f)
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

                    Text(
                        "Managed inside TheNkiri",
                        color = Muted
                    )
                }

                if (
                    downloads
                        .isNotEmpty()
                ) {
                    TextButton(
                        onClick =
                            onClear
                    ) {
                        Text(
                            "Clear history"
                        )
                    }
                }
            }
        }

        if (
            downloads.isEmpty()
        ) {
            item {
                EmptyState(
                    "Nothing downloading",
                    "Your offline movies and episodes will show here."
                )
            }
        }

        items(
            downloads,
            key = {
                it.downloadId
            }
        ) { record ->
            val snap =
                snapshots[
                    record.downloadId
                ]

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Card,
                        RoundedCornerShape(
                            16.dp
                        )
                    )
                    .padding(14.dp)
            ) {
                Text(
                    record.title,
                    fontWeight =
                        FontWeight.Black
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
                    ).joinToString(
                        " • "
                    ),
                    color = Muted
                )

                if (snap != null) {
                    Text(
                        snap.label,
                        color =
                            if (
                                snap.status ==
                                DownloadManager
                                    .STATUS_FAILED
                            ) Color(
                                0xFFF87171
                            )
                            else Color(
                                0xFF86EFAC
                            ),
                        modifier =
                            Modifier.padding(
                                top = 8.dp
                            )
                    )

                    if (
                        snap.status ==
                        DownloadManager
                            .STATUS_RUNNING ||
                        snap.status ==
                        DownloadManager
                            .STATUS_PENDING
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                snap.progress
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = 8.dp
                                    )
                        )
                    }

                    Row(
                        Modifier.padding(
                            top = 10.dp
                        ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        if (
                            snap.status ==
                            DownloadManager
                                .STATUS_SUCCESSFUL
                        ) {
                            Button(
                                onClick = {
                                    runCatching {
                                        openCompletedDownload(
                                            context,
                                            record.downloadId
                                        )
                                    }
                                }
                            ) {
                                Text(
                                    "▶ Play"
                                )
                            }
                        } else if (
                            snap.status ==
                            DownloadManager
                                .STATUS_RUNNING ||
                            snap.status ==
                            DownloadManager
                                .STATUS_PENDING ||
                            snap.status ==
                            DownloadManager
                                .STATUS_PAUSED
                        ) {
                            OutlinedButton(
                                onClick = {
                                    cancelDownload(
                                        context,
                                        record.downloadId
                                    )
                                }
                            ) {
                                Text(
                                    "Cancel"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    history:
        List<PlaybackRecord>,
    onResume:
        (PlaybackRecord) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(14.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {
        item {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        "Watch History",
                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,
                        fontWeight =
                            FontWeight.Black
                    )
                    Text(
                        "Resume from where you stopped",
                        color = Muted
                    )
                }

                if (
                    history.isNotEmpty()
                ) {
                    TextButton(
                        onClick =
                            onClear
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        if (
            history.isEmpty()
        ) {
            item {
                EmptyState(
                    "No watch history",
                    "Anything you play will appear here."
                )
            }
        }

        items(
            history,
            key = {
                "${it.mediaId}:${it.season}:${it.episode}:${it.updatedAt}"
            }
        ) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Card,
                        RoundedCornerShape(
                            14.dp
                        )
                    )
                    .clickable {
                        onResume(item)
                    }
                    .padding(10.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription =
                        item.title,
                    modifier =
                        Modifier
                            .width(72.dp)
                            .height(96.dp)
                            .clip(
                                RoundedCornerShape(
                                    9.dp
                                )
                            ),
                    contentScale =
                        ContentScale.Crop
                )

                Column(
                    Modifier
                        .weight(1f)
                        .padding(
                            start = 12.dp
                        )
                ) {
                    Text(
                        item.title,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        item.episodeLabel
                            ?: if (
                                item.completed
                            ) "Watched"
                            else "Resume",
                        color = Muted
                    )

                    LinearProgressIndicator(
                        progress = {
                            item.progress
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 8.dp
                                )
                    )
                }

                Text("▶")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    onOpenUrl:
        (String) -> Unit,
    onShare: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(14.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
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

        state.remoteNotice
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                item {
                    SettingCard(
                        "Notice"
                    ) {
                        Text(
                            it,
                            color = Muted
                        )
                    }
                }
            }

        item {
            SettingCard(
                "Connection"
            ) {
                Text(
                    state.apiStatus,
                    color = Muted
                )
            }
        }

        item {
            SettingCard(
                "Playback"
            ) {
                Text(
                    "Built-in player • resume • history • Picture-in-Picture • subtitles • auto-next",
                    color = Muted
                )
            }
        }

        item {
            SettingCard(
                "Downloads"
            ) {
                Text(
                    "In-app manager • background transfer • full-season queue • offline playback",
                    color = Muted
                )
            }
        }

        item {
            SettingCard(
                "TheNkiri"
            ) {
                SettingButton(
                    "Updates channel"
                ) {
                    onOpenUrl(
                        state.channelUrl
                    )
                }

                SettingButton(
                    "Telegram bot"
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
            }
        }

        item {
            Text(
                "TheNkiri ${BuildConfig.VERSION_NAME}",
                color = Muted
            )
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    content:
        @Composable
        ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Card,
                RoundedCornerShape(
                    16.dp
                )
            )
            .padding(14.dp)
    ) {
        Text(
            title,
            fontWeight =
                FontWeight.Black
        )

        Spacer(
            Modifier.height(8.dp)
        )

        content()
    }
}

@Composable
private fun SettingButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
private fun AppBottomBar(
    selected: RootTab,
    onSelect:
        (RootTab) -> Unit
) {
    NavigationBar(
        containerColor =
            Color(0xFF0D0F12)
    ) {
        val items =
            listOf(
                RootTab.HOME
                    to "Home",
                RootTab.FAVORITES
                    to "Library",
                RootTab.DOWNLOADS
                    to "Downloads",
                RootTab.HISTORY
                    to "History",
                RootTab.SETTINGS
                    to "Settings"
            )

        items.forEach {
            (tab, label) ->

            NavigationBarItem(
                selected =
                    selected == tab,
                onClick = {
                    onSelect(tab)
                },
                icon = {
                    Text(
                        when (tab) {
                            RootTab.HOME ->
                                "⌂"
                            RootTab.FAVORITES ->
                                "★"
                            RootTab.DOWNLOADS ->
                                "↓"
                            RootTab.HISTORY ->
                                "◷"
                            RootTab.SETTINGS ->
                                "⚙"
                        }
                    )
                },
                label = {
                    Text(label)
                }
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top = 64.dp,
                start = 24.dp,
                end = 24.dp
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
            color = Muted,
            modifier =
                Modifier.padding(
                    top = 7.dp
                )
        )
    }
}
