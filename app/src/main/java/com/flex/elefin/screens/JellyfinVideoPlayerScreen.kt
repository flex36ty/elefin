package com.flex.elefin.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.common.ParserException
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.ui.PlayerView
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.MediaStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.material.icons.Icons
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.media3.common.TrackSelectionOverride

@UnstableApi
@Composable
fun JellyfinVideoPlayerScreen(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    onBack: () -> Unit = {},
    resumePositionMs: Long = 0L,
    subtitleStreamIndex: Int? = null,
    audioStreamIndex: Int? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    
    // Load stored audio preference if not provided
    val storedAudioPreference = remember(item.Id) {
        if (audioStreamIndex == null) {
            val pref = settings.getAudioPreference(item.Id)
            Log.d("JellyfinPlayer", "Loaded stored audio preference for ${item.Id}: $pref")
            pref
        } else {
            Log.d("JellyfinPlayer", "Using provided audioStreamIndex: $audioStreamIndex")
            audioStreamIndex
        }
    }
    // Create player with enhanced codec support and LoadControl configured based on settings
    // Enable extension renderers and decoder fallback for better audio codec support (TrueHD, DTS, EAC3, etc.)
    val renderersFactory = DefaultRenderersFactory(context).apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        setEnableDecoderFallback(true)
    }
    
    // Configure track selector with better track selection
    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setForceHighestSupportedBitrate(true)
        )
    }
    
    // Configure audio attributes for media playback
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.CONTENT_TYPE_MOVIE)
        .build()
    
    val player = remember {
        if (settings.minimalBuffer4K) {
            // Configure LoadControl with minimal buffering
            // Note: minBufferMs must be >= bufferForPlaybackMs
            // Minimal buffer: 1 second to start playback, 1.5 seconds minimum buffer, 2 seconds max for playback
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    1500,  // minBufferMs - minimum buffered duration before starting playback (1.5 seconds, must be >= bufferForPlaybackMs)
                    2000,  // maxBufferMs - maximum buffered duration during playback (2 seconds)
                    1000,  // bufferForPlaybackMs - buffered duration required to start playback (1 second)
                    1500   // bufferForPlaybackAfterRebufferMs - buffered duration required after rebuffering (1.5 seconds)
                )
                .build()
            
            ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .setLoadControl(loadControl)
                .build()
                .also { 
                    Log.d("JellyfinPlayer", "Created player with minimal buffering and enhanced codec support (for 4K content): start after 1s, min 1.5s, max 2s")
                    Log.d("JellyfinPlayer", "Extension renderer mode: PREFER, Decoder fallback: enabled")
                }
        } else {
            ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .build()
                .also { 
                    Log.d("JellyfinPlayer", "Created player with default buffering and enhanced codec support")
                    Log.d("JellyfinPlayer", "Extension renderer mode: PREFER, Decoder fallback: enabled")
                }
        }
    }
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    var mediaUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var playerInitialized by remember { mutableStateOf(false) }
    var progressReportingJob by remember { mutableStateOf<Job?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var hasSeekedToResume by remember { mutableStateOf(false) } // Track if we've already seeked to resume position
    var hasRetriedWithoutRange by remember { mutableStateOf(false) } // Track if we've retried without range requests for 416 errors
    var hasRetriedWithHls by remember { mutableStateOf(false) } // Track if we've retried with HLS for parser errors
    var currentMediaSource by remember { mutableStateOf<MediaSource?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var currentSubtitleIndex by remember { mutableStateOf<Int?>(subtitleStreamIndex) }
    var lastSelectedSubtitleIndex by remember { mutableStateOf<Int?>(subtitleStreamIndex) } // Track last selected subtitle from controller
    var currentAudioIndex by remember { mutableStateOf<Int?>(storedAudioPreference) }
    var lastSelectedAudioIndex by remember { mutableStateOf<Int?>(storedAudioPreference) } // Track last selected audio from controller
    var is4KContent by remember { mutableStateOf(false) } // Track if current content is 4K
    // Store subtitle streams list for composite key registration in onTracksChanged
    var jellyfinSubtitleStreams by remember { mutableStateOf<List<MediaStream>>(emptyList()) }
    var shouldFocusPlayButton by remember { mutableStateOf(false) } // Track when to focus play button after controller is shown
    var nextEpisodeId by remember { mutableStateOf<String?>(null) } // Next episode ID for autoplay
    var nextEpisodeDetails by remember { mutableStateOf<JellyfinItem?>(null) } // Next episode details
    var showNextUpOverlay by remember { mutableStateOf(false) } // Show next up overlay
    var autoplayCountdown by remember { mutableStateOf(settings.autoplayCountdownSeconds) } // Countdown timer for autoplay
    var autoplayCancelled by remember { mutableStateOf(false) } // Track if user cancelled autoplay
    var hasFocusedPlayButtonOnStart by remember { mutableStateOf(false) } // Track if we've focused play button on initial start

    // Fetch item details and prepare video URL
    LaunchedEffect(item.Id, apiService, subtitleStreamIndex) {
        withContext(Dispatchers.IO) {
            try {
                // Get full item details with MediaSources
                val details = apiService.getItemDetails(item.Id)
                if (details != null) {
                    itemDetails = details
                    // Get the first media source
                    val mediaSource = details.MediaSources?.firstOrNull()
                    val mediaSourceId = mediaSource?.Id

                    // Detect if video is HDR/high-quality (HEVC codec and high resolution typically indicates HDR)
                    val videoStream = mediaSource?.MediaStreams?.firstOrNull { it.Type == "Video" }
                    val isHEVC = videoStream?.Codec?.contains("hevc", ignoreCase = true) == true ||
                                 videoStream?.Codec?.contains("h265", ignoreCase = true) == true
                    val width = videoStream?.Width ?: 0
                    val height = videoStream?.Height ?: 0
                    val is4KOrHigher = width >= 3840 || height >= 2160
                    val isHDROrHighQuality = isHEVC && is4KOrHigher
                    
                    // Store 4K status for buffering control
                    is4KContent = is4KOrHigher
                    
                    // Detect if audio codec is unsupported by Android (requires transcoding)
                    val audioStream = mediaSource?.MediaStreams?.firstOrNull { it.Type == "Audio" }
                    val audioCodec = audioStream?.Codec?.lowercase() ?: ""
                    // TrueHD, DTS-HD, and other lossless/high-end audio codecs aren't supported by Android AudioTrack
                    val isUnsupportedAudio = audioCodec.contains("truehd", ignoreCase = true) ||
                                            audioCodec.contains("dts-hd", ignoreCase = true) ||
                                            audioCodec.contains("dtshd", ignoreCase = true) ||
                                            audioCodec.contains("dtsx", ignoreCase = true) ||
                                            audioCodec.contains("atmos", ignoreCase = true) && audioCodec.contains("truehd", ignoreCase = true)
                    
                    // Check if AAC to AC3 transcoding is enabled
                    val shouldTranscodeAacToAc3 = settings.transcodeAacToAc3 && audioCodec.contains("aac", ignoreCase = true)
                    val needsAudioTranscoding = isUnsupportedAudio || shouldTranscodeAacToAc3
                    val targetAudioCodec = if (shouldTranscodeAacToAc3) "ac3" else null
                    
                    if (isHDROrHighQuality) {
                        Log.d("JellyfinPlayer", "HDR/high-quality video detected (${videoStream?.Codec}, ${width}x${height}) - requesting full quality")
                    }
                    if (isUnsupportedAudio) {
                        Log.d("JellyfinPlayer", "Unsupported audio codec detected (${audioStream?.Codec}) - will transcode audio while preserving video quality")
                    }
                    if (shouldTranscodeAacToAc3) {
                        Log.d("JellyfinPlayer", "AAC to AC3 transcoding enabled - transcoding audio from AAC to AC3 for universal device compatibility")
                    }
                    
                    // USER SELECTED SUBTITLE OVERRIDE
                    // Check if user explicitly selected a subtitle (from series/movie page or Jellyfin UI)
                    val selectedSubtitleStream = if (subtitleStreamIndex != null) {
                        details?.MediaSources?.firstOrNull()?.MediaStreams
                            ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                    } else {
                        // Check if Jellyfin marked a subtitle as default/selected
                        details?.MediaSources?.firstOrNull()?.MediaStreams
                            ?.find { it.Type == "Subtitle" && (it.IsDefault == true) }
                    }
                    
                    // HLS (master.m3u8) does NOT support external subtitles via SubtitleConfiguration!
                    // This is a known Media3 limitation - ExoPlayer ignores SubtitleConfiguration for HLS streams
                    // Solution: If user selected an EXTERNAL subtitle, force direct streaming (no HLS)
                    val userSelectedExternalSubtitle = selectedSubtitleStream?.IsExternal == true
                    val forceDirectStreamForSubtitles = userSelectedExternalSubtitle && needsAudioTranscoding
                    
                    if (userSelectedExternalSubtitle) {
                        Log.d("JellyfinPlayer", "📌 USER SELECTED EXTERNAL SUBTITLE")
                        Log.d("JellyfinPlayer", "   Selected: ${selectedSubtitleStream?.DisplayTitle ?: selectedSubtitleStream?.Language}")
                        Log.d("JellyfinPlayer", "   Index: ${selectedSubtitleStream?.Index}, IsExternal: ${selectedSubtitleStream?.IsExternal}")
                        
                        if (needsAudioTranscoding) {
                            Log.w("JellyfinPlayer", "⚠️ SUBTITLE PRIORITY MODE ACTIVATED")
                            Log.w("JellyfinPlayer", "   External subtitle selected + audio transcoding needed")
                            Log.w("JellyfinPlayer", "   Disabling HLS transcoding to use direct streaming")
                            Log.w("JellyfinPlayer", "   WHY: HLS playlists do NOT include external subtitles")
                            Log.w("JellyfinPlayer", "   WHY: ExoPlayer ignores SubtitleConfiguration for HLS (Media3 limitation)")
                            Log.w("JellyfinPlayer", "   RESULT: Selected subtitle will work, audio codec may not be optimal")
                            Log.w("JellyfinPlayer", "   ALTERNATIVE: Use MPV player for both subtitle + audio transcoding support")
                        } else {
                            Log.d("JellyfinPlayer", "   ✅ Direct streaming - external subtitle will load successfully")
                        }
                    }

                    // Generate video playback URL
                    // If user selected external subtitle, disable HLS transcoding to force direct streaming
                    val effectiveNeedsTranscoding = if (forceDirectStreamForSubtitles) false else needsAudioTranscoding
                    val effectiveAudioCodec = if (forceDirectStreamForSubtitles) null else targetAudioCodec
                    
                    val videoUrl = apiService.getVideoPlaybackUrl(
                        itemId = item.Id,
                        mediaSourceId = mediaSourceId,
                        subtitleStreamIndex = null,
                        preserveQuality = isHDROrHighQuality,
                        transcodeAudio = effectiveNeedsTranscoding, // Disabled if subtitles exist
                        audioCodec = effectiveAudioCodec
                    )
                    mediaUrl = videoUrl
                    Log.d("JellyfinPlayer", "Video URL: $videoUrl")
                    
                    // Check for next episode if this is an episode
                    // Use the simpler StartIndex approach: /Shows/{seriesId}/Episodes?StartIndex={currentIndex + 1}&Limit=1
                    if (details.Type == "Episode") {
                        Log.d("JellyfinPlayer", "Episode detected. NextEpisodeId from API: ${details.NextEpisodeId}")
                        Log.d("JellyfinPlayer", "Episode info: SeriesId=${details.SeriesId}, Season=${details.ParentIndexNumber}, Episode=${details.IndexNumber}")
                        
                        // Try to get next episode ID from API response first
                        var foundNextEpisode: JellyfinItem? = null
                        
                        if (details.NextEpisodeId != null) {
                            // API provided NextEpisodeId, fetch the episode
                            val nextDetails = apiService.getItemDetails(details.NextEpisodeId)
                            if (nextDetails != null) {
                                foundNextEpisode = nextDetails
                                Log.d("JellyfinPlayer", "✅ Found next episode via NextEpisodeId: ${nextDetails.Name}")
                            }
                        }
                        
                        // If NextEpisodeId is not available, use the simpler StartIndex approach
                        if (foundNextEpisode == null && details.SeriesId != null && details.IndexNumber != null) {
                            Log.d("JellyfinPlayer", "NextEpisodeId not available, using StartIndex approach...")
                            try {
                                // Use the simpler approach: /Shows/{seriesId}/Episodes?StartIndex={currentIndex}&Limit=1
                                // This gets the episode at StartIndex (which should be the next one)
                                foundNextEpisode = apiService.getNextEpisode(details.SeriesId, details.IndexNumber)
                                
                                if (foundNextEpisode == null) {
                                    // Try next season's first episode
                                    Log.d("JellyfinPlayer", "No next episode in current season, checking next season...")
                                    val seasons = apiService.getSeasons(details.SeriesId)
                                    val currentSeason = seasons.firstOrNull { it.IndexNumber == details.ParentIndexNumber }
                                    val nextSeason = seasons.firstOrNull { 
                                        currentSeason != null && it.IndexNumber == currentSeason.IndexNumber!! + 1 
                                    }
                                    
                                    if (nextSeason != null) {
                                        // Get first episode of next season
                                        foundNextEpisode = apiService.getNextEpisode(details.SeriesId, 1)?.let { episode ->
                                            // Check if this episode is in the next season
                                            if (episode.ParentIndexNumber == nextSeason.IndexNumber) {
                                                episode
                                            } else {
                                                // Get episodes from next season directly
                                                val nextSeasonEpisodes = apiService.getEpisodes(details.SeriesId, nextSeason.Id)
                                                nextSeasonEpisodes.firstOrNull()
                                            }
                                        } ?: run {
                                            val nextSeasonEpisodes = apiService.getEpisodes(details.SeriesId, nextSeason.Id)
                                            nextSeasonEpisodes.firstOrNull()
                                        }
                                        
                                        if (foundNextEpisode != null) {
                                            Log.d("JellyfinPlayer", "✅ Found next episode in next season: ${foundNextEpisode.Name}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("JellyfinPlayer", "Error finding next episode", e)
                                e.printStackTrace()
                            }
                        }
                        
                        if (foundNextEpisode != null) {
                            nextEpisodeId = foundNextEpisode.Id
                            nextEpisodeDetails = foundNextEpisode
                            Log.d("JellyfinPlayer", "✅✅✅ Next episode resolved: ${foundNextEpisode.Name}, ID: ${foundNextEpisode.Id}")
                            Log.d("JellyfinPlayer", "✅ Next episode IndexNumber: ${foundNextEpisode.IndexNumber}, Season: ${foundNextEpisode.ParentIndexNumber}")
                        } else {
                            Log.d("JellyfinPlayer", "No next episode found (this might be the last episode)")
                        }
                    }
                    
                    isLoading = false
                } else {
                    Log.e("JellyfinPlayer", "Failed to fetch item details")
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("JellyfinPlayer", "Error preparing video", e)
                isLoading = false
            }
        }
    }

    // Initialize player when media URL is ready
    LaunchedEffect(mediaUrl) {
        if (mediaUrl != null && !playerInitialized) {
            withContext(Dispatchers.Main) {
                try {
                    
                    // Get authentication headers
                    val headers = apiService.getVideoRequestHeaders()

                    // Create HTTP data source factory with headers
                    // For 416 errors, we'll retry with range requests disabled
                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Jellyfin Android TV")
                        .setAllowCrossProtocolRedirects(true)
                    
                    // Set headers using setDefaultRequestProperties
                    val headersMap = headers.toMutableMap()
                    httpDataSourceFactory.setDefaultRequestProperties(headersMap)
                    

                    // Create data source factory
                    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

                    // Store mediaUrl in local variable for smart cast
                    val currentMediaUrl = mediaUrl ?: return@withContext
                    
                    // Detect if URL is HLS (ends with .m3u8 or contains master.m3u8)
                    val isHlsUrl = currentMediaUrl.contains(".m3u8", ignoreCase = true)

                    // Load ALL external subtitles into MediaItem (Jellyfin AndroidTV approach)
                    // This allows ExoPlayer to show the subtitle button and let users switch between them
                    val mediaItem = if (itemDetails != null) {
                        try {
                            val mediaSourceIdForSubtitle = itemDetails?.MediaSources?.firstOrNull()?.Id ?: item.Id
                            
                            // Get ALL subtitle streams from Jellyfin
                            val allSubtitleStreams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                ?.filter { it.Type == "Subtitle" && it.Index != null }
                                ?: emptyList()
                            
                            Log.d("JellyfinPlayer", "Found ${allSubtitleStreams.size} subtitle stream(s) from Jellyfin")
                            
                            // Store subtitle streams for composite key registration in onTracksChanged
                            jellyfinSubtitleStreams = allSubtitleStreams
                            
                            // Reset SubtitleMapper for new playback session
                            com.flex.elefin.player.SubtitleMapper.reset()
                            
                            // Create SubtitleConfiguration for each subtitle using SubtitleMapper
                            // Uses COMPOSITE KEY approach (production-safe, used by Plex/Emby/Jellyfin TV)
                            val subtitleConfigurations = allSubtitleStreams.mapIndexed { positionIndex, stream ->
                                try {
                                    val subtitleIndex = stream.Index ?: return@mapIndexed null
                                    val subtitleUrl = apiService.getSubtitleUrl(item.Id, mediaSourceIdForSubtitle, subtitleIndex)
                                    
                                    Log.d("JellyfinPlayer", "Adding subtitle ${stream.Index}: ${stream.DisplayTitle ?: stream.Language} (${stream.Codec}) - IsExternal=${stream.IsExternal}")
                                    
                                    // Use SubtitleMapper to create configuration with position tracking
                                    // This prevents "Turkish → Spanish" type mismatches using composite keys
                                    com.flex.elefin.player.SubtitleMapper.buildSubtitleConfiguration(
                                        stream = stream,
                                        subtitleUrl = subtitleUrl,
                                        positionIndex = positionIndex
                                    )
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Failed to create subtitle config for index ${stream.Index}: ${e.message}")
                                    null
                                }
                            }.filterNotNull()
                            
                            Log.d("JellyfinPlayer", "Successfully created ${subtitleConfigurations.size} subtitle configuration(s)")
                            subtitleConfigurations.forEachIndexed { index, config ->
                                Log.d("JellyfinPlayer", "  [$index] ${config.uri}")
                                Log.d("JellyfinPlayer", "       Lang: ${config.language}, MIME: ${config.mimeType}, Label: ${config.label}")
                            }
                            
                            // Create MediaItem with ALL subtitle configurations
                            if (subtitleConfigurations.isNotEmpty()) {
                                MediaItem.Builder()
                                    .setUri(Uri.parse(currentMediaUrl))
                                    .setSubtitleConfigurations(subtitleConfigurations)
                                    .build().also {
                                        Log.d("JellyfinPlayer", "✅ MediaItem created with ${subtitleConfigurations.size} subtitle configuration(s)")
                                    }
                            } else {
                                Log.d("JellyfinPlayer", "No valid subtitle configurations - creating MediaItem without subtitles")
                                MediaItem.fromUri(Uri.parse(currentMediaUrl))
                            }
                        } catch (e: Exception) {
                            Log.e("JellyfinPlayer", "❌ Error creating MediaItem with subtitles: ${e.message}", e)
                            Log.e("JellyfinPlayer", "   Playing video without subtitles")
                            MediaItem.fromUri(Uri.parse(currentMediaUrl))
                        }
                    } else {
                        Log.d("JellyfinPlayer", "No item details - creating MediaItem without subtitles")
                        MediaItem.fromUri(Uri.parse(currentMediaUrl))
                    }
                    
                    // Create media source from MediaItem - use HLS for .m3u8 URLs, Progressive for others
                    val mediaSource: MediaSource = if (isHlsUrl) {
                        Log.d("JellyfinPlayer", "Using HLS media source for progressive playback")
                        HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                    } else {
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                    }

                    // Set media source
                    player.setMediaSource(mediaSource)

                    // Store media source for potential retry
                    currentMediaSource = mediaSource

                    // Handle player lifecycle
                    player.addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("JellyfinPlayer", "Player error: ${error.message}", error)
                            Log.e("JellyfinPlayer", "Error type: ${error.errorCode}, Cause: ${error.cause?.javaClass?.simpleName}")
                            
                            // Check for subtitle-specific errors
                            if (error.cause is ParserException || error.message?.contains("subtitle", ignoreCase = true) == true) {
                                Log.e("JellyfinPlayer", "❌ SUBTITLE LOAD ERROR: This might be why external subtitles aren't appearing!")
                                Log.e("JellyfinPlayer", "   Error details: ${error.cause?.message ?: "Unknown"}")
                            }
                            
                            // Check if it's an HTTP 416 error (Range Not Satisfiable)
                            if (error.cause is HttpDataSource.InvalidResponseCodeException) {
                                val httpError = error.cause as HttpDataSource.InvalidResponseCodeException
                                if (httpError.responseCode == 416 && !hasRetriedWithoutRange && mediaUrl != null) {
                                    Log.w("JellyfinPlayer", "HTTP 416 error detected. Retrying without range requests...")
                                    scope.launch(Dispatchers.Main) {
                                        try {
                                            // Stop current playback
                                            player.stop()
                                            player.clearMediaItems()
                                            
                                            // Create a DataSource wrapper factory that removes range requests to avoid 416 errors
                                            val noRangeDataSourceFactory = object : DataSource.Factory {
                                                private val baseHttpFactory = DefaultHttpDataSource.Factory()
                                                    .setUserAgent("Jellyfin Android TV")
                                                    .setAllowCrossProtocolRedirects(true)
                                                    .setDefaultRequestProperties(headers.toMutableMap())
                                                
                                                private val baseFactory = DefaultDataSource.Factory(context, baseHttpFactory)
                                                
                                                override fun createDataSource(): DataSource {
                                                    val baseDataSource = baseFactory.createDataSource()
                                                    
                                                    // Return a wrapper that modifies DataSpecs to remove range information
                                                    return object : DataSource {
                                                        override fun open(dataSpec: DataSpec): Long {
                                                            // Remove range information to avoid 416 errors
                                                            // If DataSpec has position or length set, remove them to request entire file
                                                            val modifiedDataSpec = if (dataSpec.position != 0L || dataSpec.length > 0) {
                                                                // Create a new DataSpec without range request (request entire file)
                                                                DataSpec.Builder()
                                                                    .setUri(dataSpec.uri)
                                                                    .setHttpMethod(dataSpec.httpMethod)
                                                                    .setHttpRequestHeaders(dataSpec.httpRequestHeaders)
                                                                    .setKey(dataSpec.key)
                                                                    .setFlags(dataSpec.flags)
                                                                    .setPosition(0L) // Start from beginning
                                                                    .setLength(C.LENGTH_UNSET.toLong()) // Request entire file
                                                                    .build()
                                                            } else {
                                                                dataSpec
                                                            }
                                                            return baseDataSource.open(modifiedDataSpec)
                                                        }
                                                        
                                                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                                            return baseDataSource.read(buffer, offset, length)
                                                        }
                                                        
                                                        override fun getUri(): android.net.Uri? {
                                                            return baseDataSource.uri
                                                        }
                                                        
                                                        override fun close() {
                                                            baseDataSource.close()
                                                        }
                                                        
                                                        override fun addTransferListener(transferListener: TransferListener) {
                                                            baseDataSource.addTransferListener(transferListener)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            // Use the custom DataSource factory that removes range requests
                                            val retryDataSourceFactory = noRangeDataSourceFactory
                                            
                                            // Recreate MediaItem
                                            val retryMediaItem = if (subtitleStreamIndex != null && itemDetails != null) {
                                                try {
                                                    val mediaSourceIdForSubtitle = itemDetails?.MediaSources?.firstOrNull()?.Id ?: item.Id
                                                    val subtitleUrl = apiService.getSubtitleUrl(item.Id, mediaSourceIdForSubtitle, subtitleStreamIndex!!)
                                                    val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                                        ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                                                    val subtitleLanguage = subtitleStream?.Language
                                                    val subtitleMimeType = MimeTypes.TEXT_VTT
                                                    
                                                    MediaItem.Builder()
                                                        .setUri(Uri.parse(mediaUrl))
                                                        .setSubtitleConfigurations(
                                                            listOf(
                                                                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                                                                    .setMimeType(subtitleMimeType)
                                                                    .setLanguage(subtitleLanguage)
                                                                    .build()
                                                            )
                                                        )
                                                        .build()
                                                } catch (e: Exception) {
                                                    MediaItem.fromUri(Uri.parse(mediaUrl))
                                                }
                                            } else {
                                                MediaItem.fromUri(Uri.parse(mediaUrl))
                                            }
                                            
                                            // Create new media source with the DataSource factory
                                            val retryMediaSource = ProgressiveMediaSource.Factory(retryDataSourceFactory as DataSource.Factory)
                                                .createMediaSource(retryMediaItem)
                                            
                                            // Set new media source and prepare
                                            player.setMediaSource(retryMediaSource)
                                            player.prepare()
                                            player.playWhenReady = true
                                            
                                            // Mark that we've retried
                                            hasRetriedWithoutRange = true
                                            hasSeekedToResume = false // Reset resume seek
                                            playerInitialized = true // Mark as initialized after retry
                                            
                                            Log.d("JellyfinPlayer", "Retried playback without range requests")
                                        } catch (e: Exception) {
                                            Log.e("JellyfinPlayer", "Error retrying playback without range requests", e)
                                        }
                                    }
                                    return // Don't log as fatal error, we're handling it
                                }
                            }
                            
                            // Check if it's a parser error (malformed file) - fallback to MP4 transcoding
                            if (error.cause is ParserException && !hasRetriedWithHls && mediaUrl != null && itemDetails != null) {
                                Log.w("JellyfinPlayer", "Parser error detected (malformed file). Falling back to MP4 transcoding...")
                                scope.launch(Dispatchers.Main) {
                                    try {
                                        // Stop current playback
                                        player.stop()
                                        player.clearMediaItems()
                                        
                                        // Get media source ID
                                        val mediaSource = itemDetails?.MediaSources?.firstOrNull()
                                        val mediaSourceId = mediaSource?.Id ?: item.Id
                                        
                                        // Generate MP4 transcoding URL (server will transcode to MP4)
                                        val base = if (apiService.serverBaseUrl.endsWith("/")) apiService.serverBaseUrl else "${apiService.serverBaseUrl}/"
                                        val mp4Url = "${base}Videos/${item.Id}/stream.mp4?VideoCodec=h264&AudioCodec=aac&mediaSourceId=$mediaSourceId&api_key=${apiService.apiKey}"
                                        
                                        // Create media source with transcoded MP4
                                        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                                            .setUserAgent("Jellyfin Android TV")
                                            .setAllowCrossProtocolRedirects(true)
                                            .setDefaultRequestProperties(apiService.getVideoRequestHeaders().toMutableMap())
                                        
                                        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
                                        
                                        // Create MediaItem
                                        val transcodedMediaItem = if (subtitleStreamIndex != null && itemDetails != null) {
                                            try {
                                                val subtitleUrl = apiService.getSubtitleUrl(item.Id, mediaSourceId, subtitleStreamIndex!!)
                                                val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                                    ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                                                val subtitleLanguage = subtitleStream?.Language
                                                val subtitleMimeType = MimeTypes.TEXT_VTT
                                                
                                                MediaItem.Builder()
                                                    .setUri(Uri.parse(mp4Url))
                                                    .setSubtitleConfigurations(
                                                        listOf(
                                                            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                                                                .setMimeType(subtitleMimeType)
                                                                .setLanguage(subtitleLanguage)
                                                                .build()
                                                        )
                                                    )
                                                    .build()
                                            } catch (e: Exception) {
                                                MediaItem.fromUri(Uri.parse(mp4Url))
                                            }
                                        } else {
                                            MediaItem.fromUri(Uri.parse(mp4Url))
                                        }
                                        
                                        val transcodedMediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                            .createMediaSource(transcodedMediaItem)
                                        
                                        // Set new media source and prepare
                                        player.setMediaSource(transcodedMediaSource)
                                        player.prepare()
                                        player.playWhenReady = true
                                        
                                        // Mark that we've retried
                                        hasRetriedWithHls = true
                                        hasSeekedToResume = false // Reset resume seek
                                        playerInitialized = true
                                        
                                        // Update mediaUrl for reference
                                        mediaUrl = mp4Url
                                        
                                        Log.d("JellyfinPlayer", "Retried playback with MP4 transcoding")
                                    } catch (e: Exception) {
                                        Log.e("JellyfinPlayer", "Error falling back to MP4 transcoding", e)
                                    }
                                }
                                return // Don't log as fatal error, we're handling it
                            }
                            
                            // For other errors, log and let the player handle it normally
                            Log.e("JellyfinPlayer", "Unhandled player error", error)
                        }

                        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                            isPlaying = isPlayingNow
                        }

                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            // Log available tracks for debugging
                            Log.d("JellyfinPlayer", "Tracks changed: ${tracks.groups.size} track groups")
                            tracks.groups.forEach { group ->
                                Log.d("JellyfinPlayer", "Track group: type=${group.type}, supported=${group.isSupported}, trackCount=${group.mediaTrackGroup.length}, selected=${group.isSelected}")
                            }
                            
                            // When tracks are available, select subtitle track if specified
                            // Handle subtitle selection or disabling
                            val textTrackGroups = tracks.groups.filter { group ->
                                group.type == androidx.media3.common.C.TRACK_TYPE_TEXT &&
                                group.isSupported
                            }
                            
                            Log.d("JellyfinPlayer", "Found ${textTrackGroups.size} supported text track groups")
                            Log.d("JellyfinPlayer", "Jellyfin subtitle streams available for matching: ${jellyfinSubtitleStreams.size}")
                            
                            // ⭐ STEP 1: REGISTER ALL EXOPLAYER TRACKS WITH COMPOSITE KEYS (Production-Safe!)
                            // This must happen BEFORE selection logic so composite keys are available
                            Log.d("JellyfinPlayer", "⭐ STARTING TRACK REGISTRATION PHASE")
                            textTrackGroups.forEachIndexed { groupIndex, group ->
                                val format = group.mediaTrackGroup.getFormat(0)
                                val trackIndex = 0 // First track in group
                                
                                Log.d("JellyfinPlayer", "  Registering ExoPlayer subtitle track group $groupIndex:")
                                Log.d("JellyfinPlayer", "    Language: '${format.language}', Label: '${format.label}'")
                                Log.d("JellyfinPlayer", "    MIME: ${format.sampleMimeType}, ID: ${format.id}")
                                
                                // Match this ExoPlayer track to a Jellyfin subtitle by language + flags
                                // ⚠️ DO NOT match by MIME - ExoPlayer transforms it to x-media3-cues!
                                
                                // Extract flags from ExoPlayer format
                                val isForced = format.label?.contains("forced", ignoreCase = true) == true
                                val isCC = format.label?.contains("cc", ignoreCase = true) == true || 
                                          format.label?.contains("sdh", ignoreCase = true) == true ||
                                          format.label?.contains("hearing impaired", ignoreCase = true) == true
                                val isExternal = format.label?.contains("external", ignoreCase = true) == true
                                
                                Log.d("JellyfinPlayer", "    Detected flags: forced=$isForced, cc=$isCC, external=$isExternal")
                                
                                val matchingStream = jellyfinSubtitleStreams.firstOrNull { stream ->
                                    // Match by language (handle ISO 639-1 vs ISO 639-2 codes)
                                    // ExoPlayer uses 2-letter (es, en, fr), Jellyfin may use 3-letter (spa, eng, fra)
                                    val langMatch = stream.Language == format.language || 
                                                   stream.Language?.take(2) == format.language?.take(2) ||
                                                   normalizeLanguageCode(stream.Language) == normalizeLanguageCode(format.language)
                                    
                                    // Match by flags (must be exact)
                                    val forcedMatch = (stream.IsForced == true) == isForced
                                    val ccMatch = (stream.IsHearingImpaired == true) == isCC
                                    val externalMatch = (stream.IsExternal == true) == isExternal
                                    
                                    val matches = langMatch && forcedMatch && ccMatch && externalMatch
                                    
                                    if (langMatch) {
                                        Log.d("JellyfinPlayer", "      Testing JF index=${stream.Index} (${stream.Language}): lang=$langMatch, forced=$forcedMatch, cc=$ccMatch, ext=$externalMatch → $matches")
                                    }
                                    
                                    matches
                                }
                                
                                if (matchingStream?.Index != null) {
                                    // Register track with composite key
                                    com.flex.elefin.player.SubtitleMapper.registerExoPlayerTrack(
                                        format = format,
                                        groupIndex = groupIndex,
                                        trackIndex = trackIndex,
                                        jellyfinIndex = matchingStream.Index,
                                        metadata = matchingStream
                                    )
                                    Log.d("JellyfinPlayer", "    ✅ Registered: Group=$groupIndex → JF index=${matchingStream.Index}")
                                } else {
                                    Log.w("JellyfinPlayer", "    ⚠️ Could NOT match to Jellyfin subtitle (CEA-608/internal?)")
                                }
                            }
                            Log.d("JellyfinPlayer", "⭐ TRACK REGISTRATION COMPLETE")
                            
                            // ⭐ STEP 2: CHECK IF USER SELECTED A SUBTITLE
                            // Find the selected track and its group index using composite key resolution
                            val selectedGroupIndex = textTrackGroups.indexOfFirst { it.isSelected }
                            
                            if (selectedGroupIndex >= 0) {
                                val selectedTextTrackGroup = textTrackGroups[selectedGroupIndex]
                                val selectedFormat = selectedTextTrackGroup.mediaTrackGroup.getFormat(0)
                                val trackIndex = 0 // First track in group
                                
                                // ⭐ RESOLVE USING COMPOSITE KEY (100% RELIABLE!)
                                // Uses stable ExoPlayer attributes: groupIndex + trackIndex + MIME + language + flags
                                val (jellyfinIndex, metadata) = com.flex.elefin.player.SubtitleMapper.resolveJellyfinIndexFromFormat(
                                    format = selectedFormat,
                                    groupIndex = selectedGroupIndex,
                                    trackIndex = trackIndex
                                )
                                
                                Log.d("JellyfinPlayer", "Subtitle selected via ExoPlayer controller:")
                                Log.d("JellyfinPlayer", "  Group=$selectedGroupIndex, Track=$trackIndex")
                                Log.d("JellyfinPlayer", "  Format.id = '${selectedFormat.id}' (ExoPlayer internal)")
                                Log.d("JellyfinPlayer", "  Language = ${selectedFormat.language}, MIME = ${selectedFormat.sampleMimeType}")
                                
                                if (jellyfinIndex != null) {
                                    Log.d("JellyfinPlayer", "🔥 Composite key resolved: Jellyfin index=$jellyfinIndex")
                                    Log.d("JellyfinPlayer", "   Metadata: ${metadata?.DisplayTitle ?: metadata?.Language}, IsExternal=${metadata?.IsExternal}")
                                    
                                    if (jellyfinIndex != lastSelectedSubtitleIndex) {
                                        lastSelectedSubtitleIndex = jellyfinIndex
                                        currentSubtitleIndex = jellyfinIndex
                                        settings.setSubtitlePreference(item.Id, jellyfinIndex)
                                        Log.d("JellyfinPlayer", "💾 Saved subtitle preference: $jellyfinIndex (COMPOSITE KEY - 100% RELIABLE)")
                                    }
                                } else {
                                    // Could not resolve - ExoPlayer internal track (CEA-608, etc.)
                                    Log.w("JellyfinPlayer", "⚠️ Composite key resolution failed")
                                    Log.w("JellyfinPlayer", "   This is likely: CEA-608, embedded metadata, or ExoPlayer auto-generated track")
                                    Log.w("JellyfinPlayer", "   Not saving preference (doesn't map to Jellyfin subtitle)")
                                }
                            } else if (lastSelectedSubtitleIndex != null) {
                                // No subtitle selected (user deselected)
                                Log.d("JellyfinPlayer", "Subtitle deselected via ExoPlayer controller")
                                lastSelectedSubtitleIndex = null
                                currentSubtitleIndex = null
                                settings.setSubtitlePreference(item.Id, null)
                            }
                            
                            // Ensure subtitle button is visible in controller when tracks are available
                            playerViewRef.value?.let { view ->
                                view.post {
                                    // Explicitly show subtitle button when tracks are available
                                    view.setShowSubtitleButton(textTrackGroups.isNotEmpty())
                                    
                                    val controller = view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                    controller?.let { controlView ->
                                        // The subtitle button should automatically appear when text tracks are available
                                        // Force a refresh of the controller to ensure button visibility
                                        controlView.invalidate()
                                        
                                        // Also ensure the subtitle button view itself is visible
                                        val subtitleButton = controlView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
                                        subtitleButton?.let { button ->
                                            button.visibility = if (textTrackGroups.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                                        }
                                        
                                        Log.d("JellyfinPlayer", "Controller invalidated to refresh subtitle button visibility. Text tracks: ${textTrackGroups.size}, Subtitle button visible: ${subtitleButton?.visibility == android.view.View.VISIBLE}")
                                    }
                                }
                            }
                            
                            // Only apply subtitleStreamIndex (from series/movie page) if it's different from current selection
                            // This prevents clearing subtitles when ExoPlayer's controller selects one
                            if (subtitleStreamIndex != null && itemDetails != null && textTrackGroups.isNotEmpty() && subtitleStreamIndex != currentSubtitleIndex) {
                                // User selected a subtitle track from series/movie page - select it
                                try {
                                    Log.d("JellyfinPlayer", "Found ${textTrackGroups.size} supported subtitle track group(s)")
                                    
                                    // Get the subtitle stream info from Jellyfin to match by language
                                    val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                        ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                                    val subtitleLanguage = subtitleStream?.Language
                                    
                                    // Get all Jellyfin subtitle streams sorted by Index to create a mapping
                                    val allJellyfinSubtitleStreams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                        ?.filter { it.Type == "Subtitle" }
                                        ?.sortedBy { it.Index ?: 0 } ?: emptyList()
                                    
                                    Log.d("JellyfinPlayer", "Jellyfin subtitle streams: ${allJellyfinSubtitleStreams.map { "Index=${it.Index}, Language=${it.Language}" }}")
                                    Log.d("JellyfinPlayer", "ExoPlayer track groups: ${textTrackGroups.mapIndexed { idx, group -> "Index=$idx, Language=${group.mediaTrackGroup.getFormat(0).language}, MimeType=${group.mediaTrackGroup.getFormat(0).sampleMimeType}" }}")
                                    
                                    // Find the track group that matches the subtitle we loaded via SubtitleConfiguration
                                    // The subtitle we loaded should be in the track groups, and we can match it by:
                                    // 1. Language (exact or partial match, including ISO 639-1 vs ISO 639-2 variations)
                                    // 2. MIME type (VTT) if we loaded it
                                    // 3. Position in sorted Jellyfin list (if we loaded it via SubtitleConfiguration, it should be at a predictable position)
                                    
                                    // Language code mapping for common variations (ISO 639-1 to ISO 639-2)
                                    val languageVariations = if (subtitleLanguage != null) {
                                        val lang = subtitleLanguage.lowercase()
                                        when {
                                            lang == "tur" || lang == "tr" -> listOf("tur", "tr", "turkish")
                                            lang == "vie" || lang == "vi" -> listOf("vie", "vi", "vietnamese")
                                            lang == "eng" || lang == "en" -> listOf("eng", "en", "english")
                                            lang == "spa" || lang == "es" -> listOf("spa", "es", "spanish")
                                            lang == "fra" || lang == "fr" -> listOf("fra", "fr", "french")
                                            lang == "deu" || lang == "de" -> listOf("deu", "de", "german")
                                            lang == "jpn" || lang == "ja" -> listOf("jpn", "ja", "japanese")
                                            lang == "kor" || lang == "ko" -> listOf("kor", "ko", "korean")
                                            lang == "chi" || lang == "zh" -> listOf("chi", "zh", "chinese")
                                            else -> listOf(lang, lang.take(2), lang.take(3))
                                        }
                                    } else {
                                        emptyList()
                                    }
                                    
                                    // Try to match by language first, then by position in sorted list
                                    val groupToSelect = if (subtitleLanguage != null) {
                                        // Find track group with matching language
                                        textTrackGroups.firstOrNull { group ->
                                            val format = group.mediaTrackGroup.getFormat(0)
                                            // Match language code (e.g., "eng" matches "en" or "eng")
                                            format.language?.let { trackLang ->
                                                trackLang.equals(subtitleLanguage, ignoreCase = true) ||
                                                trackLang.startsWith(subtitleLanguage.take(2), ignoreCase = true) ||
                                                subtitleLanguage.startsWith(trackLang.take(2), ignoreCase = true)
                                            } ?: false
                                        } ?: run {
                                            // If no language match, try to match by position in the sorted list
                                            // Find the position of this subtitle in the sorted Jellyfin subtitle list
                                            val jellyfinPosition = allJellyfinSubtitleStreams.indexOfFirst { it.Index == subtitleStreamIndex }
                                            if (jellyfinPosition >= 0 && jellyfinPosition < textTrackGroups.size) {
                                                Log.d("JellyfinPlayer", "No language match found. Using position-based matching: Jellyfin position=$jellyfinPosition, ExoPlayer groups=${textTrackGroups.size}")
                                                textTrackGroups[jellyfinPosition]
                                            } else {
                                                // Last resort: log and use first available
                                                Log.w("JellyfinPlayer", "No language match and position out of range. Available track languages: ${textTrackGroups.map { it.mediaTrackGroup.getFormat(0).language }}")
                                                Log.w("JellyfinPlayer", "Looking for language: $subtitleLanguage, Jellyfin index: $subtitleStreamIndex")
                                                textTrackGroups.firstOrNull()
                                            }
                                        }
                                    } else {
                                        // No language info, try to match by position in sorted list
                                        val jellyfinPosition = allJellyfinSubtitleStreams.indexOfFirst { it.Index == subtitleStreamIndex }
                                        if (jellyfinPosition >= 0 && jellyfinPosition < textTrackGroups.size) {
                                            Log.d("JellyfinPlayer", "No language info. Using position-based matching: Jellyfin position=$jellyfinPosition")
                                            textTrackGroups[jellyfinPosition]
                                        } else {
                                            // Last resort: use first available
                                            Log.w("JellyfinPlayer", "No language info and position out of range. Using first available track.")
                                            textTrackGroups.firstOrNull()
                                        }
                                    }
                                    
                                    if (groupToSelect != null) {
                                    val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
                                        groupToSelect.mediaTrackGroup,
                                        0 // Select first track in the group
                                    )
                                    
                                    val updatedParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .addOverride(trackSelectionOverride)
                                        .build()
                                    
                                    player.trackSelectionParameters = updatedParameters
                                    
                                    val selectedFormat = groupToSelect.mediaTrackGroup.getFormat(0)
                                    currentSubtitleIndex = subtitleStreamIndex
                                    lastSelectedSubtitleIndex = subtitleStreamIndex
                                    
                                    Log.d("JellyfinPlayer", "✅ Selected subtitle track:")
                                    Log.d("JellyfinPlayer", "   ExoPlayer: lang=${selectedFormat.language}, id=${selectedFormat.id}")
                                    Log.d("JellyfinPlayer", "   Jellyfin: index=$subtitleStreamIndex")
                                    Log.d("JellyfinPlayer", "   Composite key will be registered in onTracksChanged")
                                } else {
                                    Log.w("JellyfinPlayer", "Could not find matching ExoPlayer track group for Jellyfin subtitle index $subtitleStreamIndex")
                                }
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error selecting subtitle track: ${e.message}", e)
                                }
                            } else if (subtitleStreamIndex == null && currentSubtitleIndex == null && textTrackGroups.isNotEmpty() && textTrackGroups.none { it.isSelected }) {
                                // User explicitly selected "None" from series/movie page AND no subtitle is currently selected
                                // Only clear if no subtitle is selected via ExoPlayer's controller either
                                try {
                                    // Clear any existing subtitle overrides to ensure no subtitles are selected
                                    val updatedParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverrides() // Clear any subtitle overrides
                                        .build()
                                    
                                    player.trackSelectionParameters = updatedParameters
                                    Log.d("JellyfinPlayer", "✅ Cleared subtitle track selection (None selected)")
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error clearing subtitle track selection: ${e.message}", e)
                                }
                            }
                            
                            // Handle audio track selection
                            // Get all audio track groups (both supported and unsupported for mapping)
                            val allAudioTrackGroups = tracks.groups.filter { group ->
                                group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO
                            }
                            val audioTrackGroups = allAudioTrackGroups.filter { it.isSupported }
                            
                            Log.d("JellyfinPlayer", "Found ${allAudioTrackGroups.size} total audio track groups (${audioTrackGroups.size} supported)")
                            
                            // Log all audio tracks for debugging (both supported and unsupported)
                            allAudioTrackGroups.forEachIndexed { index, group ->
                                val format = group.mediaTrackGroup.getFormat(0)
                                Log.d("JellyfinPlayer", "Audio track group $index: language=${format.language}, codec=${format.codecs}, supported=${group.isSupported}, selected=${group.isSelected}")
                            }
                            
                            // Log current audio preference
                            val audioIndexToApply = storedAudioPreference ?: audioStreamIndex
                            Log.d("JellyfinPlayer", "Audio preference to apply: $audioIndexToApply (stored: $storedAudioPreference, provided: $audioStreamIndex, current: $currentAudioIndex)")
                            
                            // Get Jellyfin audio streams for mapping
                            val jellyfinAudioStreams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                ?.filter { it.Type == "Audio" }
                                ?.sortedBy { it.Index ?: 0 } ?: emptyList()
                            
                            Log.d("JellyfinPlayer", "Jellyfin audio streams: ${jellyfinAudioStreams.map { "Index=${it.Index}, Language=${it.Language}, Codec=${it.Codec}" }}")
                            
                            // Check if user selected an audio track via ExoPlayer's controller
                            val selectedAudioTrackGroup = audioTrackGroups.firstOrNull { it.isSelected }
                            if (selectedAudioTrackGroup != null && itemDetails != null) {
                                // User selected an audio track via ExoPlayer's controller
                                val selectedFormat = selectedAudioTrackGroup.mediaTrackGroup.getFormat(0)
                                val selectedLanguage = selectedFormat.language
                                
                                // Try to match by language to find the Jellyfin audio stream index
                                val matchingAudioStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                    ?.filter { it.Type == "Audio" }
                                    ?.firstOrNull { stream ->
                                        stream.Language?.let { lang ->
                                            lang.equals(selectedLanguage, ignoreCase = true) ||
                                            lang.startsWith(selectedLanguage?.take(2) ?: "", ignoreCase = true) ||
                                            selectedLanguage?.startsWith(lang.take(2), ignoreCase = true) == true
                                        } == true
                                    }
                                
                                val newAudioIndex = matchingAudioStream?.Index
                                
                                if (newAudioIndex != null && newAudioIndex != lastSelectedAudioIndex) {
                                    Log.d("JellyfinPlayer", "Audio track selected via ExoPlayer controller: index=$newAudioIndex, language=${matchingAudioStream.Language}")
                                    lastSelectedAudioIndex = newAudioIndex
                                    currentAudioIndex = newAudioIndex
                                    
                                    // Save preference
                                    settings.setAudioPreference(item.Id, newAudioIndex)
                                }
                            }
                            
                            // Apply audio preference (from series/movie page or stored preference) if it's different from current selection
                            // Note: We apply even if currentAudioIndex matches, but ExoPlayer has auto-selected a different track
                            // Check allAudioTrackGroups (including unsupported) so we can force selection of unsupported tracks
                            if (audioIndexToApply != null && itemDetails != null && allAudioTrackGroups.isNotEmpty()) {
                                // Check if the currently selected track matches our preference
                                // Check allAudioTrackGroups (including unsupported) to see what's currently selected
                                val currentlySelected = allAudioTrackGroups.firstOrNull { it.isSelected }
                                val needsUpdate = if (currentlySelected != null) {
                                    // Check if the selected track matches our preference
                                    val selectedFormat = currentlySelected.mediaTrackGroup.getFormat(0)
                                    val selectedLanguage = selectedFormat.language
                                    val audioStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                        ?.find { it.Type == "Audio" && it.Index == audioIndexToApply }
                                    val preferenceLanguage = audioStream?.Language
                                    
                                    // If languages don't match, or if currentAudioIndex doesn't match, we need to update
                                    val languageMatches = preferenceLanguage?.let { prefLang ->
                                        selectedLanguage?.let { selLang ->
                                            prefLang.equals(selLang, ignoreCase = true) ||
                                            prefLang.startsWith(selLang.take(2), ignoreCase = true) ||
                                            selLang.startsWith(prefLang.take(2), ignoreCase = true)
                                        } ?: false
                                    } ?: false
                                    
                                    !languageMatches || currentAudioIndex != audioIndexToApply
                                } else {
                                    // No track selected, we need to select our preference
                                    true
                                }
                                
                                if (needsUpdate) {
                                // User selected an audio track from series/movie page - select it
                                try {
                                    // Get the Jellyfin audio stream with the preferred index
                                    val preferredAudioStream = jellyfinAudioStreams.find { it.Index == audioIndexToApply }
                                    
                                    if (preferredAudioStream == null) {
                                        Log.w("JellyfinPlayer", "Jellyfin audio stream index $audioIndexToApply not found")
                                    } else {
                                        Log.d("JellyfinPlayer", "Looking for ExoPlayer track matching Jellyfin audio stream index=$audioIndexToApply, language=${preferredAudioStream.Language}, codec=${preferredAudioStream.Codec ?: "null"}")
                                        
                                        // Fix 2: Handle null codec - try to match by language even if codec is null
                                        // Fix 3: Force selection even for unsupported tracks
                                        var groupToSelect: Tracks.Group? = null
                                        var groupIndexToSelect = -1
                                        var trackIndexToSelect = 0
                                        
                                        // Try to find matching ExoPlayer track group by matching with all track groups (including unsupported)
                                        // First try to find by language and codec
                                        // Use for loop instead of forEach to allow early exit
                                        for ((groupIdx, group) in allAudioTrackGroups.withIndex()) {
                                            val format = group.mediaTrackGroup.getFormat(0)
                                            val trackLang = format.language
                                            val trackCodec = format.codecs
                                            
                                            // Match by language (primary)
                                            val languageMatch = preferredAudioStream.Language?.let { prefLang ->
                                                trackLang?.let { tLang ->
                                                    prefLang.equals(tLang, ignoreCase = true) ||
                                                    prefLang.startsWith(tLang.take(2), ignoreCase = true) ||
                                                    tLang.startsWith(prefLang.take(2), ignoreCase = true)
                                                } ?: false
                                            } ?: false
                                            
                                            // Match by codec (secondary, but only if codec is not null)
                                            val codecMatch = preferredAudioStream.Codec?.let { prefCodec ->
                                                trackCodec?.equals(prefCodec, ignoreCase = true) ?: false
                                            } ?: false
                                            
                                            // If language matches (or codec matches if both are available), select this track
                                            // Prefer supported tracks over unsupported ones
                                            if (languageMatch || codecMatch) {
                                                // If we haven't found a match yet, or if this one is supported and previous wasn't, use this one
                                                if (groupToSelect == null || (group.isSupported && !groupToSelect.isSupported)) {
                                                    groupToSelect = group
                                                    groupIndexToSelect = groupIdx
                                                    trackIndexToSelect = 0 // Select first track in the group
                                                    Log.d("JellyfinPlayer", "Found matching track: group=$groupIdx, language=$trackLang, codec=${trackCodec ?: "null"}, supported=${group.isSupported}")
                                                    
                                                    // If we found a supported match, we can break (it's the best match)
                                                    if (group.isSupported) {
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // If found a match, force selection even if unsupported (Fix 3)
                                        if (groupToSelect != null && groupIndexToSelect >= 0) {
                                            try {
                                                val trackGroup = groupToSelect.mediaTrackGroup
                                                
                                                // Fix 3: Try to force selection even for unsupported tracks
                                                // Use addOverride which sometimes works even for unsupported tracks
                                                // (especially with extension renderers enabled)
                                                val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
                                                    trackGroup,
                                                    trackIndexToSelect
                                                )
                                                
                                                val updatedParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                    .addOverride(trackSelectionOverride)
                                                    .build()
                                                
                                                player.trackSelectionParameters = updatedParameters
                                                
                                                val selectedFormat = groupToSelect.mediaTrackGroup.getFormat(0)
                                                currentAudioIndex = audioIndexToApply
                                                lastSelectedAudioIndex = audioIndexToApply
                                                
                                                if (groupToSelect.isSupported) {
                                                    Log.d("JellyfinPlayer", "✅ Selected audio track: language=${selectedFormat.language}, codec=${selectedFormat.codecs ?: "null"}, Jellyfin index=$audioIndexToApply, ExoPlayer group=$groupIndexToSelect")
                                                } else {
                                                    Log.d("JellyfinPlayer", "⚠️ Attempted to force selection of unsupported audio track: language=${selectedFormat.language}, codec=${selectedFormat.codecs ?: "null"}, Jellyfin index=$audioIndexToApply, ExoPlayer group=$groupIndexToSelect (may not work if codec truly unsupported)")
                                                }
                                            } catch (e: Exception) {
                                                Log.w("JellyfinPlayer", "Error selecting audio track: ${e.message}", e)
                                                // If it's unsupported and addOverride failed, log a warning
                                                if (!groupToSelect.isSupported) {
                                                    Log.w("JellyfinPlayer", "⚠️ Audio track index $audioIndexToApply is not supported by ExoPlayer (language=${preferredAudioStream.Language}, codec=${preferredAudioStream.Codec ?: "null"}) and cannot be forced")
                                                }
                                            }
                                        } else {
                                            Log.w("JellyfinPlayer", "⚠️ Could not find ExoPlayer track matching Jellyfin audio stream index=$audioIndexToApply (language=${preferredAudioStream.Language}, codec=${preferredAudioStream.Codec ?: "null"})")
                                            Log.d("JellyfinPlayer", "Available ExoPlayer tracks: ${allAudioTrackGroups.mapIndexed { idx, g -> 
                                                val f = g.mediaTrackGroup.getFormat(0)
                                                "Group $idx: lang=${f.language}, codec=${f.codecs ?: "null"}, supported=${g.isSupported}"
                                            }}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error selecting audio track: ${e.message}", e)
                                }
                                } else {
                                    Log.d("JellyfinPlayer", "Audio track already matches preference, no update needed")
                                }
                            } else {
                                Log.d("JellyfinPlayer", "No audio preference to apply (audioIndexToApply=$audioIndexToApply, itemDetails=${itemDetails != null}, audioTracks=${audioTrackGroups.size})")
                            }
                        }
                        
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    Log.d("JellyfinPlayer", "Player ready")
                                    // Ensure PlayerView is visible when ready
                                    playerViewRef.value?.let { view ->
                                        if (view.visibility != android.view.View.VISIBLE) {
                                            view.visibility = android.view.View.VISIBLE
                                            Log.d("JellyfinPlayer", "Made PlayerView visible")
                                        }
                                        if (view.alpha != 1f) {
                                            view.alpha = 1f
                                        }
                                        // Force a layout pass to ensure rendering
                                        view.post {
                                            view.requestLayout()
                                            view.invalidate()
                                        }
                                    }
                                    // Seek to resume position only once, when player first becomes ready
                                    if (resumePositionMs > 0 && !hasSeekedToResume) {
                                        player.seekTo(resumePositionMs)
                                        hasSeekedToResume = true
                                        Log.d("JellyfinPlayer", "Seeked to resume position: ${resumePositionMs}ms")
                                    }
                                }
                                Player.STATE_BUFFERING -> {
                                    Log.d("JellyfinPlayer", "Player buffering")
                                    // Ensure PlayerView is visible during buffering
                                    playerViewRef.value?.let { view ->
                                        if (view.visibility != android.view.View.VISIBLE) {
                                            view.visibility = android.view.View.VISIBLE
                                        }
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    Log.d("JellyfinPlayer", "🎬 STATE_ENDED - Playback ended")
                                    progressReportingJob?.cancel()
                                    progressReportingJob = null
                                    showNextUpOverlay = false
                                    
                                    // Only mark as watched if video was actually completed (watched until near the end)
                                    scope.launch {
                                        try {
                                            Log.d("JellyfinPlayer", "🎬 STATE_ENDED - Getting playback position...")
                                            // Access player on main thread
                                            val currentPositionMs = withContext(Dispatchers.Main) {
                                                player.currentPosition
                                            }
                                            val durationMs = withContext(Dispatchers.Main) {
                                                player.duration
                                            }
                                            val positionTicks = currentPositionMs * 10_000L // Convert ms to ticks
                                            
                                            Log.d("JellyfinPlayer", "🎬 STATE_ENDED - Position: ${currentPositionMs}ms / ${durationMs}ms")
                                            
                                            // Check if video was actually completed (watched at least 90% or within last 5 seconds)
                                            val isComplete = durationMs > 0 && (
                                                currentPositionMs >= durationMs - 5000 || // Within last 5 seconds
                                                currentPositionMs >= durationMs * 0.90    // Or watched 90% of video
                                            )
                                            
                                            Log.d("JellyfinPlayer", "🎬 STATE_ENDED - isComplete=$isComplete")
                                            Log.d("JellyfinPlayer", "🎬 STATE_ENDED - nextEpisodeId=$nextEpisodeId, autoplayCancelled=$autoplayCancelled, nextEpisodeDetails=${nextEpisodeDetails != null}")
                                            
                                            // Report on background thread
                                            withContext(Dispatchers.IO) {
                                                apiService.reportPlaybackStopped(item.Id, positionTicks)
                                                // Only mark as watched if video was actually completed
                                                if (isComplete) {
                                                    apiService.markAsWatched(item.Id)
                                                    Log.d("JellyfinPlayer", "✅ Marked item as watched (completed ${(currentPositionMs * 100 / durationMs).toInt()}%)")
                                                } else {
                                                    Log.d("JellyfinPlayer", "⚠️ Playback stopped early (${(currentPositionMs * 100 / durationMs).toInt()}%), not marking as watched")
                                                }
                                                
                                                // Check if there's a next episode and autoplay wasn't cancelled
                                                Log.d("JellyfinPlayer", "🎬 Checking autoplay conditions:")
                                                Log.d("JellyfinPlayer", "  - nextEpisodeId != null: ${nextEpisodeId != null}")
                                                Log.d("JellyfinPlayer", "  - !autoplayCancelled: ${!autoplayCancelled}")
                                                Log.d("JellyfinPlayer", "  - isComplete: $isComplete")
                                                Log.d("JellyfinPlayer", "  - nextEpisodeDetails != null: ${nextEpisodeDetails != null}")
                                                Log.d("JellyfinPlayer", "  - autoplayNextEpisode setting: ${settings.autoplayNextEpisode}")
                                                
                                                if (nextEpisodeId != null && !autoplayCancelled && isComplete && nextEpisodeDetails != null && settings.autoplayNextEpisode) {
                                                    Log.d("JellyfinPlayer", "✅✅✅ ALL CONDITIONS MET - Starting autoplay for next episode: $nextEpisodeId")
                                                    Log.d("JellyfinPlayer", "✅ Next episode details: ${nextEpisodeDetails!!.Name}, ID: ${nextEpisodeDetails!!.Id}")
                                                    
                                                    // IMPORTANT: Stop and release current player before starting next episode
                                                    withContext(Dispatchers.Main) {
                                                        try {
                                                            Log.d("JellyfinPlayer", "✅ Stopping and releasing current player...")
                                                            player.stop()
                                                            player.release()
                                                            Log.d("JellyfinPlayer", "✅ Player stopped and released")
                                                        } catch (e: Exception) {
                                                            Log.w("JellyfinPlayer", "Error stopping player", e)
                                                        }
                                                    }
                                                    
                                                    // Start next episode in new Activity
                                                    withContext(Dispatchers.Main) {
                                                        try {
                                                            Log.d("JellyfinPlayer", "✅ Creating intent for next episode...")
                                                            val intent = com.flex.elefin.JellyfinVideoPlayerActivity.createIntent(
                                                                context = context,
                                                                itemId = nextEpisodeDetails!!.Id,
                                                                resumePositionMs = 0L,
                                                                subtitleStreamIndex = null,
                                                                audioStreamIndex = null
                                                            )
                                                            Log.d("JellyfinPlayer", "✅ Starting activity for next episode...")
                                                            context.startActivity(intent)
                                                            // Finish current activity
                                                            Log.d("JellyfinPlayer", "✅ Calling onBack() to finish current activity")
                                                            onBack()
                                                        } catch (e: Exception) {
                                                            Log.e("JellyfinPlayer", "❌ ERROR starting next episode", e)
                                                            e.printStackTrace()
                                                            onBack()
                                                        }
                                                    }
                                                } else {
                                                    // No next episode or autoplay cancelled, go back
                                                    Log.d("JellyfinPlayer", "❌ NOT autoplaying - conditions not met:")
                                                    Log.d("JellyfinPlayer", "  - nextEpisodeId=$nextEpisodeId")
                                                    Log.d("JellyfinPlayer", "  - autoplayCancelled=$autoplayCancelled")
                                                    Log.d("JellyfinPlayer", "  - isComplete=$isComplete")
                                                    Log.d("JellyfinPlayer", "  - nextEpisodeDetails=${nextEpisodeDetails?.Name ?: "null"}")
                                                    withContext(Dispatchers.Main) {
                                                        onBack()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("JellyfinPlayer", "❌ ERROR handling playback end", e)
                                            e.printStackTrace()
                                            onBack()
                                        }
                                    }
                                }
                            }
                        }
                    })
                    
                    // Prepare and play
                    player.prepare()
                    player.playWhenReady = true
                    
                    playerInitialized = true
                    Log.d("JellyfinPlayer", "Player initialized and started")
                    
                    // Request focus on PlayerView so it can receive key events
                    playerViewRef.value?.requestFocus()
                } catch (e: Exception) {
                    Log.e("JellyfinPlayer", "Error initializing player", e)
                }
            }
        }
    }

    // Monitor playback position to show Next Up overlay
    LaunchedEffect(nextEpisodeId, playerInitialized) {
        if (nextEpisodeId != null && playerInitialized && !autoplayCancelled && settings.autoplayNextEpisode) {
            Log.d("JellyfinPlayer", "Starting Next Up overlay monitoring. nextEpisodeId=$nextEpisodeId, playerInitialized=$playerInitialized, autoplayEnabled=${settings.autoplayNextEpisode}")
            while (true) {
                delay(1000) // Check every second
                if (!playerInitialized || autoplayCancelled) {
                    Log.d("JellyfinPlayer", "Stopping overlay monitoring: playerInitialized=$playerInitialized, autoplayCancelled=$autoplayCancelled")
                    break
                }
                try {
                    val currentPositionMs = withContext(Dispatchers.Main) {
                        player.currentPosition
                    }
                    val durationMs = withContext(Dispatchers.Main) {
                        player.duration
                    }
                    if (durationMs > 0) {
                        val timeElapsed = currentPositionMs
                        val timeRemaining = durationMs - currentPositionMs
                        val countdownDurationMs = settings.autoplayCountdownSeconds * 1000L
                        
                        Log.d("JellyfinPlayer", "Position check: current=${currentPositionMs}ms, duration=${durationMs}ms, elapsed=${timeElapsed}ms, remaining=${timeRemaining}ms, showOverlay=$showNextUpOverlay, countdownDuration=${settings.autoplayCountdownSeconds}s")
                        
                        // Show overlay in last N seconds of episode (based on setting)
                        if (timeRemaining <= countdownDurationMs && timeRemaining >= 0 && !showNextUpOverlay) {
                            showNextUpOverlay = true
                            autoplayCountdown = (timeRemaining / 1000).toInt().coerceAtMost(settings.autoplayCountdownSeconds).coerceAtLeast(0)
                            Log.d("JellyfinPlayer", "✅ Showing Next Up overlay (last ${settings.autoplayCountdownSeconds} seconds, ${timeRemaining}ms remaining, countdown: $autoplayCountdown)")
                        }
                        
                        // Update countdown (count down from remaining time)
                        if (showNextUpOverlay && timeRemaining <= countdownDurationMs) {
                            val newCountdown = (timeRemaining / 1000).toInt().coerceAtMost(settings.autoplayCountdownSeconds).coerceAtLeast(0)
                            val countdownChanged = newCountdown != autoplayCountdown
                            val previousCountdown = autoplayCountdown
                            autoplayCountdown = newCountdown
                            
                            if (countdownChanged) {
                                Log.d("JellyfinPlayer", "⏱️ Countdown updated: $previousCountdown -> $autoplayCountdown (remaining: ${timeRemaining}ms)")
                            }
                        } else if (showNextUpOverlay && timeRemaining > countdownDurationMs) {
                            // If we somehow got past the countdown duration, hide overlay
                            showNextUpOverlay = false
                            Log.d("JellyfinPlayer", "Hiding overlay (remaining: ${timeRemaining}ms > ${countdownDurationMs}ms)")
                        }
                        
                        // Check for countdown == 0 to trigger autoplay
                        if (showNextUpOverlay && autoplayCountdown == 0 && nextEpisodeDetails != null && !autoplayCancelled && settings.autoplayNextEpisode) {
                            Log.d("JellyfinPlayer", "✅✅✅ COUNTDOWN REACHED 0 - Triggering autoplay")
                            Log.d("JellyfinPlayer", "✅ Next episode: ${nextEpisodeDetails!!.Name}, ID: ${nextEpisodeDetails!!.Id}")
                            Log.d("JellyfinPlayer", "✅ Conditions: nextEpisodeDetails != null: ${nextEpisodeDetails != null}, autoplayCancelled: $autoplayCancelled, autoplayEnabled: ${settings.autoplayNextEpisode}, timeRemaining: ${timeRemaining}ms")
                            showNextUpOverlay = false
                            
                            // Trigger autoplay in a separate coroutine
                            scope.launch {
                                // IMPORTANT: Stop and release current player before starting next episode
                                withContext(Dispatchers.Main) {
                                    try {
                                        Log.d("JellyfinPlayer", "✅✅✅ STOPPING AND RELEASING CURRENT PLAYER (from countdown)...")
                                        player.stop()
                                        player.release()
                                        Log.d("JellyfinPlayer", "✅✅✅ Player stopped and released")
                                    } catch (e: Exception) {
                                        Log.e("JellyfinPlayer", "❌ ERROR stopping player", e)
                                        e.printStackTrace()
                                    }
                                }
                                
                                withContext(Dispatchers.Main) {
                                    try {
                                        Log.d("JellyfinPlayer", "✅ Creating intent for next episode (from countdown)...")
                                        val intent = com.flex.elefin.JellyfinVideoPlayerActivity.createIntent(
                                            context = context,
                                            itemId = nextEpisodeDetails!!.Id,
                                            resumePositionMs = 0L,
                                            subtitleStreamIndex = null,
                                            audioStreamIndex = null
                                        )
                                        Log.d("JellyfinPlayer", "✅ Starting activity for next episode (from countdown)...")
                                        context.startActivity(intent)
                                        // Finish current activity
                                        Log.d("JellyfinPlayer", "✅ Calling onBack() to finish current activity (from countdown)")
                                        onBack()
                                    } catch (e: Exception) {
                                        Log.e("JellyfinPlayer", "❌ ERROR starting next episode from countdown", e)
                                        e.printStackTrace()
                                    }
                                }
                            }
                            
                            // Exit the monitoring loop after launching the coroutine
                            break
                        }
                    } else {
                        Log.d("JellyfinPlayer", "Duration not available yet: durationMs=$durationMs")
                    }
                } catch (e: Exception) {
                    Log.w("JellyfinPlayer", "Error monitoring playback position", e)
                }
            }
        } else {
            Log.d("JellyfinPlayer", "Overlay monitoring not started: nextEpisodeId=$nextEpisodeId, playerInitialized=$playerInitialized, autoplayCancelled=$autoplayCancelled")
        }
    }
    
    // Handle autoplay when episode ends
    LaunchedEffect(nextEpisodeId, autoplayCancelled) {
        if (nextEpisodeId != null && !autoplayCancelled && nextEpisodeDetails != null) {
            // Wait for STATE_ENDED to trigger this
            // This effect will be triggered when nextEpisodeId is set and autoplay is not cancelled
        }
    }
    
    // Monitor controller visibility and focus play button when it first appears after playback starts
    LaunchedEffect(playerInitialized, isPlaying) {
        if (playerInitialized && isPlaying && !hasFocusedPlayButtonOnStart) {
            // Check periodically for controller visibility
            var attempts = 0
            while (attempts < 20 && !hasFocusedPlayButtonOnStart) { // Try for up to 2 seconds
                kotlinx.coroutines.delay(100)
                attempts++
                
                val playerView = playerViewRef.value
                val controller = playerView?.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                
                if (controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f) {
                    // Focus on play/pause button (pause button when playing)
                    val pauseButton = controller.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_pause)
                    val playButton = controller.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play)
                    val buttonToFocus = if (pauseButton != null && isPlaying) pauseButton else playButton
                    
                    buttonToFocus?.let { button ->
                        try {
                            if (button.isFocusable) {
                                button.requestFocus()
                                hasFocusedPlayButtonOnStart = true
                                Log.d("ExoPlayer", "Focused on play/pause button when controller appeared after playback started")
                            }
                        } catch (e: Exception) {
                            Log.w("ExoPlayer", "Failed to focus play button when controller appeared: ${e.message}")
                        }
                    }
                }
            }
        }
    }
    
    // Reset the flag when playback stops or player is reinitialized
    LaunchedEffect(playerInitialized) {
        if (!playerInitialized) {
            hasFocusedPlayButtonOnStart = false
        }
    }
    
    // Report playback progress periodically when playing OR paused (to save position)
    LaunchedEffect(isPlaying, playerInitialized) {
        if (playerInitialized) {
            progressReportingJob?.cancel()
            progressReportingJob = scope.launch {
                // Report immediately on first play
                var firstReport = true
                while (true) {
                    if (firstReport) {
                        delay(1000) // Report after 1 second to ensure position is available
                        firstReport = false
                    } else {
                        delay(5000) // Then report every 5 seconds
                    }
                    if (!playerInitialized) break
                    try {
                        // Access player on main thread
                        val currentPositionMs = withContext(Dispatchers.Main) {
                            player.currentPosition
                        }
                        // Convert milliseconds to ticks: 1 second = 10,000,000 ticks, so 1 ms = 10,000 ticks
                        val positionTicks = currentPositionMs * 10_000L
                        val isPaused = withContext(Dispatchers.Main) {
                            !player.isPlaying
                        }
                        // Report progress even when paused (so Jellyfin saves the position for Continue Watching)
                        // Only report if position is valid (greater than 0)
                        if (positionTicks > 0) {
                            // Report on background thread
                            withContext(Dispatchers.IO) {
                                apiService.reportPlaybackProgress(
                                    itemId = item.Id,
                                    positionTicks = positionTicks,
                                    isPaused = isPaused
                                )
                                Log.d("JellyfinPlayer", "Reported playback progress: ${currentPositionMs}ms (paused: $isPaused)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("JellyfinPlayer", "Error reporting playback progress", e)
                    }
                }
            }
        } else {
            progressReportingJob?.cancel()
            progressReportingJob = null
        }
    }

    // Cleanup player on dispose - only release when composable is removed
    DisposableEffect(Unit) {
        onDispose {
            Log.d("JellyfinPlayer", "Releasing player")
            progressReportingJob?.cancel()
            progressReportingJob = null
            // Report final position when player is disposed (but don't mark as watched - user might have navigated away early)
            scope.launch {
                try {
                    // Access player on main thread
                    val currentPositionMs = withContext(Dispatchers.Main) {
                        player.currentPosition
                    }
                    val durationMs = withContext(Dispatchers.Main) {
                        player.duration
                    }
                    val positionTicks = currentPositionMs * 10_000L // Convert ms to ticks
                    
                    // Only mark as watched if video was actually completed (watched at least 90% or within last 5 seconds)
                    val isComplete = durationMs > 0 && (
                        currentPositionMs >= durationMs - 5000 || // Within last 5 seconds
                        currentPositionMs >= durationMs * 0.90    // Or watched 90% of video
                    )
                    
                    // Report on background thread
                    withContext(Dispatchers.IO) {
                        apiService.reportPlaybackStopped(item.Id, positionTicks)
                        // Only mark as watched if video was actually completed
                        if (isComplete) {
                            apiService.markAsWatched(item.Id)
                            Log.d("JellyfinPlayer", "Marked item as watched on dispose (completed ${(currentPositionMs * 100 / durationMs).toInt()}%)")
                        } else {
                            Log.d("JellyfinPlayer", "Playback stopped early on dispose (${(currentPositionMs * 100 / durationMs).toInt()}%), not marking as watched")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("JellyfinPlayer", "Error reporting final playback position", e)
                }
            }
            player.release()
        }
    }

    // BackHandler to handle back button
    BackHandler(enabled = true) {
        // Exit player on back button
        onBack()
    }

    // Get series name if this is an episode
    var seriesName by remember { mutableStateOf<String?>(null) }
    
    // Title overlay visibility - show initially, hide after 10 seconds or when controller is visible
    var titleOverlayVisible by remember { mutableStateOf(true) }
    
    // Hide title overlay after 10 seconds
    LaunchedEffect(Unit) {
        delay(10000) // 10 seconds
        titleOverlayVisible = false
    }
    
    // Check if ExoPlayer controller is visible and hide title overlay accordingly
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // Check every 100ms
            playerViewRef.value?.let { view ->
                val controller = view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                if (controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f) {
                    // Controller is visible, hide title overlay
                    titleOverlayVisible = false
                }
            }
        }
    }
    
    LaunchedEffect(itemDetails?.SeriesId, apiService) {
        if (itemDetails?.Type == "Episode" && itemDetails?.SeriesId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val series = apiService.getItemDetails(itemDetails!!.SeriesId!!)
                    seriesName = series?.Name
                } catch (e: Exception) {
                    Log.w("JellyfinPlayer", "Error fetching series name", e)
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Handle key events for video controls
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionCenter,  // DPAD center
                        Key.Enter,             // Enter key
                        Key.NumPadEnter -> {
                            val playerView = playerViewRef.value
                            if (playerView != null) {
                                // Check if controller is currently visible
                                val controller = playerView.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                val isControllerShowing = controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f
                                
                                if (!isControllerShowing) {
                                    // Controller is hidden, show it
                                    playerView.showController()
                                    Log.d("ExoPlayer", "Enter/OK pressed - showing controller")
                                    // Set flag to focus play button after controller is shown
                                    shouldFocusPlayButton = true
                                    // Ensure PlayerView has focus so it can receive further key events
                                    playerView.requestFocus()
                                    // Consume the event to prevent it from being handled elsewhere
                                    true
                                } else {
                                    // Controller is already showing, let it handle the event (for play/pause)
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        Key.DirectionLeft -> {
                            // Cancel autoplay if overlay is showing
                            if (showNextUpOverlay) {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                                Log.d("JellyfinPlayer", "Autoplay cancelled by user (Left key)")
                                true
                            } else {
                                // Check if controller is showing - if so, don't seek (allow navigation)
                                val controller = playerViewRef.value?.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                val isControllerShowing = controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f
                                
                                if (!isControllerShowing) {
                                // Seek backward 15 seconds (don't show controller)
                                scope.launch(Dispatchers.Main) {
                                    val currentPos = player.currentPosition
                                    val seekTo = (currentPos - 15000).coerceAtLeast(0)
                                    player.seekTo(seekTo)
                                    Log.d("ExoPlayer", "Left pressed - seeking backward to ${seekTo}ms")
                                }
                                true // Consume event
                                } else {
                                    false // Don't consume - let controller handle navigation
                                }
                            }
                        }
                        Key.DirectionRight -> {
                            // Cancel autoplay if overlay is showing
                            if (showNextUpOverlay) {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                                Log.d("JellyfinPlayer", "Autoplay cancelled by user (Right key)")
                                true
                            } else {
                                // Check if controller is showing - if so, don't seek (allow navigation)
                                val controller = playerViewRef.value?.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                val isControllerShowing = controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f
                                
                                if (!isControllerShowing) {
                                // Seek forward 15 seconds (don't show controller)
                                scope.launch(Dispatchers.Main) {
                                    val currentPos = player.currentPosition
                                    val duration = player.duration
                                    val seekTo = if (duration > 0) {
                                        (currentPos + 15000).coerceAtMost(duration)
                                    } else {
                                        currentPos + 15000
                                    }
                                    player.seekTo(seekTo)
                                    Log.d("ExoPlayer", "Right pressed - seeking forward to ${seekTo}ms")
                                }
                                true // Consume event
                                } else {
                                    false // Don't consume - let controller handle navigation
                                }
                            }
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            // Cancel autoplay if overlay is showing
                            if (showNextUpOverlay) {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                                Log.d("JellyfinPlayer", "Autoplay cancelled by user (Up/Down key)")
                                true
                            } else {
                                false
                            }
                        }
                        Key.Menu -> {
                            // Open settings menu when Menu key is pressed
                            showSettingsMenu = true
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        when {
            mediaUrl != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                // Enable built-in controller for proper Android TV support
                                useController = true
                                // Show controller automatically
                                controllerShowTimeoutMs = 5000 // Hide after 5 seconds of inactivity
                                // Enable subtitle track selection in controller
                                controllerAutoShow = true
                                // Explicitly show subtitle button - this is the key!
                                setShowSubtitleButton(true)
                                // Make sure controller is focusable for TV
                                controllerHideOnTouch = false // On TV, don't hide on touch
                                // Make PlayerView focusable so it can receive key events
                                isFocusable = true
                                isFocusableInTouchMode = false // Not needed for TV
                                
                                // Ensure view is visible and properly sized
                                visibility = android.view.View.VISIBLE
                                alpha = 1f
                                
                                playerViewRef.value = this
                                
                                // Hide next/previous track buttons and ensure subtitle button is visible
                                post {
                                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
                                    
                                    // Ensure the view is properly attached and visible
                                    visibility = android.view.View.VISIBLE
                                    alpha = 1f
                                    
                                    // Explicitly show subtitle button again after view is attached
                                    setShowSubtitleButton(true)
                                    
                                    // Get the PlayerControlView and ensure subtitle button is visible
                                    val controller = findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                    controller?.let { controlView ->
                                        // Customize control button focus color to purple (transparent)
                                        val transparentPurple = android.graphics.Color.argb(150, 156, 39, 176) // Purple with transparency
                                        
                                        // Apply custom focus color to all control buttons and seekbar
                                        val buttonIds = listOf(
                                            androidx.media3.ui.R.id.exo_play,
                                            androidx.media3.ui.R.id.exo_pause,
                                            androidx.media3.ui.R.id.exo_ffwd,
                                            androidx.media3.ui.R.id.exo_rew,
                                            androidx.media3.ui.R.id.exo_subtitle,
                                            androidx.media3.ui.R.id.exo_settings,
                                            androidx.media3.ui.R.id.exo_progress,
                                            androidx.media3.ui.R.id.exo_position,
                                            androidx.media3.ui.R.id.exo_duration,
                                            androidx.media3.ui.R.id.exo_repeat_toggle,
                                            androidx.media3.ui.R.id.exo_shuffle
                                        )
                                        
                                        // Function to apply purple focus styling with round shape and 10% larger size
                                        fun applyPurpleFocus(view: android.view.View) {
                                            // Store original background
                                            val originalBackground = view.background
                                            
                                            view.setOnFocusChangeListener { v, hasFocus ->
                                                if (hasFocus) {
                                                    // Use post to ensure dimensions are available and avoid interfering with focus navigation
                                                    v.post {
                                                        val width = v.width
                                                        val height = v.height
                                                        
                                                        if (width > 0 && height > 0) {
                                                            // Create a round drawable
                                                            val drawable = android.graphics.drawable.GradientDrawable().apply {
                                                                setColor(transparentPurple)
                                                                // Make it round by setting corner radius to half of larger dimension
                                                                val maxDimension = maxOf(width, height)
                                                                val cornerRadius = maxDimension * 0.5f
                                                                setCornerRadius(cornerRadius)
                                                            }
                                                            
                                                            // Create a LayerDrawable with the round drawable and add padding to make it appear larger
                                                            val padding = (maxOf(width, height) * 0.05f).toInt()
                                                            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(drawable))
                                                            layerDrawable.setLayerInset(0, -padding, -padding, -padding, -padding)
                                                            
                                                            // Set the drawable as background
                                                            v.background = layerDrawable
                                                        } else {
                                                            // Fallback: just set color if dimensions not available
                                                            v.setBackgroundColor(transparentPurple)
                                                        }
                                                    }
                                                } else {
                                                    // Reset to original background immediately (don't use post)
                                                    v.background = originalBackground
                                                }
                                            }
                                        }
                                        
                                        buttonIds.forEach { buttonId ->
                                            findViewById<android.view.View>(buttonId)?.let { button ->
                                                applyPurpleFocus(button)
                                            }
                                        }
                                        
                                        // Apply to all child views recursively to catch any other buttons
                                        fun applyToAllChildren(parent: android.view.ViewGroup) {
                                            for (i in 0 until parent.childCount) {
                                                val child = parent.getChildAt(i)
                                                if (child is android.view.ViewGroup) {
                                                    applyToAllChildren(child)
                                                } else if (child is android.widget.Button || 
                                                          child is android.widget.ImageButton ||
                                                          (child.isFocusable && child.isClickable)) {
                                                    applyPurpleFocus(child)
                                                }
                                            }
                                        }
                                        
                                        // Apply styling to all focusable/clickable children
                                        applyToAllChildren(controlView)
                                        
                                        // Force a refresh to ensure subtitle button is visible
                                        controlView.invalidate()
                                        Log.d("ExoPlayer", "PlayerControlView initialized, subtitle button explicitly enabled, focus color set to purple for all controls")
                                    }
                                    
                                    // Request layout to ensure proper rendering
                                    requestLayout()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            // Update player reference when view changes
                            if (view.player != player) {
                                view.player = player
                            }
                            // Ensure view is focusable and can receive key events
                            if (!view.isFocusable) {
                                view.isFocusable = true
                            }
                            // Ensure view is visible
                            if (view.visibility != android.view.View.VISIBLE) {
                                view.visibility = android.view.View.VISIBLE
                            }
                            if (view.alpha != 1f) {
                                view.alpha = 1f
                            }
                            // Explicitly show subtitle button - this ensures it's visible when tracks are available
                            view.setShowSubtitleButton(true)
                            
                            // Hide next/previous track buttons whenever controller is shown
                            view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                            view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
                            
                            // Ensure subtitle button is visible when tracks are available
                            val controller = view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                            controller?.let { controlView ->
                                // Force refresh to show subtitle button
                                controlView.invalidate()
                                
                                // Explicitly make subtitle button visible if it exists
                                val subtitleButton = controlView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
                                subtitleButton?.visibility = android.view.View.VISIBLE
                            }
                        }
                    )
                    
                    // Next Up Overlay
                    if (showNextUpOverlay && nextEpisodeDetails != null) {
                        NextUpOverlay(
                            nextEpisode = nextEpisodeDetails!!,
                            countdown = autoplayCountdown,
                            onCancel = {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                            }
                        )
                    }
                    
                    // Watch for controller visibility and focus play button whenever it appears
                    LaunchedEffect(shouldFocusPlayButton, playerInitialized) {
                        if (shouldFocusPlayButton && playerInitialized) {
                            // Wait for controller to be fully rendered
                            kotlinx.coroutines.delay(300)
                            
                            val playerView = playerViewRef.value
                            val controller = playerView?.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                            
                            if (controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f) {
                                // Focus on play/pause button instead of settings
                                val playButton = controller.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play)
                                val pauseButton = controller.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_pause)
                                val buttonToFocus = if (player.isPlaying && pauseButton != null) pauseButton else playButton
                                
                                buttonToFocus?.let { button ->
                                    try {
                                        if (button.isFocusable) {
                                            button.requestFocus()
                                            Log.d("ExoPlayer", "Focused on play/pause button when controls shown")
                                        } else {
                                            // If not focusable, make it focusable and try again
                                            button.isFocusable = true
                                            button.isFocusableInTouchMode = false
                                            kotlinx.coroutines.delay(50)
                                            button.requestFocus()
                                            Log.d("ExoPlayer", "Made button focusable and focused on play/pause button")
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ExoPlayer", "Failed to focus play button: ${e.message}")
                                    }
                                }
                            }
                            
                            // Reset flag
                            shouldFocusPlayButton = false
                        }
                    }
                    
                    // Monitor controller visibility continuously and focus play button when it appears
                    LaunchedEffect(playerInitialized) {
                        if (!playerInitialized) return@LaunchedEffect
                        
                        var wasControllerVisible = false
                        while (true) {
                            kotlinx.coroutines.delay(100) // Check every 100ms
                            
                            val playerView = playerViewRef.value
                            val controller = playerView?.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                            val isControllerVisible = controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f
                            
                            // If controller just became visible, focus play button
                            if (isControllerVisible && !wasControllerVisible) {
                                kotlinx.coroutines.delay(200) // Wait for controller to be fully rendered
                                
                                val playButton = controller?.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play)
                                val pauseButton = controller?.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_pause)
                                val buttonToFocus = if (player.isPlaying && pauseButton != null) pauseButton else playButton
                                
                                buttonToFocus?.let { button ->
                                    try {
                                        // Clear any existing focus first
                                        val currentFocused = controller.findFocus()
                                        currentFocused?.clearFocus()
                                        
                                        // Make button focusable if needed
                                        if (!button.isFocusable) {
                                            button.isFocusable = true
                                            button.isFocusableInTouchMode = false
                                        }
                                        
                                        // Request focus on play/pause button
                                        button.requestFocus()
                                        Log.d("ExoPlayer", "Focused on play/pause button when controller appeared (monitored)")
                                    } catch (e: Exception) {
                                        Log.w("ExoPlayer", "Failed to focus play button when controller appeared: ${e.message}")
                                    }
                                }
                            }
                            
                            wasControllerVisible = isControllerVisible
                        }
                    }
                    
                    // Title overlay at the top - disappears after 10 seconds or when controller is visible
                    val displayName = itemDetails?.Name ?: item.Name
                    val isEpisode = itemDetails?.Type == "Episode"
                    // Check if controller is visible
                    val controller = playerViewRef.value?.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                    val isControllerShowing = controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f
                    val showTitle = titleOverlayVisible && !isControllerShowing && (displayName.isNotEmpty() || (isEpisode && seriesName != null))
                    
                    if (showTitle) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 32.dp, top = 32.dp)
                        ) {
                            // Show series name for episodes, or movie/show name for others
                            if (isEpisode && seriesName != null) {
                                androidx.tv.material3.Text(
                                    text = seriesName!!,
                                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            } else if (!isEpisode && displayName.isNotEmpty()) {
                                androidx.tv.material3.Text(
                                    text = displayName,
                                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            
                            // Show episode name below series name for episodes
                            if (isEpisode && displayName.isNotEmpty()) {
                                androidx.tv.material3.Text(
                                    text = displayName,
                                    style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                    
                }
            }
            isLoading -> {
                // Loading indicator could go here
            }
        }
        
        // Settings menu with subtitle picker
        if (showSettingsMenu) {
            ExoPlayerSettingsMenu(
                item = itemDetails ?: item,
                apiService = apiService,
                currentSubtitleIndex = currentSubtitleIndex,
                onDismiss = { showSettingsMenu = false },
                player = player,
                jellyfinSubtitleStreams = jellyfinSubtitleStreams, // Pass for composite key registration
                onSubtitleSelected = { subtitleIndex ->
                    currentSubtitleIndex = subtitleIndex
                    // Reload player with new subtitle
                    scope.launch(Dispatchers.Main) {
                        try {
                            val currentPos = player.currentPosition
                            val wasPlaying = player.isPlaying
                            
                            // Stop and clear current media
                            player.stop()
                            player.clearMediaItems()
                            
                            // Recreate MediaItem with new subtitle
                            val mediaSourceIdForSubtitle = itemDetails?.MediaSources?.firstOrNull()?.Id ?: item.Id
                            val mediaItem = if (subtitleIndex != null && itemDetails != null) {
                                try {
                                    val subtitleUrl = apiService.getSubtitleUrl(item.Id, mediaSourceIdForSubtitle, subtitleIndex)
                                    val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                        ?.find { it.Type == "Subtitle" && it.Index == subtitleIndex }
                                    val subtitleLanguage = subtitleStream?.Language
                                    val subtitleMimeType = MimeTypes.TEXT_VTT
                                    
                                    MediaItem.Builder()
                                        .setUri(Uri.parse(mediaUrl))
                                        .setSubtitleConfigurations(
                                            listOf(
                                                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                                                    .setMimeType(subtitleMimeType)
                                                    .setLanguage(subtitleLanguage)
                                                    .build()
                                            )
                                        )
                                        .build()
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error creating MediaItem with subtitle, using video only: ${e.message}", e)
                                    MediaItem.fromUri(Uri.parse(mediaUrl))
                                }
                            } else {
                                MediaItem.fromUri(Uri.parse(mediaUrl))
                            }
                            
                            // Get headers
                            val headers = apiService.getVideoRequestHeaders()
                            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                                .setUserAgent("Jellyfin Android TV")
                                .setAllowCrossProtocolRedirects(true)
                                .setDefaultRequestProperties(headers.toMutableMap())
                            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
                            
                            // Create and set new media source
                            val newMediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(mediaItem)
                            player.setMediaSource(newMediaSource)
                            player.prepare()
                            
                            // Restore position and playing state
                            player.seekTo(currentPos)
                            player.playWhenReady = wasPlaying
                            
                            showSettingsMenu = false
                        } catch (e: Exception) {
                            Log.e("JellyfinPlayer", "Error reloading with new subtitle", e)
                        }
                    }
                }
            )
        }
    }
}

// Data class to hold audio track information
data class AudioTrackInfo(
    val group: Tracks.Group,
    val index: Int,
    val language: String?,
    val label: String?,
    val codec: String?,
    val isSelected: Boolean,
    val channelCount: Int,
    val sampleRate: Int
)

@Composable
fun ExoPlayerSettingsMenu(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    currentSubtitleIndex: Int?,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int?) -> Unit,
    player: ExoPlayer? = null,
    jellyfinSubtitleStreams: List<MediaStream> = emptyList() // For composite key registration
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }
    
    // Fetch full item details to get MediaSources with subtitle and audio streams
    LaunchedEffect(item.Id, apiService) {
        withContext(Dispatchers.IO) {
            try {
                val details = apiService.getItemDetails(item.Id)
                itemDetails = details
                isLoadingSubtitles = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when composable leaves composition - don't log as error
                throw e // Re-throw to respect cancellation
            } catch (e: Exception) {
                Log.e("ExoPlayerSettingsMenu", "Error fetching item details", e)
                isLoadingSubtitles = false
            }
        }
    }
    
    // Update tracks when player tracks change
    // Capture jellyfinSubtitleStreams in the effect scope
    DisposableEffect(player, jellyfinSubtitleStreams) {
        val listener = if (player != null) {
            // Get initial tracks if available
            val initialTracks = player.currentTracks
            if (initialTracks.groups.isNotEmpty()) {
                currentTracks = initialTracks
            }
            
            // Listen for track changes
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    currentTracks = tracks
                    
                    // Log detected subtitle tracks for debugging
                    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    Log.d("JellyfinPlayer", "ExoPlayer detected ${textGroups.size} subtitle track group(s)")
                    if (textGroups.isEmpty()) {
                        Log.w("JellyfinPlayer", "⚠️ No subtitle tracks detected by ExoPlayer")
                        Log.w("JellyfinPlayer", "   This usually means:")
                        Log.w("JellyfinPlayer", "   1. The subtitle URL returned 404 (file doesn't exist)")
                        Log.w("JellyfinPlayer", "   2. The subtitle MIME type is incorrect")
                        Log.w("JellyfinPlayer", "   3. ExoPlayer couldn't parse the subtitle file")
                    } else {
                        // Log detected subtitle tracks for debugging
                        textGroups.forEachIndexed { idx, group ->
                            val format = group.mediaTrackGroup.getFormat(0)
                            Log.d("JellyfinPlayer", "  ExoPlayer subtitle track group $idx:")
                            Log.d("JellyfinPlayer", "    Format.id: '${format.id}', Lang: ${format.language}, MIME: ${format.sampleMimeType}")
                            Log.d("JellyfinPlayer", "    Label: ${format.label}, Selected: ${group.isSelected}")
                        }
                    }
                }
                
            }.also { player.addListener(it) }
        } else null
        
        onDispose {
            listener?.let { player?.removeListener(it) }
        }
    }
    
    // Get audio tracks from ExoPlayer
    val audioTracks = remember(currentTracks) {
        currentTracks?.groups?.filter { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.isSupported
        }?.mapIndexedNotNull { index, group ->
            // Get format info from the first track in the group
            if (group.mediaTrackGroup.length > 0) {
                val format = group.mediaTrackGroup.getFormat(0)
                AudioTrackInfo(
                    group = group,
                    index = index,
                    language = format.language,
                    label = format.label,
                    codec = format.codecs,
                    isSelected = group.isSelected,
                    channelCount = format.channelCount,
                    sampleRate = format.sampleRate
                )
            } else null
        } ?: emptyList()
    }
    
    // Get audio streams from Jellyfin MediaSources for additional metadata
    val audioStreams = remember(itemDetails?.MediaSources) {
        itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Audio" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
    }
    
    // Get subtitle streams from MediaSources
    val subtitleStreams = remember(itemDetails?.MediaSources) {
        itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Subtitle" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight(0.6f),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Dialog title
                    Text(
                        text = "Player Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.8f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Audio section - ABOVE subtitles
                    if (player != null && audioTracks.isNotEmpty()) {
                        Text(
                            text = "Audio Tracks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.8f
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(audioTracks.size) { index ->
                                val track = audioTracks[index]
                                val trackTitle = buildString {
                                    track.label?.let { append(it) }
                                    if (isEmpty()) {
                                        track.language?.let { append(it) } ?: append("Unknown")
                                    }
                                }
                                val trackInfo = buildString {
                                    track.codec?.let { 
                                        if (isNotEmpty()) append(", ")
                                        append(it)
                                    }
                                    if (track.channelCount > 0) {
                                        if (isNotEmpty()) append(", ")
                                        append("${track.channelCount}ch")
                                    }
                                    if (track.sampleRate > 0) {
                                        if (isNotEmpty()) append(", ")
                                        append("${track.sampleRate / 1000}kHz")
                                    }
                                }
                                
                                ListItem(
                                    selected = track.isSelected,
                                    onClick = {
                                        // Select audio track
                                        try {
                                            val trackSelectionOverride = TrackSelectionOverride(
                                                track.group.mediaTrackGroup,
                                                0 // Select first track in the group
                                            )
                                            val updatedParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .addOverride(trackSelectionOverride)
                                                .build()
                                            player.trackSelectionParameters = updatedParameters
                                            Log.d("ExoPlayerSettingsMenu", "Selected audio track: $trackTitle")
                                        } catch (e: Exception) {
                                            Log.e("ExoPlayerSettingsMenu", "Error selecting audio track", e)
                                        }
                                    },
                                    headlineContent = {
                                        Column {
                                            Text(
                                                text = trackTitle,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                )
                                            )
                                            if (trackInfo.isNotEmpty()) {
                                                Text(
                                                    text = trackInfo,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    // Subtitle section - BELOW audio tracks
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.8f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                    
                    // Vertical list of subtitle options
                    if (isLoadingSubtitles) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading subtitles...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // "None" option to disable subtitles
                            item {
                                ListItem(
                                    selected = currentSubtitleIndex == null,
                                    onClick = {
                                        onSubtitleSelected(null)
                                    },
                                    headlineContent = {
                                        Text(
                                            text = "None (Off)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // Subtitle stream options
                            items(subtitleStreams) { stream ->
                                val subtitleTitle = stream.DisplayTitle
                                    ?: stream.Language
                                    ?: "Unknown"
                                val subtitleInfo = buildString {
                                    if (stream.IsDefault == true) append("Default")
                                    if (stream.IsForced == true) {
                                        if (isNotEmpty()) append(", ")
                                        append("Forced")
                                    }
                                    if (stream.IsExternal == true) {
                                        if (isNotEmpty()) append(", ")
                                        append("External")
                                    }
                                }
                                
                                ListItem(
                                    selected = stream.Index == currentSubtitleIndex,
                                    onClick = {
                                        stream.Index?.let { index ->
                                            onSubtitleSelected(index)
                                        }
                                    },
                                    headlineContent = {
                                        Column {
                                            Text(
                                                text = subtitleTitle,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                )
                                            )
                                            if (subtitleInfo.isNotEmpty()) {
                                                Text(
                                                    text = subtitleInfo,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    // Speed section - BELOW subtitles
                    if (player != null) {
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.8f
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
                        )
                        
                        val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                        val currentSpeed = player.playbackParameters.speed
                        val currentSpeedIndex = speedOptions.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }.takeIf { it >= 0 } ?: 3
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(speedOptions.size) { index ->
                                val speed = speedOptions[index]
                                val speedText = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x"
                                
                                ListItem(
                                    selected = index == currentSpeedIndex,
                                    onClick = {
                                        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                                        Log.d("ExoPlayerSettingsMenu", "Changed playback speed to ${speed}x")
                                    },
                                    headlineContent = {
                                        Text(
                                            text = speedText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NextUpOverlay(
    nextEpisode: JellyfinItem,
    countdown: Int,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.0f)), // Transparent background
        contentAlignment = Alignment.BottomEnd
    ) {
        androidx.tv.material3.Surface(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.8f),
                contentColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                // Episode info
                nextEpisode.SeriesName?.let { seriesName ->
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                // Episode number and name
                val episodeInfo = buildString {
                    nextEpisode.ParentIndexNumber?.let { seasonNum ->
                        append("S$seasonNum")
                    }
                    nextEpisode.IndexNumber?.let { episodeNum ->
                        if (isNotEmpty()) append(" • ")
                        append("E$episodeNum")
                    }
                    if (isNotEmpty() && nextEpisode.Name.isNotEmpty()) {
                        append(" — ")
                    }
                    append(nextEpisode.Name)
                }
                
                Text(
                    text = episodeInfo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 2
                )
                
                // Countdown
                Text(
                    text = "Autoplay in $countdown…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Normalizes ISO 639-1 (2-letter) and ISO 639-2/T (3-letter) language codes to a common format.
 * This helps match subtitles when ExoPlayer uses different language code standards than Jellyfin.
 * 
 * Examples:
 * - "es" -> "es"
 * - "spa" -> "es"
 * - "en" -> "en"
 * - "eng" -> "en"
 * - "fr" -> "fr"
 * - "fra" -> "fr"
 * - "tur" -> "tr"
 * - "chi" -> "zh"
 */
private fun normalizeLanguageCode(languageCode: String?): String? {
    if (languageCode == null) return null
    
    // Map common ISO 639-2/T (3-letter) codes to ISO 639-1 (2-letter) codes
    val iso639Map = mapOf(
        "eng" to "en",
        "spa" to "es",
        "fra" to "fr",
        "deu" to "de",
        "ita" to "it",
        "por" to "pt",
        "rus" to "ru",
        "jpn" to "ja",
        "chi" to "zh",
        "kor" to "ko",
        "ara" to "ar",
        "tur" to "tr",
        "pol" to "pl",
        "nld" to "nl",
        "swe" to "sv",
        "dan" to "da",
        "fin" to "fi",
        "nor" to "no",
        "ces" to "cs",
        "hun" to "hu",
        "tha" to "th",
        "vie" to "vi",
        "ind" to "id",
        "heb" to "he",
        "ukr" to "uk",
        "ron" to "ro",
        "ell" to "el",
        "cat" to "ca",
        "hrv" to "hr",
        "slk" to "sk",
        "bul" to "bg",
        "srp" to "sr",
        "slv" to "sl",
        "lit" to "lt",
        "lav" to "lv",
        "est" to "et",
        "isl" to "is",
        "msa" to "ms",
        "fil" to "tl",
        "hin" to "hi",
        "ben" to "bn",
        "tam" to "ta",
        "tel" to "te",
        "mar" to "mr",
        "urd" to "ur",
        "fas" to "fa",
        "swa" to "sw"
    )
    
    val lowerCode = languageCode.lowercase()
    
    // If it's a 3-letter code and we have a mapping, return the 2-letter equivalent
    if (lowerCode.length == 3 && iso639Map.containsKey(lowerCode)) {
        return iso639Map[lowerCode]
    }
    
    // If it's already 2 letters, return as-is
    if (lowerCode.length == 2) {
        return lowerCode
    }
    
    // Fallback: return first 2 characters
    return lowerCode.take(2)
}

