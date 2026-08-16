@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ytune.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ── Glass design tokens ────────────────────────────────────────────── */

private val GlassWhite = Color.White.copy(alpha = 0.07f)
private val GlassBorder = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.09f))
private val GlassShape = RoundedCornerShape(16.dp)
private val GlassShapeSmall = RoundedCornerShape(12.dp)

/* ── Activity ───────────────────────────────────────────────────────── */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(icon, android.view.View.SCALE_X, 1f, 1.08f, 0.92f),
                    ObjectAnimator.ofFloat(icon, android.view.View.SCALE_Y, 1f, 1.08f, 0.92f),
                    ObjectAnimator.ofFloat(provider.view, android.view.View.ALPHA, 1f, 0f)
                )
                duration = 360
                interpolator = DecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = provider.remove()
                })
                start()
            }
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        setContent { YtuneTheme { YtuneApp() } }
    }
}

/* ── ViewModel ──────────────────────────────────────────────────────── */

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
    fun playlistTracks(id: String): Flow<List<TrackEntity>> = repository.playlistTracks(id)
    fun removeFromPlaylist(id: String, videoId: String) = viewModelScope.launch { repository.removeFromPlaylist(id, videoId) }
    fun deletePlaylist(id: String) = viewModelScope.launch { repository.deletePlaylist(id) }
    fun setQuality(value: String) = viewModelScope.launch { repository.setQuality(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { repository.setWifiOnly(value) }
}

/* ── Navigation ─────────────────────────────────────────────────────── */

private enum class Destination { Home, Search, Library, Downloads }
private val LocalPendingDownloads = compositionLocalOf<Set<String>> { emptySet() }

private fun Destination.iconFilled(): ImageVector = when (this) {
    Destination.Home -> Icons.Filled.Home
    Destination.Search -> Icons.Filled.Search
    Destination.Library -> Icons.Filled.LibraryMusic
    Destination.Downloads -> Icons.Filled.Download
}
private fun Destination.iconOutlined(): ImageVector = when (this) {
    Destination.Home -> Icons.Outlined.Home
    Destination.Search -> Icons.Outlined.Search
    Destination.Library -> Icons.Outlined.LibraryMusic
    Destination.Downloads -> Icons.Outlined.Download
}

/* ── Root composable ────────────────────────────────────────────────── */

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

    /* Play a single track (adds to queue) */
    fun play(track: TrackSummary) { connection.play(track, replaceQueue = false); vm.recordPlayed(track) }
    /* Play from a list context so next/prev works */
    fun playFrom(tracks: List<TrackSummary>, index: Int) { connection.playAll(tracks, index); vm.recordPlayed(tracks[index]) }
    val favoriteIds = remember(favorites) { favorites.map { it.track.videoId }.toSet() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(!showPlayer, enter = slideInVertically { it } + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)), exit = slideOutVertically { it } + fadeOut()) {
                Column {
                    playback.current?.let { track ->
                        MiniPlayer(track, playback.playing, playback.positionMs, playback.durationMs, connection::toggle, connection::next) { showPlayer = true }
                    }
                    LiquidNavBar(destination.value) { destination.value = it }
                }
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalPendingDownloads provides pendingDownloads) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedVisibility(showPlayer, enter = slideInVertically { it } + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)), exit = slideOutVertically { it } + fadeOut()) {
                    FullPlayer(playback, connection) { showPlayer = false }
                }
                AnimatedVisibility(!showPlayer, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                    Crossfade(destination.value, animationSpec = tween(240), label = "nav") { dest ->
                        when (dest) {
                            Destination.Home -> HomeScreen(vm, { destination.value = Destination.Search; vm.search(it) }, ::play, ::playFrom, { vm.toggleFavorite(it) }, favoriteIds, downloader::download, connection::addToQueue, playlists, vm::addToPlaylist)
                            Destination.Search -> SearchScreen(vm, ::play, ::playFrom, { vm.toggleFavorite(it) }, favoriteIds, downloader::download, connection::addToQueue, playlists, vm::addToPlaylist)
                            Destination.Library -> LibraryScreen(favorites, history, playlists, settings, vm::setQuality, vm::createPlaylist, ::play, ::playFrom, { vm.toggleFavorite(it) }, downloader::download, connection::addToQueue, vm::addToPlaylist, vm::playlistTracks, vm::removeFromPlaylist, vm::deletePlaylist)
                            Destination.Downloads -> DownloadsScreen(downloads, downloadProgress, ::play, ::playFrom, downloader)
                        }
                    }
                }
                if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            }
        }
    }
}

