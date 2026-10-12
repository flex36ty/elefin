package com.flex.elefin.player.cinema

import com.flex.elefin.screens.PlayerControlButton
import com.flex.elefin.screens.PlayerSeekBar
import com.flex.elefin.screens.AspectModeButton
import androidx.compose.material.icons.filled.ClosedCaption
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.*
import com.flex.elefin.JellyfinAppTheme
import com.flex.elefin.subtitles.ServerSubtitleSearchDialog
import kotlinx.coroutines.delay

/** Independently implemented television player; shares only the MPV engine adapter. */
class CinemaPlayerActivity : ComponentActivity() {
    private lateinit var controller: CinemaController
    private var controls by mutableStateOf(true)
    private var seekOverlay by mutableStateOf(false)
    private var lastSeekKeyTime = 0L
    private var menu by mutableStateOf<String?>(null)
    private var interaction by mutableStateOf(0L)
    companion object {
        fun createIntent(context: Context, url: String, headers: String, title: String,
                         itemId: String, resumePositionMs: Long = 0L) =
            Intent(context, CinemaPlayerActivity::class.java).apply {
                putExtra("url", url); putExtra("headers", headers); putExtra("title", title)
                putExtra("item_id", itemId); putExtra("resume_ms", resumePositionMs)
            }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (savedInstanceState != null) intent.putExtra("resume_ms", savedInstanceState.getLong("position_ms"))
        controller = CinemaController(this, intent)
        setContent { JellyfinAppTheme { Player() } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("position_ms", (controller.position * 1000).toLong())
        super.onSaveInstanceState(outState)
    }
    override fun onPause() { if (::controller.isInitialized) controller.pause(); super.onPause() }
    override fun onDestroy() { if (::controller.isInitialized) controller.release(); super.onDestroy() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val horizontal = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        if (::controller.isInitialized && horizontal && !controls && menu == null && controller.error == null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0 || event.eventTime - lastSeekKeyTime >= 250L) {
                    lastSeekKeyTime = event.eventTime
                    controller.seek(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -15 else 15)
                    seekOverlay = true
                    interaction++
                }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && ::controller.isInitialized) {
            interaction++
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { controller.togglePause(); controls = true; return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { controller.pause(); controls = true; return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { if (controller.paused) controller.togglePause(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { controller.seek(-15); controls = true; return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { controller.seek(15); controls = true; return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (!controls && menu == null) {
                    controls = true
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    private fun retry() {
        val next = Intent(intent).putExtra("resume_ms", (controller.position * 1000).toLong())
        controller.release()
        finish()
        startActivity(next)
    }
    @Composable
    private fun Player() {
        val playFocus = remember { FocusRequester() }
        var timelineFocused by remember { mutableStateOf(false) }
        BackHandler {
            when { menu != null && menu != "Settings" -> menu = "Settings"; menu != null -> menu = null; controls -> controls = false; seekOverlay -> seekOverlay = false; else -> finish() }
        }
        LaunchedEffect(seekOverlay, interaction, controls, menu) {
            if (controls || menu != null) seekOverlay = false
            else if (seekOverlay) { delay(3_000); seekOverlay = false }
        }
        LaunchedEffect(controls, controller.paused, menu, interaction, controller.error) {
            if (controls && !controller.paused && menu == null && controller.error == null) {
                delay(5_000)
                controls = false
            }
        }
        LaunchedEffect(controls, menu, controller.error) {
            if (controls && menu == null && controller.error == null) {
                androidx.compose.runtime.withFrameNanos { }
                if (controls && menu == null && controller.error == null) {
                    runCatching { playFocus.requestFocus() }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { controller.createView() }, modifier = Modifier.fillMaxSize())
            if (controller.buffering) Text("Loading…", Modifier.align(Alignment.Center), color = Color.White)
            if (controls && menu == null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f))) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 24.dp, end = 24.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = .5f), contentColor = Color.White)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(controller.displayTitle, style = MaterialTheme.typography.titleLarge, color = Color.White,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (controller.episodeTitle.isNotEmpty()) {
                                Text(controller.episodeTitle, style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = .9f), modifier = Modifier.padding(top = 4.dp),
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 32.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cinemaTime(controller.position), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(cinemaTime(controller.duration), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        PlayerSeekBar(currentPosition = (controller.position * 1000).toLong(),
                            duration = (controller.duration * 1000).toLong(), onSeek = { controller.seekTo(it) })
                        if (controller.resolution.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(8.dp),
                                colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = .5f), contentColor = Color.White),
                                modifier = Modifier.padding(top = 8.dp)) {
                                Text(controller.resolution, color = Color.White.copy(alpha = .9f),
                                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            PlayerControlButton(Icons.Default.FastRewind, "Rewind 15s", { controller.seek(-15) })
                            Spacer(Modifier.width(32.dp))
                            PlayerControlButton(if (controller.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                if (controller.paused) "Play" else "Pause", { controller.togglePause() }, Modifier.focusRequester(playFocus))
                            Spacer(Modifier.width(32.dp))
                            PlayerControlButton(Icons.Default.FastForward, "Forward 15s", { controller.seek(15) })
                            Spacer(Modifier.width(32.dp))
                            AspectModeButton(controller.aspectMode, { controller.cycleAspect() })
                            Spacer(Modifier.width(32.dp))
                            PlayerControlButton(Icons.Default.ClosedCaption, "Subtitles", { controller.refreshTracks(); menu = "Subtitles" })
                        }
                    }
                }
            }
            if (seekOverlay && !controls && menu == null && controller.error == null) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                    .padding(horizontal = 48.dp, vertical = 32.dp)) {
                    controller.trickplayInfo?.let { info ->
                        controller.api?.let { api ->
                            TrickplayPreview(api, controller.previewItemId, controller.previewSourceId, info,
                                (controller.position * 1000).toLong(), Modifier.align(Alignment.CenterHorizontally))
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cinemaTime(controller.position), color = Color.White)
                        Text(cinemaTime(controller.duration), color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        val fraction = if (controller.duration > 0) (controller.position / controller.duration).coerceIn(0.0, 1.0).toFloat() else 0f
                        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        drawRoundRect(Color.White.copy(alpha = .3f), cornerRadius = radius)
                        if (fraction > 0) drawRoundRect(Color.White, size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height), cornerRadius = radius)
                    }
                }
            }
            controller.error?.let { message ->
                CinemaMenu("Playback", { finish() }) {
                    item { Text(message, Modifier.padding(12.dp)) }
                    item { Button(onClick = { retry() }) { Text("Retry") } }
                    item { Button(onClick = { finish() }) { Text("Exit") } }
                }
            }
        }
        when (menu) {
            "Settings" -> CinemaMenu("Player Settings", { menu = null }) {
                items(listOf("Audio", "Subtitles", "Speed")) { category ->
                    ListItem(selected = false, headlineContent = { Text(category) }, onClick = { menu = category })
                }
            }
            "Audio", "Subtitles" -> {
                val subtitles = menu == "Subtitles"
                val firstEntryFocus = remember(menu) { FocusRequester() }
                val visibleTracks = controller.tracks.filter { it.kind == if (subtitles) "sub" else "audio" }
                CinemaMenu(menu!!, { menu = "Settings" },
                    firstEntryFocus = firstEntryFocus.takeIf { subtitles || visibleTracks.isNotEmpty() }) {
                    if (subtitles) item { ListItem(modifier = Modifier.focusRequester(firstEntryFocus), selected = controller.subtitleId == -1,
                        headlineContent = { Text("Off") }, onClick = { controller.subtitlesOff(); menu = null }) }
                    items(visibleTracks) { track ->
                        ListItem(modifier = if (!subtitles && track == visibleTracks.firstOrNull()) Modifier.focusRequester(firstEntryFocus) else Modifier,
                            selected = track.id == if (subtitles) controller.subtitleId else controller.audioId,
                            headlineContent = { Text(track.label) }, onClick = { controller.select(track); menu = null })
                    }
                    if (subtitles) items(controller.serverSubtitleTracks) { stream ->
                        ListItem(selected = false, headlineContent = { Text(cinemaTrackLabel(stream, null, stream.Language, "Server subtitle")) },
                            onClick = { controller.selectServerSubtitle(stream); menu = null })
                    }
                    if (subtitles && controller.api != null && !intent.getStringExtra("item_id").isNullOrEmpty()) item {
                        ListItem(selected = false, headlineContent = { Text("Find subtitles on server") }, onClick = { menu = "Search" })
                    }
                }
            }
            "Speed" -> CinemaMenu("Playback speed", { menu = "Settings" }) {
                items(listOf(.5, .75, 1.0, 1.25, 1.5, 2.0)) { rate ->
                    ListItem(selected = controller.speed == rate, headlineContent = { Text("${rate}×") },
                        onClick = { controller.setRate(rate); menu = null })
                }
            }
            "Search" -> controller.api?.let { api -> ServerSubtitleSearchDialog(
                itemId = intent.getStringExtra("item_id").orEmpty(), api = api,
                onDownloaded = { controller.attachSubtitle(it) }, onDismiss = { menu = null }) }
        }
    }
}

@Composable
private fun CinemaMenu(title: String, dismiss: () -> Unit, firstEntryFocus: FocusRequester? = null, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val closeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = dismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .85f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .5f))) {
            Column(Modifier.fillMaxWidth(.4f).fillMaxHeight(.6f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                Button(onClick = dismiss, modifier = Modifier.focusRequester(closeFocus)) { Text("Close") }
            }
        }
        }
        LaunchedEffect(title, firstEntryFocus) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { (firstEntryFocus ?: closeFocus).requestFocus() }
                .onFailure { runCatching { closeFocus.requestFocus() } }
        }
    }
}

internal fun cinemaTime(seconds: Double): String {
    val total = if (seconds.isFinite()) seconds.toLong().coerceAtLeast(0) else 0
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, total / 60 % 60, total % 60)
    else "%d:%02d".format(java.util.Locale.ROOT, total / 60, total % 60)
}
