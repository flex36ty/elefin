package com.flex.elefin.player.cinema

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import com.flex.elefin.jellyfin.*
import com.flex.elefin.player.SubtitleDownloader
import com.flex.elefin.subtitles.ServerSubtitleSelection
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVView
import kotlinx.coroutines.*
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class CinemaTrack(val id: Int, val kind: String, val label: String, val streamIndex: Int?)

/** Owns one engine session. All native access, including destruction, stays on the main thread. */
internal class CinemaController(private val context: Context, private val request: Intent) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var view: MPVView? = null
    private var dolby: CinemaDolbyPlayer? = null
    private var released = false
    private var copyDecoderAttempted = false
    private val decoderPrefs = context.getSharedPreferences("cinema_decoder_compatibility", Context.MODE_PRIVATE)
    private val videoCodec = request.getStringExtra("video_codec").orEmpty().lowercase(java.util.Locale.ROOT)
    private val decoderKey = "${android.os.Build.FINGERPRINT}|$videoCodec"
    private var copyRecoveryPosition: Double? = null

    private var fallbackAttempted = false
    private var fallbackPending = false
    private var loaded = false
    private var reporting = false
    private var reportJob: Job? = null
    private val reportLock = Mutex()
    private var sessionReported = false
    private val itemId = request.getStringExtra("item_id").orEmpty()
    val title = request.getStringExtra("title") ?: "Video"
    var displayTitle by mutableStateOf(title); private set
    var episodeTitle by mutableStateOf(""); private set
    var logoUrl by mutableStateOf<String?>(null); private set
    val api = JellyfinConfig(context).let { config ->
        if (config.isConfigured() && !request.getBooleanExtra("is_trailer", false))
            JellyfinApiService(config.serverUrl, config.accessToken, config.userId, config) else null
    }
    var position by mutableStateOf(request.getLongExtra("resume_ms", 0L) / 1000.0); private set
    var duration by mutableStateOf(0.0); private set
    var paused by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var speed by mutableStateOf(1.0); private set
    var aspectMode by mutableStateOf(com.flex.elefin.screens.AspectMode.FIT); private set
    var resolution by mutableStateOf(""); private set
    var playingItem: JellyfinItem? = null; private set
    var completed by mutableStateOf(false); private set
    var nextEpisode by mutableStateOf<JellyfinItem?>(null); private set
    var nextEpisodeResolved by mutableStateOf(false); private set
    var creditsStartMs by mutableStateOf<Long?>(null); private set
    private var introStartMs by mutableStateOf<Long?>(null)
    private var introEndMs by mutableStateOf<Long?>(null)
    val introRange get() = if (playingItem?.Type == "Episode")
        cinemaIntroRange(introStartMs, introEndMs, chapters, (duration * 1000).toLong()) else null
    private var chapters by mutableStateOf<List<ChapterInfo>>(emptyList())
    val creditsChapter get() = cinemaCreditsChapter(chapters, (duration * 1000).toLong())
    var error by mutableStateOf<String?>(null); private set
    var tracks by mutableStateOf<List<CinemaTrack>>(emptyList()); private set
    var audioId by mutableStateOf(-1); private set
    var subtitleId by mutableStateOf(-1); private set
    private var audioIndex = request.getIntExtra("audio_stream_index", -1)
    private var subtitleIndex = if (request.hasExtra("subtitle_stream_index")) request.getIntExtra("subtitle_stream_index", -1)
        else AppSettings(context).getSubtitlePreference(itemId) ?: -1
    private var source: MediaSource? = null
    var trickplayInfo by mutableStateOf<TrickplayInfo?>(null); private set
    val previewItemId get() = itemId
    val previewSourceId get() = source?.Id ?: itemId
    private var subtitleJob: Job? = null
    private val subtitleFileIndices = mutableMapOf<String, Int>()
    private val attachedSubtitleIndices = mutableMapOf<Int, Int>()
    var serverSubtitleTracks by mutableStateOf<List<MediaStream>>(emptyList()); private set
    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
            if (property == "track-list") scope.launch { nativeAction { refreshTracks() } }
        }
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: Boolean) = Unit
        override fun eventProperty(property: String, value: String) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
        override fun event(eventId: Int) {
            // Never call JNI inside MPV's observer lock or on its event thread.
            scope.launch {
                if (released) return@launch
                when (eventId) {
                    MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> try { onLoaded() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { buffering = false; error = "Playback settings could not be loaded. Retry or exit." }
                    MPVLib.MpvEvent.MPV_EVENT_END_FILE -> if (!released) {
                        buffering = false
                        if (loaded && MPVLib.getPropertyBoolean("eof-reached") == true) completePlayback()
                        else retryServerStream()
                    }
                }
            }
        }
    }

    private fun applyVideoEnhancements(directory: File) {
        val settings = AppSettings(context)
        // A fresh MPV instance uses the fast defaults when all three options are off.
        if (!settings.cinemaSharpenUpscale && !settings.cinemaDeband && !settings.cinemaDenoise) return
        fun option(name: String, value: String) {
            runCatching { MPVLib.setPropertyString(name, value) }
                .onFailure { android.util.Log.w("CinemaPlayer", "Enhancement option unavailable: $name", it) }
        }
        if (settings.cinemaSharpenUpscale) {
            option("scale", "spline36")
            option("cscale", "spline36")
            option("sharpen", "0.25")
        }
        if (settings.cinemaDeband) {
            option("deband-iterations", "1")
            option("deband-threshold", "32")
            option("deband-range", "16")
            option("deband-grain", "0")
            option("deband", "yes")
        }
        if (settings.cinemaDenoise) {
            runCatching {
                val shader = File(directory, "denoise.glsl")
                context.assets.open("cinema/denoise.glsl").use { input ->
                    shader.outputStream().use { input.copyTo(it) }
                }
                option("glsl-shaders", shader.absolutePath)
            }.onFailure { android.util.Log.w("CinemaPlayer", "Denoise shader unavailable", it) }
        }
        android.util.Log.i("CinemaPlayer", "Video enhancements requested: sharpen=${settings.cinemaSharpenUpscale}, deband=${settings.cinemaDeband}, denoise=${settings.cinemaDenoise}")
    }

    fun createView(): android.view.View {
        check(view == null && dolby == null)
        // Realtek AVC copy output can tint the padded bottom rows; use Media3's
        // native decoder surface rather than copying its YUV buffers through MPV.
        val realtekAvc = videoCodec == "h264" && runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos.any {
                !it.isEncoder && it.name.startsWith("OMX.realtek.", ignoreCase = true) &&
                    it.supportedTypes.any { type -> type.equals("video/avc", ignoreCase = true) }
            }
        }.getOrDefault(false)
        if (request.getBooleanExtra("native_dolby_vision", false) || realtekAvc) {
            val engine = CinemaDolbyPlayer(context, requireNotNull(request.getStringExtra("url")),
                request.getStringExtra("headers"), (position * 1000).toLong(),
                onReady = { scope.launch {
                    try { onLoaded() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { buffering = false; error = "Playback settings could not be loaded. Retry or exit." }
                } },
                onTracks = { scope.launch { if (!released) refreshTracks() } },
                onEnded = { completePlayback() },
                onError = { retryServerStream() })
            dolby = engine
            scope.launch {
                while (isActive) {
                    position = engine.player.currentPosition.coerceAtLeast(0) / 1000.0
                    duration = engine.player.duration.coerceAtLeast(0) / 1000.0
                    paused = !engine.player.playWhenReady
                    buffering = engine.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                    audioId = engine.selected("audio")
                    subtitleId = engine.selected("sub")
                    delay(300)
                }
            }
            return engine.view
        }
        return MPVView(context).also { engine ->
            try {
                val config = File(context.filesDir, "cinema-mpv").apply { mkdirs() }
                engine.setHttpHeaders(request.getStringExtra("headers"))
                engine.initialize(config.absolutePath, context.cacheDir.absolutePath)
                view = engine
                engine.addObserver(observer)
                MPVLib.setPropertyString("osd-level", "0")
                applyVideoEnhancements(config)
                if (decoderPrefs.getBoolean(decoderKey, false)) {
                    MPVLib.setPropertyString("hwdec", "mediacodec-copy")
                    copyDecoderAttempted = true
                }

                // TV speaker routes can accept an 8-channel AudioTrack yet never advance its clock.
                // Mix locally for built-in speakers; leave external surround routes to MPV.
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val outputs = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                val speakerOnly = outputs.any { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } &&
                    outputs.none { it.type != android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
                        it.type != android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE &&
                        it.type != android.media.AudioDeviceInfo.TYPE_TELEPHONY }
                if (speakerOnly) {
                    MPVLib.setPropertyString("audio-channels", "stereo")
                    android.util.Log.i("CinemaPlayer", "Built-in speaker output: downmixing audio to stereo")
                }

                val subtitleSettings = AppSettings(context)
                fun mpvColor(value: Int) = String.format(java.util.Locale.ROOT, "#%08X", value)
                MPVLib.setPropertyString("sub-ass-override", "force")
                MPVLib.setPropertyString("sub-color", mpvColor(subtitleSettings.exoSubtitleTextColor))
                val metrics = context.resources.displayMetrics
                val size = subtitleSettings.exoSubtitleTextSize * metrics.scaledDensity * 720.0 / metrics.heightPixels.coerceAtLeast(1)
                MPVLib.setPropertyDouble("sub-font-size", size)
                MPVLib.setPropertyString("sub-border-style", if (subtitleSettings.exoSubtitleBgTransparent) "outline-and-shadow" else "background-box")
                MPVLib.setPropertyString("sub-back-color", mpvColor(if (subtitleSettings.exoSubtitleBgTransparent) 0 else subtitleSettings.exoSubtitleBgColor))

                engine.playFile(requireNotNull(request.getStringExtra("url")), request.getStringExtra("audio_url"))
                scope.launch {
                    while (isActive) {
                        if (loaded && error == null) nativeAction {
                            position = engine.timePos ?: position
                            copyRecoveryPosition?.let { recoveryStart ->
                                if (!fallbackAttempted && position >= recoveryStart + 3 && videoCodec.isNotEmpty()) {
                                    decoderPrefs.edit().putBoolean(decoderKey, true).apply()
                                    copyRecoveryPosition = null
                                }
                            }
                            duration = engine.duration ?: duration
                            paused = engine.paused ?: paused
                            buffering = MPVLib.getPropertyBoolean("paused-for-cache") == true
                            audioId = engine.aid
                            subtitleId = engine.sid
                            if (engine.eofReached == true && error == null) {
                                paused = true
                                completePlayback()
                            }
                        }
                        delay(300)
                    }
                }
                scope.launch {
                    var unchangedSince = android.os.SystemClock.elapsedRealtime()
                    var lastPosition = position
                    while (isActive && !released) {
                        delay(1_000)
                        if (!loaded || completed || paused || error != null || kotlin.math.abs(position - lastPosition) > 0.2) {
                            unchangedSince = android.os.SystemClock.elapsedRealtime()
                            lastPosition = position
                            continue
                        }
                        if (android.os.SystemClock.elapsedRealtime() - unchangedSince >= 10_000) {
                            unchangedSince = android.os.SystemClock.elapsedRealtime()
                            if (!copyDecoderAttempted && engine.hwdecActive == "mediacodec") {
                                copyDecoderAttempted = true
                                copyRecoveryPosition = position
                                android.util.Log.w("CinemaPlayer", "Playback clock stalled; retrying with mediacodec-copy")
                                MPVLib.setPropertyString("hwdec", "mediacodec-copy")
                                engine.seekTo(position)
                            } else if (!fallbackAttempted) {
                                android.util.Log.w("CinemaPlayer", "Playback clock still stalled; requesting server fallback")
                                retryServerStream()
                            }
                        }
                    }
                }
                scope.launch { delay(30_000); if (!loaded && !fallbackPending) retryServerStream() }
            } catch (e: Exception) {
                error = "Could not start the player. Close other playback and retry."
                buffering = false
            }
        }
    }

    private fun retryServerStream() {
        if (released || fallbackPending) return
        val service = api
        if (fallbackAttempted || service == null || (view == null && dolby == null)) {
            buffering = false
            error = "Unable to play this video, including the server fallback."
            return
        }
        fallbackAttempted = true
        fallbackPending = true
        buffering = true
        error = null
        scope.launch {
            try {
                val url = withTimeout(20_000) { service.getPlaybackFallbackUrl(itemId, audioIndex) }
                    ?: throw IllegalStateException("No server fallback available")
                if (released) return@launch
                loaded = false
                android.util.Log.i("CinemaPlayer", "Retrying playback with negotiated server stream")
                if (dolby != null) dolby?.retryStream(url, (position * 1000).toLong())
                else MPVLib.command(arrayOf("loadfile", url, "replace"))
                fallbackPending = false
                delay(45_000)
                if (!released && !loaded) {
                    buffering = false
                    error = "The server could not start the fallback stream."
                }
            } catch (e: TimeoutCancellationException) {
                buffering = false
                error = "The server took too long to prepare a fallback stream."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                buffering = false
                error = "The server could not prepare a fallback stream."
                android.util.Log.w("CinemaPlayer", "Server fallback failed: ${e.javaClass.simpleName}")
            } finally { fallbackPending = false }
        }
    }

    private fun completePlayback() {
        if (released || completed) return
        completed = true
        buffering = false
        paused = true
    }

    private fun resolveNextEpisode(details: JellyfinItem?) {
        scope.launch {
            try {
                val service = api ?: return@launch
                val markers = withTimeoutOrNull(3_000) { service.getMediaSegments(itemId) }
                creditsStartMs = markers?.creditsStartMs
                introStartMs = markers?.introStartMs
                introEndMs = markers?.introEndMs
                val seriesId = details?.SeriesId
                val seasonNumber = details?.ParentIndexNumber
                val episodeNumber = details?.IndexNumber
                if (details?.Type != "Episode" || seriesId == null || seasonNumber == null || episodeNumber == null) return@launch
                withTimeoutOrNull(12_000) {
                    withContext(Dispatchers.IO) {
                        val seasons = service.getSeasons(seriesId).filter { it.IndexNumber != null }
                            .sortedBy { it.IndexNumber }
                        for (season in seasons.filter { it.IndexNumber!! >= seasonNumber }) {
                            val candidate = service.getEpisodes(seriesId, season.Id)
                                .filter { it.Id != itemId && it.IndexNumber != null &&
                                    (season.IndexNumber!! > seasonNumber || it.IndexNumber!! > episodeNumber) }
                                .minByOrNull { it.IndexNumber!! }
                            if (candidate != null) {
                                withContext(Dispatchers.Main) { nextEpisode = candidate }
                                break
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("CinemaPlayer", "Next episode lookup unavailable", e) }
            finally { nextEpisodeResolved = true }
        }
    }

    private fun applySdrHighlightEnhancement() {
        val settings = AppSettings(context)
        if (view == null || !settings.cinemaHdrLike || settings.cinemaHdrLikeStrength == 0) return
        runCatching {
            val transfer = MPVLib.getPropertyString("video-params/gamma")
            val primaries = MPVLib.getPropertyString("video-params/primaries")
            if (!cinemaSupportsHdrLike(transfer, primaries)) {
                android.util.Log.i("CinemaPlayer", "SDR enhancement skipped: transfer=$transfer, primaries=$primaries")
                return
            }
            val shader = File(context.filesDir, "cinema-mpv/hdr-like.glsl")
            shader.writeText(cinemaHdrLikeShader(settings.cinemaHdrLikeStrength))
            MPVLib.command(arrayOf("change-list", "glsl-shaders", "append", shader.absolutePath))
            android.util.Log.i("CinemaPlayer", "SDR highlight enhancement requested: ${settings.cinemaHdrLikeStrength}%")
        }.onFailure { android.util.Log.w("CinemaPlayer", "SDR enhancement unavailable", it) }
    }

    private suspend fun onLoaded() {
        if (loaded) return
        loaded = true
        applySdrHighlightEnhancement()
        startReporting()
        buffering = false
        view?.seekTo(position)
        view?.paused = paused
        if (api != null && itemId.isNotEmpty()) {
            val details = withTimeoutOrNull(8_000) { withContext(Dispatchers.IO) { api.getItemDetails(itemId) } }
            playingItem = details
            chapters = details?.Chapters.orEmpty()
            resolveNextEpisode(details)
            source = details?.MediaSources?.firstOrNull()
            trickplayInfo = details?.Trickplay?.entries?.firstOrNull {
                it.key.replace("-", "").equals(previewSourceId.replace("-", ""), ignoreCase = true)
            }?.value?.values?.filter { it.frame(0) != null }?.minByOrNull { it.Width }

            if (details?.Type == "Episode") {
                displayTitle = details.SeriesName?.takeIf { it.isNotBlank() } ?: details.Name
                episodeTitle = listOfNotNull(
                    details.IndexNumber?.let { episode -> details.ParentIndexNumber?.let { "S$it E$episode" } ?: "Episode $episode" },
                    details.Name.takeIf { it.isNotBlank() && it != displayTitle }
                ).joinToString(" · ")
            } else {
                displayTitle = details?.Name?.takeIf { it.isNotBlank() } ?: title
            }
            val logoTag = details?.ImageTags?.get("Logo")
            val logoId = when {
                details == null -> null
                details.Type == "Episode" -> details.SeriesId?.takeIf { it.isNotBlank() }
                    ?: details.Id.takeIf { logoTag != null }
                logoTag != null -> details.Id
                else -> null
            }
            logoUrl = logoId?.let {
                api.getImageUrl(it, "Logo", logoTag.takeIf { logoId == details?.Id },
                    maxWidth = 960, maxHeight = 300, quality = 90)
            }
            serverSubtitleTracks = source?.MediaStreams.orEmpty().filter { it.Type == "Subtitle" && it.IsExternal == true } +
                com.flex.elefin.subtitles.OpenSubtitlesApi.getDownloadedSubtitles(context, itemId).map {
                    MediaStream(Type = "Subtitle", IsExternal = true, Path = it.filePath,
                        Codec = File(it.filePath).extension, Language = it.language, DisplayTitle = it.release)
                }
        }
        if (api == null || itemId.isEmpty()) nextEpisodeResolved = true
        if (released) return
        source?.MediaStreams?.firstOrNull { it.Type == "Video" }?.let {
            resolution = "${it.Width ?: 0} x ${it.Height ?: 0}" + if (requiresNativeDolbyVision(it)) "  Dolby Vision" else ""
        }
        refreshTracks()
        if (audioIndex < 0) audioIndex = AppSettings(context).getAudioPreference(itemId)
            ?: com.flex.elefin.player.preferredAudioIndex(source?.MediaStreams.orEmpty()) ?: -1
        tracks.firstOrNull { it.kind == "audio" && it.streamIndex == audioIndex }?.let { select(it, rememberChoice = false) }
        val subtitleFile = request.getStringExtra("subtitle_file")
        when {
            subtitleFile != null -> attachFile(subtitleFile, subtitleIndex)
            subtitleIndex < 0 -> subtitlesOff()
            else -> {
                val track = tracks.firstOrNull { it.kind == "sub" && it.streamIndex == subtitleIndex }
                if (track != null) select(track)
                else source?.MediaStreams?.firstOrNull { it.Type == "Subtitle" && it.Index == subtitleIndex }?.let { stream ->
                    api?.let { service ->
                        val path = stream.Path?.let { File(it) }?.takeIf {
                it.isFile && it.canonicalPath.startsWith(File(context.filesDir, "downloaded_subtitles").canonicalPath + File.separator)
            }?.absolutePath ?: SubtitleDownloader.downloadSubtitle(context, service, itemId, source?.Id ?: itemId, stream)
                        if (path != null && !released) attachFile(path, subtitleIndex)
                    }
                }
            }
        }
    }

    fun refreshTracks() {
        if (released) return
        dolby?.let { tracks = it.tracks(source?.MediaStreams.orEmpty()); return }
        if (view == null) return
        tracks = (0 until (MPVLib.getPropertyInt("track-list/count") ?: 0)).mapNotNull { i ->
            val kind = MPVLib.getPropertyString("track-list/$i/type") ?: return@mapNotNull null
            if (kind != "audio" && kind != "sub") return@mapNotNull null
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: return@mapNotNull null
            if (kind == "sub") {
                val filename = MPVLib.getPropertyString("track-list/$i/external-filename")
                subtitleFileIndices[filename]?.let { attachedSubtitleIndices[id] = it }
            }
            val index = if (kind == "sub" && attachedSubtitleIndices.containsKey(id)) attachedSubtitleIndices[id]
                else cinemaServerStreamIndex(source?.MediaStreams.orEmpty(), MPVLib.getPropertyInt("track-list/$i/ff-index"))
            val label = cinemaTrackLabel(source?.MediaStreams?.firstOrNull { it.Index == index },
                MPVLib.getPropertyString("track-list/$i/title"), MPVLib.getPropertyString("track-list/$i/lang"), "Track $id")
            CinemaTrack(id, kind, label, index)
        }
    }
    fun select(track: CinemaTrack, rememberChoice: Boolean = true) {
        if (track.kind == "audio" && rememberChoice) AppSettings(context).setAudioPreference(itemId, track.streamIndex)
        if (track.kind == "sub") {
            subtitleJob?.cancel()
            if ((track.streamIndex ?: -1) >= 0) api?.let { com.flex.elefin.subtitles.clearLocalProviderSubtitle(context, it, itemId) }
            AppSettings(context).setSubtitlePreference(itemId, track.streamIndex)
            if (dolby == null) MPVLib.setPropertyBoolean("sub-visibility", true)
        }
        dolby?.let {
            it.select(track.id)
            if (track.kind == "audio") { audioId = track.id; audioIndex = track.streamIndex ?: -1 }
            else { subtitleId = track.id; subtitleIndex = track.streamIndex ?: -1 }
            return
        }
        if (track.kind == "audio") { view?.aid = track.id; audioId = track.id; audioIndex = track.streamIndex ?: -1 }
        else { view?.sid = track.id; subtitleId = track.id; subtitleIndex = track.streamIndex ?: -1 }
    }
    fun selectServerSubtitle(stream: MediaStream) {
        val service = api ?: return
        subtitleJob?.cancel()
        subtitleJob = scope.launch {
            val path = stream.Path?.let { File(it) }?.takeIf {
                it.isFile && it.canonicalPath.startsWith(File(context.filesDir, "downloaded_subtitles").canonicalPath + File.separator)
            }?.absolutePath ?: SubtitleDownloader.downloadSubtitle(context, service, itemId, source?.Id ?: itemId, stream)
            if (!released) {
                if (path != null) {
                    if (stream.Index == null) api?.let { com.flex.elefin.subtitles.selectLocalProviderSubtitle(context, it, itemId, path) }
                    else api?.let { com.flex.elefin.subtitles.clearLocalProviderSubtitle(context, it, itemId) }
                    attachFile(path, stream.Index ?: -1, stream); subtitleIndex = stream.Index ?: -1
                }
                else error = "The subtitle could not be loaded. Retry playback or exit."
            }
        }
    }
    fun subtitlesOff() { api?.let { com.flex.elefin.subtitles.clearLocalProviderSubtitle(context, it, itemId) }; subtitleJob?.cancel(); dolby?.subtitlesOff(); view?.sid = -1; subtitleId = -1; subtitleIndex = -1; AppSettings(context).setSubtitlePreference(itemId, -1) }
    fun togglePause() { paused = !paused; dolby?.player?.playWhenReady = !paused; view?.paused = paused; savePausePosition() }
    fun pause() { paused = true; dolby?.player?.pause(); view?.paused = true; savePausePosition() }
    fun seek(delta: Int) { dolby?.player?.let { it.seekTo((it.currentPosition + delta * 1000L).coerceAtLeast(0)) }; view?.seek(delta) }
    fun setRate(rate: Double) { speed = rate; dolby?.player?.setPlaybackSpeed(rate.toFloat()); view?.playbackSpeed = rate }
    fun seekTo(milliseconds: Long) {
        val target = if (duration > 0) milliseconds.coerceIn(0, (duration * 1000).toLong()) else milliseconds.coerceAtLeast(0)
        dolby?.player?.seekTo(target)
        view?.seekTo(target / 1000.0)
    }
    fun cycleAspect() {
        aspectMode = aspectMode.next()
        dolby?.setAspect(aspectMode)
        if (view != null) {
            val mode = aspectMode
            MPVLib.setPropertyDouble("panscan", if (mode == com.flex.elefin.screens.AspectMode.FILL) 1.0 else 0.0)
            MPVLib.setPropertyString("keepaspect", if (mode == com.flex.elefin.screens.AspectMode.STRETCH) "no" else "yes")
            MPVLib.setPropertyString("video-unscaled", if (mode == com.flex.elefin.screens.AspectMode.ORIGINAL) "yes" else "no")
            MPVLib.setPropertyString("video-aspect-override", when (mode) {
                com.flex.elefin.screens.AspectMode.LETTERBOX -> "16:9"
                com.flex.elefin.screens.AspectMode.CINEMA -> "2.39"
                else -> "-1"
            })
        }
    }
    private fun attachFile(path: String, serverIndex: Int, metadata: MediaStream? = null) {
        AppSettings(context).setSubtitlePreference(itemId, serverIndex)
        dolby?.let {
            it.attachSubtitle(path, serverIndex, metadata?.Codec ?: source?.MediaStreams?.firstOrNull { stream -> stream.Index == serverIndex }?.Codec, metadata)
            return
        }
        subtitleFileIndices[path] = serverIndex
        subtitleFileIndices["file://$path"] = serverIndex
        MPVLib.setPropertyBoolean("sub-visibility", true)
        MPVLib.command(arrayOf("sub-add", path, "select", metadata?.DisplayTitle ?: "Downloaded subtitle", metadata?.Language ?: "und"))
        android.util.Log.i("CinemaPlayer", "Subtitle attached: server index=$serverIndex")
        refreshTracks()
    }
    private inline fun nativeAction(action: () -> Unit) {
        if (released || view == null) return
        try { action() }
        catch (e: Exception) {
            buffering = false
            error = "The player stopped responding. Retry or exit."
        }
    }
    suspend fun attachSubtitle(selection: ServerSubtitleSelection) {
        val service = api ?: error("No Jellyfin session")
        val path = selection.localPath ?: SubtitleDownloader.downloadSubtitle(context, service, itemId, selection.sourceId, selection.stream)
            ?: error("Subtitle saved on the server but could not be loaded. Try again.")
        check(!released) { "Playback has closed." }
        attachFile(path, selection.stream.Index ?: -1, selection.stream)
        subtitleIndex = selection.stream.Index ?: -1
    }
    private fun startReporting() {
        if (reporting || released || api == null || itemId.isEmpty()) return
        reporting = true
        reportJob = scope.launch {
            while (isActive) {
                reportProgressNow()
                delay(5_000)
            }
        }
    }

    private suspend fun reportProgressNow() {
        val service = api ?: return
        reportLock.withLock {
            if (released || !reporting) return
            withTimeoutOrNull(5_000) {
                if (!sessionReported) sessionReported = service.reportPlaybackStart(itemId, ticks(), audioIndex, subtitleIndex)
                if (sessionReported) service.reportPlaybackProgress(itemId, ticks(), paused,
                    playbackRate = speed, audioStreamIndex = audioIndex, subtitleStreamIndex = subtitleIndex)
            }
        }
    }

    private fun savePausePosition() {
        if (!loaded || released) return
        position = dolby?.player?.currentPosition?.let { it.coerceAtLeast(0) / 1000.0 } ?: view?.timePos ?: position
        scope.launch { reportProgressNow() }
    }

    private fun ticks() = (position.coerceAtLeast(0.0) * 10_000_000).toLong()
    fun release(markWatched: Boolean = false) {
        if (released) return
        released = true
        try {
            dolby?.let { engine ->
                position = engine.player.currentPosition.coerceAtLeast(0) / 1000.0
                engine.release()
            }
            view?.let { engine ->
                try { position = engine.timePos ?: position }
                finally {
                    try { engine.removeObserver(observer) }
                    finally { engine.destroy() }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("CinemaPlayer", "Engine release failed", e)
        } finally {
            view = null
            dolby = null
            reportJob?.cancel()
            job.cancel()
        }
        if (completed && duration > 0) position = duration
        // A bounded final report owns its own job so Activity destruction cannot cancel it.
        if (reporting) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reportLock.withLock {
                    withTimeoutOrNull(10_000) {
                        repeat(3) {
                            if (!sessionReported) sessionReported = api?.reportPlaybackStart(itemId, ticks(), audioIndex, subtitleIndex) == true
                            if (api?.reportPlaybackStopped(itemId, ticks(), audioIndex, subtitleIndex) == true) {
                                if (completed || markWatched) api?.markAsWatched(itemId)
                                return@withTimeoutOrNull
                            }
                            delay(500)
                        }
                    }
                }
            }
            finally { coroutineContext.cancel() }
        }
    }
}