/* ── iOS-style compact navigation bar ───────────────────────────────── */

@Composable
private fun LiquidNavBar(current: Destination, onSelect: (Destination) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Destination.entries.forEach { dest ->
                val selected = current == dest
                val scale by animateFloatAsState(if (selected) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "nav-scale")
                val alpha by animateFloatAsState(if (selected) 1f else 0.55f, tween(200), label = "nav-alpha")
                Column(
                    modifier = Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(dest) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(if (selected) dest.iconFilled() else dest.iconOutlined(), dest.name, Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(dest.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/* ── Compact mini player ────────────────────────────────────────────── */

@Composable
private fun MiniPlayer(track: TrackSummary, playing: Boolean, positionMs: Long, durationMs: Long, toggle: () -> Unit, next: () -> Unit, expand: () -> Unit) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(progress, tween(300), label = "mini-progress")
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = expand),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column {
            /* Thin progress line */
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(animatedProgress).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
            }
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(track.artists.joinToString().ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(toggle, Modifier.size(36.dp)) {
                    Crossfade(playing, label = "mini-pp") { p ->
                        Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", Modifier.size(22.dp))
                    }
                }
                IconButton(next, Modifier.size(36.dp)) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(20.dp)) }
            }
        }
    }
}

/* ── Home ────────────────────────────────────────────────────────────── */

