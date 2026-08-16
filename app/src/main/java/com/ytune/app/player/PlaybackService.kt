package com.ytune.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ytune.app.MainActivity
import com.ytune.app.YtuneApplication
import com.ytune.app.data.local.TrackEntity
import com.ytune.app.data.toSummary
import kotlinx.coroutines.*

@UnstableApi
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val app get() = application as YtuneApplication

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(PlaybackManager.cacheFactory))
            .build()
        val intent = Intent(this, MainActivity::class.java)
        val activity = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val id = item?.mediaId ?: return
                scope.launch(Dispatchers.IO) { app.repository.localTrack(id)?.toSummary()?.let { app.repository.recordPlayed(it) } }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) { persist() }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { persist() }
            override fun onRepeatModeChanged(repeatMode: Int) { persist() }
        })
        scope.launch { restore() }
        scope.launch {
            while (isActive) { delay(5_000); persist() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private suspend fun restore() {
        val queue = withContext(Dispatchers.IO) { app.repository.savedQueue().mapNotNull { it.track } }
        if (queue.isEmpty()) return
        val state = withContext(Dispatchers.IO) { app.repository.playbackPreferences() }
        player.setMediaItems(queue.map(::mediaItem), state.currentIndex.coerceIn(queue.indices), state.positionMs)
        player.repeatMode = state.repeatMode
        player.shuffleModeEnabled = state.shuffle
        player.prepare()
    }

    private fun persist() {
        if (!::player.isInitialized) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val current = player.currentMediaItem?.mediaId
        val position = player.currentPosition.coerceAtLeast(0)
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val repeat = player.repeatMode
        val shuffle = player.shuffleModeEnabled
        scope.launch(Dispatchers.IO) {
            app.repository.saveQueue(ids)
            app.repository.savePlayback(current, position, index, repeat, shuffle)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) { persist(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        persist()
        session?.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun mediaItem(track: TrackEntity): MediaItem = MediaItem.Builder()
        .setMediaId(track.videoId)
        .setUri(app.repository.playbackUrl(track.videoId))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).setArtworkUri(track.artworkUrl?.let(android.net.Uri::parse)).build())
        .build()
}
