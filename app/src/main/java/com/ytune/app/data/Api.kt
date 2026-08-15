package com.ytune.app.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable data class SearchResponse(val results: List<TrackSummary> = emptyList(), val meta: SearchMeta? = null)
@Serializable data class SearchMeta(val query: String = "", val count: Int = 0, val limit: Int = 10)
@Serializable data class TrackSummary(val video_id: String, val title: String, val artists: List<String> = emptyList(), val artist: String? = null, val duration_seconds: Double? = null, val highest_resolution_thumbnail: String? = null, val thumbnail: String? = null, val official: Boolean? = null)
@Serializable data class TrackEnvelope(val track: Track)
@Serializable data class Track(val video_id: String, val title: String, val artists: List<String> = emptyList(), val artist: String? = null, val album: String? = null, val album_art_url: String? = null, val highest_resolution_thumbnail: String? = null, val duration_seconds: Double? = null)
@Serializable data class StreamResponse(val video_id: String, val title: String, val formats: List<AudioFormat> = emptyList(), val recommended_format: AudioFormat? = null)
@Serializable data class AudioFormat(val format_id: String, val ext: String? = null, val audio_codec: String? = null, val audio_bitrate_kbps: Double? = null, val playback_url: String? = null)
@Serializable data class LyricsEnvelope(val lyrics: Lyrics?)
@Serializable data class Lyrics(val found: Boolean = false, val instrumental: Boolean = false, val synced_lyrics: String? = null, val plain_lyrics: String? = null, val preferred_type: String? = null)
@Serializable data class PlaylistEnvelope(val playlist: Playlist, val tracks: List<PlaylistTrack> = emptyList(), val offset: Int = 0, val limit: Int = 50, val total: Int = 0)
@Serializable data class Playlist(val playlist_id: String, val title: String, val uploader: String? = null, val description: String? = null, val thumbnail: String? = null, val track_count: Int = 0)
@Serializable data class PlaylistTrack(val position: Int, val video_id: String, val title: String, val uploader: String? = null, val duration_seconds: Double? = null, val thumbnail: String? = null)
@Serializable data class HealthResponse(val status: String, val database: String? = null, val version: String? = null)
@Serializable data class YtdlpStatus(val installed_version: String, val latest_version: String, val update_available: Boolean)

interface YtuneApi { @GET("api/v1/search") suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 25): SearchResponse; @GET("api/v1/tracks/{id}") suspend fun track(@Path("id") id: String): TrackEnvelope; @GET("api/v1/tracks/{id}/stream") suspend fun stream(@Path("id") id: String, @Query("quality") quality: String = "best"): StreamResponse; @GET("api/v1/tracks/{id}/lyrics") suspend fun lyrics(@Path("id") id: String): LyricsEnvelope; @POST("api/v1/tracks/{id}/lyrics/refresh") suspend fun refreshLyrics(@Path("id") id: String): LyricsEnvelope; @GET("api/v1/playlists/{id}") suspend fun playlist(@Path("id") id: String, @Query("offset") offset: Int = 0, @Query("limit") limit: Int = 50): PlaylistEnvelope; @GET("health") suspend fun health(): HealthResponse; @GET("api/v1/system/ytdlp") suspend fun ytdlp(): YtdlpStatus }
