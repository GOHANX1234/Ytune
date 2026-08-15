package com.ytune.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.ytune.app.data.*
import com.ytune.app.ui.theme.YtuneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YtuneTheme { YtuneApp() } }
    }
}

class YtuneViewModel : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<TrackSummary>>(emptyList())
    var favorites by mutableStateOf<List<TrackSummary>>(emptyList())
    var playlist by mutableStateOf<PlaylistEnvelope?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun search(term: String = query) {
        if (term.isBlank()) return
        query = term
        playlistIdFrom(term)?.let { loadPlaylist(it); return }
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { YtuneRepository.search(term).results }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Search failed" }
            loading = false
        }
    }

    fun toggleFavorite(track: TrackSummary) {
        favorites = if (favorites.any { it.video_id == track.video_id }) favorites.filterNot { it.video_id == track.video_id } else favorites + track
    }

    fun loadPlaylist(id: String) = viewModelScope.launch {
        loading = true
        runCatching { YtuneRepository.playlist(id) }.onSuccess { playlist = it }.onFailure { error = it.message }
        loading = false
    }

    private fun playlistIdFrom(value: String): String? = Regex("(?:[?&]list=|^)([A-Za-z0-9_-]{10,})").find(value)?.groupValues?.getOrNull(1)?.takeIf { it.startsWith("PL") || it.startsWith("OLAK") || value.contains("list=") }
}

private enum class Destination { Home, Search, Library }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtuneApp(vm: YtuneViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }
    var destination by remember { mutableStateOf(Destination.Home) }
    var current by remember { mutableStateOf<TrackSummary?>(null) }
    var playing by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var showLyrics by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener { override fun onIsPlayingChanged(value: Boolean) { playing = value } }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    fun play(track: TrackSummary) {
        vm.error = null
        val playbackUrl = "${YtuneRepository.baseUrl}api/v1/tracks/${track.video_id}/play"
        player.setMediaItem(MediaItem.fromUri(playbackUrl))
        player.prepare()
        player.play()
        current = track
    }

    Scaffold(
        bottomBar = {
            Column {
                current?.let { track -> MiniPlayer(track, playing, { if (playing) player.pause() else player.play() }, { showLyrics = true; scope.launch { lyrics = runCatching { YtuneRepository.lyrics(track.video_id).lyrics }.getOrNull() } }) }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Destination.entries.forEach { item ->
                        val icon = when (item) { Destination.Home -> Icons.Default.Home; Destination.Search -> Icons.Default.Search; Destination.Library -> Icons.Default.LibraryMusic }
                        NavigationBarItem(destination == item, { destination = item }, { Icon(icon, item.name) }, label = { Text(item.name) })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                Destination.Home -> HomeScreen(vm, { vm.search(it); destination = Destination.Search }, { play(it) })
                Destination.Search -> SearchScreen(vm, { play(it) })
                Destination.Library -> LibraryScreen(vm.favorites, { play(it) }, { vm.toggleFavorite(it) })
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)) }
        }
    }

    if (showLyrics) ModalBottomSheet(onDismissRequest = { showLyrics = false }) { LyricsSheet(current, lyrics) }
}

@Composable
private fun HomeScreen(vm: YtuneViewModel, search: (String) -> Unit, play: (TrackSummary) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Text("Ytune", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black); Text("Music for right now", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFF5C1832)))).padding(24.dp)) {
                Column(Modifier.align(Alignment.BottomStart)) { Text("Find your next repeat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Search millions of tracks and play instantly") }
            }
        }
        item { Text("Start listening", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Top hits", "Chill mix", "Workout", "Focus", "New music")) { term -> SuggestionChip(onClick = { search(term) }, label = { Text(term) }) } } }
        vm.playlist?.let { loaded -> item { Text(loaded.playlist.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${loaded.playlist.track_count} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }; items(loaded.tracks) { TrackRow(it.asSummary(), play, {}, false) } }
        if (vm.results.isNotEmpty()) { item { Text("Recently discovered", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; items(vm.results.take(5)) { TrackRow(it, play, { vm.toggleFavorite(it) }, vm.favorites.any { favorite -> favorite.video_id == it.video_id }) } }
    }
}

@Composable
private fun SearchScreen(vm: YtuneViewModel, play: (TrackSummary) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Songs, artists, albums, or playlist link") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = vm::search) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Search") } })
        Spacer(Modifier.height(12.dp))
        LazyColumn { items(vm.results) { track -> TrackRow(track, play, { vm.toggleFavorite(track) }, vm.favorites.any { it.video_id == track.video_id }) } }
    }
}

@Composable
private fun LibraryScreen(favorites: List<TrackSummary>, play: (TrackSummary) -> Unit, remove: (TrackSummary) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) { Text("Your Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Spacer(Modifier.height(16.dp)); if (favorites.isEmpty()) Text("Save tracks with the heart button and they will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) else LazyColumn { items(favorites) { TrackRow(it, play, { remove(it) }, true) } } }
}

@Composable
private fun TrackRow(track: TrackSummary, play: (TrackSummary) -> Unit, favorite: () -> Unit, selected: Boolean) {
    Row(Modifier.fillMaxWidth().clickable { play(track) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TrackArtwork(track.highest_resolution_thumbnail ?: track.thumbnail, Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(track.artists.joinToString().ifBlank { "YouTube Music" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = favorite) { Icon(if (selected) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
    }
}

@Composable
private fun MiniPlayer(track: TrackSummary, playing: Boolean, toggle: () -> Unit, lyrics: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) { Row(Modifier.fillMaxWidth().height(70.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { TrackArtwork(track.highest_resolution_thumbnail ?: track.thumbnail, Modifier.size(54.dp)); Text(track.title, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); IconButton(onClick = lyrics) { Icon(Icons.Default.Lyrics, "Lyrics") }; IconButton(onClick = toggle) { Icon(if (playing) Icons.Default.PauseCircle else Icons.Default.PlayCircle, if (playing) "Pause" else "Play", Modifier.size(32.dp)) } } }
}

@Composable
private fun TrackArtwork(url: String?, modifier: Modifier) {
    if (url.isNullOrBlank()) {
        Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        AsyncImage(url, null, modifier.clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun LyricsSheet(track: TrackSummary?, lyrics: Lyrics?) { Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) { Text(track?.title ?: "Lyrics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); Text(when { lyrics == null -> "Loading lyrics..."; lyrics.instrumental -> "Instrumental"; !lyrics.found -> "Lyrics are not available for this track."; else -> lyrics.synced_lyrics ?: lyrics.plain_lyrics ?: "Lyrics are not available." }, style = MaterialTheme.typography.bodyLarge) } }

private fun PlaylistTrack.asSummary() = TrackSummary(video_id = video_id, title = title, artists = listOfNotNull(uploader), duration_seconds = duration_seconds, thumbnail = thumbnail)
