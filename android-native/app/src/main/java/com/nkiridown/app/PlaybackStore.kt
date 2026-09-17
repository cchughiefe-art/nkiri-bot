package com.nkiridown.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlaybackStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("nkiri_playback", Context.MODE_PRIVATE)

    fun all(): List<PlaybackRecord> {
        val raw = prefs.getString("history", "[]") ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        PlaybackRecord(
                            mediaId = o.optString("mediaId"),
                            title = o.optString("title"),
                            poster = o.optString("poster")
                                .takeIf { it.isNotBlank() && it != "null" },
                            provider = o.optString("provider", "moviex"),
                            type = o.optString("type", "movie"),
                            season = if (o.isNull("season")) null else o.optInt("season"),
                            episode = if (o.isNull("episode")) null else o.optInt("episode"),
                            episodeLabel = o.optString("episodeLabel")
                                .takeIf { it.isNotBlank() && it != "null" },
                            positionMs = o.optLong("positionMs"),
                            durationMs = o.optLong("durationMs"),
                            updatedAt = o.optLong("updatedAt"),
                            completed = o.optBoolean("completed", false)
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun continueWatching(): List<PlaybackRecord> =
        all().filter {
            !it.completed &&
            it.positionMs > 15_000L &&
            it.durationMs > 30_000L
        }

    fun history(): List<PlaybackRecord> = all()

    fun find(
        mediaId: String,
        season: Int?,
        episode: Int?
    ): PlaybackRecord? =
        all().firstOrNull {
            it.mediaId == mediaId &&
            it.season == season &&
            it.episode == episode
        }

    fun save(record: PlaybackRecord) {
        val items = all().toMutableList()
        items.removeAll {
            it.mediaId == record.mediaId &&
            it.season == record.season &&
            it.episode == record.episode
        }
        items.add(0, record)
        write(items.take(200))
    }

    fun clear() {
        prefs.edit().remove("history").apply()
    }

    private fun write(items: List<PlaybackRecord>) {
        val array = JSONArray()

        items.forEach {
            array.put(
                JSONObject()
                    .put("mediaId", it.mediaId)
                    .put("title", it.title)
                    .put("poster", it.poster)
                    .put("provider", it.provider)
                    .put("type", it.type)
                    .put("season", it.season)
                    .put("episode", it.episode)
                    .put("episodeLabel", it.episodeLabel)
                    .put("positionMs", it.positionMs)
                    .put("durationMs", it.durationMs)
                    .put("updatedAt", it.updatedAt)
                    .put("completed", it.completed)
            )
        }

        prefs.edit()
            .putString("history", array.toString())
            .apply()
    }
}
