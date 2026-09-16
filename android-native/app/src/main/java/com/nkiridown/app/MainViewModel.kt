package com.nkiridown.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RootTab {
    HOME,
    FAVORITES,
    DOWNLOADS,
    SETTINGS
}

enum class DetailScreen {
    NONE,
    TITLE,
    EPISODES,
    QUALITIES
}

data class UiState(
    val rootTab: RootTab = RootTab.HOME,
    val detailScreen: DetailScreen = DetailScreen.NONE,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val apiStatus: String = "Checking API…",
    val apiBaseUrl: String = "",
    val results: List<SearchItem> = emptyList(),
    val sectionTitle: String = "Discover",
    val title: TitleInfo? = null,
    val season: Int? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val episode: EpisodeItem? = null,
    val sources: List<SourceItem> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val downloads: List<DownloadRecord> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isCurrentFavorite: Boolean = false,
    val updateAvailable: Boolean = false,
    val forceUpdate: Boolean = false,
    val latestVersionName: String? = null,
    val updateUrl: String? = null,
    val remoteNotice: String? = null,
    val channelUrl: String = "https://t.me/voidupdatezone",
    val botUrl: String = "https://t.me/nkiridownbot",
    val lastCrash: String? = null
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val store =
        LocalStore(application)

    private var api =
        NkiriApi(
            store.apiBaseUrl()
        )

    private val _state =
        MutableStateFlow(
            UiState(
                apiBaseUrl =
                    store.apiBaseUrl(),
                favorites =
                    store.favorites(),
                downloads =
                    store.downloads(),
                recentSearches =
                    store.recentSearches(),
                lastCrash =
                    LocalCrashReporter.get(
                        application
                    )
            )
        )

    val state:
        StateFlow<UiState> =
        _state.asStateFlow()

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    loading = true
                )

            try {
                coroutineScope {
                    val health =
                        async {
                            runCatching {
                                api.health()
                            }
                        }

                    val config =
                        async {
                            runCatching {
                                api.config()
                            }
                        }

                    val latest =
                        async {
                            runCatching {
                                api.latest("all")
                            }
                        }

                    health.await()
                        .onSuccess {
                            name ->
                            _state.value =
                                _state.value.copy(
                                    apiStatus =
                                        "$name • Online"
                                )
                        }
                        .onFailure {
                            _state.value =
                                _state.value.copy(
                                    apiStatus =
                                        "API offline"
                                )
                        }

                    config.await()
                        .onSuccess {
                            applyRemoteConfig(
                                it
                            )
                        }

                    latest.await()
                        .onSuccess {
                            items ->
                            _state.value =
                                _state.value.copy(
                                    results =
                                        items,
                                    sectionTitle =
                                        "Discover"
                                )
                        }
                        .onFailure {
                            error ->
                            if (
                                _state.value.results
                                    .isEmpty()
                            ) {
                                _state.value =
                                    _state.value.copy(
                                        error =
                                            error.message
                                                ?: "Could not load the catalog."
                                    )
                            }
                        }
                }
            } finally {
                _state.value =
                    _state.value.copy(
                        loading = false
                    )
            }
        }
    }

    private fun applyRemoteConfig(
        config: RemoteAppConfig
    ) {
        _state.value =
            _state.value.copy(
                updateAvailable =
                    config.latestVersionCode >
                    BuildConfig.VERSION_CODE,
                forceUpdate =
                    config.forceUpdate,
                latestVersionName =
                    config.latestVersionName,
                updateUrl =
                    config.updateUrl,
                remoteNotice =
                    config.notice,
                channelUrl =
                    config.channelUrl,
                botUrl =
                    config.botUrl
            )
    }

    fun selectTab(
        tab: RootTab
    ) {
        _state.value =
            _state.value.copy(
                rootTab = tab,
                detailScreen =
                    DetailScreen.NONE,
                error = null
            )

        refreshLocalLists()
    }

    fun setQuery(
        value: String
    ) {
        _state.value =
            _state.value.copy(
                query = value
            )
    }

    fun clearError() {
        _state.value =
            _state.value.copy(
                error = null
            )
    }

    fun clearCrashLog() {
        LocalCrashReporter.clear(
            getApplication()
        )

        _state.value =
            _state.value.copy(
                lastCrash = null
            )
    }

    fun search(
        forcedQuery: String? = null
    ) {
        val q =
            (
                forcedQuery
                    ?: _state.value.query
            ).trim()

        if (q.length < 2) return

        _state.value =
            _state.value.copy(
                query = q
            )

        request {
            val results =
                api.search(q)

            store.addRecentSearch(q)

            _state.value =
                _state.value.copy(
                    rootTab =
                        RootTab.HOME,
                    detailScreen =
                        DetailScreen.NONE,
                    results = results,
                    sectionTitle =
                        "Search results",
                    recentSearches =
                        store.recentSearches()
                )
        }
    }

    fun loadLatest(
        type: String,
        label: String
    ) {
        request {
            val results =
                api.latest(type)

            _state.value =
                _state.value.copy(
                    rootTab =
                        RootTab.HOME,
                    detailScreen =
                        DetailScreen.NONE,
                    results = results,
                    sectionTitle =
                        label
                )
        }
    }

    fun openSearchItem(
        item: SearchItem
    ) {
        request {
            val info =
                api.title(item.id)

            val sources =
                if (
                    info.type ==
                    "movie"
                ) {
                    api.sources(
                        info.id
                    ).sources
                } else {
                    emptyList()
                }

            _state.value =
                _state.value.copy(
                    detailScreen =
                        DetailScreen.TITLE,
                    title = info,
                    season = null,
                    episodes =
                        emptyList(),
                    episode = null,
                    sources = sources,
                    isCurrentFavorite =
                        store.isFavorite(
                            info.id
                        )
                )
        }
    }

    fun openFavorite(
        item: FavoriteItem
    ) {
        openSearchItem(
            SearchItem(
                id = item.id,
                title = item.title,
                displayTitle = item.title,
                year = null,
                type = item.type,
                poster = item.poster,
                rating = null,
                genre = "",
                provider = item.provider
            )
        )
    }

    fun toggleFavorite() {
        val title =
            _state.value.title
                ?: return

        val favorite =
            store.toggleFavorite(
                FavoriteItem(
                    id = title.id,
                    title = title.title,
                    type = title.type,
                    poster = title.poster,
                    provider = title.provider
                )
            )

        _state.value =
            _state.value.copy(
                isCurrentFavorite =
                    favorite,
                favorites =
                    store.favorites()
            )
    }

    fun openSeason(
        season: Int
    ) {
        val title =
            _state.value.title
                ?: return

        request {
            val episodes =
                api.episodes(
                    title.id,
                    season
                )

            _state.value =
                _state.value.copy(
                    detailScreen =
                        DetailScreen.EPISODES,
                    season = season,
                    episodes = episodes,
                    episode = null,
                    sources =
                        emptyList()
                )
        }
    }

    fun openEpisode(
        episode: EpisodeItem
    ) {
        val title =
            _state.value.title
                ?: return

        request {
            val sources =
                api.sources(
                    id = title.id,
                    season = episode.season,
                    episode = episode.episode
                ).sources

            _state.value =
                _state.value.copy(
                    detailScreen =
                        DetailScreen.QUALITIES,
                    episode = episode,
                    sources = sources
                )
        }
    }

    fun resolveSource(
        source: SourceItem,
        onReady:
            (SourceItem) -> Unit
    ) {
        val title =
            _state.value.title
                ?: return

        request {
            val episode =
                _state.value.episode

            val response =
                api.sources(
                    id = title.id,
                    season = episode?.season,
                    episode = episode?.episode,
                    quality =
                        source.quality
                            .takeIf {
                                it > 0
                            }
                )

            val selected =
                response.selected
                    ?: response.sources
                        .firstOrNull()
                    ?: throw ApiException(
                        "No working source was returned."
                    )

            onReady(selected)
        }
    }

    fun recordDownload(
        downloadId: Long,
        source: SourceItem
    ) {
        val title =
            _state.value.title
                ?: return

        store.addDownload(
            DownloadRecord(
                downloadId =
                    downloadId,
                mediaId =
                    title.id,
                title =
                    title.title,
                episodeLabel =
                    _state.value.episode
                        ?.label,
                quality =
                    source.quality,
                sizeText =
                    source.sizeText,
                createdAt =
                    System.currentTimeMillis()
            )
        )

        _state.value =
            _state.value.copy(
                downloads =
                    store.downloads()
            )
    }

    fun clearDownloads() {
        store.clearDownloads()

        _state.value =
            _state.value.copy(
                downloads =
                    emptyList()
            )
    }

    fun clearRecentSearches() {
        store.clearRecentSearches()

        _state.value =
            _state.value.copy(
                recentSearches =
                    emptyList()
            )
    }

    fun saveApiBaseUrl(
        value: String
    ) {
        val normalized =
            value.trim().trimEnd('/')

        if (
            !normalized.startsWith(
                "http://"
            ) &&
            !normalized.startsWith(
                "https://"
            )
        ) {
            _state.value =
                _state.value.copy(
                    error =
                        "API URL must start with http:// or https://"
                )
            return
        }

        store.setApiBaseUrl(
            normalized
        )

        api =
            NkiriApi(normalized)

        _state.value =
            _state.value.copy(
                apiBaseUrl =
                    normalized,
                apiStatus =
                    "Checking API…"
            )

        checkApi()
    }

    fun checkApi() {
        viewModelScope.launch {
            val health =
                runCatching {
                    api.health()
                }

            health
                .onSuccess {
                    name ->
                    _state.value =
                        _state.value.copy(
                            apiStatus =
                                "$name • Online"
                        )
                }
                .onFailure {
                    _state.value =
                        _state.value.copy(
                            apiStatus =
                                "API offline"
                        )
                }

            runCatching {
                api.config()
            }.onSuccess {
                applyRemoteConfig(it)
            }
        }
    }

    fun back() {
        val current =
            _state.value

        _state.value =
            when (
                current.detailScreen
            ) {
                DetailScreen.QUALITIES ->
                    current.copy(
                        detailScreen =
                            DetailScreen.EPISODES,
                        error = null
                    )

                DetailScreen.EPISODES ->
                    current.copy(
                        detailScreen =
                            DetailScreen.TITLE,
                        error = null,
                        episode = null,
                        sources =
                            emptyList()
                    )

                DetailScreen.TITLE ->
                    current.copy(
                        detailScreen =
                            DetailScreen.NONE,
                        error = null,
                        title = null,
                        season = null,
                        episodes =
                            emptyList(),
                        episode = null,
                        sources =
                            emptyList()
                    )

                DetailScreen.NONE ->
                    current
            }
    }

    private fun refreshLocalLists() {
        _state.value =
            _state.value.copy(
                favorites =
                    store.favorites(),
                downloads =
                    store.downloads(),
                recentSearches =
                    store.recentSearches()
            )
    }

    private fun request(
        block:
            suspend () -> Unit
    ) {
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    loading = true,
                    error = null
                )

            try {
                block()
            } catch (
                error: Exception
            ) {
                _state.value =
                    _state.value.copy(
                        error =
                            error.message
                                ?: "Something went wrong."
                    )
            } finally {
                _state.value =
                    _state.value.copy(
                        loading = false
                    )
            }
        }
    }
}
