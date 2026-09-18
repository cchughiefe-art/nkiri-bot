package com.nkiridown.app.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nkiridown.app.AppPreferences
import com.nkiridown.app.DetailScreen
import com.nkiridown.app.FeatureFlags
import com.nkiridown.app.MainViewModel
import com.nkiridown.app.NkiriV2App
import com.nkiridown.app.PlaybackRecord
import com.nkiridown.app.SourceItem
import com.nkiridown.app.ui.screens.details.*
import com.nkiridown.app.ui.screens.discover.DiscoverScreen
import com.nkiridown.app.ui.screens.downloads.DownloadsScreen
import com.nkiridown.app.ui.screens.home.HomeScreen
import com.nkiridown.app.ui.screens.search.SearchScreen
import com.nkiridown.app.ui.screens.you.YouScreen
import com.nkiridown.app.ui.theme.*

@Composable
fun TheNkiriRoot(
    viewModel: MainViewModel,
    onDownload: (SourceItem) -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownloadSeason: (Int) -> Unit,
    onResumePlayback: (PlaybackRecord) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit
) {
    if (FeatureFlags.PREMIUM_UI) {
        PremiumNkiriTheme {
            NkiriApp(
                viewModel,
                onDownload,
                onPlay,
                onDownloadSeason,
                onResumePlayback,
                onOpenUrl,
                onShare
            )
        }
    } else {
        NkiriV2App(
            viewModel,
            onDownload,
            onPlay,
            onDownloadSeason,
            onResumePlayback,
            onOpenUrl,
            onShare
        )
    }
}

@Composable
private fun NkiriApp(
    viewModel: MainViewModel,
    onDownload: (SourceItem) -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownloadSeason: (Int) -> Unit,
    onResumePlayback: (PlaybackRecord) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var youSection by rememberSaveable { mutableStateOf(YouSection.OVERVIEW) }
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context.applicationContext) }
    var dataSaver by rememberSaveable { mutableStateOf(prefs.dataSaverEnabled()) }

    BackHandler(enabled = state.detailScreen != DetailScreen.NONE) {
        viewModel.back()
    }

    Scaffold(
        containerColor = NkiriBackground,
        bottomBar = {
            if (state.detailScreen == DetailScreen.NONE) {
                NavigationBar(
                    containerColor = NkiriSurface,
                    tonalElevation = 0.dp
                ) {
                    NavItem(AppDestination.HOME, destination, Icons.Default.Home, "Home") { destination = it }
                    if (FeatureFlags.DISCOVER) {
                        NavItem(AppDestination.DISCOVER, destination, Icons.Default.Explore, "Discover") { destination = it }
                    }
                    if (FeatureFlags.NEW_SEARCH) {
                        NavItem(AppDestination.SEARCH, destination, Icons.Default.Search, "Search") { destination = it }
                    }
                    NavItem(AppDestination.DOWNLOADS, destination, Icons.Default.Download, "Downloads") { destination = it }
                    NavItem(AppDestination.YOU, destination, Icons.Default.Person, "You") {
                        destination = it
                        youSection = YouSection.OVERVIEW
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.detailScreen) {
                DetailScreen.TITLE ->
                    TitleDetailsScreen(
                        state = state,
                        onBack = viewModel::back,
                        onFavorite = viewModel::toggleFavorite,
                        onSeason = viewModel::openSeason,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onDownloadSeason = onDownloadSeason,
                        onOpenTitle = viewModel::openSearchItem,
                        onOpenUrl = onOpenUrl
                    )

                DetailScreen.EPISODES ->
                    EpisodesScreen(
                        state = state,
                        onBack = viewModel::back,
                        onEpisode = viewModel::openEpisode,
                        onDownloadSeason = onDownloadSeason
                    )

                DetailScreen.QUALITIES ->
                    QualityScreen(
                        state = state,
                        onBack = viewModel::back,
                        onPlay = onPlay,
                        onDownload = onDownload
                    )

                DetailScreen.NONE ->
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            fadeIn(tween(NkiriMotion.ScreenMs)) togetherWith
                                fadeOut(tween(NkiriMotion.ScreenMs))
                        },
                        label = "rootNavigation"
                    ) { target ->
                        when (target) {
                            AppDestination.HOME ->
                                HomeScreen(
                                    state = state,
                                    onOpenSearch = { destination = AppDestination.SEARCH },
                                    onOpenProfile = {
                                        destination = AppDestination.YOU
                                        youSection = YouSection.OVERVIEW
                                    },
                                    onOpenTitle = viewModel::openSearchItem,
                                    onResume = onResumePlayback,
                                    onLibrary = viewModel::toggleFavorite,
                                    onRetry = viewModel::onNetworkAvailable
                                )

                            AppDestination.DISCOVER ->
                                DiscoverScreen(
                                    state = state,
                                    onFilter = viewModel::loadLatest,
                                    onOpenTitle = viewModel::openSearchItem
                                )

                            AppDestination.SEARCH ->
                                SearchScreen(
                                    state = state,
                                    onQueryChange = viewModel::setQuery,
                                    onSearch = viewModel::search,
                                    onClearRecent = viewModel::clearRecentSearches,
                                    onOpenTitle = viewModel::openSearchItem
                                )

                            AppDestination.DOWNLOADS ->
                                DownloadsScreen(
                                    onLegacyClear = viewModel::clearDownloads
                                )

                            AppDestination.YOU ->
                                YouScreen(
                                    state = state,
                                    section = youSection,
                                    dataSaver = dataSaver,
                                    onSection = { youSection = it },
                                    onDataSaver = {
                                        dataSaver = it
                                        prefs.setDataSaverEnabled(it)
                                    },
                                    onOpenFavorite = viewModel::openFavorite,
                                    onResume = onResumePlayback,
                                    onClearHistory = viewModel::clearHistory,
                                    onOpenUrl = onOpenUrl,
                                    onShare = onShare
                                )
                        }
                    }
            }

            if (state.forceUpdate && !state.updateUrl.isNullOrBlank()) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {
                        Button(onClick = { onOpenUrl(state.updateUrl!!) }) {
                            Text("Update now")
                        }
                    },
                    title = { Text("Update required") },
                    text = {
                        Text(
                            "A newer version of TheNkiri is required to continue."
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    item: AppDestination,
    selected: AppDestination,
    icon: ImageVector,
    label: String,
    onSelect: (AppDestination) -> Unit
) {
    NavigationBarItem(
        selected = item == selected,
        onClick = { onSelect(item) },
        icon = {
            Icon(
                icon,
                contentDescription = label
            )
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = NkiriViolet,
            selectedTextColor = NkiriText,
            indicatorColor = NkiriPurple.copy(alpha = 0.20f),
            unselectedIconColor = NkiriMuted,
            unselectedTextColor = NkiriMuted
        )
    )
}
