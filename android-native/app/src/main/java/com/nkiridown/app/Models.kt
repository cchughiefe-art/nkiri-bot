package com.nkiridown.app

data class SearchItem(
    val id: String,
    val title: String,
    val displayTitle: String,
    val year: Int?,
    val type: String,
    val poster: String?,
    val rating: Double?,
    val genre: String
)

data class SeasonItem(
    val season: Int,
    val maxEp: Int
)

data class TitleInfo(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val year: Int?,
    val genre: String,
    val country: String?,
    val rating: Double?,
    val poster: String?,
    val subtitles: String,
    val trailer: String?,
    val seasons: List<SeasonItem>
)

data class EpisodeItem(
    val season: Int,
    val episode: Int,
    val label: String
)

data class SourceItem(
    val quality: Int,
    val size: Long,
    val sizeText: String,
    val format: String,
    val url: String?
)

data class SourceResponse(
    val sources: List<SourceItem>,
    val selected: SourceItem?
)
