package com.localshelf.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

private val AppBg = Color(0xFF080B0F)
private val AppPanel = Color(0xFF11171E)
private val AppPanel2 = Color(0xFF18212A)
private val AppAccent = Color(0xFF00A4DC)
private val AppMuted = Color(0xFFA5AFBA)

private enum class SortMode { TITLE, RECENT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocalShelfApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalShelfApp() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()

    var media by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedMovie by remember { mutableStateOf<LocalMedia?>(null) }
    var selectedShow by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<LocalMedia?>(null) }
    var prefsRevision by remember { mutableIntStateOf(0) }

    suspend fun rescan() {
        loading = true
        media = scanAllMedia(context, prefs.folders())
        loading = false
    }

    LaunchedEffect(Unit) { rescan() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs.addFolder(uri.toString())
            scope.launch { rescan() }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppAccent,
            background = AppBg,
            surface = AppPanel,
            surfaceVariant = AppPanel2,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        val currentPlayer = playing
        if (currentPlayer != null) {
            PlayerScreen(
                media = currentPlayer,
                prefs = prefs,
                onBack = {
                    playing = null
                    prefsRevision++
                }
            )
            return@MaterialTheme
        }

        val movieDetails = selectedMovie
        if (movieDetails != null) {
            MovieDetailsScreen(
                media = movieDetails,
                prefs = prefs,
                prefsRevision = prefsRevision,
                onPrefsChanged = { prefsRevision++ },
                onPlay = { playing = movieDetails },
                onBack = { selectedMovie = null }
            )
            return@MaterialTheme
        }

        val showDetails = selectedShow
        if (showDetails != null) {
            ShowDetailsScreen(
                showName = showDetails,
                episodes = media.filter { it.show == showDetails },
                prefs = prefs,
                prefsRevision = prefsRevision,
                onPrefsChanged = { prefsRevision++ },
                onPlay = { playing = it },
                onBack = { selectedShow = null }
            )
            return@MaterialTheme
        }

        Scaffold(
            containerColor = AppBg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("LocalShelf", fontWeight = FontWeight.Bold)
                            Text("On-device media", color = AppMuted, fontSize = 12.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { rescan() } }) { Icon(Icons.Default.Refresh, "Rescan") }
                        IconButton(onClick = { folderPicker.launch(null) }) { Icon(Icons.Default.CreateNewFolder, "Add media folder") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBg)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = AppPanel) {
                    val items = listOf(
                        Triple("Home", Icons.Default.Home, 0),
                        Triple("Movies", Icons.Default.Movie, 1),
                        Triple("Shows", Icons.Default.Tv, 2),
                        Triple("Library", Icons.Default.Folder, 3)
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icon, label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = AppAccent)
                    prefs.folders().isEmpty() -> EmptyLibrary { folderPicker.launch(null) }
                    else -> when (tab) {
                        0 -> HomeScreen(
                            media = media,
                            prefs = prefs,
                            prefsRevision = prefsRevision,
                            onMovie = { selectedMovie = it },
                            onShow = { selectedShow = it }
                        )
                        1 -> MoviesScreen(media.filter { !it.isEpisode }, prefs, prefsRevision) { selectedMovie = it }
                        2 -> ShowsScreen(media, prefs, prefsRevision) { selectedShow = it }
                        else -> LibraryScreen(
                            prefs = prefs,
                            mediaCount = media.size,
                            onAdd = { folderPicker.launch(null) },
                            onRemove = { uri ->
                                prefs.removeFolder(uri)
                                scope.launch { rescan() }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(80.dp), tint = AppAccent)
        Spacer(Modifier.height(18.dp))
        Text("Your media. No server.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Choose a folder containing your movies and shows. LocalShelf keeps access to that folder and builds the library directly on this phone.",
            color = AppMuted,
            modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
        )
        Button(onClick = onAdd) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Choose media folder")
        }
    }
}

