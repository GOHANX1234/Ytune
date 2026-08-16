@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ytune.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.ytune.app.data.*
import com.ytune.app.data.local.*
import com.ytune.app.player.*
import com.ytune.app.ui.theme.YtuneTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        setContent { YtuneTheme { YtuneApp() } }
    }
}

class YtuneViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val app = application as YtuneApplication
    val repository = app.repository
    val favorites = repository.favorites
    val history = repository.history
    val recentDiscoveries = repository.recentDiscoveries
    val searchHistory = repository.searchHistory
    val playlists = repository.playlists
    val downloads = repository.downloads
    val settings = repository.settings
    var query by mutableStateOf("")
    var results by mutableStateOf<List<TrackSummary>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private val _playlist = MutableStateFlow<PlaylistEnvelope?>(null)
    val playlist = _playlist.asStateFlow()
    private var searchJob: Job? = null

    fun search(term: String = query) {
        val normalized = term.trim()
        query = term
        searchJob?.cancel()
        if (normalized.isBlank()) { results = emptyList(); _playlist.value = null; loading = false; return }
        searchJob = viewModelScope.launch {
            loading = true; error = null
            try {
                val id = PlaylistIdParser.parse(normalized)
                if (id != null) _playlist.value = repository.playlist(id) else results = repository.search(normalized).results
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (query.trim() == normalized) error = failure.message ?: "Request failed"
            } finally {
                if (query.trim() == normalized) loading = false
            }
        }
    }

    fun clearSearchResults() { searchJob?.cancel(); results = emptyList(); _playlist.value = null; loading = false }
    fun clearSearchHistory() = viewModelScope.launch { repository.clearSearchHistory() }

    fun toggleFavorite(track: TrackSummary) = viewModelScope.launch { repository.toggleFavorite(track) }
    fun recordPlayed(track: TrackSummary) = viewModelScope.launch { repository.recordPlayed(track) }
    fun createPlaylist(name: String) = viewModelScope.launch { if (name.isNotBlank()) repository.createPlaylist(name) }
    fun addToPlaylist(id: String, track: TrackSummary) = viewModelScope.launch { repository.addToPlaylist(id, track) }
    fun setQuality(value: String) = viewModelScope.launch { repository.setQuality(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { repository.setWifiOnly(value) }
}

private enum class Destination { Home, Search, Library, Downloads }
private val LocalPendingDownloads = compositionLocalOf<Set<String>> { emptySet() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtuneApp(vm: YtuneViewModel = viewModel()) {
    val context = LocalContext.current
    val connection = remember { PlaybackConnection(context) }
    val playback by connection.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle(emptyList())
    val history by vm.history.collectAsStateWithLifecycle(emptyList())
    val downloads by vm.downloads.collectAsStateWithLifecycle(emptyList())
    val playlists by vm.playlists.collectAsStateWithLifecycle(emptyList())
    val settings by vm.settings.collectAsStateWithLifecycle(PlaybackPreferences())
    val destination = remember { mutableStateOf(Destination.Home) }
    var showPlayer by remember { mutableStateOf(false) }
    val downloader = remember { DownloadController(context) }
    val pendingDownloads by downloader.pending.collectAsStateWithLifecycle()
    val downloadProgress by downloader.progress.collectAsStateWithLifecycle()
    val downloadError by downloader.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(downloadError) { downloadError?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it) } }
    DisposableEffect(connection) { connection.connect(); onDispose { connection.disconnect() } }
    fun play(track: TrackSummary) { connection.play(track, replaceQueue = false); vm.recordPlayed(track) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!showPlayer) Column {
                playback.current?.let { MiniPlayer(it, playback.playing, connection::toggle, connection::next) { showPlayer = true } }
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(destination.value == item, { destination.value = item }, { Icon(item.icon(), item.name) }, label = { Text(item.name) })
                    }
                }
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalPendingDownloads provides pendingDownloads) {
            Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
                if (showPlayer) {
                    FullPlayer(playback, connection) { showPlayer = false }
                } else when (destination.value) {
                    Destination.Home -> HomeScreen(vm, { destination.value = Destination.Search; vm.search(it) }, ::play, downloader::download, connection::addToQueue, playlists, vm::addToPlaylist)
                    Destination.Search -> SearchScreen(vm, ::play, { vm.toggleFavorite(it) }, favorites.map { it.track.videoId }.toSet(), downloader::download, connection::addToQueue, playlists, vm::addToPlaylist)
                    Destination.Library -> LibraryScreen(favorites, history, playlists, settings, vm::setQuality, vm::createPlaylist, ::play, { vm.toggleFavorite(it) }, downloader::download, connection::addToQueue, vm::addToPlaylist)
                    Destination.Downloads -> DownloadsScreen(downloads, downloadProgress, ::play, downloader)
                }
                if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable private fun HomeScreen(vm: YtuneViewModel, search: (String) -> Unit, play: (TrackSummary) -> Unit, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    val remotePlaylist by vm.playlist.collectAsStateWithLifecycle()
    val recent by vm.recentDiscoveries.collectAsStateWithLifecycle(emptyList())
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Ytune", style = MaterialTheme.typography.headlineLarge); Text("Everything you love, right here", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text("Songs, artists, or playlist links") }, trailingIcon = { if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else IconButton({ search(vm.query) }) { Icon(Icons.Default.Search, "Search") } }) }
        item { Text("Quick starts", style = MaterialTheme.typography.titleLarge); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Top hits", "Chill", "Workout", "Focus").forEach { SuggestionChip({ search(it) }, label = { Text(it) }) } } }
        remotePlaylist?.let { loaded -> item { Text(loaded.playlist.title, style = MaterialTheme.typography.titleLarge); Text("${loaded.tracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(loaded.tracks) { item -> val track = TrackSummary(item.video_id, item.title, listOfNotNull(item.uploader), duration_seconds = item.duration_seconds, thumbnail = item.thumbnail); TrackRow(track, play, {}, false, download, queue, playlists, addToPlaylist) } }
        if (recent.isNotEmpty()) { item { Text("Recently discovered", style = MaterialTheme.typography.titleLarge) }; items(recent.take(12), key = { it.track.videoId }) { TrackRow(it.track.toSummary(), play, {}, false, download, queue, playlists, addToPlaylist) } }
    }
}

@Composable private fun SearchScreen(vm: YtuneViewModel, play: (TrackSummary) -> Unit, favorite: (TrackSummary) -> Unit, selected: Set<String>, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    val searches by vm.searchHistory.collectAsStateWithLifecycle(emptyList())
    val suggestions = searches.map { it.query }.filter { vm.query.isBlank() || it.contains(vm.query, ignoreCase = true) }.take(6)
    LaunchedEffect(vm.query) { delay(450); if (vm.query.trim().length >= 2) vm.search(vm.query) else vm.clearSearchResults() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp), trailingIcon = { if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else IconButton({ vm.search() }) { Icon(Icons.Default.Search, "Search") } })
        if (suggestions.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Recent searches", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)); IconButton(vm::clearSearchHistory, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ClearAll, "Clear search history") } }; LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(suggestions) { suggestion -> SuggestionChip({ vm.query = suggestion; vm.search(suggestion) }, label = { Text(suggestion, maxLines = 1) }) } } }
        Spacer(Modifier.height(10.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(vm.results, key = { it.video_id }) { TrackRow(it, play, { favorite(it) }, it.video_id in selected, download, queue, playlists, addToPlaylist) } }
    }
}

@Composable private fun LibraryScreen(favorites: List<FavoriteTrack>, history: List<HistoryTrack>, playlists: List<LocalPlaylistEntity>, settings: PlaybackPreferences, setQuality: (String) -> Unit, createPlaylist: (String) -> Unit, play: (TrackSummary) -> Unit, remove: (TrackSummary) -> Unit, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, addToPlaylist: (String, TrackSummary) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }; var showSettings by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Your Library", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f)); IconButton({ showSettings = true }) { Icon(Icons.Default.Settings, "Settings") } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton({ showCreate = true }) { Icon(Icons.Default.Add, "Create playlist") } } }
        items(playlists) { Text(it.name, Modifier.fillMaxWidth().padding(vertical = 10.dp), style = MaterialTheme.typography.titleMedium) }
        item { Text("Favorites", style = MaterialTheme.typography.titleLarge) }
        if (favorites.isEmpty()) item { Text("Favorite tracks to keep them here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(favorites) { TrackRow(it.track.toSummary(), play, { remove(it.track.toSummary()) }, true, download, queue, playlists, addToPlaylist) }
        item { Spacer(Modifier.height(12.dp)); Text("History", style = MaterialTheme.typography.titleLarge) }
        items(history.filter { it.track != null }.take(20)) { TrackRow(it.track!!.toSummary(), play, {}, false, download, queue, playlists, addToPlaylist) }
    }
    if (showCreate) AlertDialog(onDismissRequest = { showCreate = false }, title = { Text("New playlist") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }) }, confirmButton = { TextButton({ createPlaylist(name); name = ""; showCreate = false }) { Text("Create") } }, dismissButton = { TextButton({ showCreate = false }) { Text("Cancel") } })
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Column { Text("Streaming quality"); SingleChoiceSegmentedButtonRow { listOf("low", "medium", "best").forEachIndexed { index, value -> SegmentedButton(selected = settings.quality == value, onClick = { setQuality(value) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(value.replaceFirstChar { it.uppercase() }) } } }; Spacer(Modifier.height(12.dp)); Text("Downloads use any available network.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton({ showSettings = false }) { Text("Done") } })
}

@Composable private fun DownloadsScreen(downloads: List<DownloadEntity>, progress: Map<String, Float>, play: (TrackSummary) -> Unit, downloader: DownloadController) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        if (downloads.isEmpty()) Text("Downloaded tracks will be available without a connection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn { items(downloads) { item ->
            val track = TrackSummary(item.videoId, item.title.ifBlank { item.videoId }, item.artists.split("\u001f").filter { it.isNotBlank() }, highest_resolution_thumbnail = item.artworkUrl)
            val percent = progress[item.videoId] ?: item.percent
            val active = item.state == androidx.media3.exoplayer.offline.Download.STATE_QUEUED || item.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
            Column {
                ListItem(modifier = Modifier.clickable { if (!active) play(track) }, headlineContent = { Text(track.title) }, supportingContent = { Text(if (item.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) "Available offline" else if (item.error != null) "Download failed" else "Downloading ${percent.toInt()}%") }, trailingContent = { IconButton({ downloader.remove(item.videoId) }) { Icon(Icons.Default.Delete, "Remove download") } })
                if (active) LinearProgressIndicator(progress = { (percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        } }
    }
}

@Composable private fun TrackRow(track: TrackSummary, play: (TrackSummary) -> Unit, favorite: () -> Unit, selected: Boolean, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val downloading = track.video_id in LocalPendingDownloads.current
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) { ListItem(modifier = Modifier.clickable { play(track) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer), leadingContent = { AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.size(58.dp), contentScale = ContentScale.Crop) }, headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(track.artists.joinToString().ifBlank { track.uploader ?: "YouTube Music" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingContent = { Row { IconButton(favorite) { Icon(if (selected) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") }; Box { if (downloading) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center), strokeWidth = 2.dp) else IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Add to queue") }, { queue(track); menu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }); DropdownMenuItem({ Text(if (downloading) "Starting download…" else "Download") }, { if (!downloading) download(track); menu = false }, enabled = !downloading, leadingIcon = { if (downloading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Download, null) }); playlists.forEach { list -> DropdownMenuItem({ Text("Add to ${list.name}") }, { addToPlaylist(list.id, track); menu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }) } } } } }) }
}

@Composable private fun MiniPlayer(track: TrackSummary, playing: Boolean, toggle: () -> Unit, next: () -> Unit, expand: () -> Unit) {
    Surface(tonalElevation = 3.dp) { Row(Modifier.fillMaxWidth().height(68.dp).clickable(onClick = expand).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.size(52.dp)); Text(track.title, Modifier.weight(1f).padding(10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play") }; IconButton(next) { Icon(Icons.Default.SkipNext, "Next") } } }
}

@Composable private fun FullPlayer(state: PlaybackState, connection: PlaybackConnection, close: () -> Unit) {
    val track = state.current ?: return
    val app = LocalContext.current.applicationContext as YtuneApplication
    val scope = rememberCoroutineScope()
    var lyrics by remember(track.video_id) { mutableStateOf<String?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    val syncedLines = remember(lyrics) { parseLrc(lyrics) }
    val activeLyric = syncedLines.indexOfLast { it.timeMs <= state.positionMs }.coerceAtLeast(0)
    val lyricListState = rememberLazyListState()
    LaunchedEffect(activeLyric, showLyrics) { if (showLyrics && syncedLines.isNotEmpty()) lyricListState.animateScrollToItem(activeLyric.coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text("Now playing", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.fillMaxWidth().heightIn(max = 340.dp).aspectRatio(1f).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(16.dp)); Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artists.joinToString(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(value = state.positionMs.toFloat(), onValueChange = { connection.seek(it.toLong()) }, valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat())
        Row(Modifier.fillMaxWidth()) { Text(formatTime(state.positionMs), style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall) }
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(connection::toggleShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; IconButton(connection::previous, enabled = state.hasPrevious) { Icon(Icons.Default.SkipPrevious, "Previous") }; FilledIconButton(connection::toggle) { Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play") }; IconButton(connection::next, enabled = state.hasNext) { Icon(Icons.Default.SkipNext, "Next") }; IconButton(connection::cycleRepeat) { Icon(Icons.Default.Repeat, "Repeat", tint = if (state.repeatMode != 0) MaterialTheme.colorScheme.primary else LocalContentColor.current) } }
        TextButton({ showLyrics = !showLyrics; if (lyrics == null) scope.launch { lyrics = runCatching { app.repository.lyrics(track.video_id).lyrics?.let { it.synced_lyrics ?: it.plain_lyrics } }.getOrNull() ?: "Lyrics are not available." } }) { Icon(Icons.Default.Lyrics, null); Spacer(Modifier.width(8.dp)); Text("Lyrics") }
        if (showLyrics) {
            if (lyrics == null) CircularProgressIndicator(Modifier.size(24.dp))
            else if (syncedLines.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), state = lyricListState, contentPadding = PaddingValues(vertical = 8.dp)) { itemsIndexed(syncedLines) { index, line -> Text(line.text, Modifier.fillMaxWidth().clickable { connection.seek(line.timeMs) }.padding(vertical = 5.dp), style = if (index == activeLyric) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, color = if (index == activeLyric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }
            else Text(lyrics.orEmpty(), Modifier.fillMaxWidth().heightIn(max = 280.dp), style = MaterialTheme.typography.bodyLarge)
        }
        }
        if (state.queue.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text("Up next", style = MaterialTheme.typography.titleMedium); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { itemsIndexed(state.queue) { index, item -> ListItem(modifier = Modifier.clickable { connection.seekToItem(index) }, headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(item.artists.joinToString()) }, trailingContent = { if (index == state.currentIndex) Icon(Icons.Default.PlayArrow, "Playing") }) } } }
    }
}

private data class LrcLine(val timeMs: Long, val text: String)
private fun formatTime(value: Long): String = "%d:%02d".format(value.coerceAtLeast(0) / 60_000, value.coerceAtLeast(0) / 1_000 % 60)
private fun parseLrc(value: String?): List<LrcLine> = value.orEmpty().lineSequence().flatMap { raw ->
    val match = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)").find(raw) ?: return@flatMap emptySequence()
    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
    sequenceOf(LrcLine((match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()) * 1000 + fraction, match.groupValues[4]))
}.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()

private fun Destination.icon() = when (this) { Destination.Home -> Icons.Default.Home; Destination.Search -> Icons.Default.Search; Destination.Library -> Icons.Default.LibraryMusic; Destination.Downloads -> Icons.Default.Download }
