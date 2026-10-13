package com.flex.elefin.player.cinema

import com.flex.elefin.screens.PlayerControlButton
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.flex.elefin.screens.PlayerSeekBar
import com.flex.elefin.screens.AspectModeButton
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Audiotrack
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
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
    private val refreshRate by lazy { CinemaRefreshRate(this) }
    private var seekOverlay by mutableStateOf(false)
    private var lastSeekKeyTime = 0L
    private var menu by mutableStateOf<String?>(null)
    private var interaction by mutableStateOf(0L)
    private var nextCountdown by mutableStateOf<Int?>(null)
    private var autoplayCancelled by mutableStateOf(false)
    private var advancing = false
    private var creditsSkipped by mutableStateOf(false)
    private var resumed by mutableStateOf(false)
    override fun onResume() { super.onResume(); resumed = true }
    private fun startNextEpisode() {
        val next = controller.nextEpisode ?: return
        if (advancing || isFinishing) return
        advancing = true
        nextCountdown = null
        controller.release(markWatched = true)
        startActivity(com.flex.elefin.JellyfinVideoPlayerActivity.createIntent(this, next.Id, itemName = next.Name))
        finish()
    }
    private val canSkipIntro: Boolean
        get() {
            if (!::controller.isInitialized || controls || menu != null || controller.error != null || controller.completed || advancing) return false
            val intro = controller.introRange ?: return false
            val positionMs = (controller.position * 1000).toLong()
            return positionMs >= intro.startMs && positionMs < intro.endMs
        }
    private val canSkipCredits: Boolean
        get() {
            if (!::controller.isInitialized || controls || menu != null || controller.error != null || controller.completed || advancing || !controller.nextEpisodeResolved) return false
            val positionMs = (controller.position * 1000).toLong()
            val chapter = controller.creditsChapter
            return if (chapter != null) positionMs >= chapter.startMs && positionMs < chapter.endMs
            else controller.creditsStartMs?.let { it > 0 && positionMs >= it && positionMs < controller.duration * 1000 } == true
        }
    private fun skipCredits() {
        val chapter = controller.creditsChapter
        if (chapter != null && chapter.endMs < (controller.duration * 1000).toLong()) {
            creditsSkipped = true
            nextCountdown = null
            controller.seekTo(chapter.endMs)
            return
        }
        if (controller.nextEpisode != null) startNextEpisode() else returnToInfo(markWatched = true)
    }
    private fun returnToInfo(markWatched: Boolean = false) {
        if (advancing || isFinishing) return
        advancing = true
        val item = controller.playingItem
        controller.release(markWatched = markWatched)
        val detailsIntent = when {
            item?.Type == "Episode" && item.SeriesId != null ->
                com.flex.elefin.SeriesDetailsActivity.createIntent(this,
                    item.copy(Id = item.SeriesId, Name = item.SeriesName ?: item.Name, Type = "Series"),
                    episodeId = item.Id, autoFocusNextUp = true)
            item?.Type == "Movie" -> com.flex.elefin.MovieDetailsActivity.createIntent(this, item)
            else -> null
        }
        detailsIntent?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
        finish()
    }
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
    override fun onPause() { refreshRate.restore(); resumed = false; if (::controller.isInitialized) controller.pause(); super.onPause() }
    override fun onDestroy() { if (::controller.isInitialized) controller.release(); super.onDestroy() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (nextCountdown != null && !controls && menu == null &&
            event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (event.action == KeyEvent.ACTION_UP) startNextEpisode()
            return true
        }
        if (canSkipIntro && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (event.action == KeyEvent.ACTION_UP) controller.introRange?.let { controller.seekTo(it.endMs) }
            return true
        }
        if (canSkipCredits && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (event.action == KeyEvent.ACTION_UP) skipCredits()
            return true
        }
        if (nextCountdown != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) { autoplayCancelled = true; nextCountdown = null }
            return true
        }
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
        // Keep the painter alive while controls hide so reopening them uses the loaded logo.
        val logoRequest = remember(controller.logoUrl) {
            controller.logoUrl?.let { url ->
                ImageRequest.Builder(this@CinemaPlayerActivity).data(url)
                    .headers(controller.api!!.getImageRequestHeaders())
                    // Load before Image is composed; otherwise the fallback can wait on its size.
                    .size(960, 300).build()
            }
        }
        val logoPainter = rememberAsyncImagePainter(logoRequest)

        var timelineFocused by remember { mutableStateOf(false) }
        val autoplaySettings = remember { com.flex.elefin.jellyfin.AppSettings(this@CinemaPlayerActivity) }
        LaunchedEffect(controller) {
            var elapsed = 0L
            while (true) {
                delay(250)
                if (!resumed || advancing || controller.error != null) continue
                val next = controller.nextEpisode
                val canAdvance = autoplaySettings.autoplayNextEpisode && !autoplayCancelled && next != null
                if (controller.completed && controller.nextEpisodeResolved && !canAdvance) {
                    returnToInfo()
                    return@LaunchedEffect
                }
                val countdownSeconds = autoplaySettings.autoplayCountdownSeconds
                val nearEnd = cinemaAutoplayDue(controller.completed, controller.duration,
                    controller.position, if (creditsSkipped) null else controller.creditsStartMs, countdownSeconds)
                if (!canAdvance || !nearEnd) {
                    nextCountdown = null
                    elapsed = 0
                    continue
                }
                if (menu != null || (!controller.completed && (controller.paused || controller.buffering))) continue
                if (nextCountdown == null) { elapsed = 0; controls = false; seekOverlay = false }
                nextCountdown = (countdownSeconds - elapsed / 1000).toInt().coerceAtLeast(0)
                if (elapsed >= countdownSeconds * 1000L) {
                    startNextEpisode()
                    return@LaunchedEffect
                }
                elapsed += 250
            }
        }
        BackHandler {
            when { menu != null && menu != "Settings" -> menu = "Settings"; menu != null -> menu = null; controls -> controls = false; seekOverlay -> seekOverlay = false; else -> finish() }
        }
        LaunchedEffect(resumed) {
            if (resumed && com.flex.elefin.jellyfin.AppSettings(this@CinemaPlayerActivity).cinemaMatchRefreshRate) {
                while (true) {
                    refreshRate.match(controller.sourceFrameRate())
                    delay(1_000)
                }
            }
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
            if (controls && menu == null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f))) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 24.dp, end = 24.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = .5f), contentColor = Color.White)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (logoPainter.state is AsyncImagePainter.State.Success) {
                                Image(
                                    painter = logoPainter,
                                    contentDescription = controller.displayTitle,
                                    modifier = Modifier.width(280.dp).height(80.dp),
                                    alignment = Alignment.CenterStart,
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(controller.displayTitle, style = MaterialTheme.typography.titleLarge, color = Color.White,
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
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
                            PlayerControlButton(Icons.Default.Audiotrack, "Audio", { controller.refreshTracks(); menu = "Audio" })
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
                        Text(cinemaTime(controller.position), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Text(cinemaTime(controller.duration), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    // Match PlayerSeekBar's 24dp layout slot and centered 6dp track.
                    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                            val fraction = if (controller.duration > 0) (controller.position / controller.duration).coerceIn(0.0, 1.0).toFloat() else 0f
                            val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                            drawRoundRect(Color.White.copy(alpha = .3f), cornerRadius = radius)
                            if (fraction > 0) drawRoundRect(Color.White, size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height), cornerRadius = radius)
                        }
                    }
                    // Reserve the normal controls' footer so either bar has the same baseline.
                    if (controller.resolution.isNotEmpty()) {
                        Text(controller.resolution, color = Color.Transparent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                                .clearAndSetSemantics { })
                    }
                    Spacer(Modifier.height(24.dp + 48.dp))
                }
            }
            if (nextCountdown == null && (canSkipIntro || canSkipCredits)) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = if (nextCountdown == null) 48.dp else 250.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = SurfaceDefaults.colors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text(if (canSkipIntro) "OK  ·  Skip intro" else "OK  ·  Skip credits", color = Color.Black,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            nextCountdown?.takeIf { !controls && menu == null }?.let { seconds ->
                controller.nextEpisode?.let { next ->
                    com.flex.elefin.screens.NextUpOverlay(next, seconds, actionHint = "Press OK to play next now · Back to cancel") {
                        autoplayCancelled = true
                        nextCountdown = null
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
                    if (!subtitles && visibleTracks.isEmpty()) item {
                        Text("No audio tracks available", Modifier.padding(12.dp))
                    }
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
