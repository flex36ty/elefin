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
import androidx.media3.common.ParserException
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
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

@UnstableApi
@Composable
fun JellyfinVideoPlayerScreen(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    onBack: () -> Unit = {},
    resumePositionMs: Long = 0L,
    subtitleStreamIndex: Int? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember {
        ExoPlayer.Builder(context).build()
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

                    // Generate video playback URL WITHOUT subtitle stream index
                    // We'll load subtitles separately via MediaItem.SubtitleConfiguration for better ExoPlayer compatibility
                    val videoUrl = apiService.getVideoPlaybackUrl(
                        itemId = item.Id,
                        mediaSourceId = mediaSourceId,
                        subtitleStreamIndex = null // Don't include in URL, load separately
                    )
                    mediaUrl = videoUrl
                    Log.d("JellyfinPlayer", "Video URL: $videoUrl")
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

                    // Create MediaItem with subtitle configuration if subtitle is requested
                    // MediaItem.SubtitleConfiguration is the proper Media3 way to load external subtitles
                    val mediaItem = if (subtitleStreamIndex != null && itemDetails != null) {
                        try {
                            val mediaSourceIdForSubtitle = itemDetails?.MediaSources?.firstOrNull()?.Id ?: item.Id
                            val subtitleUrl = apiService.getSubtitleUrl(item.Id, mediaSourceIdForSubtitle, subtitleStreamIndex!!)
                            
                            // Get subtitle stream info for language detection
                            val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                            val subtitleLanguage = subtitleStream?.Language
                            
                            // Jellyfin serves subtitles as VTT format
                            val subtitleMimeType = MimeTypes.TEXT_VTT
                            
                            Log.d("JellyfinPlayer", "Adding external subtitle to MediaItem: $subtitleUrl (format: $subtitleMimeType, language: $subtitleLanguage)")
                            
                            // Create MediaItem with subtitle configuration
                            // This loads the subtitle separately from the video stream
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
                    
                    // Create media source from MediaItem
                    val mediaSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)

                    // Set media source
                    player.setMediaSource(mediaSource)

                    // Store media source for potential retry
                    currentMediaSource = mediaSource

                    // Handle player lifecycle
                    player.addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("JellyfinPlayer", "Player error: ${error.message}", error)
                            
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
                            // When tracks are available, select subtitle track if specified
                            // Handle subtitle selection or disabling
                            val textTrackGroups = tracks.groups.filter { group ->
                                group.type == androidx.media3.common.C.TRACK_TYPE_TEXT &&
                                group.isSupported
                            }
                            
                            if (subtitleStreamIndex != null && itemDetails != null && textTrackGroups.isNotEmpty()) {
                                // User selected a subtitle track - select it
                                try {
                                    Log.d("JellyfinPlayer", "Found ${textTrackGroups.size} supported subtitle track group(s)")
                                    
                                    // Get the subtitle stream info from Jellyfin to match by language
                                    val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                        ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                                    val subtitleLanguage = subtitleStream?.Language
                                    
                                    // Try to match by language first, then fall back to index
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
                                            // If no language match, log available languages for debugging
                                            Log.d("JellyfinPlayer", "No language match found. Available track languages: ${textTrackGroups.map { it.mediaTrackGroup.getFormat(0).language }}")
                                            Log.d("JellyfinPlayer", "Looking for language: $subtitleLanguage")
                                            // Fall back to index-based selection
                                            val indexToSelect = subtitleStreamIndex!!.coerceIn(0, textTrackGroups.size - 1)
                                            textTrackGroups[indexToSelect]
                                        }
                                    } else {
                                        // No language info, use index-based selection
                                        val indexToSelect = subtitleStreamIndex!!.coerceIn(0, textTrackGroups.size - 1)
                                        textTrackGroups[indexToSelect]
                                    }
                                    
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
                                    Log.d("JellyfinPlayer", "✅ Selected subtitle track: language=${selectedFormat.language}, id=${selectedFormat.id}, index=$subtitleStreamIndex")
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error selecting subtitle track: ${e.message}", e)
                                }
                            } else if (subtitleStreamIndex == null) {
                                // User selected "None" - explicitly clear any subtitle track selection
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
                        }
                        
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    Log.d("JellyfinPlayer", "Player ready")
                                    // Seek to resume position only once, when player first becomes ready
                                    if (resumePositionMs > 0 && !hasSeekedToResume) {
                                        player.seekTo(resumePositionMs)
                                        hasSeekedToResume = true
                                        Log.d("JellyfinPlayer", "Seeked to resume position: ${resumePositionMs}ms")
                                    }
                                }
                                Player.STATE_BUFFERING -> {
                                    Log.d("JellyfinPlayer", "Player buffering")
                                }
                                Player.STATE_ENDED -> {
                                    Log.d("JellyfinPlayer", "Playback ended")
                                    progressReportingJob?.cancel()
                                    progressReportingJob = null
                                    // Only mark as watched if video was actually completed (watched until near the end)
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
                                            
                                            // Check if video was actually completed (watched at least 90% or within last 5 seconds)
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
                                                    Log.d("JellyfinPlayer", "Marked item as watched (completed ${(currentPositionMs * 100 / durationMs).toInt()}%)")
                                                } else {
                                                    Log.d("JellyfinPlayer", "Playback stopped early (${(currentPositionMs * 100 / durationMs).toInt()}%), not marking as watched")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("JellyfinPlayer", "Error handling playback end", e)
                                        }
                                    }
                                    onBack()
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
        if (itemDetails?.Type == "Episode" && itemDetails?.SeriesId != null && apiService != null) {
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
                                // Always show controller when Enter/OK is pressed
                                // This ensures it comes back after being hidden
                                playerView.showController()
                                Log.d("ExoPlayer", "Enter/OK pressed - showing controller")
                            }
                            // Don't consume the event - let it pass through so buttons can still receive clicks
                            false
                        }
                        Key.DirectionLeft -> {
                            // Seek backward 15 seconds (don't show controller)
                            scope.launch(Dispatchers.Main) {
                                val currentPos = player.currentPosition
                                val seekTo = (currentPos - 15000).coerceAtLeast(0)
                                player.seekTo(seekTo)
                                Log.d("ExoPlayer", "Left pressed - seeking backward to ${seekTo}ms")
                            }
                            true // Consume event
                        }
                        Key.DirectionRight -> {
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
                                // Make sure controller is focusable for TV
                                controllerHideOnTouch = false // On TV, don't hide on touch
                                // Make PlayerView focusable so it can receive key events
                                isFocusable = true
                                isFocusableInTouchMode = false // Not needed for TV
                                playerViewRef.value = this
                                
                                // Hide next/previous track buttons
                                post {
                                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
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
                            // Hide next/previous track buttons whenever controller is shown
                            view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                            view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
                        }
                    )
                    
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
    }
}