@Composable
private fun HomeScreen(vm: YtuneViewModel, search: (String) -> Unit, play: (TrackSummary) -> Unit, playFrom: (List<TrackSummary>, Int) -> Unit, favorite: (TrackSummary) -> Unit, selected: Set<String>, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    val remotePlaylist by vm.playlist.collectAsStateWithLifecycle()
    val recent by vm.recentDiscoveries.collectAsStateWithLifecycle(emptyList())
    val recentTracks = remember(recent) { recent.map { it.track.toSummary() } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)); Text("Ytune", style = MaterialTheme.typography.headlineLarge); Text("Everything you love, right here", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        item { GlassTextField(vm.query, { vm.query = it }, "Songs, artists, or playlist links", vm.loading) { search(vm.query) } }
        item { Text("Quick starts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)); Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Top hits", "Chill", "Workout", "Focus").forEach { GlassChip(it) { search(it) } } } }
        remotePlaylist?.let { loaded ->
            val plTracks = remember(loaded) { loaded.tracks.map { item -> TrackSummary(item.video_id, item.title, listOfNotNull(item.uploader), duration_seconds = item.duration_seconds, thumbnail = item.thumbnail) } }
            item { Text(loaded.playlist.title, style = MaterialTheme.typography.titleLarge); Text("${loaded.tracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            itemsIndexed(plTracks) { index, track -> TrackRow(track, { playFrom(plTracks, index) }, { favorite(track) }, track.video_id in selected, download, queue, playlists, addToPlaylist) }
        }
        if (recentTracks.isNotEmpty()) {
            item { Text("Recently discovered", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            itemsIndexed(recentTracks.take(12), key = { _, t -> t.video_id }) { index, track -> TrackRow(track, { playFrom(recentTracks.take(12), index) }, { favorite(track) }, track.video_id in selected, download, queue, playlists, addToPlaylist) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ── Search ──────────────────────────────────────────────────────────── */

@Composable
private fun SearchScreen(vm: YtuneViewModel, play: (TrackSummary) -> Unit, playFrom: (List<TrackSummary>, Int) -> Unit, favorite: (TrackSummary) -> Unit, selected: Set<String>, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit) {
    val searches by vm.searchHistory.collectAsStateWithLifecycle(emptyList())
    val suggestions = searches.map { it.query }.filter { vm.query.isBlank() || it.contains(vm.query, ignoreCase = true) }.take(6)
    LaunchedEffect(vm.query) { delay(450); if (vm.query.trim().length >= 2) vm.search(vm.query) else vm.clearSearchResults() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        GlassTextField(vm.query, { vm.query = it }, "What do you want to listen to?", vm.loading) { vm.search() }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Recent", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); IconButton(vm::clearSearchHistory, Modifier.size(32.dp)) { Icon(Icons.Default.ClearAll, "Clear", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(suggestions) { suggestion -> GlassChip(suggestion) { vm.query = suggestion; vm.search(suggestion) } } }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(vm.results, key = { _, t -> t.video_id }) { index, track ->
                TrackRow(track, { playFrom(vm.results, index) }, { favorite(track) }, track.video_id in selected, download, queue, playlists, addToPlaylist)
            }
        }
    }
}

/* ── Library ─────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(favorites: List<FavoriteTrack>, history: List<HistoryTrack>, playlists: List<LocalPlaylistEntity>, settings: PlaybackPreferences, setQuality: (String) -> Unit, createPlaylist: (String) -> Unit, play: (TrackSummary) -> Unit, playFrom: (List<TrackSummary>, Int) -> Unit, favorite: (TrackSummary) -> Unit, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, addToPlaylist: (String, TrackSummary) -> Unit, playlistTracks: (String) -> Flow<List<TrackEntity>>, removeFromPlaylist: (String, String) -> Unit, deletePlaylist: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    val favoriteIds = favorites.map { it.track.videoId }.toSet()
    val favTracks = remember(favorites) { favorites.map { it.track.toSummary() } }
    val histTracks = remember(history) { history.filter { it.track != null }.take(20).map { it.track!!.toSummary() } }

    if (selectedPlaylist != null) {
        val tracksFlow = remember(selectedPlaylist.id) { playlistTracks(selectedPlaylist.id) }
        val tracks by tracksFlow.collectAsStateWithLifecycle(emptyList())
        BackHandler { selectedPlaylistId = null }
        PlaylistDetailScreen(selectedPlaylist, tracks, play, playFrom, favorite, favoriteIds, download, queue, playlists, addToPlaylist, { removeFromPlaylist(selectedPlaylist.id, it) }, { selectedPlaylistId = null }, { deletePlaylist(selectedPlaylist.id); selectedPlaylistId = null })
        return
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Your Library", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f)); IconButton({ showSettings = true }) { Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); IconButton({ showCreate = true }) { Icon(Icons.Default.Add, "Create", tint = MaterialTheme.colorScheme.primary) } } }
        items(playlists, key = { it.id }) { playlist ->
            Surface(onClick = { selectedPlaylistId = playlist.id }, shape = GlassShapeSmall, color = GlassWhite, border = GlassBorder, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("View songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Box(Modifier.size(40.dp).clip(GlassShapeSmall).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) } },
                    trailingContent = { Icon(Icons.Default.ChevronRight, "Open", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
        if (favTracks.isNotEmpty()) {
            item { Text("Favorites", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(favTracks) { index, track -> TrackRow(track, { playFrom(favTracks, index) }, { favorite(track) }, true, download, queue, playlists, addToPlaylist) }
        } else {
            item { Text("Favorites", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            item { Text("Favourite tracks to keep them here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        if (histTracks.isNotEmpty()) {
            item { Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(histTracks) { index, track -> TrackRow(track, { playFrom(histTracks, index) }, { favorite(track) }, track.video_id in favoriteIds, download, queue, playlists, addToPlaylist) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (showCreate) AlertDialog(onDismissRequest = { showCreate = false }, title = { Text("New playlist") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, shape = GlassShapeSmall) }, confirmButton = { TextButton({ createPlaylist(name); name = ""; showCreate = false }) { Text("Create") } }, dismissButton = { TextButton({ showCreate = false }) { Text("Cancel") } })
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Column { Text("Streaming quality", style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); SingleChoiceSegmentedButtonRow { listOf("low", "medium", "best").forEachIndexed { index, value -> SegmentedButton(selected = settings.quality == value, onClick = { setQuality(value) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(value.replaceFirstChar { it.uppercase() }) } } } } }, confirmButton = { TextButton({ showSettings = false }) { Text("Done") } })
}

/* ── Playlist detail ─────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailScreen(playlist: LocalPlaylistEntity, tracks: List<TrackEntity>, play: (TrackSummary) -> Unit, playFrom: (List<TrackSummary>, Int) -> Unit, favorite: (TrackSummary) -> Unit, selected: Set<String>, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit, remove: (String) -> Unit, close: () -> Unit, delete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val summaries = remember(tracks) { tracks.map { it.toSummary() } }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(playlist.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text("${tracks.size} ${if (tracks.size == 1) "song" else "songs"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp))
        Spacer(Modifier.height(12.dp))
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Songs added to this playlist will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(summaries, key = { _, t -> t.video_id }) { index, summary ->
                    TrackRow(summary, { playFrom(summaries, index) }, { favorite(summary) }, summary.video_id in selected, download, queue, playlists, addToPlaylist, onRemove = { remove(summary.video_id) })
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete playlist?") }, text = { Text("${playlist.name} and its song list will be removed.") }, confirmButton = { TextButton({ confirmDelete = false; delete() }) { Text("Delete") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

/* ── Downloads ───────────────────────────────────────────────────────── */

@Composable
private fun DownloadsScreen(downloads: List<DownloadEntity>, progress: Map<String, Float>, play: (TrackSummary) -> Unit, playFrom: (List<TrackSummary>, Int) -> Unit, downloader: DownloadController) {
    val context = LocalContext.current
    val completed = remember(downloads) { downloads.filter { it.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED } }
    val completedTracks = remember(completed) { completed.map { TrackSummary(it.videoId, it.title.ifBlank { it.videoId }, it.artists.split("\u001f").filter { a -> a.isNotBlank() }, highest_resolution_thumbnail = it.artworkUrl) } }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineLarge)
        Text("Your music, available offline", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (downloads.isEmpty()) Text("Downloaded tracks will be available without a connection.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(downloads, key = { _, d -> d.videoId }) { index, item ->
                val track = TrackSummary(item.videoId, item.title.ifBlank { item.videoId }, item.artists.split("\u001f").filter { it.isNotBlank() }, highest_resolution_thumbnail = item.artworkUrl)
                val percent = progress[item.videoId] ?: item.percent
                val active = item.state == androidx.media3.exoplayer.offline.Download.STATE_QUEUED || item.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
                val isCompleted = item.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
                val artist = track.artists.joinToString().ifBlank { "Unknown artist" }
                val artwork = ArtworkCache.displayUri(context, item.videoId, item.artworkUrl)
                val completedIndex = if (isCompleted) completed.indexOfFirst { it.videoId == item.videoId } else -1
                Surface(shape = GlassShapeSmall, color = GlassWhite, border = GlassBorder, modifier = Modifier.fillMaxWidth().animateItem().clickable(enabled = isCompleted) { if (completedIndex >= 0) playFrom(completedTracks, completedIndex) else play(track) }) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(artwork, null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (isCompleted) Icons.Default.OfflinePin else if (item.error != null) Icons.Default.ErrorOutline else Icons.Default.Download, null, Modifier.size(14.dp), tint = if (item.error != null) MaterialTheme.colorScheme.error else if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isCompleted) "Available offline" else if (item.error != null) "Failed" else "${percent.toInt()}%", style = MaterialTheme.typography.labelSmall, color = if (item.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isCompleted) IconButton({ if (completedIndex >= 0) playFrom(completedTracks, completedIndex) else play(track) }, Modifier.size(32.dp)) { Icon(Icons.Default.PlayArrow, "Play", Modifier.size(20.dp)) }
                                IconButton({ downloader.remove(item.videoId) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.DeleteOutline, "Remove", Modifier.size(18.dp)) }
                            }
                        }
                        if (active) LinearProgressIndicator(progress = { (percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.White.copy(alpha = 0.06f))
                    }
                }
            }
        }
    }
}

