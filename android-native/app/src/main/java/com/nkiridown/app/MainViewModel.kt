package com.nkiridown.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen {
    SEARCH,
    TITLE,
    EPISODES,
    QUALITIES
}

data class UiState(
    val screen: Screen = Screen.SEARCH,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<SearchItem> = emptyList(),
    val title: TitleInfo? = null,
    val season: Int? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val episode: EpisodeItem? = null,
    val sources: List<SourceItem> = emptyList(),
    val apiName: String = "Connecting…"
)

class MainViewModel(
    private val api: NkiriApi = NkiriApi()
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { api.health() }
                .onSuccess { name ->
                    _state.value = _state.value.copy(apiName = name)
                }
                .onFailure {
                    _state.value = _state.value.copy(apiName = "API offline")
                }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return

        launchRequest {
            val results = api.search(q)
            _state.value = _state.value.copy(
                screen = Screen.SEARCH,
                results = results
            )
        }
    }

    fun openTitle(item: SearchItem) {
        launchRequest {
            val info = api.title(item.id)
            val sources = if (info.type == "movie") {
                api.sources(info.id).sources
            } else {
                emptyList()
            }

            _state.value = _state.value.copy(
                screen = Screen.TITLE,
                title = info,
                season = null,
                episodes = emptyList(),
                episode = null,
                sources = sources
            )
        }
    }

    fun openSeason(season: Int) {
        val title = _state.value.title ?: return

        launchRequest {
            val episodes = api.episodes(title.id, season)
            _state.value = _state.value.copy(
                screen = Screen.EPISODES,
                season = season,
                episodes = episodes,
                episode = null,
                sources = emptyList()
            )
        }
    }

    fun openEpisode(episode: EpisodeItem) {
        val title = _state.value.title ?: return

        launchRequest {
            val sources = api.sources(
                id = title.id,
                season = episode.season,
                episode = episode.episode
            ).sources

            _state.value = _state.value.copy(
                screen = Screen.QUALITIES,
                episode = episode,
                sources = sources
            )
        }
    }

    fun back() {
        val current = _state.value
        _state.value = when (current.screen) {
            Screen.QUALITIES -> current.copy(
                screen = Screen.EPISODES,
                error = null
            )
            Screen.EPISODES -> current.copy(
                screen = Screen.TITLE,
                error = null,
                episode = null,
                sources = emptyList()
            )
            Screen.TITLE -> current.copy(
                screen = Screen.SEARCH,
                error = null,
                title = null,
                season = null,
                episodes = emptyList(),
                episode = null,
                sources = emptyList()
            )
            Screen.SEARCH -> current
        }
    }

    private fun launchRequest(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            try {
                block()
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    error = error.message ?: "Something went wrong."
                )
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}
