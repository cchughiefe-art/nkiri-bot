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
    HISTORY,
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
    val homeSections: List<HomeSection> = emptyList(),
    val recommendations: List<SearchItem> = emptyList(),
    val sectionTitle: String = "Discover",
    val title: TitleInfo? = null,
    val season: Int? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val episodeSources: Map<Int, SourceItem> = emptyMap(),
    val episode: EpisodeItem? = null,
    val sources: List<SourceItem> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val downloads: List<DownloadRecord> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val continueWatching: List<PlaybackRecord> = emptyList(),
    val history: List<PlaybackRecord> = emptyList(),
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

    private val playback =
        PlaybackStore(application)

    private val appPreferences =
        AppPreferences(application)

    private var api =
        NkiriApi(
            store.apiBaseUrl()
        )

    private val _state =
        MutableStateFlow(
            UiState(
                apiBaseUrl = store.apiBaseUrl(),
                favorites = store.favorites(),
                downloads = store.downloads(),
                recentSearches = store.recentSearches(),
                continueWatching =
                    playback.continueWatching(),
                history =
                    playback.history(),
                lastCrash =
                    LocalCrashReporter.get(application)
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

                    val home =
                        async {
                            runCatching {
                                api.home()
                            }
                        }

                    health.await()
                        .onSuccess {
                            _state.value =
                                _state.value.copy(
                                    apiStatus =
                                        "$it • Online"
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
                            applyRemoteConfig(it)
                        }

                    latest.await()
                        .onSuccess {
                            _state.value =
                                _state.value.copy(
                                    results = it,
                                    sectionTitle =
                                        "Discover"
                                )
                        }
                        .onFailure {
                            if (
                                _state.value.results
                                    .isEmpty()
                            ) {
                                _state.value =
                                    _state.value.copy(
                                        error =
                                            it.message
                                                ?: "Could not load the catalog."
                                    )
                            }
                        }

                    home.await()
                        .onSuccess { sections ->
                            if (sections.isNotEmpty()) {
                                _state.value =
                                    _state.value.copy(
                                        homeSections = sections,
                                        results =
                                            sections
                                                .firstOrNull()
                                                ?.items
                                                .orEmpty(),
                                        sectionTitle =
                                            "Discover"
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

    fun onNetworkAvailable() {
        viewModelScope.launch {
            runCatching {
                api.health()
            }.onSuccess {
                _state.value =
                    _state.value.copy(
                        apiStatus =
                            "$it • Online",
                        error = null
                    )
            }

            runCatching {
                api.config()
            }.onSuccess {
                applyRemoteConfig(it)
            }

            runCatching {
                api.home()
            }.onSuccess { sections ->
                if (sections.isNotEmpty()) {
                    _state.value =
                        _state.value.copy(
                            homeSections =
                                sections,
                            results =
                                if (
                                    _state.value.detailScreen ==
                                    DetailScreen.NONE &&
                                    _state.value.query.isBlank()
                                ) {
                                    sections
                                        .firstOrNull()
                                        ?.items
                                        .orEmpty()
                                } else {
                                    _state.value.results
                                }
                        )
                }
            }
        }
    }

    fun onNetworkLost() {
        _state.value =
            _state.value.copy(
                apiStatus =
                    "Offline • waiting for connection"
            )
    }

    fun refreshPlayback() {
        _state.value =
            _state.value.copy(
                continueWatching =
                    playback.continueWatching(),
                history =
                    playback.history()
            )
    }

    fun clearHistory() {
        playback.clear()
        refreshPlayback()
    }

    fun selectTab(tab: RootTab) {
        _state.value =
            _state.value.copy(
                rootTab = tab,
                detailScreen =
                    DetailScreen.NONE,
                error = null
            )

        refreshLocalLists()
        refreshPlayback()
    }

    fun setQuery(value: String) {
        _state.value =
            _state.value.copy(
                query = value
            )
    }

    fun clearRecentSearches() {
        store.clearRecentSearches()
        _state.value =
            _state.value.copy(
                recentSearches = emptyList()
            )
    }

    fun toggleFavorite(item: SearchItem) {
        val favorite =
            store.toggleFavorite(
                FavoriteItem(
                    id = item.id,
                    title = item.title,
                    type = item.type,
                    poster = item.poster,
                    provider = item.provider
                )
            )

        _state.value =
            _state.value.copy(
                favorites = store.favorites(),
                isCurrentFavorite =
                    if (_state.value.title?.id == item.id) favorite
                    else _state.value.isCurrentFavorite
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
                    rootTab = RootTab.HOME,
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
                    rootTab = RootTab.HOME,
                    detailScreen =
                        DetailScreen.NONE,
                    results = results,
                    sectionTitle = label
                )
        }
    }

    fun openSearchItem(
        item: SearchItem
    ) {
        val preview =
            TitleInfo(
                id = item.id,
                provider = item.provider,
                type = item.type,
                title = item.title,
                description = "",
                year = item.year,
                genre = item.genre,
                country = null,
                rating = item.rating,
                poster = item.poster,
                subtitles = "",
                trailer = null,
                seasons = emptyList()
            )

        _state.value =
            _state.value.copy(
                detailScreen = DetailScreen.TITLE,
                title = preview,
                season = null,
                episodes = emptyList(),
                episodeSources = emptyMap(),
                episode = null,
                sources = emptyList(),
                recommendations = emptyList(),
                isCurrentFavorite = store.isFavorite(item.id),
                loading = true,
                error = null
            )

        viewModelScope.launch {
            try {
                val info = api.title(item.id)

                if (
                    _state.value.title?.id != item.id ||
                    _state.value.detailScreen != DetailScreen.TITLE
                ) {
                    return@launch
                }

                _state.value =
                    _state.value.copy(
                        title = info,
                        isCurrentFavorite = store.isFavorite(info.id)
                    )

                coroutineScope {
                    val sources =
                        async {
                            if (info.type == "movie") {
                                runCatching {
                                    api.sources(info.id).sources
                                }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }
                        }

                    val recommendations =
                        async {
                            runCatching {
                                api.latest(
                                    if (info.type == "series") "series" else "movie"
                                )
                                    .filterNot { it.id == info.id }
                                    .take(12)
                            }.getOrDefault(emptyList())
                        }

                    val loadedSources = sources.await()
                    val loadedRecommendations = recommendations.await()

                    if (
                        _state.value.title?.id == item.id &&
                        _state.value.detailScreen == DetailScreen.TITLE
                    ) {
                        _state.value =
                            _state.value.copy(
                                sources = loadedSources,
                                recommendations = loadedRecommendations
                            )
                    }
                }
            } catch (error: Exception) {
                if (_state.value.title?.id == item.id) {
                    _state.value =
                        _state.value.copy(
                            error = error.message ?: "Could not load this title."
                        )
                }
            } finally {
                if (_state.value.title?.id == item.id) {
                    _state.value =
                        _state.value.copy(
                            loading = false
                        )
                }
            }
        }
    }

    fun openFavorite(
        item: FavoriteItem
    ) {
        openSearchItem(
            SearchItem(
                id = item.id,
                title = item.title,
                displayTitle =
                    item.title,
                year = null,
                type = item.type,
                poster = item.poster,
                rating = null,
                genre = "",
                provider =
                    item.provider
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
                    poster =
                        title.poster,
                    provider =
                        title.provider
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

        _state.value =
            _state.value.copy(
                detailScreen = DetailScreen.EPISODES,
                season = season,
                episodes = emptyList(),
                episodeSources = emptyMap(),
                episode = null,
                sources = emptyList(),
                loading = true,
                error = null
            )

        viewModelScope.launch {
            try {
                val episodes =
                    api.episodes(
                        title.id,
                        season
                    ).sortedBy {
                        it.episode
                    }

                if (
                    _state.value.title?.id == title.id &&
                    _state.value.season == season &&
                    _state.value.detailScreen == DetailScreen.EPISODES
                ) {
                    _state.value =
                        _state.value.copy(
                            episodes = episodes,
                            loading = false
                        )
                }

                for (episode in episodes) {
                    if (
                        _state.value.title?.id != title.id ||
                        _state.value.season != season ||
                        _state.value.detailScreen != DetailScreen.EPISODES
                    ) {
                        break
                    }

                    val response =
                        runCatching {
                            api.sources(
                                id = title.id,
                                season = episode.season,
                                episode = episode.episode
                            )
                        }.getOrNull()
                            ?: continue

                    val preferredQuality =
                        appPreferences.preferredQuality()

                    val source =
                        response.sources
                            .firstOrNull {
                                it.quality == preferredQuality &&
                                !it.external &&
                                !it.url.isNullOrBlank()
                            }
                            ?: response.selected
                                ?.takeIf {
                                    !it.external &&
                                    !it.url.isNullOrBlank()
                                }
                            ?: response.sources
                                .firstOrNull {
                                    !it.external &&
                                    !it.url.isNullOrBlank()
                                }
                            ?: continue

                    _state.value =
                        _state.value.copy(
                            episodeSources =
                                _state.value.episodeSources +
                                    (episode.episode to source)
                        )
                }
            } catch (error: Exception) {
                if (
                    _state.value.title?.id == title.id &&
                    _state.value.season == season
                ) {
                    _state.value =
                        _state.value.copy(
                            error =
                                error.message
                                    ?: "Could not load episodes."
                        )
                }
            } finally {
                if (
                    _state.value.title?.id == title.id &&
                    _state.value.season == season
                ) {
                    _state.value =
                        _state.value.copy(
                            loading = false
                        )
                }
            }
        }
    }

    fun openEpisode(
        episode: EpisodeItem
    ) {
        val title =
            _state.value.title
                ?: return

        _state.value =
            _state.value.copy(
                detailScreen = DetailScreen.QUALITIES,
                episode = episode,
                sources = emptyList(),
                loading = true,
                error = null
            )

        viewModelScope.launch {
            try {
                val sources =
                    api.sources(
                        id = title.id,
                        season = episode.season,
                        episode = episode.episode
                    ).sources

                if (
                    _state.value.title?.id == title.id &&
                    _state.value.episode == episode &&
                    _state.value.detailScreen == DetailScreen.QUALITIES
                ) {
                    _state.value =
                        _state.value.copy(
                            sources = sources
                        )
                }
            } catch (error: Exception) {
                if (
                    _state.value.title?.id == title.id &&
                    _state.value.episode == episode
                ) {
                    _state.value =
                        _state.value.copy(
                            error = error.message ?: "Could not load qualities."
                        )
                }
            } finally {
                if (
                    _state.value.title?.id == title.id &&
                    _state.value.episode == episode
                ) {
                    _state.value =
                        _state.value.copy(
                            loading = false
                        )
                }
            }
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
                    season =
                        episode?.season,
                    episode =
                        episode?.episode,
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

    fun resumePlayback(
        record: PlaybackRecord,
        onReady:
            (
                SourceItem,
                TitleInfo,
                EpisodeItem?
            ) -> Unit
    ) {
        request {
            val title =
                api.title(
                    record.mediaId
                )

            val episode =
                if (
                    record.season != null &&
                    record.episode != null
                ) {
                    EpisodeItem(
                        season =
                            record.season,
                        episode =
                            record.episode,
                        label =
                            record.episodeLabel
                                ?: "S${record.season.toString().padStart(2, '0')}E${record.episode.toString().padStart(2, '0')}"
                    )
                } else {
                    null
                }

            val response =
                api.sources(
                    id =
                        record.mediaId,
                    season =
                        record.season,
                    episode =
                        record.episode
                )

            val source =
                response.selected
                    ?: response.sources
                        .firstOrNull()
                    ?: throw ApiException(
                        "No playable source was returned."
                    )

            onReady(
                source,
                title,
                episode
            )
        }
    }

    fun downloadEpisodes(
        episodes: List<EpisodeItem>,
        onReady:
            (
                SourceItem,
                EpisodeItem
            ) -> Unit,
        onDone:
            (Int) -> Unit
    ) {
        val title =
            _state.value.title
                ?: return

        val ordered =
            episodes
                .distinctBy {
                    it.episode
                }
                .sortedBy {
                    it.episode
                }

        request {
            var queued = 0

            for (episode in ordered) {
                var source =
                    _state.value.episodeSources[
                        episode.episode
                    ]

                if (
                    source == null ||
                    source.external ||
                    source.url.isNullOrBlank()
                ) {
                    val response =
                        runCatching {
                            api.sources(
                                id = title.id,
                                season = episode.season,
                                episode = episode.episode
                            )
                        }.getOrNull()
                            ?: continue

                    val preferredQuality =
                        appPreferences.preferredQuality()

                    source =
                        response.sources
                            .firstOrNull {
                                it.quality == preferredQuality &&
                                !it.external &&
                                !it.url.isNullOrBlank()
                            }
                            ?: response.selected
                                ?.takeIf {
                                    !it.external &&
                                    !it.url.isNullOrBlank()
                                }
                            ?: response.sources
                                .firstOrNull {
                                    !it.external &&
                                    !it.url.isNullOrBlank()
                                }
                            ?: continue

                    _state.value =
                        _state.value.copy(
                            episodeSources =
                                _state.value.episodeSources +
                                    (episode.episode to source)
                        )
                }

                val readySource =
                    source
                        ?: continue

                if (
                    !readySource.external &&
                    !readySource.url.isNullOrBlank()
                ) {
                    onReady(
                        readySource,
                        episode
                    )
                    queued += 1
                }
            }

            onDone(queued)
        }
    }

    fun downloadSeason(
        season: Int,
        onReady:
            (
                SourceItem,
                EpisodeItem
            ) -> Unit,
        onDone:
            (Int) -> Unit
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

            var queued = 0

            for (
                episode in episodes
            ) {
                val response =
                    runCatching {
                        api.sources(
                            id =
                                title.id,
                            season =
                                episode.season,
                            episode =
                                episode.episode
                        )
                    }.getOrNull()
                        ?: continue

                val source =
                    response.selected
                        ?: response.sources
                            .firstOrNull()
                        ?: continue

                if (
                    !source.external &&
                    !source.url
                        .isNullOrBlank()
                ) {
                    onReady(
                        source,
                        episode
                    )
                    queued += 1
                }
            }

            onDone(queued)
        }
    }

    fun recordDownload(
        downloadId: Long,
        source: SourceItem,
        overrideEpisode:
            EpisodeItem? = null
    ) {
        val title =
            _state.value.title
                ?: return

        val episode =
            overrideEpisode
                ?: _state.value.episode

        store.addDownload(
            DownloadRecord(
                downloadId =
                    downloadId,
                mediaId =
                    title.id,
                title =
                    title.title,
                episodeLabel =
                    episode?.label,
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

    fun saveApiBaseUrl(
        value: String
    ) {
        val normalized =
            value.trim()
                .trimEnd('/')

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
            NkiriApi(
                normalized
            )

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
            runCatching {
                api.health()
            }.onSuccess {
                _state.value =
                    _state.value.copy(
                        apiStatus =
                            "$it • Online"
                    )
            }.onFailure {
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
                            emptyList(),
                        recommendations =
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
