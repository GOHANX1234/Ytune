@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ytune.app.player

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.scheduler.Requirements
import com.ytune.app.R
import com.ytune.app.YtuneApplication
import com.ytune.app.data.TrackSummary
import com.ytune.app.data.local.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

private const val DOWNLOAD_CHANNEL = "ytune_downloads"
private const val DOWNLOAD_NOTIFICATION = 2001

class YtuneDownloadService : DownloadService(DOWNLOAD_NOTIFICATION, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, DOWNLOAD_CHANNEL, R.string.app_name, R.string.downloads_description) {
    private val helper by lazy { DownloadNotificationHelper(this, DOWNLOAD_CHANNEL) }
    override fun getDownloadManager(): DownloadManager = PlaybackManager.downloadManager
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification =
        helper.buildProgressNotification(this, android.R.drawable.stat_sys_download, null, null, downloads, notMetRequirements)
}

class DownloadController(private val context: Context) {
    private val app = context.applicationContext as YtuneApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress
    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
            _pending.value = _pending.value - download.request.id
            if (finalException != null) _error.value = finalException.message ?: "Download failed"
            scope.launch {
                val track = app.repository.localTrack(download.request.id)
                val saved = app.repository.downloads.first().firstOrNull { it.videoId == download.request.id }
                app.repository.saveDownload(DownloadEntity(
                    videoId = download.request.id,
                    title = track?.title ?: saved?.title.orEmpty(),
                    artists = track?.artists ?: saved?.artists.orEmpty(),
                    artworkUrl = track?.artworkUrl ?: saved?.artworkUrl,
                    state = download.state,
                    percent = download.percentDownloaded,
                    bytesDownloaded = download.bytesDownloaded,
                    error = finalException?.message
                ))
            }
        }
        override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
            scope.launch { app.repository.removeDownload(download.request.id) }
        }
    }
    init {
        PlaybackManager.downloadManager.addListener(listener)
        scope.launch {
            val savedDownloads = app.repository.downloads.first().associateBy { it.videoId }
            val cursor = PlaybackManager.downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED)
            cursor.use {
                while (it.moveToNext()) {
                    val videoId = it.download.request.id
                    val track = app.repository.localTrack(videoId)
                    val saved = savedDownloads[videoId]
                    ArtworkCache.ensureCached(context, videoId, track?.artworkUrl ?: saved?.artworkUrl, app.repository.httpClient())
                }
            }
        }
        scope.launch {
            while (isActive) {
                val active = PlaybackManager.downloadManager.currentDownloads
                val savedDownloads = app.repository.downloads.first().associateBy { it.videoId }
                _progress.value = active.associate { it.request.id to it.percentDownloaded.coerceAtLeast(0f) }
                active.forEach { download ->
                    val track = app.repository.localTrack(download.request.id)
                    val saved = savedDownloads[download.request.id]
                    app.repository.saveDownload(DownloadEntity(
                        videoId = download.request.id,
                        title = track?.title ?: saved?.title.orEmpty(),
                        artists = track?.artists ?: saved?.artists.orEmpty(),
                        artworkUrl = track?.artworkUrl ?: saved?.artworkUrl,
                        state = download.state,
                        percent = download.percentDownloaded.coerceAtLeast(0f),
                        bytesDownloaded = download.bytesDownloaded
                    ))
                }
                delay(750)
            }
        }
    }

    fun download(track: TrackSummary) {
        _pending.value = _pending.value + track.video_id
        scope.launch {
            runCatching {
                _error.value = null
                PlaybackManager.downloadManager.requirements = Requirements(Requirements.NETWORK)
                app.repository.saveDownload(DownloadEntity(track.video_id, track.title, track.artists.joinToString("\u001f"), track.highest_resolution_thumbnail ?: track.thumbnail, Download.STATE_QUEUED))
                val request = DownloadRequest.Builder(track.video_id, Uri.parse(app.repository.playbackUrl(track.video_id)))
                    .setData(track.title.toByteArray()).build()
                DownloadService.sendAddDownload(context, YtuneDownloadService::class.java, request, true)
                ArtworkCache.ensureCached(context, track.video_id, track.highest_resolution_thumbnail ?: track.thumbnail, app.repository.httpClient())
            }.onFailure {
                _pending.value = _pending.value - track.video_id
                _error.value = it.message ?: "Unable to start download"
            }
        }
    }
    fun remove(videoId: String) { _error.value = null; DownloadService.sendRemoveDownload(context, YtuneDownloadService::class.java, videoId, true) }
}
