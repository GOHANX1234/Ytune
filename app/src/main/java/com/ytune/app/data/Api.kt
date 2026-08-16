package com.ytune.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

@Serializable data class ApiErrorEnvelope(val error: ApiErrorBody)
@Serializable data class ApiErrorBody(val code: String, val message: String, val details: ErrorDetails? = null)
@Serializable data class ErrorDetails(val errors: List<ValidationError> = emptyList())
@Serializable data class ValidationError(val loc: List<String> = emptyList(), val msg: String = "", val type: String = "")

@Serializable data class SearchResponse(val results: List<TrackSummary> = emptyList(), val meta: SearchMeta? = null)
@Serializable data class SearchMeta(val query: String = "", val count: Int = 0, val limit: Int = 10)
@Serializable data class Thumbnail(val url: String, val width: Int? = null, val height: Int? = null)
@Serializable data class TrackSummary(
    val video_id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val artist: String? = null,
    val uploader: String? = null,
    val duration_seconds: Double? = null,
    val webpage_url: String? = null,
    val highest_resolution_thumbnail: String? = null,
    val thumbnail: String? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
    val official: Boolean? = null,
    val result_type: String? = null,
    val relevance_score: Double? = null
)

@Serializable data class TrackEnvelope(val track: Track, val cached: Boolean = false)
@Serializable data class Track(
    val video_id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val artist: String? = null,
    val uploader: String? = null,
    val album: String? = null,
    val album_art_url: String? = null,
    val highest_resolution_thumbnail: String? = null,
    val duration_seconds: Double? = null,
    val release_year: Int? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val audio_formats: List<AudioFormat> = emptyList()
)

@Serializable data class StreamResponse(
    val video_id: String,
    val title: String,
    val resolved_at: String? = null,
    val expires_at: String? = null,
    val ephemeral: Boolean = true,
    val formats: List<AudioFormat> = emptyList(),
    val recommended_format: AudioFormat? = null,
    val selection: StreamSelection? = null
)
@Serializable data class StreamSelection(val container: String? = null, val codec: String? = null, val quality: String = "best")
@Serializable data class AudioFormat(
    val format_id: String,
    val ext: String? = null,
    val protocol: String? = null,
    val audio_codec: String? = null,
    val audio_bitrate_kbps: Double? = null,
    val sample_rate_hz: Int? = null,
    val file_size: Long? = null,
    val playback_url: String? = null
)

@Serializable data class LyricsEnvelope(val lyrics: Lyrics?, val cached: Boolean = false, val refreshed: Boolean = false)
@Serializable data class Lyrics(
    val video_id: String? = null,
    val found: Boolean = false,
    val instrumental: Boolean = false,
    val synced_lyrics: String? = null,
    val plain_lyrics: String? = null,
    val preferred_type: String? = null,
    val match_confidence: String? = null,
    val fetched_at: String? = null
)

@Serializable data class PlaylistEnvelope(val playlist: Playlist, val tracks: List<PlaylistTrack> = emptyList(), val offset: Int = 0, val limit: Int = 50, val count: Int = 0, val total: Int = 0)
@Serializable data class Playlist(val playlist_id: String, val title: String, val uploader: String? = null, val description: String? = null, val thumbnail: String? = null, val track_count: Int = 0)
@Serializable data class PlaylistTrack(val position: Int, val video_id: String, val title: String, val uploader: String? = null, val duration_seconds: Double? = null, val thumbnail: String? = null)
@Serializable data class HealthResponse(val status: String, val database: String? = null, val version: String? = null, val timestamp: String? = null)
@Serializable data class YtdlpStatus(val installed_version: String, val latest_version: String, val update_available: Boolean, val checked_at: String? = null)

interface YtuneApi {
    @GET("api/v1/search") suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 25, @Query("type") type: String = "song"): SearchResponse
    @GET("api/v1/tracks/{id}") suspend fun track(@Path("id") id: String): TrackEnvelope
    @GET("api/v1/tracks/{id}/stream") suspend fun stream(@Path("id") id: String, @Query("quality") quality: String = "best"): StreamResponse
    @GET("api/v1/tracks/{id}/lyrics") suspend fun lyrics(@Path("id") id: String): LyricsEnvelope
    @POST("api/v1/tracks/{id}/lyrics/refresh") suspend fun refreshLyrics(@Path("id") id: String): LyricsEnvelope
    @GET("api/v1/playlists/{id}") suspend fun playlist(@Path("id") id: String, @Query("offset") offset: Int = 0, @Query("limit") limit: Int = 50): PlaylistEnvelope
    @GET("health") suspend fun health(): HealthResponse
    @GET("api/v1/system/ytdlp") suspend fun ytdlp(): YtdlpStatus
}
