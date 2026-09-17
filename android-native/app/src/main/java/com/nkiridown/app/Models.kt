package com.nkiridown.app

data class SearchItem(
    val id: String,
    val title: String,
    val displayTitle: String,
    val year: Int?,
    val type: String,
    val poster: String?,
    val rating: Double?,
    val genre: String,
    val provider: String
)

data class SeasonItem(val season: Int, val maxEp: Int)

data class TitleInfo(
    val id: String,
    val provider: String,
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
    val url: String?,
    val type: String = "direct",
    val external: Boolean = false,
    val pageUrl: String? = null
)

data class SourceResponse(
    val provider: String,
    val sources: List<SourceItem>,
    val selected: SourceItem?
)

data class FavoriteItem(
    val id: String,
    val title: String,
    val type: String,
    val poster: String?,
    val provider: String
)

data class DownloadRecord(
    val downloadId: Long,
    val mediaId: String,
    val title: String,
    val episodeLabel: String?,
    val quality: Int,
    val sizeText: String,
    val createdAt: Long
)

data class PlaybackRecord(
    val mediaId: String,
    val title: String,
    val poster: String?,
    val provider: String,
    val type: String,
    val season: Int?,
    val episode: Int?,
    val episodeLabel: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs.toDouble())
                .coerceIn(0.0, 1.0).toFloat()
        } else 0f
}

data class RemoteAppConfig(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateUrl: String?,
    val forceUpdate: Boolean,
    val notice: String?,
    val channelUrl: String,
    val botUrl: String
)
