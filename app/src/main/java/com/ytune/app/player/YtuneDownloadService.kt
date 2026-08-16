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

private const val DOWNLOAD_CHANNEL = "ytune_downloads"
private const val DOWNLOAD_NOTIFICATION = 2001

class YtuneDownloadService : DownloadService(DOWNLOAD_NOTIFICATION, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, DOWNLOAD_CHANNEL, R.string.app_name, 0) {
    private val helper by lazy { DownloadNotificationHelper(this, DOWNLOAD_CHANNEL) }
    override fun getDownloadManager(): DownloadManager = PlaybackManager.downloadManager
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification =
        helper.buildProgressNotification(this, android.R.drawable.stat_sys_download, null, null, downloads, notMetRequirements)
}

class DownloadController(private val context: Context) {
    private val app = context.applicationContext as YtuneApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
            scope.launch {
                val track = app.repository.localTrack(download.request.id)
                app.repository.saveDownload(DownloadEntity(
                    videoId = download.request.id,
                    title = track?.title.orEmpty(),
                    artists = track?.artists.orEmpty(),
                    artworkUrl = track?.artworkUrl,
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
    init { PlaybackManager.downloadManager.addListener(listener) }

    fun download(track: TrackSummary) {
        scope.launch {
            val wifiOnly = app.repository.playbackPreferences().downloadWifiOnly
            PlaybackManager.downloadManager.requirements = Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
            app.repository.saveDownload(DownloadEntity(track.video_id, track.title, track.artists.joinToString("\u001f"), track.highest_resolution_thumbnail ?: track.thumbnail, Download.STATE_QUEUED))
            val request = DownloadRequest.Builder(track.video_id, Uri.parse(app.repository.playbackUrl(track.video_id)))
                .setData(track.title.toByteArray()).build()
            DownloadService.sendAddDownload(context, YtuneDownloadService::class.java, request, false)
        }
    }
    fun remove(videoId: String) { DownloadService.sendRemoveDownload(context, YtuneDownloadService::class.java, videoId, false) }
}
