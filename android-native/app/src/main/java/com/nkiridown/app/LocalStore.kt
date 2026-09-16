package com.nkiridown.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(
    context: Context
) {
    private val prefs =
        context.getSharedPreferences(
            "nkiri_app",
            Context.MODE_PRIVATE
        )

    fun apiBaseUrl(): String =
        prefs.getString(
            "api_base_url",
            BuildConfig.API_BASE_URL
        )
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf {
                it.startsWith("http://") ||
                it.startsWith("https://")
            }
            ?: BuildConfig.API_BASE_URL

    fun setApiBaseUrl(
        value: String
    ) {
        prefs.edit()
            .putString(
                "api_base_url",
                value.trim().trimEnd('/')
            )
            .apply()
    }

    fun recentSearches(): List<String> =
        readStringArray(
            "recent_searches"
        )

    fun addRecentSearch(
        value: String
    ) {
        val q = value.trim()
        if (q.isBlank()) return

        val values =
            recentSearches()
                .filterNot {
                    it.equals(
                        q,
                        ignoreCase = true
                    )
                }
                .toMutableList()

        values.add(0, q)

        writeStringArray(
            "recent_searches",
            values.take(12)
        )
    }

    fun clearRecentSearches() {
        prefs.edit()
            .remove(
                "recent_searches"
            )
            .apply()
    }

    fun favorites(): List<FavoriteItem> {
        val raw =
            prefs.getString(
                "favorites",
                "[]"
            ) ?: "[]"

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (
                    index in 0 until array.length()
                ) {
                    val item =
                        array.getJSONObject(index)

                    add(
                        FavoriteItem(
                            id =
                                item.optString("id"),
                            title =
                                item.optString("title"),
                            type =
                                item.optString(
                                    "type",
                                    "movie"
                                ),
                            poster =
                                item.optString("poster")
                                    .takeIf {
                                        it.isNotBlank() &&
                                        it != "null"
                                    },
                            provider =
                                item.optString(
                                    "provider",
                                    "moviex"
                                )
                        )
                    )
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    fun isFavorite(
        id: String
    ): Boolean =
        favorites().any {
            it.id == id
        }

    fun toggleFavorite(
        item: FavoriteItem
    ): Boolean {
        val values =
            favorites()
                .toMutableList()

        val index =
            values.indexOfFirst {
                it.id == item.id
            }

        val nowFavorite =
            if (index >= 0) {
                values.removeAt(index)
                false
            } else {
                values.add(0, item)
                true
            }

        val array =
            JSONArray()

        values.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("type", it.type)
                    .put("poster", it.poster)
                    .put("provider", it.provider)
            )
        }

        prefs.edit()
            .putString(
                "favorites",
                array.toString()
            )
            .apply()

        return nowFavorite
    }

    fun downloads(): List<DownloadRecord> {
        val raw =
            prefs.getString(
                "downloads",
                "[]"
            ) ?: "[]"

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (
                    index in 0 until array.length()
                ) {
                    val item =
                        array.getJSONObject(index)

                    add(
                        DownloadRecord(
                            downloadId =
                                item.optLong("downloadId"),
                            mediaId =
                                item.optString("mediaId"),
                            title =
                                item.optString("title"),
                            episodeLabel =
                                item.optString("episodeLabel")
                                    .takeIf {
                                        it.isNotBlank() &&
                                        it != "null"
                                    },
                            quality =
                                item.optInt("quality"),
                            sizeText =
                                item.optString("sizeText"),
                            createdAt =
                                item.optLong("createdAt")
                        )
                    )
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    fun addDownload(
        record: DownloadRecord
    ) {
        val values =
            downloads()
                .toMutableList()

        values.add(0, record)

        val array =
            JSONArray()

        values.take(100)
            .forEach {
                array.put(
                    JSONObject()
                        .put(
                            "downloadId",
                            it.downloadId
                        )
                        .put(
                            "mediaId",
                            it.mediaId
                        )
                        .put(
                            "title",
                            it.title
                        )
                        .put(
                            "episodeLabel",
                            it.episodeLabel
                        )
                        .put(
                            "quality",
                            it.quality
                        )
                        .put(
                            "sizeText",
                            it.sizeText
                        )
                        .put(
                            "createdAt",
                            it.createdAt
                        )
                )
            }

        prefs.edit()
            .putString(
                "downloads",
                array.toString()
            )
            .apply()
    }

    fun clearDownloads() {
        prefs.edit()
            .remove(
                "downloads"
            )
            .apply()
    }

    private fun readStringArray(
        key: String
    ): List<String> {
        val raw =
            prefs.getString(
                key,
                "[]"
            ) ?: "[]"

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (
                    index in 0 until array.length()
                ) {
                    val value =
                        array.optString(index)

                    if (value.isNotBlank()) {
                        add(value)
                    }
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    private fun writeStringArray(
        key: String,
        values: List<String>
    ) {
        val array =
            JSONArray()

        values.forEach {
            array.put(it)
        }

        prefs.edit()
            .putString(
                key,
                array.toString()
            )
            .apply()
    }
}
