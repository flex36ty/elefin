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
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    // Create player with LoadControl configured based on settings
    // If minimal buffer for 4K is enabled, use minimal buffering (will apply to all content when enabled)
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
            
            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build()
                .also { Log.d("JellyfinPlayer", "Created player with minimal buffering (for 4K content): start after 1s, min 1.5s, max 2s") }
        } else {
        ExoPlayer.Builder(context).build()
                .also { Log.d("JellyfinPlayer", "Created player with default buffering") }
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
    var is4KContent by remember { mutableStateOf(false) } // Track if current content is 4K

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
                    
                    if (isHDROrHighQuality) {
                        Log.d("JellyfinPlayer", "HDR/high-quality video detected (${videoStream?.Codec}, ${width}x${height}) - requesting full quality")
                    }
                    if (isUnsupportedAudio) {
                        Log.d("JellyfinPlayer", "Unsupported audio codec detected (${audioStream?.Codec}) - will transcode audio while preserving video quality")
                    }

                    // Generate video playback URL WITHOUT subtitle stream index
                    // We'll load subtitles separately via MediaItem.SubtitleConfiguration for better ExoPlayer compatibility
                    val videoUrl = apiService.getVideoPlaybackUrl(
                        itemId = item.Id,
                        mediaSourceId = mediaSourceId,
                        subtitleStreamIndex = null, // Don't include in URL, load separately
                        preserveQuality = isHDROrHighQuality, // Preserve quality for HDR videos
                        transcodeAudio = isUnsupportedAudio // Transcode unsupported audio codecs
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

                    // Store mediaUrl in local variable for smart cast
                    val currentMediaUrl = mediaUrl ?: return@withContext
                    
                    // Detect if URL is HLS (ends with .m3u8 or contains master.m3u8)
                    val isHlsUrl = currentMediaUrl.contains(".m3u8", ignoreCase = true)
                    
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
                                .setUri(Uri.parse(currentMediaUrl))
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
                            MediaItem.fromUri(Uri.parse(currentMediaUrl))
                        }
                    } else {
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
                            
                            // Check if user selected a subtitle via ExoPlayer's controller
                            // When ExoPlayer's controller selects a subtitle, ExoPlayer already handles it
                            // We just need to save the preference - NO RELOAD needed
                            val selectedTextTrackGroup = textTrackGroups.firstOrNull { it.isSelected }
                            if (selectedTextTrackGroup != null && itemDetails != null) {
                                // User selected a subtitle track via ExoPlayer's controller
                                // Find the corresponding Jellyfin subtitle stream index
                                val selectedFormat = selectedTextTrackGroup.mediaTrackGroup.getFormat(0)
                                val selectedLanguage = selectedFormat.language
                                
                                // Try to match by language or MIME type to find the Jellyfin subtitle index
                                val matchingSubtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                    ?.filter { it.Type == "Subtitle" }
                                    ?.firstOrNull { stream ->
                                        // Match by language
                                        stream.Language?.let { lang ->
                                            lang.equals(selectedLanguage, ignoreCase = true) ||
                                            lang.startsWith(selectedLanguage?.take(2) ?: "", ignoreCase = true) ||
                                            selectedLanguage?.startsWith(lang.take(2), ignoreCase = true) == true
                                        } == true
                                    }
                                
                                val newSubtitleIndex = matchingSubtitleStream?.Index
                                
                                if (newSubtitleIndex != null && newSubtitleIndex != lastSelectedSubtitleIndex) {
                                    Log.d("JellyfinPlayer", "Subtitle selected via ExoPlayer controller: index=$newSubtitleIndex, language=${matchingSubtitleStream.Language}")
                                    lastSelectedSubtitleIndex = newSubtitleIndex
                                    currentSubtitleIndex = newSubtitleIndex
                                    
                                    // Save preference - ExoPlayer already has the subtitle track active, no reload needed
                                    settings.setSubtitlePreference(item.Id, newSubtitleIndex)
                                }
                            } else if (selectedTextTrackGroup == null && lastSelectedSubtitleIndex != null && textTrackGroups.isNotEmpty()) {
                                // User deselected subtitles via ExoPlayer's controller
                                // ExoPlayer already disabled the subtitle - just save preference, no reload needed
                                Log.d("JellyfinPlayer", "Subtitle deselected via ExoPlayer controller")
                                lastSelectedSubtitleIndex = null
                                currentSubtitleIndex = null
                                
                                // Save preference
                                settings.setSubtitlePreference(item.Id, null)
                            }
                            
                            // Ensure subtitle button is visible in controller when tracks are available
                            playerViewRef.value?.let { view ->
                                view.post {
                                    val controller = view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                    controller?.let { controlView ->
                                        // The subtitle button should automatically appear when text tracks are available
                                        // Force a refresh of the controller to ensure button visibility
                                        controlView.invalidate()
                                        Log.d("JellyfinPlayer", "Controller invalidated to refresh subtitle button visibility")
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
                                    currentSubtitleIndex = subtitleStreamIndex
                                    lastSelectedSubtitleIndex = subtitleStreamIndex
                                    Log.d("JellyfinPlayer", "✅ Selected subtitle track: language=${selectedFormat.language}, id=${selectedFormat.id}, index=$subtitleStreamIndex")
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error selecting subtitle track: ${e.message}", e)
                                }
                            } else if (subtitleStreamIndex == null && currentSubtitleIndex == null && textTrackGroups.isNotEmpty() && selectedTextTrackGroup == null) {
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
                                // Always show controller when Enter/OK is pressed
                                // This ensures it comes back after being hidden
                                playerView.showController()
                                Log.d("ExoPlayer", "Enter/OK pressed - showing controller")
                            }
                            // Don't consume the event - let it pass through so buttons can still receive clicks
                            false
                        }
                        Key.DirectionLeft -> {
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
                        Key.DirectionRight -> {
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
                                        
                                        // Function to apply purple focus styling
                                        fun applyPurpleFocus(view: android.view.View) {
                                            view.setOnFocusChangeListener { v, hasFocus ->
                                                if (hasFocus) {
                                                    v.setBackgroundColor(transparentPurple)
                                                } else {
                                                    v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                            // Explicitly show subtitle button
                            view.setShowSubtitleButton(true)
                            // Hide next/previous track buttons whenever controller is shown
                            view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                            view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
                            
                            // Ensure subtitle button is visible when tracks are available
                            val controller = view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                            controller?.invalidate() // Refresh controller to show subtitle button if tracks are available
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
        
        // Settings menu with subtitle picker
        if (showSettingsMenu) {
            ExoPlayerSettingsMenu(
                item = itemDetails ?: item,
                apiService = apiService,
                currentSubtitleIndex = currentSubtitleIndex,
                onDismiss = { showSettingsMenu = false },
                player = player,
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

@Composable
fun ExoPlayerSettingsMenu(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    currentSubtitleIndex: Int?,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int?) -> Unit,
    player: ExoPlayer? = null
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }
    
    // Fetch full item details to get MediaSources with subtitle streams
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
                    
                    // Subtitle section - ABOVE speed picker
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
                                        player.playbackParameters = PlaybackParameters(speed)
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

