@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ytune.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
    val playlists = repository.playlists
    val downloads = repository.downloads
    val settings = repository.settings
    var query by mutableStateOf("")
    var results by mutableStateOf<List<TrackSummary>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private val _playlist = MutableStateFlow<PlaylistEnvelope?>(null)
    val playlist = _playlist.asStateFlow()

    fun search(term: String = query) {
        if (term.isBlank()) return
        query = term
        viewModelScope.launch {
            loading = true; error = null
            runCatching {
                val id = PlaylistIdParser.parse(term)
                if (id != null) _playlist.value = repository.playlist(id) else results = repository.search(term).results
            }.onFailure { error = it.message ?: "Request failed" }
            loading = false
        }
    }

    fun toggleFavorite(track: TrackSummary) = viewModelScope.launch { repository.toggleFavorite(track) }
    fun recordPlayed(track: TrackSummary) = viewModelScope.launch { repository.recordPlayed(track) }
    fun createPlaylist(name: String) = viewModelScope.launch { if (name.isNotBlank()) repository.createPlaylist(name) }
    fun addToPlaylist(id: String, track: TrackSummary) = viewModelScope.launch { repository.addToPlaylist(id, track) }
    fun setQuality(value: String) = viewModelScope.launch { repository.setQuality(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { repository.setWifiOnly(value) }
}

private enum class Destination { Home, Search, Library, Downloads }

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
    DisposableEffect(connection) { connection.connect(); onDispose { connection.disconnect() } }
    fun play(track: TrackSummary) { connection.play(track, replaceQueue = true); vm.recordPlayed(track) }

    Scaffold(
        bottomBar = {
            Column {
                playback.current?.let { MiniPlayer(it, playback.playing, connection::toggle, connection::next) { showPlayer = true } }
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(destination.value == item, { destination.value = item }, { Icon(item.icon(), item.name) }, label = { Text(item.name) })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination.value) {
                Destination.Home -> HomeScreen(vm, { destination.value = Destination.Search; vm.search(it) }, ::play, downloader::download, connection::addToQueue, playlists, vm::addToPlaylist)
                Destination.Search -> SearchScreen(vm, ::play, { vm.toggleFavorite(it) }, favorites.map { it.track.videoId }.toSet(), downloader::download, connection::addToQueue, playlists, vm::addToPlaylist)
                Destination.Library -> LibraryScreen(favorites, history, playlists, settings, vm::setQuality, vm::setWifiOnly, vm::createPlaylist, ::play, { vm.toggleFavorite(it) }, downloader::download, connection::addToQueue, vm::addToPlaylist)
                Destination.Downloads -> DownloadsScreen(downloads, history, ::play, downloader)
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) }
        }
    }
    if (showPlayer) ModalBottomSheet(onDismissRequest = { showPlayer = false }) { FullPlayer(playback, connection) }
}

@Composable private fun HomeScreen(vm: YtuneViewModel, search: (String) -> Unit, play: (TrackSummary) -> Unit, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    val remotePlaylist by vm.playlist.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Ytune", style = MaterialTheme.typography.displaySmall); Text("Your music, kept on your device", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search songs, artists, or playlist links") }, trailingIcon = { IconButton({ search(vm.query) }) { Icon(Icons.Default.Search, "Search") } }) }
        item { Text("Quick starts", style = MaterialTheme.typography.titleLarge); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Top hits", "Chill", "Workout", "Focus").forEach { SuggestionChip({ search(it) }, label = { Text(it) }) } } }
        remotePlaylist?.let { loaded -> item { Text(loaded.playlist.title, style = MaterialTheme.typography.titleLarge); Text("${loaded.tracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(loaded.tracks) { item -> val track = TrackSummary(item.video_id, item.title, listOfNotNull(item.uploader), duration_seconds = item.duration_seconds, thumbnail = item.thumbnail); TrackRow(track, play, {}, false, download, queue, playlists, addToPlaylist) } }
        if (vm.results.isNotEmpty()) { item { Text("Recently discovered", style = MaterialTheme.typography.titleLarge) }; items(vm.results.take(8)) { TrackRow(it, play, {}, false, download, queue, playlists, addToPlaylist) } }
    }
}

@Composable private fun SearchScreen(vm: YtuneViewModel, play: (TrackSummary) -> Unit, favorite: (TrackSummary) -> Unit, selected: Set<String>, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { IconButton({ vm.search() }) { Icon(Icons.Default.Search, "Search") } })
        LazyColumn { items(vm.results) { TrackRow(it, play, { favorite(it) }, it.video_id in selected, download, queue, playlists, addToPlaylist) } }
    }
}

