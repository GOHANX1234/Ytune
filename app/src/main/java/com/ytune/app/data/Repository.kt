package com.ytune.app.data

import android.content.Context
import com.ytune.app.data.local.*
import com.ytune.app.player.NativeSecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class YtuneException(val code: String, override val message: String, val retryable: Boolean, val status: Int? = null, val requestId: String? = null) : IOException(message)

class YtuneRepository(
    context: Context,
    private val dao: LibraryDao,
    private val preferences: UserPreferences
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val endpoint: String by lazy { NativeSecurity.baseUrl().ensureTrailingSlash() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("X-Request-ID", NativeSecurity.requestId(chain.request().url.encodedPath))
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .apply {
            if (com.ytune.app.BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()
    private val api: YtuneApi by lazy {
        Retrofit.Builder().baseUrl(endpoint).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(YtuneApi::class.java)
    }

    val favorites: Flow<List<FavoriteTrack>> = dao.favorites()
    val history: Flow<List<HistoryTrack>> = dao.history()
    val playlists: Flow<List<LocalPlaylistEntity>> = dao.playlists()
    val downloads: Flow<List<DownloadEntity>> = dao.downloads()
    val settings: Flow<PlaybackPreferences> = preferences.playback

    suspend fun search(query: String, limit: Int = 25): SearchResponse = apiCall {
        api.search(query.trim(), limit).also { response -> dao.upsertTracks(response.results.map { it.toEntity() }) }
    }

    suspend fun track(id: String): TrackEnvelope = apiCall {
        api.track(id).also { dao.upsertTrack(it.track.toEntity()) }
    }

    suspend fun stream(id: String, quality: String): StreamResponse = apiCall { api.stream(id, quality) }
    suspend fun lyrics(id: String): LyricsEnvelope = apiCall { api.lyrics(id) }
    suspend fun refreshLyrics(id: String): LyricsEnvelope = apiCall { api.refreshLyrics(id) }
    suspend fun health(): HealthResponse = apiCall { api.health() }
    suspend fun ytdlp(): YtdlpStatus = apiCall { api.ytdlp() }

    suspend fun playlist(id: String, pageSize: Int = 100): PlaylistEnvelope {
        var offset = 0
        val tracks = mutableListOf<PlaylistTrack>()
        var first: PlaylistEnvelope? = null
        do {
            val page = apiCall { api.playlist(id, offset, pageSize) }
            if (first == null) first = page
            tracks += page.tracks
            offset += page.tracks.size
        } while (page.tracks.isNotEmpty() && offset < page.total)
        dao.upsertTracks(tracks.map { it.toEntity() })
        return requireNotNull(first).copy(tracks = tracks, offset = 0, limit = tracks.size, count = tracks.size)
    }

    suspend fun toggleFavorite(track: TrackSummary) {
        dao.upsertTrack(track.toEntity())
        if (dao.isFavorite(track.video_id)) dao.unfavorite(track.video_id) else dao.favorite(FavoriteEntity(track.video_id))
    }

    suspend fun recordPlayed(track: TrackSummary) {
        dao.upsertTrack(track.toEntity())
        dao.addHistory(HistoryEntity(videoId = track.video_id))
        dao.trimHistory()
    }

    suspend fun createPlaylist(name: String): LocalPlaylistEntity = LocalPlaylistEntity(java.util.UUID.randomUUID().toString(), name.trim()).also { dao.upsertPlaylist(it) }
    suspend fun addToPlaylist(playlistId: String, track: TrackSummary) {
        dao.upsertTrack(track.toEntity())
        dao.addPlaylistTrack(LocalPlaylistTrackEntity(playlistId, track.video_id, dao.playlistSize(playlistId)))
    }
    fun playlistTracks(id: String): Flow<List<TrackEntity>> = dao.playlistTracks(id)
    suspend fun removeFromPlaylist(playlistId: String, videoId: String) = dao.removePlaylistTrack(playlistId, videoId)
    suspend fun deletePlaylist(id: String) = dao.deletePlaylist(id)

    fun playbackUrl(videoId: String, formatId: String? = null): String {
        val url = endpoint.toHttpUrl().newBuilder().addPathSegments("api/v1/tracks/$videoId/play")
        if (formatId != null) url.addQueryParameter("format_id", formatId)
        return url.build().toString()
    }

    internal fun httpClient(): OkHttpClient = client
    suspend fun localTrack(id: String): TrackEntity? = dao.track(id)
    suspend fun savedQueue(): List<QueueTrack> = dao.queue()
    suspend fun saveQueue(ids: List<String>) { dao.clearQueue(); dao.insertQueue(ids.mapIndexed { index, id -> QueueEntity(index, id) }) }
    suspend fun saveDownload(value: DownloadEntity) = dao.upsertDownload(value)
    suspend fun removeDownload(id: String) = dao.removeDownload(id)
    suspend fun playbackPreferences(): PlaybackPreferences = preferences.playback.first()
    suspend fun savePlayback(videoId: String?, positionMs: Long, index: Int, repeatMode: Int, shuffle: Boolean) = preferences.savePlayback(videoId, positionMs, index, repeatMode, shuffle)
    suspend fun setQuality(value: String) = preferences.setQuality(value)
    suspend fun setWifiOnly(value: Boolean) = preferences.setWifiOnly(value)

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val body = runCatching { error.response()?.errorBody()?.string()?.let { json.decodeFromString<ApiErrorEnvelope>(it).error } }.getOrNull()
        val status = error.code()
        throw YtuneException(body?.code ?: "http_error", body?.message ?: "Request failed ($status)", status in listOf(429, 500, 502, 503), status, error.response()?.headers()?.get("X-Request-ID"))
    } catch (error: IOException) {
        throw YtuneException("network_error", "Check your connection and try again", true, requestId = null)
    }
}

fun TrackSummary.toEntity() = TrackEntity(video_id, title, artists.joinToString("\u001f"), artist ?: uploader, null, highest_resolution_thumbnail ?: thumbnail ?: thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url, duration_seconds?.times(1000)?.toLong())
fun Track.toEntity() = TrackEntity(video_id, title, artists.joinToString("\u001f"), artist ?: uploader, album, album_art_url ?: highest_resolution_thumbnail, duration_seconds?.times(1000)?.toLong())
fun PlaylistTrack.toEntity() = TrackEntity(video_id, title, listOfNotNull(uploader).joinToString("\u001f"), uploader, null, thumbnail, duration_seconds?.times(1000)?.toLong())
fun TrackEntity.toSummary() = TrackSummary(videoId, title, artists.split("\u001f").filter { it.isNotBlank() }, artist, duration_seconds = durationMs?.div(1000.0), highest_resolution_thumbnail = artworkUrl)
private fun String.ensureTrailingSlash() = if (endsWith('/')) this else "$this/"
