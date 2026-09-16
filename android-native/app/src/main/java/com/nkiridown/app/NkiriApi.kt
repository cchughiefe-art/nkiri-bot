package com.nkiridown.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ApiException(
    message: String
) : Exception(message)

class NkiriApi(
    baseUrl: String
) {
    val baseUrl: String =
        baseUrl.trim().trimEnd('/')

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                20,
                TimeUnit.SECONDS
            )
            .readTimeout(
                75,
                TimeUnit.SECONDS
            )
            .callTimeout(
                90,
                TimeUnit.SECONDS
            )
            .retryOnConnectionFailure(
                true
            )
            .build()

    private suspend fun get(
        path: String
    ): JSONObject =
        withContext(
            Dispatchers.IO
        ) {
            val request =
                Request.Builder()
                    .url(
                        "$baseUrl$path"
                    )
                    .header(
                        "Accept",
                        "application/json"
                    )
                    .header(
                        "User-Agent",
                        "TheNkiri-Android/1.0"
                    )
                    .get()
                    .build()

            var lastError:
                Exception? = null

            repeat(2) {
                try {
                    client.newCall(request)
                        .execute()
                        .use {
                            response ->
                            val body =
                                response.body
                                    ?.string()
                                    .orEmpty()

                            val json =
                                runCatching {
                                    JSONObject(body)
                                }.getOrElse {
                                    throw ApiException(
                                        "Server returned an invalid response (${response.code})."
                                    )
                                }

                            if (
                                !response.isSuccessful ||
                                json.optString(
                                    "status"
                                ) != "success"
                            ) {
                                val message =
                                    json.optJSONObject(
                                        "error"
                                    )
                                        ?.optString(
                                            "message"
                                        )
                                        ?.takeIf {
                                            it.isNotBlank()
                                        }
                                        ?: "Request failed (${response.code})."

                                throw ApiException(
                                    message
                                )
                            }

                            return@withContext json
                        }
                } catch (
                    error: Exception
                ) {
                    lastError = error
                }
            }

            throw lastError
                ?: ApiException(
                    "Network request failed."
                )
        }


    suspend fun config(): RemoteAppConfig {
        val data =
            get("/api/config")
                .getJSONObject("data")

        return RemoteAppConfig(
            latestVersionCode =
                data.optInt(
                    "latestVersionCode",
                    BuildConfig.VERSION_CODE
                ),
            latestVersionName =
                data.optString(
                    "latestVersionName",
                    BuildConfig.VERSION_NAME
                ),
            updateUrl =
                data.optStringOrNull(
                    "updateUrl"
                ),
            forceUpdate =
                data.optBoolean(
                    "forceUpdate",
                    false
                ),
            notice =
                data.optStringOrNull(
                    "notice"
                ),
            channelUrl =
                data.optString(
                    "channelUrl",
                    "https://t.me/voidupdatezone"
                ),
            botUrl =
                data.optString(
                    "botUrl",
                    "https://t.me/nkiridownbot"
                )
        )
    }

    suspend fun health():
        String {
        val data =
            get("/health")
                .getJSONObject(
                    "data"
                )

        return data.optString(
            "name",
            "TheNkiri App API"
        )
    }

    suspend fun search(
        query: String
    ): List<SearchItem> {
        val encoded =
            query.urlPart()

        val array =
            get(
                "/api/search?q=$encoded&perPage=20"
            )
                .getJSONArray(
                    "data"
                )

        return array.toSearchItems()
    }

    suspend fun latest(
        type: String
    ): List<SearchItem> {
        val array =
            get(
                "/api/latest?type=${type.urlPart()}&perPage=20"
            )
                .getJSONArray(
                    "data"
                )

        return array.toSearchItems()
    }

    suspend fun title(
        id: String
    ): TitleInfo {
        val data =
            get(
                "/api/title/${id.urlPart()}"
            )
                .getJSONObject(
                    "data"
                )

        return TitleInfo(
            id =
                data.optString("id"),
            provider =
                data.optString(
                    "provider",
                    "moviex"
                ),
            type =
                data.optString(
                    "type",
                    "movie"
                ),
            title =
                data.optString("title"),
            description =
                data.optString(
                    "description"
                ),
            year =
                data.optIntOrNull(
                    "year"
                ),
            genre =
                data.optString("genre"),
            country =
                data.optStringOrNull(
                    "country"
                ),
            rating =
                data.optDoubleOrNull(
                    "rating"
                ),
            poster =
                data.optStringOrNull(
                    "poster"
                ),
            subtitles =
                data.optString(
                    "subtitles"
                ),
            trailer =
                data.optStringOrNull(
                    "trailer"
                ),
            seasons =
                data.optJSONArray(
                    "seasons"
                ).toSeasons()
        )
    }

    suspend fun episodes(
        id: String,
        season: Int
    ): List<EpisodeItem> {
        val data =
            get(
                "/api/title/${id.urlPart()}/episodes?season=$season"
            )
                .getJSONObject(
                    "data"
                )

        val array =
            data.optJSONArray(
                "episodes"
            ) ?: JSONArray()

        return buildList {
            for (
                i in 0 until array.length()
            ) {
                val item =
                    array.getJSONObject(i)

                add(
                    EpisodeItem(
                        season =
                            item.optInt(
                                "season"
                            ),
                        episode =
                            item.optInt(
                                "episode"
                            ),
                        label =
                            item.optString(
                                "label"
                            )
                    )
                )
            }
        }
    }

    suspend fun sources(
        id: String,
        season: Int? = null,
        episode: Int? = null,
        quality: Int? = null
    ): SourceResponse {
        val params =
            mutableListOf<String>()

        if (season != null) {
            params += "season=$season"
        }

        if (episode != null) {
            params += "episode=$episode"
        }

        if (quality != null) {
            params += "quality=$quality"
        }

        val suffix =
            if (params.isEmpty()) {
                ""
            } else {
                "?${params.joinToString("&")}"
            }

        val data =
            get(
                "/api/source/${id.urlPart()}$suffix"
            )
                .getJSONObject(
                    "data"
                )

        return SourceResponse(
            provider =
                data.optString(
                    "provider",
                    "moviex"
                ),
            sources =
                data.optJSONArray(
                    "sources"
                ).toSources(),
            selected =
                data.optJSONObject(
                    "selected"
                )?.toSource()
        )
    }

    private fun String.urlPart():
        String =
        URLEncoder.encode(
            this,
            StandardCharsets.UTF_8.toString()
        ).replace(
            "+",
            "%20"
        )

    private fun JSONArray.toSearchItems():
        List<SearchItem> =
        buildList {
            for (
                i in 0 until length()
            ) {
                val item =
                    getJSONObject(i)

                add(
                    SearchItem(
                        id =
                            item.optString("id"),
                        title =
                            item.optString(
                                "title"
                            ),
                        displayTitle =
                            item.optString(
                                "displayTitle",
                                item.optString(
                                    "title"
                                )
                            ),
                        year =
                            item.optIntOrNull(
                                "year"
                            ),
                        type =
                            item.optString(
                                "type",
                                "movie"
                            ),
                        poster =
                            item.optStringOrNull(
                                "poster"
                            ),
                        rating =
                            item.optDoubleOrNull(
                                "rating"
                            ),
                        genre =
                            item.optString(
                                "genre"
                            ),
                        provider =
                            item.optString(
                                "provider",
                                "moviex"
                            )
                    )
                )
            }
        }

    private fun JSONArray?.toSeasons():
        List<SeasonItem> {
        val array =
            this ?: return emptyList()

        return buildList {
            for (
                i in 0 until array.length()
            ) {
                val item =
                    array.getJSONObject(i)

                add(
                    SeasonItem(
                        season =
                            item.optInt(
                                "season"
                            ),
                        maxEp =
                            item.optInt(
                                "maxEp"
                            )
                    )
                )
            }
        }
    }

    private fun JSONArray?.toSources():
        List<SourceItem> {
        val array =
            this ?: return emptyList()

        return buildList {
            for (
                i in 0 until array.length()
            ) {
                add(
                    array.getJSONObject(i)
                        .toSource()
                )
            }
        }
    }

    private fun JSONObject.toSource():
        SourceItem =
        SourceItem(
            quality =
                optInt("quality"),
            size =
                optLong("size"),
            sizeText =
                optString(
                    "sizeText"
                ),
            format =
                optString(
                    "format",
                    "mp4"
                ),
            url =
                optStringOrNull(
                    "url"
                )
        )
}

private fun JSONObject.optStringOrNull(
    key: String
): String? {
    if (isNull(key)) return null

    return optString(key)
        .takeIf {
            it.isNotBlank() &&
            it != "null"
        }
}

private fun JSONObject.optIntOrNull(
    key: String
): Int? {
    if (isNull(key)) return null

    val value =
        optInt(
            key,
            Int.MIN_VALUE
        )

    return value.takeUnless {
        it == Int.MIN_VALUE
    }
}

private fun JSONObject.optDoubleOrNull(
    key: String
): Double? {
    if (isNull(key)) return null

    val value =
        optDouble(
            key,
            Double.NaN
        )

    return value.takeUnless {
        it.isNaN()
    }
}