@Composable
private fun HomeScreen(
    media: List<LocalMedia>,
    prefs: Prefs,
    prefsRevision: Int,
    onMovie: (LocalMedia) -> Unit,
    onShow: (String) -> Unit
) {
    val continueItems = media.filter {
        val progress = prefs.progress(it.uri)
        progress > 30_000 && it.durationMs > 0 && progress < it.durationMs * 0.95
    }.sortedByDescending { prefs.progress(it.uri) }
    val favorites = media.filter { prefs.isFavorite(it.uri) }.take(20)
    val recent = media.sortedByDescending { it.modified }.take(20)
    val shows = media.mapNotNull { it.show }.distinct().sorted()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("OFFLINE LIBRARY", color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Watch what’s already on your phone", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("${media.count { !it.isEpisode }} movies • ${shows.size} shows", color = AppMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (continueItems.isNotEmpty()) item {
            MediaRow("Continue Watching", continueItems.take(12), prefs, prefsRevision, onMovie)
        }
        if (favorites.isNotEmpty()) item {
            MediaRow("Favorites", favorites, prefs, prefsRevision, onMovie)
        }
        if (recent.isNotEmpty()) item {
            MediaRow("Recently Added", recent, prefs, prefsRevision, onMovie)
        }
        if (shows.isNotEmpty()) item {
            Column(Modifier.padding(top = 8.dp)) {
                SectionTitle("TV Shows")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shows) { show ->
                        val episodes = media.filter { it.show == show }
                        ShowPosterCard(show, episodes, onClick = { onShow(show) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRow(
    title: String,
    items: List<LocalMedia>,
    prefs: Prefs,
    prefsRevision: Int,
    onClick: (LocalMedia) -> Unit
) {
    Column(Modifier.padding(top = 6.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.uri }) { item ->
                MediaPosterCard(item, prefs, prefsRevision, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
}

@Composable
private fun SearchAndSort(
    query: String,
    onQuery: (String) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search your library") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sort == SortMode.TITLE, onClick = { onSort(SortMode.TITLE) }, label = { Text("Title") })
            FilterChip(selected = sort == SortMode.RECENT, onClick = { onSort(SortMode.RECENT) }, label = { Text("Recently added") })
        }
    }
}

@Composable
private fun MoviesScreen(media: List<LocalMedia>, prefs: Prefs, prefsRevision: Int, onClick: (LocalMedia) -> Unit) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortMode.TITLE) }
    val filtered = media.filter { it.title.contains(query, true) }.let { list ->
        when (sort) {
            SortMode.TITLE -> list.sortedBy { it.title.lowercase(Locale.getDefault()) }
            SortMode.RECENT -> list.sortedByDescending { it.modified }
        }
    }

    Column {
        Text("Movies", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp))
        SearchAndSort(query, { query = it }, sort, { sort = it })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(145.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            gridItems(filtered, key = { it.uri }) { item ->
                MediaPosterCard(item, prefs, prefsRevision, Modifier.fillMaxWidth()) { onClick(item) }
            }
        }
    }
}

@Composable
private fun ShowsScreen(media: List<LocalMedia>, prefs: Prefs, prefsRevision: Int, onShow: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortMode.TITLE) }
    val names = media.mapNotNull { it.show }.distinct().filter { it.contains(query, true) }
    val sorted = when (sort) {
        SortMode.TITLE -> names.sorted()
        SortMode.RECENT -> names.sortedByDescending { show -> media.filter { it.show == show }.maxOfOrNull { it.modified } ?: 0L }
    }

    Column {
        Text("TV Shows", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp))
        SearchAndSort(query, { query = it }, sort, { sort = it })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(145.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            gridItems(sorted) { show ->
                ShowPosterCard(show, media.filter { it.show == show }, Modifier.fillMaxWidth()) { onShow(show) }
            }
        }
    }
}

