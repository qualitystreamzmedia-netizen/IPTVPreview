package com.example.iptvpreview.data

import androidx.room.withTransaction
import com.example.iptvpreview.data.database.IptvDatabase
import com.example.iptvpreview.data.local.*
import com.example.iptvpreview.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun importXtreamVod(playlist: Playlist, client: OkHttpClient, db: IptvDatabase) = withContext(Dispatchers.IO) {
    require(playlist.type == PlaylistType.XTREAM) { "VOD import requires an Xtream playlist." }
    val config = JSONObject(playlist.configJson)
    val host = config.getString("host").trim().trimEnd('/').toHttpUrl()
    val user = config.getString("user")
    val pass = config.getString("pass")
    suspend fun api(action: String, seriesId: String? = null): String {
        currentCoroutineContext().ensureActive()
        val url = host.newBuilder().addPathSegment("player_api.php")
            .addQueryParameter("username", user).addQueryParameter("password", pass)
            .addQueryParameter("action", action).apply {
                if (seriesId != null) addQueryParameter("series_id", seriesId)
            }.build()
        val result = client.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "VOD request failed (HTTP ${it.code})." }
            it.body?.string() ?: error("Empty VOD response.")
        }
        currentCoroutineContext().ensureActive()
        return result
    }
    fun array(text: String, key: String): JSONArray = if (text.trimStart().startsWith("[")) JSONArray(text)
        else JSONObject(text).optJSONArray(key) ?: error("Invalid VOD response.")
    fun JSONObject.text(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
    fun JSONObject.positiveInt(key: String): Int? = optInt(key).takeIf { it > 0 }
    fun mediaUrl(kind: String, id: String, extension: String?) = host.newBuilder()
        .addPathSegment(kind).addPathSegment(user).addPathSegment(pass)
        .addPathSegment("$id.${extension?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) } ?: "mp4"}").build().toString()
    fun item(obj: JSONObject, info: JSONObject, id: String, type: VodType, name: String, season: Int? = null): VodItemEntity {
        val backdrop = info.optJSONArray("backdrop_path")?.optString(0)?.takeIf { it.isNotBlank() }
        val seconds = info.positiveInt("duration_secs") ?: obj.positiveInt("duration_secs")
        val rating = info.optDouble("rating", obj.optDouble("rating", Double.NaN)).toFloat().takeIf { it.isFinite() }
        return VodItemEntity("${playlist.id}_${if (type == VodType.MOVIE) "mov" else "ep"}_$id", playlist.id, type, name,
            mediaUrl(if (type == VodType.MOVIE) "movie" else "series", id, obj.text("container_extension")),
            obj.text("stream_icon") ?: info.text("movie_image") ?: info.text("cover"), backdrop,
            seconds?.div(60), info.positiveInt("year_released") ?: info.positiveInt("year")
                ?: info.text("releaseDate")?.take(4)?.toIntOrNull(), rating, info.text("plot"), season,
            if (type == VodType.SERIES) obj.positiveInt("episode_num") else null)
    }
    val explicit = config.has("movie_api") || config.has("series_api")
    fun enabled(key: String) = !explicit || (config.has(key) && config.opt(key) != false)
    val moviesEnabled = enabled("movie_api")
    val seriesEnabled = enabled("series_api")
    val rows = mutableListOf<VodItemEntity>()
    if (moviesEnabled) {
        val movies = array(api("get_vod_streams"), "vod_streams")
        for (i in 0 until movies.length()) {
            currentCoroutineContext().ensureActive()
            val movie = movies.getJSONObject(i)
            val id = movie.getString("stream_id")
            rows += item(movie, movie, id, VodType.MOVIE, movie.getString("name"))
        }
    }
    if (seriesEnabled) {
        val series = array(api("get_series"), "series")
        for (i in 0 until series.length()) {
            val show = series.getJSONObject(i)
            val details = JSONObject(api("get_series_info", show.getString("series_id")))
            val episodes = details.optJSONObject("episodes") ?: error("Missing series episodes.")
            val showInfo = details.optJSONObject("info") ?: show
            for (season in episodes.keys()) {
                val list = episodes.getJSONArray(season)
                for (j in 0 until list.length()) {
                    currentCoroutineContext().ensureActive()
                    val episode = list.getJSONObject(j)
                    val info = episode.optJSONObject("info") ?: JSONObject()
                    val row = item(episode, info, episode.getString("id"), VodType.SERIES,
                        "${show.getString("name")} — ${episode.text("title") ?: "Episode ${j + 1}"}",
                        episode.positiveInt("season") ?: season.toIntOrNull())
                    rows += row.copy(posterUrl = row.posterUrl ?: showInfo.text("cover"),
                        plot = row.plot ?: showInfo.text("plot"))
                }
            }
        }
    }
    currentCoroutineContext().ensureActive()
    db.withTransaction {
        val dao = db.vodDao()
        if (moviesEnabled) dao.deleteByPlaylistAndType(playlist.id, VodType.MOVIE)
        if (seriesEnabled) dao.deleteByPlaylistAndType(playlist.id, VodType.SERIES)
        rows.chunked(500).forEach { currentCoroutineContext().ensureActive(); dao.insertAll(it) }
    }
}