/* ── Glass track row ─────────────────────────────────────────────────── */

@Composable
private fun TrackRow(track: TrackSummary, play: () -> Unit, favorite: () -> Unit, selected: Boolean, download: (TrackSummary) -> Unit, queue: (TrackSummary) -> Unit, playlists: List<LocalPlaylistEntity>, addToPlaylist: (String, TrackSummary) -> Unit, onRemove: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val downloading = track.video_id in LocalPendingDownloads.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh), label = "row-scale")
    Surface(
        shape = GlassShapeSmall,
        color = GlassWhite,
        border = GlassBorder,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        ListItem(
            modifier = Modifier.clickable(interactionSource = interactionSource, indication = null) { play() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) },
            headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(track.artists.joinToString().ifBlank { track.uploader ?: "YouTube Music" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(favorite, Modifier.size(32.dp)) {
                        Crossfade(selected, label = "fav") { isFav ->
                            Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", Modifier.size(18.dp), tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box {
                        if (downloading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else IconButton({ menu = true }, Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "More", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Add to queue") }, { queue(track); menu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) })
                            DropdownMenuItem({ Text(if (downloading) "Starting…" else "Download") }, { if (!downloading) download(track); menu = false }, enabled = !downloading, leadingIcon = { if (downloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Download, null) })
                            onRemove?.let { action -> DropdownMenuItem({ Text("Remove") }, { action(); menu = false }, leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) }) }
                            playlists.forEach { list -> DropdownMenuItem({ Text("Add to ${list.name}") }, { addToPlaylist(list.id, track); menu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }) }
                        }
                    }
                }
            }
        )
    }
}