@Composable
private fun MediaPosterCard(
    media: LocalMedia,
    prefs: Prefs,
    prefsRevision: Int,
    modifier: Modifier = Modifier.width(150.dp),
    onClick: () -> Unit
) {
    val progress = prefs.progress(media.uri)
    val watched = prefs.isWatched(media.uri)
    val favorite = prefs.isFavorite(media.uri)
    val fraction = if (media.durationMs > 0) (progress.toFloat() / media.durationMs).coerceIn(0f, 1f) else 0f

    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(AppPanel, RoundedCornerShape(10.dp))) {
            VideoThumbnail(media.uri, Modifier.fillMaxSize())
            if (favorite) {
                Icon(Icons.Default.Favorite, "Favorite", tint = AppAccent, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (watched) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopStart).padding(7.dp)) {
                    Icon(Icons.Default.CheckCircle, "Watched", tint = Color.White, modifier = Modifier.padding(4.dp).size(18.dp))
                }
            }
            if (fraction > 0f) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(5.dp).align(Alignment.BottomCenter))
            }
        }
        Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp))
        Text(
            when {
                media.isEpisode -> "S%02dE%02d".format(media.season, media.episode)
                media.year != null -> media.year.toString()
                else -> formatDuration(media.durationMs)
            },
            color = AppMuted,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ShowPosterCard(show: String, episodes: List<LocalMedia>, modifier: Modifier = Modifier.width(150.dp), onClick: () -> Unit) {
    val thumb = episodes.sortedWith(compareBy<LocalMedia> { it.season ?: 0 }.thenBy { it.episode ?: 0 }).firstOrNull()?.uri
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(AppPanel, RoundedCornerShape(10.dp))) {
            VideoThumbnail(thumb, Modifier.fillMaxSize())
            Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.72f), modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)) {
                Text("${episodes.size} EP", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
            }
        }
        Text(show, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp))
        Text("${episodes.mapNotNull { it.season }.distinct().size} seasons", color = AppMuted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieDetailsScreen(
    media: LocalMedia,
    prefs: Prefs,
    prefsRevision: Int,
    onPrefsChanged: () -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val progress = prefs.progress(media.uri)
    val isFavorite = prefs.isFavorite(media.uri)
    val watched = prefs.isWatched(media.uri)

    Scaffold(
        containerColor = AppBg,
        topBar = {
            TopAppBar(
                title = { Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBg)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                VideoThumbnail(media.uri, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Column(Modifier.padding(20.dp)) {
                    Text(media.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(media.year?.toString(), formatDuration(media.durationMs).takeIf { it.isNotBlank() }).joinToString(" • "),
                        color = AppMuted,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onPlay) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (progress > 30_000) "Resume ${formatPosition(progress)}" else "Play")
                        }
                        FilledTonalIconButton(onClick = { prefs.toggleFavorite(media.uri); onPrefsChanged() }) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
                        }
                        FilledTonalIconButton(onClick = { prefs.setWatched(media.uri, !watched); onPrefsChanged() }) {
                            Icon(if (watched) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Watched")
                        }
                    }
                    if (media.subtitles.isNotEmpty()) {
                        Text("${media.subtitles.size} external subtitle track${if (media.subtitles.size == 1) "" else "s"} found", color = AppMuted, modifier = Modifier.padding(top = 18.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 18.dp))
                    Text("File", fontWeight = FontWeight.SemiBold)
                    Text(media.fileName, color = AppMuted, modifier = Modifier.padding(top = 5.dp))
                    Text("Stored and played directly from the folder you selected.", color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowDetailsScreen(
    showName: String,
    episodes: List<LocalMedia>,
    prefs: Prefs,
    prefsRevision: Int,
    onPrefsChanged: () -> Unit,
    onPlay: (LocalMedia) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val ordered = episodes.sortedWith(compareBy<LocalMedia> { it.season ?: 0 }.thenBy { it.episode ?: 0 })
    val bySeason = ordered.groupBy { it.season ?: 0 }.toSortedMap()
    val hero = ordered.firstOrNull()?.uri

    Scaffold(
        containerColor = AppBg,
        topBar = {
            TopAppBar(
                title = { Text(showName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBg)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                VideoThumbnail(hero, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Column(Modifier.padding(20.dp)) {
                    Text(showName, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${bySeason.size} seasons • ${episodes.size} episodes", color = AppMuted, modifier = Modifier.padding(top = 5.dp))
                    val resume = ordered.firstOrNull { prefs.progress(it.uri) > 30_000 && !prefs.isWatched(it.uri) }
                    val next = resume ?: ordered.firstOrNull { !prefs.isWatched(it.uri) } ?: ordered.firstOrNull()
                    if (next != null) {
                        Button(onClick = { onPlay(next) }, modifier = Modifier.padding(top = 16.dp)) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (resume != null) "Resume S%02dE%02d".format(next.season, next.episode) else "Play S%02dE%02d".format(next.season, next.episode))
                        }
                    }
                }
            }

            bySeason.forEach { (season, eps) ->
                item {
                    Text("Season $season", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
                items(eps, key = { it.uri }) { ep ->
                    EpisodeRow(ep, prefs, prefsRevision, onPrefsChanged, onPlay)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: LocalMedia,
    prefs: Prefs,
    prefsRevision: Int,
    onPrefsChanged: () -> Unit,
    onPlay: (LocalMedia) -> Unit
) {
    val progress = prefs.progress(ep.uri)
    val watched = prefs.isWatched(ep.uri)
    Card(
        colors = CardDefaults.cardColors(containerColor = AppPanel),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp).fillMaxWidth().clickable { onPlay(ep) }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(130.dp).aspectRatio(16f / 9f).background(AppPanel2, RoundedCornerShape(8.dp))) {
                VideoThumbnail(ep.uri, Modifier.fillMaxSize())
                Icon(Icons.Default.PlayCircle, null, modifier = Modifier.align(Alignment.Center).size(36.dp), tint = Color.White)
                if (progress > 0 && ep.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.toFloat() / ep.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter)
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("${ep.episode ?: "?"}. ${ep.episodeTitle ?: ep.title}", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatDuration(ep.durationMs), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = { prefs.setWatched(ep.uri, !watched); onPrefsChanged() }) {
                Icon(if (watched) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Toggle watched", tint = if (watched) AppAccent else AppMuted)
            }
        }
    }
}

@Composable
private fun LibraryScreen(prefs: Prefs, mediaCount: Int, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val folders = prefs.folders().toList().sorted()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Library", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("$mediaCount video files indexed", color = AppMuted, modifier = Modifier.padding(top = 4.dp))
            Text("Only folders you explicitly choose are scanned. LocalShelf does not need a server or an account.", color = AppMuted, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add folder")
            }
            Spacer(Modifier.height(8.dp))
        }
        items(folders) { raw ->
            val uri = Uri.parse(raw)
            ListItem(
                headlineContent = { Text(uri.lastPathSegment ?: "Media folder", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(raw, maxLines = 2, overflow = TextOverflow.Ellipsis, color = AppMuted) },
                leadingContent = { Icon(Icons.Default.Folder, null, tint = AppAccent) },
                trailingContent = { IconButton(onClick = { onRemove(raw) }) { Icon(Icons.Default.DeleteOutline, "Remove") } },
                colors = ListItemDefaults.colors(containerColor = AppPanel)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
}

private fun formatPosition(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "%d:%02d".format(hours, minutes) else "${minutes}m"
}