@Composable private fun LibraryScreen(favorites: List<FavoriteTrack>, history: List<HistoryTrack>, playlists: List<LocalPlaylistEntity>, settings: PlaybackPreferences, setQuality: (String) -> Unit, setWifiOnly: (Boolean) -> Unit, createPlaylist: (String) -> Unit, play: (TrackSummary) -> Unit, remove: (TrackSummary) -> Unit, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, addToPlaylist: (String, TrackSummary) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }; var showSettings by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Column { Text("Streaming quality"); SingleChoiceSegmentedButtonRow { listOf("low", "medium", "best").forEachIndexed { index, value -> SegmentedButton(selected = settings.quality == value, onClick = { setQuality(value) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(value.replaceFirstChar { it.uppercase() }) } } }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Download on Wi-Fi only", Modifier.weight(1f)); Switch(settings.downloadWifiOnly, setWifiOnly) } } }, confirmButton = { TextButton({ showSettings = false }) { Text("Done") } })
}

@Composable private fun DownloadsScreen(downloads: List<DownloadEntity>, history: List<HistoryTrack>, play: (TrackSummary) -> Unit, downloader: DownloadController) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        if (downloads.isEmpty()) Text("Downloaded tracks will be available without a connection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn { items(downloads) { item ->
            val track = TrackSummary(item.videoId, item.title.ifBlank { item.videoId }, item.artists.split("\u001f").filter { it.isNotBlank() }, highest_resolution_thumbnail = item.artworkUrl)
            ListItem(modifier = Modifier.clickable { play(track) }, headlineContent = { Text(track.title) }, supportingContent = { Text(if (item.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) "Available offline" else "Downloading ${item.percent.toInt()}%") }, trailingContent = { IconButton({ downloader.remove(item.videoId) }) { Icon(Icons.Default.Delete, "Remove download") } })
        } }
    }
}

@Composable private fun TrackRow(track: TrackSummary, play: (TrackSummary) -> Unit, favorite: () -> Unit, selected: Boolean, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(modifier = Modifier.clickable { play(track) }, leadingContent = { AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.size(56.dp), contentScale = ContentScale.Crop) }, headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(track.artists.joinToString().ifBlank { track.uploader ?: "YouTube Music" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingContent = { Row { IconButton(favorite) { Icon(if (selected) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") }; Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Add to queue") }, { queue(track); menu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }); DropdownMenuItem({ Text("Download") }, { download(track); menu = false }, leadingIcon = { Icon(Icons.Default.Download, null) }); playlists.forEach { list -> DropdownMenuItem({ Text("Add to ${list.name}") }, { addToPlaylist(list.id, track); menu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }) } } } } })
}

@Composable private fun MiniPlayer(track: TrackSummary, playing: Boolean, toggle: () -> Unit, next: () -> Unit, expand: () -> Unit) {
    Surface(tonalElevation = 3.dp) { Row(Modifier.fillMaxWidth().height(68.dp).clickable(onClick = expand).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.size(52.dp)); Text(track.title, Modifier.weight(1f).padding(10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play") }; IconButton(next) { Icon(Icons.Default.SkipNext, "Next") } } }
}

@Composable private fun FullPlayer(state: PlaybackState, connection: PlaybackConnection) {
    val track = state.current ?: return
    val app = LocalContext.current.applicationContext as YtuneApplication
    val scope = rememberCoroutineScope()
    var lyrics by remember(track.video_id) { mutableStateOf<String?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(28.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(20.dp)); Text(track.title, style = MaterialTheme.typography.headlineSmall); Text(track.artists.joinToString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = state.positionMs.toFloat(), onValueChange = { connection.seek(it.toLong()) }, valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat())
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(connection::toggleShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; IconButton(connection::previous) { Icon(Icons.Default.SkipPrevious, "Previous") }; FilledIconButton(connection::toggle) { Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play") }; IconButton(connection::next) { Icon(Icons.Default.SkipNext, "Next") }; IconButton(connection::cycleRepeat) { Icon(Icons.Default.Repeat, "Repeat", tint = if (state.repeatMode != 0) MaterialTheme.colorScheme.primary else LocalContentColor.current) } }
        TextButton({ showLyrics = !showLyrics; if (lyrics == null) scope.launch { lyrics = runCatching { app.repository.lyrics(track.video_id).lyrics?.let { it.synced_lyrics ?: it.plain_lyrics } }.getOrNull() ?: "Lyrics are not available." } }) { Icon(Icons.Default.Lyrics, null); Spacer(Modifier.width(8.dp)); Text("Lyrics") }
        if (showLyrics) Text(lyrics ?: "Loading lyrics...", Modifier.fillMaxWidth().heightIn(max = 280.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Destination.icon() = when (this) { Destination.Home -> Icons.Default.Home; Destination.Search -> Icons.Default.Search; Destination.Library -> Icons.Default.LibraryMusic; Destination.Downloads -> Icons.Default.Download }
