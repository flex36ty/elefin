package com.flex.elefin.screens

import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.flex.elefin.*
import com.flex.elefin.jellyfin.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberLibraryDrawerEntries(
    enabled: Boolean, api: JellyfinApiService?, settings: AppSettings,
    libraryId: String, libraryName: String
): List<TvDrawerEntry> {
    val context = LocalContext.current
    var libraries by remember(api, libraryId) {
        mutableStateOf(listOf(JellyfinLibrary(libraryId, libraryName)))
    }
    LaunchedEffect(enabled, api) {
        if (enabled && api != null) {
            try { libraries = api.getLibraries() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the current library available while offline. */ }
        }
    }
    return libraries.filter { it.isVisibleInSideNavigation(settings.showMusicLibraries) }.map { library ->
        val icon = when (library.CollectionType) {
            "movies" -> Icons.Default.Movie
            "tvshows" -> Icons.Default.Tv
            "music" -> Icons.Default.LibraryMusic
            else -> Icons.Default.VideoLibrary
        }
        TvDrawerEntry(library.Id, library.Name, icon, library.Id == libraryId) {
            if (library.Id != libraryId) {
                val intent = when (library.CollectionType) {
                    "movies" -> MoviesLibraryActivity.createIntent(context, library.Id, library.Name)
                    "tvshows" -> TvShowsLibraryActivity.createIntent(context, library.Id, library.Name)
                    "music" -> MusicActivity.createIntent(context)
                    else -> Intent(context, MainActivity::class.java)
                        .putExtra("select_tab_action", "library:${library.Id}")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun LibraryDrawerTabs(libraryName: String, selectedTab: String, onSelect: (String) -> Unit, contentFocus: FocusRequester, modifier: Modifier = Modifier) {
    val tabs = listOf("Recommendations" to "recommendations", "Browse Library" to "library", "Discover" to "discover")
    val activeTabFocus = remember { FocusRequester() }
    Row(modifier = modifier.widthIn(max = 740.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = libraryName,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.90f),
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp).padding(end = 16.dp)
        )
    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.second == selectedTab }.coerceAtLeast(0),
        modifier = Modifier.widthIn(max = 560.dp)
            .focusProperties { enter = { activeTabFocus } }.focusGroup(),
        separator = { Spacer(Modifier.width(11.dp)) },
        indicator = { positions, focused ->
            TabRowDefaults.UnderlinedIndicator(positions[tabs.indexOfFirst { it.second == selectedTab }.coerceAtLeast(0)], focused)
        }
    ) {
        tabs.forEach { (label, id) ->
            var focused by remember { mutableStateOf(false) }
            Tab(colors = TabDefaults.underlinedIndicatorTabColors(), modifier = Modifier
                .then(if (selectedTab == id) Modifier.focusRequester(activeTabFocus) else Modifier)
                .onFocusChanged { focused = it.hasFocus }
                .then(if (focused) Modifier.background(Color.White, RoundedCornerShape(4.dp)) else Modifier)
                .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    // Empty/loading tabs have no card target yet. Keep focus on the tab.
                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        runCatching { contentFocus.requestFocus() }
                    }
                    true
                } else false
            }, selected = selectedTab == id, onFocus = { }, onClick = { onSelect(id) }) {
                Text(label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.90f),
                    color = if (focused) Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 13.44.dp, vertical = 4.dp))
            }
        }
    }
    }
}

@Composable
internal fun LibraryBrowseTools(
    genres: List<String>, selectedGenre: String?, onGenreSelected: (String?) -> Unit,
    sortType: SortType, onSortSelected: (SortType) -> Unit,
    entryFocus: FocusRequester, contentFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var focusedControl by remember { mutableIntStateOf(0) }
    val genreFocus = remember { FocusRequester() }
    val optionFocus = remember { FocusRequester() }
    val detectedGenres = remember(genres) {
        genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }
    val close: () -> Unit = {
        val target = if (expanded == "genres") genreFocus else entryFocus
        expanded = null
        target.requestFocus()
    }
    BackHandler(expanded != null) { close() }
    Column(modifier.zIndex(2f).onFocusChanged { if (!it.hasFocus) expanded = null }.focusGroup()) {
        TabRow(
            selectedTabIndex = focusedControl,
            separator = { Spacer(Modifier.width(24.dp)) },
            indicator = { positions, hasFocus -> TabRowDefaults.UnderlinedIndicator(positions[focusedControl], hasFocus) },
            modifier = Modifier.width(320.dp).onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        runCatching { (if (expanded != null) optionFocus else contentFocus).requestFocus() }
                    }
                    true
                } else false
            }
        ) {
            Tab(selected = expanded == "sort", onFocus = { focusedControl = 0 },
                onClick = { expanded = if (expanded == "sort") null else "sort" },
                colors = TabDefaults.underlinedIndicatorTabColors(), modifier = Modifier.focusRequester(entryFocus)) {
                Text("Sort", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
            Tab(selected = expanded == "genres", onFocus = { focusedControl = 1 },
                onClick = { expanded = if (expanded == "genres") null else "genres" },
                colors = TabDefaults.underlinedIndicatorTabColors(), modifier = Modifier.focusRequester(genreFocus)) {
                Text(selectedGenre?.let { "Genre: $it" } ?: "Genres: All", maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        if (expanded != null) {
            Surface(modifier = Modifier.padding(top = 8.dp).width(320.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.025f))))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = SurfaceDefaults.colors(containerColor = Color(0xFF171B24).copy(alpha = 0.80f), contentColor = Color.White)) {
                LazyColumn(Modifier.heightIn(max = 260.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (expanded == "sort") {
                        items(listOf(SortType.Alphabetically to "Alphabetically", SortType.DateAdded to "Date Added", SortType.DateReleased to "Date Released")) { (value, label) ->
                            ListItem(selected = sortType == value,
                                modifier = if (value == SortType.Alphabetically) Modifier.focusRequester(optionFocus) else Modifier,
                                onClick = { onSortSelected(value); close() }, headlineContent = { Text(label) })
                        }
                    } else {
                        item {
                            ListItem(selected = selectedGenre == null, modifier = Modifier.focusRequester(optionFocus),
                                onClick = { onGenreSelected(null); close() }, headlineContent = { Text("All Genres") })
                        }
                        items(detectedGenres, key = { it }) { genre ->
                            ListItem(selected = selectedGenre == genre, onClick = { onGenreSelected(genre); close() }, headlineContent = { Text(genre) })
                        }
                        if (detectedGenres.isEmpty()) item { Text("No genres found in this library.") }
                    }
                }
            }
            LaunchedEffect(expanded) { withFrameNanos { }; optionFocus.requestFocus() }
        }
    }
}

/** Shared drawer filtering for home and movie/TV library screens. */
internal fun JellyfinLibrary.isVisibleInSideNavigation(showMusic: Boolean): Boolean {
    val excludedTypes = setOf("playlists", "playlist", "livetv", "livetvchannel", "livetvchannelgroup")
    if (CollectionType?.lowercase(java.util.Locale.ROOT) in excludedTypes ||
        Type?.lowercase(java.util.Locale.ROOT) in excludedTypes) return false
    if (Name.equals("Playlists", ignoreCase = true) ||
        Name.equals("Live TV", ignoreCase = true) ||
        Name.equals("Collections", ignoreCase = true)) return false
    return showMusic || !CollectionType.equals("music", ignoreCase = true)
}
