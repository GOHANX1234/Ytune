@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ytune.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ytune.app.YtuneApplication
import com.ytune.app.data.TrackSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class PlaybackState(
    val connected: Boolean = false,
    val current: TrackSummary? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val queue: List<TrackSummary> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

class PlaybackConnection(context: Context) {
    private val app = context.applicationContext as YtuneApplication
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (future != null) return
        future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync().also { value ->
            value.addListener({
                runCatching { value.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(listener)
                    publish(mediaController)
                    positionJob?.cancel()
                    positionJob = scope.launch { while (isActive) { delay(250); controller?.let(::publish) } }
                }
            }, ContextCompat.getMainExecutor(app))
        }
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        positionJob?.cancel()
        positionJob = null
        _state.value = PlaybackState()
    }

    fun play(track: TrackSummary, replaceQueue: Boolean = false) {
        scope.launch {
            val quality = app.repository.settings.first().quality
            val format = runCatching { app.repository.stream(track.video_id, quality).recommended_format?.format_id }.getOrNull()
            val item = track.mediaItem(app, format)
            controller?.apply {
                if (replaceQueue || mediaItemCount == 0) setMediaItem(item) else addMediaItem(item)
                if (!replaceQueue && mediaItemCount > 1) seekTo(mediaItemCount - 1, 0)
                prepare()
                play()
            }
        }
    }
    fun addToQueue(track: TrackSummary) { controller?.addMediaItem(track.mediaItem(app)) }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seek(position: Long) { controller?.seekTo(position) }
    fun seekToItem(index: Int) { controller?.seekTo(index, 0); controller?.play() }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }

    private fun publish(player: Player) {
        val metadata = player.currentMediaItem?.mediaMetadata
        _state.value = PlaybackState(
            connected = true,
            current = player.currentMediaItem?.let { item -> TrackSummary(item.mediaId, metadata?.title?.toString().orEmpty(), listOfNotNull(metadata?.artist?.toString()), highest_resolution_thumbnail = metadata?.artworkUri?.toString(), duration_seconds = player.duration.takeIf { it > 0 }?.div(1000.0)) },
            playing = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            repeatMode = player.repeatMode,
            shuffle = player.shuffleModeEnabled,
            queue = (0 until player.mediaItemCount).map { index ->
                val item = player.getMediaItemAt(index)
                TrackSummary(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), listOfNotNull(item.mediaMetadata.artist?.toString()), highest_resolution_thumbnail = item.mediaMetadata.artworkUri?.toString())
            },
            currentIndex = player.currentMediaItemIndex,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem()
        )
    }
}

private fun TrackSummary.mediaItem(app: YtuneApplication, formatId: String? = null) = MediaItem.Builder()
    .setMediaId(video_id)
    .setUri(app.repository.playbackUrl(video_id, formatId))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artists.joinToString()).setArtworkUri((highest_resolution_thumbnail ?: thumbnail)?.let(android.net.Uri::parse)).build())
    .build()
