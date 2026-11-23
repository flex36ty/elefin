package com.flex.elefin.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.flex.elefin.jellyfin.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.flex.elefin.jellyfin.AppSettings

internal data class TvDrawerEntry(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val libraryType: String? = null,
    val onClick: () -> Unit
)

@Composable
internal fun rememberTvDrawerEnabled(settings: AppSettings): Boolean {
    var enabled by remember(settings) { mutableStateOf(settings.modalNavigationDrawerEnabled) }
    DisposableEffect(settings) {
        val unsubscribe = settings.observeModalNavigationDrawer { enabled = it }
        onDispose { unsubscribe() }
    }
    return enabled
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TvNavigationDrawer(
    enabled: Boolean,
    entries: List<TvDrawerEntry>,
    settingsEntry: TvDrawerEntry,
    recommendationReturn: RecommendationDrawerReturn? = null,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val state = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current
    val account = LibraryNewContent.account(context)
    val libraryEntries = entries.filter { it.libraryType == "movies" || it.libraryType == "tvshows" }
    var newLibraries by remember(account) { mutableStateOf(emptySet<String>()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val selectedLibraries = libraryEntries.filter { it.selected }.map { it.id }
    LaunchedEffect(account, selectedLibraries) {
        if (account != null) selectedLibraries.forEach {
            LibraryNewContent.opened(context, account, it)
            newLibraries = newLibraries - it
        }
    }
    LaunchedEffect(account, libraryEntries.map { it.id to it.libraryType }, selectedLibraries, lifecycle) {
        if (account == null) return@LaunchedEffect
        LibraryNewContent.baseline(context, account)
        val config = JellyfinConfig(context)
        val api = JellyfinApiService(config.serverUrl, config.accessToken, config.userId, config)
        try {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    for (entry in libraryEntries) {
                        if (entry.id in selectedLibraries) continue
                        try {
                            val added = libraryAddedMillis(api.latestLibraryAddition(entry.id, entry.libraryType == "tvshows"))
                            if (LibraryNewContent.account(context) == account) {
                                newLibraries = if (LibraryNewContent.hasNew(context, account, entry.id, added))
                                    newLibraries + entry.id else newLibraries - entry.id
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* Keep the previous indicator when offline. */ }
                    }
                    delay(60_000)
                }
            }
        } finally { api.close() }
    }
    val settings = remember(context) { AppSettings(context) }
    val focusHighlight = remember { DrawerFocusHighlight() }
    val contentFocus = remember { FocusRequester() }
    var contentReceivedFocus by remember { mutableStateOf(false) }
    val selectedFocus = remember { FocusRequester() }
    val hasSelectedEntry = entries.any { it.selected && it.enabled }
    val close: () -> Unit = {
        state.setValue(DrawerValue.Closed)
        if (recommendationReturn != null) {
            recommendationReturn.requestFocus()
        } else {
            contentFocus.requestFocus()
        }
    }
    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (state.currentValue == DrawerValue.Open &&
                event.nativeKeyEvent.keyCode in listOf(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) close()
                true
            } else false
        },
        drawerState = state,
        scrimBrush = Brush.horizontalGradient(
            0f to Color.Black,
            0.22f to Color.Black.copy(alpha = 0.96f),
            0.50f to Color.Black.copy(alpha = 0.66f),
            1f to Color.Black.copy(alpha = 0.12f)
        ),
        drawerContent = { drawerValue ->
            val expanded = drawerValue == DrawerValue.Open
            if (!contentReceivedFocus) {
                // Do not offer rail targets to Android's initial focus search.
                Spacer(Modifier.width(60.dp).fillMaxHeight())
            } else Column(
                Modifier.fillMaxHeight()
                    .drawerFocusHighlight(focusHighlight, expanded, !settings.disableUIAnimations && !settings.lowPowerMode)
                    .onFocusChanged { focus ->
                        // Synchronize even when a rail item receives initial launch focus.
                        state.setValue(if (focus.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                    }
                    .focusProperties { enter = { if (hasSelectedEntry) selectedFocus else FocusRequester.Default } }
                    .focusGroup()
                    .padding(horizontal = 8.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DrawerServerHeader(expanded = expanded, modifier = Modifier.drawerFocusTarget(focusHighlight, "server_header", 12.dp), sharedFocusHighlight = expanded)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.forEach { entry ->
                        key(entry.id) {
                            CompactDrawerItem(entry, expanded, Modifier.drawerFocusTarget(focusHighlight, entry.id).then(if (entry.selected && entry.enabled) Modifier.focusRequester(selectedFocus) else Modifier), entry.id in newLibraries) {
                                if (account != null && entry.libraryType in listOf("movies", "tvshows")) {
                                    LibraryNewContent.opened(context, account, entry.id)
                                    newLibraries = newLibraries - entry.id
                                }
                                close(); entry.onClick()
                            }
                        }
                    }
                }
                CompactDrawerItem(settingsEntry, expanded, Modifier.drawerFocusTarget(focusHighlight, settingsEntry.id)) { close(); settingsEntry.onClick() }
            }
        }
    ) {
        Box(Modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) contentReceivedFocus = true }.focusRequester(contentFocus).then(if (recommendationReturn == null) Modifier.focusRestorer() else Modifier).focusGroup()) {
            CompositionLocalProvider(LocalRecommendationDrawerReturn provides recommendationReturn) {
                content()
            }
        }
    }
    // Registered after screen content so an open drawer takes priority over screen Back handlers.
    BackHandler(state.currentValue == DrawerValue.Open) { close() }
}

/** Small glyphs inside full-size focus targets keep remote navigation comfortable. */
@Composable
private fun CompactDrawerItem(entry: TvDrawerEntry, expanded: Boolean, modifier: Modifier = Modifier, isNew: Boolean = false, onClick: () -> Unit) {
    Surface(
        selected = entry.selected,
        enabled = entry.enabled,
        onClick = onClick,
        modifier = modifier.width(if (expanded) 176.dp else 44.dp).height(44.dp),
        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFCCC9D0),
            selectedContainerColor = Color.White.copy(alpha = 0.12f),
            selectedContentColor = Color.White,
            focusedContainerColor = if (expanded) Color.Transparent else Color(0xFFE7E2EA),
            focusedContentColor = Color(0xFF202025),
            focusedSelectedContainerColor = if (expanded) Color.Transparent else Color(0xFFE7E2EA),
            focusedSelectedContentColor = Color(0xFF202025)
        )
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp)) {
                Icon(entry.icon, contentDescription = if (isNew) "${entry.title}, new content" else entry.title, modifier = Modifier.size(18.dp))
                if (isNew && !expanded) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-4).dp).size(6.dp).background(Color(0xFFF0B5A6), CircleShape))
            }
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(entry.title, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isNew) {
                    Spacer(Modifier.width(6.dp))
                    Text("NEW", color = Color(0xFF512E29), fontSize = 9.sp,
                        modifier = Modifier.background(Color(0xFFF0B5A6), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}