/* ── Full player ─────────────────────────────────────────────────────── */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPlayer(state: PlaybackState, connection: PlaybackConnection, close: () -> Unit) {
    BackHandler { close() }
    val track = state.current ?: return
    val app = LocalContext.current.applicationContext as YtuneApplication
    val scope = rememberCoroutineScope()
    var lyrics by remember(track.video_id) { mutableStateOf<String?>(null) }
    var lyricsLoading by remember(track.video_id) { mutableStateOf(false) }
    val pager = rememberPagerState { 2 }
    val syncedLines = remember(lyrics) { parseLrc(lyrics) }
    val activeLyric = syncedLines.indexOfLast { it.timeMs <= state.positionMs }.coerceAtLeast(0)
    val lyricListState = rememberLazyListState()
    LaunchedEffect(pager.currentPage, track.video_id) {
        if (pager.currentPage == 1 && lyrics == null && !lyricsLoading) {
            lyricsLoading = true
            lyrics = runCatching {
                val cached = app.repository.cachedLyrics(track.video_id)
                if (cached != null && cached.found) {
                    cached.syncedLyrics ?: cached.plainLyrics
                } else {
                    app.repository.lyrics(track.video_id).lyrics?.let { it.synced_lyrics ?: it.plain_lyrics }
                }
            }.getOrNull() ?: "Lyrics are not available."
            lyricsLoading = false
        }
    }
    LaunchedEffect(activeLyric, pager.currentPage) { if (pager.currentPage == 1 && syncedLines.isNotEmpty()) lyricListState.animateScrollToItem(activeLyric) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        /* Top bar */
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(close, Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (pager.currentPage == 0) "Now Playing" else "Lyrics", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ scope.launch { pager.animateScrollToPage(if (pager.currentPage == 0) 1 else 0) } }, Modifier.size(40.dp)) {
                Icon(if (pager.currentPage == 0) Icons.Default.Lyrics else Icons.Default.GraphicEq, "Toggle", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        /* Pager */
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            if (page == 0) PlayerPage(state, connection) { scope.launch { pager.animateScrollToPage(1) } }
            else LyricsPage(lyrics, lyricsLoading, syncedLines, activeLyric, lyricListState, connection)
        }
        /* Page indicator */
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
            repeat(2) { page ->
                val width by animateDpAsState(if (pager.currentPage == page) 16.dp else 6.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dot-w")
                Box(Modifier.padding(2.dp).size(width, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (pager.currentPage == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

/* ── Player page ─────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlayerPage(state: PlaybackState, connection: PlaybackConnection, showLyrics: () -> Unit) {
    val track = state.current ?: return
    val sliderPos = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val displayPos = if (dragging) sliderPos.floatValue else state.positionMs.toFloat()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        /* Artwork */
        item {
            AsyncImage(track.highest_resolution_thumbnail ?: track.thumbnail, null,
                Modifier.fillMaxWidth().widthIn(max = 400.dp).aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop)
        }
        /* Title + artist */
        item {
            Spacer(Modifier.height(20.dp))
            Text(track.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artists.joinToString().ifBlank { track.uploader ?: "Unknown artist" }, Modifier.fillMaxWidth().padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        /* Slider */
        item {
            Spacer(Modifier.height(16.dp))
            Slider(
                value = displayPos,
                onValueChange = { sliderPos.floatValue = it; dragging = true },
                onValueChangeFinished = { connection.seek(sliderPos.floatValue.toLong()); dragging = false },
                valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(formatTime(displayPos.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("-${formatTime((state.durationMs - displayPos.toLong()).coerceAtLeast(0))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        /* Transport controls */
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                /* Shuffle */
                IconButton(connection::toggleShuffle, Modifier.size(44.dp)) {
                    Icon(Icons.Default.Shuffle, "Shuffle", Modifier.size(22.dp), tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.shuffle) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                /* Previous */
                IconButton(connection::previous, Modifier.size(48.dp), enabled = state.hasPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(30.dp), tint = if (state.hasPrevious) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                /* Play/Pause */
                FilledIconButton(connection::toggle, Modifier.size(60.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Crossfade(state.playing, animationSpec = tween(180), label = "pp") { playing ->
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", Modifier.size(30.dp))
                    }
                }
                /* Next */
                IconButton(connection::next, Modifier.size(48.dp), enabled = state.hasNext) {
                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(30.dp), tint = if (state.hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                /* Repeat */
                IconButton(connection::cycleRepeat, Modifier.size(44.dp)) {
                    Icon(if (state.repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat", Modifier.size(22.dp), tint = if (state.repeatMode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.repeatMode != 0) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        /* Lyrics shortcut */
        item {
            OutlinedButton(showLyrics, Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
                Icon(Icons.Default.Lyrics, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Lyrics")
            }
        }
        /* Up next */
        if (state.queue.isNotEmpty()) {
            item { Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text("Up next", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text("${state.queue.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } }
            itemsIndexed(state.queue, key = { index, item -> "$index-${item.video_id}" }) { index, item ->
                val isCurrent = index == state.currentIndex
                ListItem(
                    modifier = Modifier.fillMaxWidth().clip(GlassShapeSmall).clickable { connection.seekToItem(index) },
                    colors = ListItemDefaults.colors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent),
                    leadingContent = { AsyncImage(item.highest_resolution_thumbnail ?: item.thumbnail, null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) },
                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text(item.artists.joinToString(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { if (isCurrent) Icon(Icons.Default.GraphicEq, "Playing", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                )
            }
        }
    }
}

/* ── Lyrics page ─────────────────────────────────────────────────────── */

@Composable
private fun LyricsPage(lyrics: String?, loading: Boolean, synced: List<LrcLine>, active: Int, listState: androidx.compose.foundation.lazy.LazyListState, connection: PlaybackConnection) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp) }
        lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Swipe here to load lyrics", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge) }
        synced.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(synced) { index, line ->
                val isActive = index == active
                val alpha by animateFloatAsState(if (isActive) 1f else 0.4f, tween(300), label = "lyric-alpha")
                val scale by animateFloatAsState(if (isActive) 1f else 0.95f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "lyric-scale")
                Text(
                    line.text,
                    Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clickable { connection.seek(line.timeMs) },
                    style = if (isActive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
            }
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp)) { item { Text(lyrics, style = MaterialTheme.typography.titleLarge, lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 1.25f, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) } }
    }
}

/* ── Shared glass components ─────────────────────────────────────────── */

@Composable
private fun GlassTextField(value: String, onChange: (String) -> Unit, placeholder: String, loading: Boolean, onSearch: () -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium) },
        trailingIcon = {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            else IconButton(onSearch) { Icon(Icons.Default.Search, "Search", Modifier.size(20.dp)) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = GlassWhite,
            unfocusedContainerColor = GlassWhite,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun GlassChip(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = GlassWhite, border = GlassBorder) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/* ── Utilities ───────────────────────────────────────────────────────── */

private data class LrcLine(val timeMs: Long, val text: String)
private fun formatTime(value: Long): String = "%d:%02d".format(value.coerceAtLeast(0) / 60_000, value.coerceAtLeast(0) / 1_000 % 60)
private fun parseLrc(value: String?): List<LrcLine> = value.orEmpty().lineSequence().flatMap { raw ->
    val match = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)").find(raw) ?: return@flatMap emptySequence()
    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
    sequenceOf(LrcLine((match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()) * 1000 + fraction, match.groupValues[4]))
}.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()
