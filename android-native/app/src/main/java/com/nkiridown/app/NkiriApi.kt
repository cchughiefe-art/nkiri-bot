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

class ApiException(message: String) : Exception(message)

class NkiriApi(
    baseUrl: String = BuildConfig.API_BASE_URL
) {
    val baseUrl: String = baseUrl.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Accept", "application/json")
            .header("User-Agent", "TheNkiri-Android/0.1")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw ApiException("Server returned an invalid response (${response.code}).")
            }

            if (!response.isSuccessful || json.optString("status") != "success") {
                val message = json.optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Request failed (${response.code})."
                throw ApiException(message)
            }

            json
        }
    }

    suspend fun health(): String {
        val data = get("/health").getJSONObject("data")
        return data.optString("name", "TheNkiri App API")
    }

    suspend fun search(query: String): List<SearchItem> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val array = get("/api/search?q=$encoded").getJSONArray("data")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    SearchItem(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        displayTitle = item.optString("displayTitle", item.optString("title")),
                        year = item.optIntOrNull("year"),
                        type = item.optString("type", "movie"),
                        poster = item.optStringOrNull("poster"),
                        rating = item.optDoubleOrNull("rating"),
                        genre = item.optString("genre")
                    )
                )
            }
        }
    }

    suspend fun title(id: String): TitleInfo {
        val data = get("/api/title/${id.urlPart()}").getJSONObject("data")
        return TitleInfo(
            id = data.optString("id"),
            type = data.optString("type", "movie"),
            title = data.optString("title"),
            description = data.optString("description"),
            year = data.optIntOrNull("year"),
            genre = data.optString("genre"),
            country = data.optStringOrNull("country"),
            rating = data.optDoubleOrNull("rating"),
            poster = data.optStringOrNull("poster"),
            subtitles = data.optString("subtitles"),
            trailer = data.optStringOrNull("trailer"),
            seasons = data.optJSONArray("seasons").toSeasons()
        )
    }

    suspend fun episodes(id: String, season: Int): List<EpisodeItem> {
        val data = get(
            "/api/title/${id.urlPart()}/episodes?season=$season"
        ).getJSONObject("data")

        val array = data.optJSONArray("episodes") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    EpisodeItem(
                        season = item.optInt("season"),
                        episode = item.optInt("episode"),
                        label = item.optString("label")
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
        val params = mutableListOf<String>()
        if (season != null) params += "season=$season"
        if (episode != null) params += "episode=$episode"
        if (quality != null) params += "quality=$quality"

        val suffix = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        val data = get("/api/source/${id.urlPart()}$suffix").getJSONObject("data")

        return SourceResponse(
            sources = data.optJSONArray("sources").toSources(),
            selected = data.optJSONObject("selected")?.toSource()
        )
    }

    private fun String.urlPart(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

    private fun JSONArray?.toSeasons(): List<SeasonItem> {
        val array = this ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    SeasonItem(
                        season = item.optInt("season"),
                        maxEp = item.optInt("maxEp")
                    )
                )
            }
        }
    }

    private fun JSONArray?.toSources(): List<SourceItem> {
        val array = this ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(array.getJSONObject(i).toSource())
            }
        }
    }

    private fun JSONObject.toSource() = SourceItem(
        quality = optInt("quality"),
        size = optLong("size"),
        sizeText = optString("sizeText"),
        format = optString("format", "mp4"),
        url = optStringOrNull("url")
    )
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (isNull(key)) return null
    return optInt(key).takeIf { it != 0 }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return value.takeUnless { it.isNaN() || it == 0.0 }
}
