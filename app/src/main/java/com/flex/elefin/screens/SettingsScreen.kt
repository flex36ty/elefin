package com.flex.elefin.screens

import coil.annotation.ExperimentalCoilApi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Person
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.flex.elefin.jellyfin.AppSettings
import coil.ImageLoader
import coil.imageLoader
import coil.disk.DiskCache
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import android.widget.Toast
import com.flex.elefin.updater.GitHubRelease
import com.flex.elefin.updater.UpdateService
import android.content.pm.PackageManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.flex.elefin.jellyfin.JellyfinConfig
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

// Settings categories
enum class SettingsCategory(val title: String, val icon: ImageVector) {
    PLAYBACK("Playback", Icons.Default.PlayArrow),
    VIDEO("Video", Icons.Default.Videocam),
    SUBTITLES("Audio & Subtitles", Icons.Default.Subtitles),
    APPEARANCE("Appearance", Icons.Default.Palette),
    PERFORMANCE("Performance", Icons.Default.Speed),
    LIBRARY("Library", Icons.Default.VideoLibrary),
    ADVANCED("Advanced", Icons.Default.Settings),
    UPDATES("Updates", Icons.Default.Update),
    JELLYSEERR("Jellyseerr (Discover Content)", Icons.Default.Videocam),
    ACCOUNT("Account", Icons.Default.Person)
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    
    // Selected category
    var selectedCategory by remember { mutableStateOf(SettingsCategory.PLAYBACK) }
    
    // All settings state
    var mpvEnabled by remember { mutableStateOf(settings.isMpvEnabled) }
    
    // MPV download state
    var isMpvInstalled by remember { mutableStateOf(false) }
    var isMpvDownloading by remember { mutableStateOf(false) }
    var mpvDownloadProgress by remember { mutableStateOf(0f) }
    var mpvInstallCheckTrigger by remember { mutableStateOf(0) }
    
    // Check if mpv-elefin is installed (re-checks when mpvInstallCheckTrigger changes)
    LaunchedEffect(mpvInstallCheckTrigger) {
        isMpvInstalled = try {
            context.packageManager.getPackageInfo("com.flex.mpvelefin", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    // Periodically check for MPV installation after download is triggered
    LaunchedEffect(isMpvDownloading) {
        if (!isMpvDownloading && mpvInstallCheckTrigger > 0) {
            // After download completes, periodically check if MPV was installed
            repeat(10) { // Check for up to ~30 seconds
                kotlinx.coroutines.delay(3000)
                val nowInstalled = try {
                    context.packageManager.getPackageInfo("com.flex.mpvelefin", 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                if (nowInstalled) {
                    isMpvInstalled = true
                    return@LaunchedEffect
                }
            }
        }
    }
    var debugOutlinesEnabled by remember { mutableStateOf(settings.showDebugOutlines) }
    var preloadLibraryImagesEnabled by remember { mutableStateOf(settings.preloadLibraryImages) }
    var cacheLibraryImagesEnabled by remember { mutableStateOf(settings.cacheLibraryImages) }
    var useGlideEnabled by remember { mutableStateOf(settings.useGlide) }
    var reducePosterResolutionEnabled by remember { mutableStateOf(settings.reducePosterResolution) }
    var animatedPlayButtonEnabled by remember { mutableStateOf(settings.useAnimatedPlayButton) }
    var use24HourTimeEnabled by remember { mutableStateOf(settings.use24HourTime) }
    var longPressDurationSeconds by remember { mutableStateOf(settings.longPressDurationSeconds) }
    var remoteThemingEnabled by remember { mutableStateOf(settings.remoteThemingEnabled) }
    var darkModeEnabled by remember { mutableStateOf(settings.darkModeEnabled) }
    var autoRefreshEnabled by remember { mutableStateOf(settings.autoRefreshEnabled) }
    var autoRefreshIntervalMinutes by remember { mutableStateOf(settings.autoRefreshIntervalMinutes) }
    var hideShowsWithZeroEpisodesEnabled by remember { mutableStateOf(settings.hideShowsWithZeroEpisodes) }
    var minimalBuffer4KEnabled by remember { mutableStateOf(settings.minimalBuffer4K) }
    var transcodeAacToAc3Enabled by remember { mutableStateOf(settings.transcodeAacToAc3) }
    var useLogoForTitleEnabled by remember { mutableStateOf(settings.useLogoForTitle) }
    var autoplayNextEpisodeEnabled by remember { mutableStateOf(settings.autoplayNextEpisode) }
    var autoplayCountdownSeconds by remember { mutableStateOf(settings.autoplayCountdownSeconds) }
    var autoUpdateEnabled by remember { mutableStateOf(settings.autoUpdateEnabled) }
    var skipIntroEnabled by remember { mutableStateOf(settings.skipIntroEnabled) }
    var skipCreditsEnabled by remember { mutableStateOf(settings.skipCreditsEnabled) }
    
    // Server-side transcoding settings
    var serverTranscodingEnabled by remember { mutableStateOf(settings.serverTranscodingEnabled) }
    var transcodeAV1 by remember { mutableStateOf(settings.transcodeAV1) }
    var transcodeHEVC by remember { mutableStateOf(settings.transcodeHEVC) }
    var transcodeTargetCodec by remember { mutableStateOf(settings.transcodeTargetCodec) }
    var transcodeMaxBitrate by remember { mutableStateOf(settings.transcodeMaxBitrateMbps) }
    var autoTranscodeOnError by remember { mutableStateOf(settings.autoTranscodeOnError) }
    var fallbackToMpv by remember { mutableStateOf(settings.fallbackToMpv) }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<com.flex.elefin.updater.GitHubRelease?>(null) }
    var checkingForUpdates by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }

    // ExoPlayer Subtitle customization settings
    var exoSubtitleTextSize by remember { mutableStateOf(settings.exoSubtitleTextSize) }
    var exoSubtitleBgTransparent by remember { mutableStateOf(settings.exoSubtitleBgTransparent) }
    var showExoSubtitleColorDialog by remember { mutableStateOf(false) }
    var showExoSubtitleBgColorDialog by remember { mutableStateOf(false) }

    // Video Enhancement settings
    var useGLEnhancements by remember { mutableStateOf(settings.useGLEnhancements) }
    var enableFakeHDR by remember { mutableStateOf(settings.enableFakeHDR) }
    var enableSharpening by remember { mutableStateOf(settings.enableSharpening) }
    var hdrStrength by remember { mutableStateOf(settings.hdrStrength) }
    var sharpenStrength by remember { mutableStateOf(settings.sharpenStrength) }
    var enableFrameBlending by remember { mutableStateOf(settings.enableFrameBlending) }
    var frameBlendStrength by remember { mutableStateOf(settings.frameBlendStrength) }
    
    // UI Performance settings
    var disableUIAnimations by remember { mutableStateOf(settings.disableUIAnimations) }
    var useSimpleCards by remember { mutableStateOf(settings.useSimpleCards) }
    var useGoogleTvCards by remember { mutableStateOf(settings.useGoogleTvCards) }
    var lowPowerMode by remember { mutableStateOf(settings.lowPowerMode) }
    var use4KBackgrounds by remember { mutableStateOf(settings.use4KBackgrounds) }
    
    // Logout confirmation
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    // Jellyseerr state variables
    var jellyseerrUrl by remember { mutableStateOf(settings.jellyseerrUrl) }
    var showJellyseerrUrlDialog by remember { mutableStateOf(false) }
    var jellyseerrAuthType by remember { mutableStateOf(settings.jellyseerrAuthType) }
    var jellyseerrApiKey by remember { mutableStateOf(settings.jellyseerrApiKey) }
    var jellyseerrUsername by remember { mutableStateOf(settings.jellyseerrUsername) }
    var jellyseerrSessionCookie by remember { mutableStateOf(settings.jellyseerrSessionCookie) }
    var showJellyseerrApiKeyDialog by remember { mutableStateOf(false) }
    var showJellyseerrLoginDialog by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var jellyseerrEnabled by remember { mutableStateOf(settings.jellyseerrEnabled) }
    var jellyseerrSearchEnabled by remember { mutableStateOf(settings.jellyseerrSearchEnabled) }

    // OpenSubtitles state variables
    var openSubtitlesApiKey by remember { mutableStateOf(settings.openSubtitlesApiKey) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var openSubtitlesUsername by remember { mutableStateOf(settings.openSubtitlesUsername) }
    var openSubtitlesPassword by remember { mutableStateOf(settings.openSubtitlesPassword) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showClearSubtitlesDialog by remember { mutableStateOf(false) }
    var downloadedSubtitlesCount by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Main content: Categories on left, Settings on right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Left column: Categories
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .padding(end = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsCategory.entries.forEach { category ->
                        CategoryItem(
                            category = category,
                            isSelected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
                
                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )
                
                // Right panel: Settings for selected category
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Category title
                    Text(
                        text = selectedCategory.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    when (selectedCategory) {
                        SettingsCategory.PLAYBACK -> {
                            // MPV Player Toggle
                            SettingToggle(
                                title = "Use MPV Player (Experimental)",
                                description = if (isMpvInstalled) {
                                    "Experimental. Uses mpv-elefin companion app for playback. Better codec support and HDR passthrough. (Installed ✓)"
                                } else {
                                    "Experimental. Requires mpv-elefin APK to be installed separately."
                                },
                                isEnabled = mpvEnabled,
                                onToggle = {
                                    mpvEnabled = !mpvEnabled
                                    settings.isMpvEnabled = mpvEnabled
                                }
                            )
                            
                            // Download MPV Button
                            if (!isMpvInstalled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                if (isMpvDownloading) {
                                    // Show download progress
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Downloading mpv-elefin... ${(mpvDownloadProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { mpvDownloadProgress },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else {
                                    // Download button
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isMpvDownloading = true
                                                mpvDownloadProgress = 0f
                                                
                                                try {
                                                    val mpvApkUrl = "https://github.com/flex36ty/elefin/releases/download/1.1.11/mpv-universal-release.apk"
                                                    val apkFile = File(context.cacheDir, "mpv-elefin.apk")
                                                    
                                                    // Download the APK
                                                    withContext(Dispatchers.IO) {
                                                        val url = java.net.URL(mpvApkUrl)
                                                        val connection = url.openConnection() as java.net.HttpURLConnection
                                                        connection.instanceFollowRedirects = true
                                                        connection.connect()
                                                        
                                                        val fileLength = connection.contentLength.toLong()
                                                        
                                                        connection.inputStream.use { input ->
                                                            apkFile.outputStream().use { output ->
                                                                val buffer = ByteArray(8192)
                                                                var bytesRead: Int
                                                                var totalBytesRead = 0L
                                                                
                                                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                                                    output.write(buffer, 0, bytesRead)
                                                                    totalBytesRead += bytesRead
                                                                    if (fileLength > 0) {
                                                                        mpvDownloadProgress = totalBytesRead.toFloat() / fileLength
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Install the APK
                                                    val apkUri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        apkFile
                                                    )
                                                    
                                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(installIntent)
                                                    
                                                    Toast.makeText(context, "Installing mpv-elefin...", Toast.LENGTH_SHORT).show()
                                                    
                                                    // Trigger re-check of MPV installation status
                                                    mpvInstallCheckTrigger++
                                                    
                                                } catch (e: Exception) {
                                                    android.util.Log.e("Settings", "Failed to download mpv-elefin", e)
                                                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isMpvDownloading = false
                                                    mpvDownloadProgress = 0f
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        colors = ButtonDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download & Install mpv-elefin")
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Skip Intro
                            SettingToggle(
                                title = "Skip Intro",
                                description = "Show skip button during episode intros (requires Intro Skipper plugin)",
                                isEnabled = skipIntroEnabled,
                                onToggle = {
                                    skipIntroEnabled = !skipIntroEnabled
                                    settings.skipIntroEnabled = skipIntroEnabled
                                }
                            )
                            
                            // Skip Credits
                            SettingToggle(
                                title = "Skip Credits",
                                description = "Show skip button during episode credits/outro",
                                isEnabled = skipCreditsEnabled,
                                onToggle = {
                                    skipCreditsEnabled = !skipCreditsEnabled
                                    settings.skipCreditsEnabled = skipCreditsEnabled
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Server-Side Transcoding Section Header
                            Text(
                                text = "Server-Side Transcoding",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            
                            // Auto-transcode on playback error
                            SettingToggle(
                                title = "Auto-Transcode on Error",
                                description = "Automatically retry with server transcoding when direct play fails",
                                isEnabled = autoTranscodeOnError,
                                onToggle = {
                                    autoTranscodeOnError = !autoTranscodeOnError
                                    settings.autoTranscodeOnError = autoTranscodeOnError
                                }
                            )
                            
                            // Fallback to MPV player
                            SettingToggle(
                                title = "Fallback to MPV Player",
                                description = if (isMpvInstalled) {
                                    "Use MPV when ExoPlayer fails and transcoding is disabled (MPV installed ✓)"
                                } else {
                                    "Use MPV when ExoPlayer fails (requires mpv-elefin to be installed)"
                                },
                                isEnabled = fallbackToMpv,
                                onToggle = {
                                    fallbackToMpv = !fallbackToMpv
                                    settings.fallbackToMpv = fallbackToMpv
                                }
                            )
                            
                            // Server Transcoding Master Toggle
                            SettingToggle(
                                title = "Always Transcode",
                                description = "Always request server transcoding for selected codecs (AV1, HEVC)",
                                isEnabled = serverTranscodingEnabled,
                                onToggle = {
                                    serverTranscodingEnabled = !serverTranscodingEnabled
                                    settings.serverTranscodingEnabled = serverTranscodingEnabled
                                }
                            )
                            
                            if (serverTranscodingEnabled) {
                                // Transcode AV1
                                SettingToggle(
                                    title = "Transcode AV1",
                                    description = "Request transcoding for AV1 video (recommended for Shield TV)",
                                    isEnabled = transcodeAV1,
                                    onToggle = {
                                        transcodeAV1 = !transcodeAV1
                                        settings.transcodeAV1 = transcodeAV1
                                    }
                                )
                                
                                // Transcode HEVC
                                SettingToggle(
                                    title = "Transcode HEVC/H.265",
                                    description = "Request transcoding for HEVC video (only if device doesn't support it)",
                                    isEnabled = transcodeHEVC,
                                    onToggle = {
                                        transcodeHEVC = !transcodeHEVC
                                        settings.transcodeHEVC = transcodeHEVC
                                    }
                                )
                                
                                // Target Codec
                                SettingCycle(
                                    title = "Target Codec",
                                    description = "Transcode to: ${transcodeTargetCodec.uppercase()}",
                                    currentValue = transcodeTargetCodec.uppercase(),
                                    onCycle = {
                                        transcodeTargetCodec = if (transcodeTargetCodec == "h264") "hevc" else "h264"
                                        settings.transcodeTargetCodec = transcodeTargetCodec
                                    }
                                )
                                
                                // Max Bitrate
                                SettingSlider(
                                    title = "Max Video Bitrate",
                                    description = "${transcodeMaxBitrate} Mbps (higher = better quality)",
                                    onDecrease = {
                                        transcodeMaxBitrate = (transcodeMaxBitrate - 5).coerceAtLeast(5)
                                        settings.transcodeMaxBitrateMbps = transcodeMaxBitrate
                                    },
                                    onIncrease = {
                                        transcodeMaxBitrate = (transcodeMaxBitrate + 5).coerceAtMost(120)
                                        settings.transcodeMaxBitrateMbps = transcodeMaxBitrate
                                    },
                                    canDecrease = transcodeMaxBitrate > 5,
                                    canIncrease = transcodeMaxBitrate < 120
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Autoplay Next Episode
                            SettingToggle(
                                title = "Autoplay Next Episode",
                                description = "Automatically play the next episode when the current one ends",
                                isEnabled = autoplayNextEpisodeEnabled,
                                onToggle = {
                                    autoplayNextEpisodeEnabled = !autoplayNextEpisodeEnabled
                                    settings.autoplayNextEpisode = autoplayNextEpisodeEnabled
                                }
                            )
                            
                            // Autoplay Countdown Duration
                            if (autoplayNextEpisodeEnabled) {
                                SettingCycle(
                                    title = "Autoplay Countdown",
                                    description = "Time before episode ends to show countdown (${autoplayCountdownSeconds}s)",
                                    currentValue = "${autoplayCountdownSeconds}s",
                                    onCycle = {
                                        autoplayCountdownSeconds = when (autoplayCountdownSeconds) {
                                            10 -> 15
                                            15 -> 30
                                            30 -> 45
                                            45 -> 60
                                            60 -> 90
                                            90 -> 120
                                            120 -> 10
                                            else -> 10
                                        }
                                        settings.autoplayCountdownSeconds = autoplayCountdownSeconds
                                    }
                                )
                            }
                        }
                        
                        SettingsCategory.VIDEO -> {
                            // Enable GL Enhancements
                            SettingToggle(
                                title = "GL Video Processing",
                                description = "Use OpenGL for advanced video effects (HDR simulation, sharpening)",
                                isEnabled = useGLEnhancements,
                                onToggle = {
                                    useGLEnhancements = !useGLEnhancements
                                    settings.useGLEnhancements = useGLEnhancements
                                    if (!useGLEnhancements) {
                                        enableFakeHDR = false
                                        enableSharpening = false
                                        enableFrameBlending = false
                                        settings.enableFakeHDR = false
                                        settings.enableSharpening = false
                                        settings.enableFrameBlending = false
                                    }
                                }
                            )
                            
                            if (useGLEnhancements) {
                                // Fake HDR
                                SettingToggle(
                                    title = "Fake HDR",
                                    description = "Simulate HDR with tone mapping and brightness boost",
                                    isEnabled = enableFakeHDR,
                                    onToggle = {
                                        enableFakeHDR = !enableFakeHDR
                                        settings.enableFakeHDR = enableFakeHDR
                                    }
                                )
                                
                                if (enableFakeHDR) {
                                    SettingSlider(
                                        title = "HDR Strength",
                                        description = "Strength: %.1f (range: 1.0-2.0)".format(hdrStrength),
                                        onDecrease = {
                                            hdrStrength = (hdrStrength - 0.1f).coerceAtLeast(1.0f)
                                            settings.hdrStrength = hdrStrength
                                        },
                                        onIncrease = {
                                            hdrStrength = (hdrStrength + 0.1f).coerceAtMost(2.0f)
                                            settings.hdrStrength = hdrStrength
                                        },
                                        canDecrease = hdrStrength > 1.0f,
                                        canIncrease = hdrStrength < 2.0f
                                    )
                                }
                                
                                // Sharpening
                                SettingToggle(
                                    title = "Sharpening",
                                    description = "Enhance image sharpness using edge detection",
                                    isEnabled = enableSharpening,
                                    onToggle = {
                                        enableSharpening = !enableSharpening
                                        settings.enableSharpening = enableSharpening
                                    }
                                )
                                
                                if (enableSharpening) {
                                    SettingSlider(
                                        title = "Sharpening Strength",
                                        description = "Strength: %.1f (range: 0.0-1.0)".format(sharpenStrength),
                                        onDecrease = {
                                            sharpenStrength = (sharpenStrength - 0.1f).coerceAtLeast(0.0f)
                                            settings.sharpenStrength = sharpenStrength
                                        },
                                        onIncrease = {
                                            sharpenStrength = (sharpenStrength + 0.1f).coerceAtMost(1.0f)
                                            settings.sharpenStrength = sharpenStrength
                                        },
                                        canDecrease = sharpenStrength > 0.0f,
                                        canIncrease = sharpenStrength < 1.0f
                                    )
                                }
                                
                                // Frame Blending
                                SettingToggle(
                                    title = "Frame Blending",
                                    description = "Simulates smooth motion by blending frames (soap opera effect)",
                                    isEnabled = enableFrameBlending,
                                    onToggle = {
                                        enableFrameBlending = !enableFrameBlending
                                        settings.enableFrameBlending = enableFrameBlending
                                    }
                                )
                                
                                if (enableFrameBlending) {
                                    SettingSlider(
                                        title = "Blend Strength",
                                        description = "Strength: %.1f (range: 0.0-1.0)".format(frameBlendStrength),
                                        onDecrease = {
                                            frameBlendStrength = (frameBlendStrength - 0.1f).coerceAtLeast(0.0f)
                                            settings.frameBlendStrength = frameBlendStrength
                                        },
                                        onIncrease = {
                                            frameBlendStrength = (frameBlendStrength + 0.1f).coerceAtMost(1.0f)
                                            settings.frameBlendStrength = frameBlendStrength
                                        },
                                        canDecrease = frameBlendStrength > 0.0f,
                                        canIncrease = frameBlendStrength < 1.0f
                                    )
                                }
                                
                            }
                        }
                        
                        SettingsCategory.SUBTITLES -> {
                            // ExoPlayer Subtitle Text Size
                            SettingSlider(
                                title = "Subtitle Text Size",
                                description = "Size: $exoSubtitleTextSize (range: 20-100)",
                                onDecrease = {
                                    if (exoSubtitleTextSize > 20) {
                                        exoSubtitleTextSize -= 5
                                        settings.exoSubtitleTextSize = exoSubtitleTextSize
                                    }
                                },
                                onIncrease = {
                                    if (exoSubtitleTextSize < 100) {
                                        exoSubtitleTextSize += 5
                                        settings.exoSubtitleTextSize = exoSubtitleTextSize
                                    }
                                },
                                canDecrease = exoSubtitleTextSize > 20,
                                canIncrease = exoSubtitleTextSize < 100
                            )
                            
                            // Subtitle Text Color
                            SettingButton(
                                title = "Subtitle Text Color",
                                description = "Choose subtitle text color",
                                buttonText = "Choose Color",
                                onClick = { showExoSubtitleColorDialog = true }
                            )
                            
                            // Subtitle Background Transparency
                            SettingToggle(
                                title = "Transparent Subtitle Background",
                                description = "Make subtitle background transparent or opaque",
                                isEnabled = exoSubtitleBgTransparent,
                                onToggle = {
                                    exoSubtitleBgTransparent = !exoSubtitleBgTransparent
                                    settings.exoSubtitleBgTransparent = exoSubtitleBgTransparent
                                },
                                enabledText = "Transparent",
                                disabledText = "Opaque"
                            )
                            
                            // Subtitle Background Color
                            if (!exoSubtitleBgTransparent) {
                                SettingButton(
                                    title = "Subtitle Background Color",
                                    description = "Choose subtitle background color",
                                    buttonText = "Choose Color",
                                    onClick = { showExoSubtitleBgColorDialog = true }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // OpenSubtitles API Key
                            SettingButton(
                                title = "OpenSubtitles API Key",
                                description = if (openSubtitlesApiKey.isNotBlank()) 
                                    "API key configured ✓" 
                                else 
                                    "Required for subtitle downloads. Get free key at opensubtitles.com",
                                buttonText = if (openSubtitlesApiKey.isNotBlank()) "Change" else "Set Key",
                                onClick = { showApiKeyDialog = true }
                            )
                            
                            if (showApiKeyDialog) {
                                var apiKeyInput by remember { mutableStateOf(openSubtitlesApiKey) }
                                AlertDialog(
                                    onDismissRequest = { showApiKeyDialog = false },
                                    title = { Text("OpenSubtitles API Key") },
                                    text = {
                                        Column {
                                            Text(
                                                "Get your free API key at:\nhttps://www.opensubtitles.com/en/consumers\n\nFree tier: 100 downloads/day",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(bottom = 16.dp)
                                            )
                                            OutlinedTextField(
                                                value = apiKeyInput,
                                                onValueChange = { apiKeyInput = it },
                                                label = { Text("API Key") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                openSubtitlesApiKey = apiKeyInput
                                                settings.openSubtitlesApiKey = apiKeyInput
                                                showApiKeyDialog = false
                                            }
                                        ) {
                                            Text("Save")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showApiKeyDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                            
                            SettingButton(
                                title = "OpenSubtitles Login",
                                description = if (openSubtitlesUsername.isNotBlank()) 
                                    "Logged in as: $openSubtitlesUsername ✓" 
                                else 
                                    "Required for downloading subtitles",
                                buttonText = if (openSubtitlesUsername.isNotBlank()) "Change" else "Login",
                                onClick = { showLoginDialog = true }
                            )
                            
                            if (showLoginDialog) {
                                var usernameInput by remember { mutableStateOf(openSubtitlesUsername) }
                                var passwordInput by remember { mutableStateOf(openSubtitlesPassword) }
                                AlertDialog(
                                    onDismissRequest = { showLoginDialog = false },
                                    title = { Text("OpenSubtitles Login") },
                                    text = {
                                        Column {
                                            Text(
                                                "Enter your OpenSubtitles.com account credentials.\nCreate a free account at opensubtitles.com if needed.",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(bottom = 16.dp)
                                            )
                                            OutlinedTextField(
                                                value = usernameInput,
                                                onValueChange = { usernameInput = it },
                                                label = { Text("Username") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = passwordInput,
                                                onValueChange = { passwordInput = it },
                                                label = { Text("Password") },
                                                singleLine = true,
                                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                openSubtitlesUsername = usernameInput
                                                openSubtitlesPassword = passwordInput
                                                settings.openSubtitlesUsername = usernameInput
                                                settings.openSubtitlesPassword = passwordInput
                                                showLoginDialog = false
                                            }
                                        ) {
                                            Text("Save")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showLoginDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                            
                            // Clear Downloaded Subtitles
                            // Count downloaded subtitles on first composition
                            LaunchedEffect(Unit) {
                                val subtitlesDir = java.io.File(context.filesDir, "downloaded_subtitles")
                                downloadedSubtitlesCount = if (subtitlesDir.exists()) {
                                    subtitlesDir.walkTopDown()
                                        .filter { it.isFile && it.extension in listOf("srt", "vtt", "ass", "ssa", "sub") }
                                        .count()
                                } else 0
                            }
                            
                            SettingButton(
                                title = "Clear Downloaded Subtitles",
                                description = if (downloadedSubtitlesCount > 0) 
                                    "$downloadedSubtitlesCount subtitle file(s) stored locally" 
                                else 
                                    "No downloaded subtitles",
                                buttonText = "Clear",
                                onClick = { showClearSubtitlesDialog = true }
                            )
                            
                            if (showClearSubtitlesDialog) {
                                AlertDialog(
                                    onDismissRequest = { showClearSubtitlesDialog = false },
                                    title = { Text("Clear Downloaded Subtitles?") },
                                    text = {
                                        Text(
                                            "This will delete all $downloadedSubtitlesCount downloaded subtitle file(s) from OpenSubtitles.\n\nYou can always download them again.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                // Delete all downloaded subtitles
                                                val subtitlesDir = java.io.File(context.filesDir, "downloaded_subtitles")
                                                if (subtitlesDir.exists()) {
                                                    subtitlesDir.deleteRecursively()
                                                    android.util.Log.d("Settings", "Cleared all downloaded subtitles")
                                                }
                                                downloadedSubtitlesCount = 0
                                                showClearSubtitlesDialog = false
                                                
                                                // Show toast
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Downloaded subtitles cleared",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        ) {
                                            Text("Clear", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showClearSubtitlesDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Transcode AAC to AC3
                            SettingToggle(
                                title = "Transcode AAC to AC3",
                                description = "Transcode all AAC audio to AC3 (5.1 max). AC3 is universally supported.",
                                isEnabled = transcodeAacToAc3Enabled,
                                onToggle = {
                                    transcodeAacToAc3Enabled = !transcodeAacToAc3Enabled
                                    settings.transcodeAacToAc3 = transcodeAacToAc3Enabled
                                }
                            )
                        }
                        
                        SettingsCategory.JELLYSEERR -> {
                            // Jellyseerr URL
                            SettingButton(
                                title = "Jellyseerr URL",
                                description = if (jellyseerrUrl.isNotBlank()) 
                                    jellyseerrUrl
                                else 
                                    "Set your Jellyseerr/Overseerr server URL",
                                buttonText = if (jellyseerrUrl.isNotBlank()) "Change" else "Set URL",
                                onClick = { showJellyseerrUrlDialog = true }
                            )
                            
                            if (showJellyseerrUrlDialog) {
                                var urlInput by remember { mutableStateOf(jellyseerrUrl) }
                                Dialog(
                                    onDismissRequest = { showJellyseerrUrlDialog = false },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            modifier = Modifier.width(500.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = SurfaceDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(32.dp),
                                                verticalArrangement = Arrangement.spacedBy(24.dp)
                                            ) {
                                                Text(
                                                    text = "Jellyseerr URL",
                                                    style = MaterialTheme.typography.headlineSmall
                                                )
                                                
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = "Enter the full URL of your Jellyseerr instance (e.g., http://192.168.1.50:5055)",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                    
                                                    OutlinedTextField(
                                                         value = urlInput,
                                                         onValueChange = { urlInput = it },
                                                         label = { Text("URL") },
                                                         placeholder = { Text("http://ip:port") },
                                                         singleLine = true,
                                                         modifier = Modifier.fillMaxWidth(),
                                                         colors = TextFieldDefaults.colors(
                                                             focusedTextColor = Color.White,
                                                             unfocusedTextColor = Color.White,
                                                             focusedContainerColor = Color.Transparent,
                                                             unfocusedContainerColor = Color.Transparent,
                                                             cursorColor = MaterialTheme.colorScheme.primary,
                                                             focusedLabelColor = MaterialTheme.colorScheme.primary,
                                                             unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                                             focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                             unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                                                         )
                                                     )
                                                }
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    Button(
                                                        onClick = { showJellyseerrUrlDialog = false },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.colors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                        )
                                                    ) {
                                                        Text("Cancel")
                                                    }
                                                    
                                                    Button(
                                                        onClick = {
                                                            jellyseerrUrl = urlInput
                                                            settings.jellyseerrUrl = urlInput
                                                            showJellyseerrUrlDialog = false
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Save")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Authentication method toggle
                            SettingCycle(
                                title = "Authentication Method",
                                description = when (jellyseerrAuthType) {
                                    "api_key" -> "Using API Key (recommended for admin access)"
                                    "credentials" -> "Using Username/Password login"
                                    else -> "Select authentication method"
                                },
                                currentValue = when (jellyseerrAuthType) {
                                    "api_key" -> "API Key"
                                    "credentials" -> "Login"
                                    else -> "API Key"
                                },
                                onCycle = {
                                    jellyseerrAuthType = when (jellyseerrAuthType) {
                                        "api_key" -> "credentials"
                                        else -> "api_key"
                                    }
                                    settings.jellyseerrAuthType = jellyseerrAuthType
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Show appropriate authentication option based on type
                            if (jellyseerrAuthType == "api_key") {
                                // API Key authentication
                                SettingButton(
                                    title = "Jellyseerr API Key",
                                    description = if (jellyseerrApiKey.isNotBlank()) 
                                        "API key configured ✓" 
                                    else 
                                        "Get from Jellyseerr Settings > General",
                                    buttonText = if (jellyseerrApiKey.isNotBlank()) "Change" else "Set Key",
                                    onClick = { showJellyseerrApiKeyDialog = true }
                                )
                                
                                if (showJellyseerrApiKeyDialog) {
                                    var apiKeyInput by remember { mutableStateOf(jellyseerrApiKey) }
                                    Dialog(
                                        onDismissRequest = { showJellyseerrApiKeyDialog = false },
                                        properties = DialogProperties(usePlatformDefaultWidth = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.width(500.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = SurfaceDefaults.colors(
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(32.dp),
                                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                                ) {
                                                    Text(
                                                        text = "Jellyseerr API Key",
                                                        style = MaterialTheme.typography.headlineSmall
                                                    )
                                                    
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text(
                                                            text = "Enter your Jellyseerr API key. Find it in Jellyseerr: Settings > General > API Key",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                        )
                                                        
                                                        OutlinedTextField(
                                                             value = apiKeyInput,
                                                             onValueChange = { apiKeyInput = it },
                                                             label = { Text("API Key") },
                                                             singleLine = true,
                                                             modifier = Modifier.fillMaxWidth(),
                                                             colors = TextFieldDefaults.colors(
                                                                 focusedTextColor = Color.White,
                                                                 unfocusedTextColor = Color.White,
                                                                 focusedContainerColor = Color.Transparent,
                                                                 unfocusedContainerColor = Color.Transparent,
                                                                 cursorColor = MaterialTheme.colorScheme.primary,
                                                                 focusedLabelColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                                                 focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                                                             )
                                                         )
                                                    }
                                                    
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { showJellyseerrApiKeyDialog = false },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.colors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                        ) {
                                                            Text("Cancel")
                                                        }
                                                        
                                                        Button(
                                                            onClick = {
                                                                jellyseerrApiKey = apiKeyInput
                                                                settings.jellyseerrApiKey = apiKeyInput
                                                                showJellyseerrApiKeyDialog = false
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Save")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Username/Password authentication
                                SettingButton(
                                    title = "Jellyseerr Login",
                                    description = if (jellyseerrSessionCookie.isNotBlank() && jellyseerrUsername.isNotBlank()) 
                                        "Logged in as ${jellyseerrUsername} ✓" 
                                    else 
                                        "Sign in with your Jellyseerr or Jellyfin account",
                                    buttonText = if (jellyseerrSessionCookie.isNotBlank()) "Re-Login" else "Sign In",
                                    onClick = { 
                                        showJellyseerrLoginDialog = true
                                        loginError = null
                                    }
                                )
                                
                                // Logout button (only show if logged in)
                                if (jellyseerrSessionCookie.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SettingButton(
                                        title = "Sign Out",
                                        description = "Clear stored credentials",
                                        buttonText = "Sign Out",
                                        onClick = {
                                            settings.clearJellyseerrCredentials()
                                            jellyseerrSessionCookie = ""
                                            jellyseerrUsername = ""
                                            jellyseerrApiKey = ""
                                            jellyseerrAuthType = "api_key"
                                        }
                                    )
                                }
                                
                                if (showJellyseerrLoginDialog) {
                                    var usernameInput by remember { mutableStateOf("") }
                                    var passwordInput by remember { mutableStateOf("") }
                                    var useJellyfinAuth by remember { mutableStateOf(true) }
                                    
                                    Dialog(
                                        onDismissRequest = { 
                                            if (!isLoggingIn) {
                                                showJellyseerrLoginDialog = false 
                                            }
                                        },
                                        properties = DialogProperties(usePlatformDefaultWidth = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.width(500.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = SurfaceDefaults.colors(
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(32.dp),
                                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                                ) {
                                                    Text(
                                                        text = "Jellyseerr Login",
                                                        style = MaterialTheme.typography.headlineSmall
                                                    )
                                                    
                                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                        Text(
                                                            text = "Sign in with your credentials.",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                        )
                                                        
                                                        // Auth type selector
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Button(
                                                                onClick = { useJellyfinAuth = true },
                                                                modifier = Modifier.weight(1f),
                                                                colors = ButtonDefaults.colors(
                                                                    containerColor = if (useJellyfinAuth) 
                                                                        MaterialTheme.colorScheme.primary 
                                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                                )
                                                            ) {
                                                                Text("Jellyfin")
                                                            }
                                                            Button(
                                                                onClick = { useJellyfinAuth = false },
                                                                modifier = Modifier.weight(1f),
                                                                colors = ButtonDefaults.colors(
                                                                    containerColor = if (!useJellyfinAuth) 
                                                                        MaterialTheme.colorScheme.primary 
                                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                                )
                                                            ) {
                                                                Text("Local/Email")
                                                            }
                                                        }
                                                        
                                                        OutlinedTextField(
                                                             value = usernameInput,
                                                             onValueChange = { usernameInput = it },
                                                             label = { Text(if (useJellyfinAuth) "Jellyfin Username" else "Email") },
                                                             singleLine = true,
                                                             enabled = !isLoggingIn,
                                                             modifier = Modifier.fillMaxWidth(),
                                                             colors = TextFieldDefaults.colors(
                                                                 focusedTextColor = Color.White,
                                                                 unfocusedTextColor = Color.White,
                                                                 focusedContainerColor = Color.Transparent,
                                                                 unfocusedContainerColor = Color.Transparent,
                                                                 cursorColor = MaterialTheme.colorScheme.primary,
                                                                 focusedLabelColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                                                 focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                                                             )
                                                         )
                                                         
                                                         OutlinedTextField(
                                                             value = passwordInput,
                                                             onValueChange = { passwordInput = it },
                                                             label = { Text("Password") },
                                                             singleLine = true,
                                                             enabled = !isLoggingIn,
                                                             visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                                             modifier = Modifier.fillMaxWidth(),
                                                             colors = TextFieldDefaults.colors(
                                                                 focusedTextColor = Color.White,
                                                                 unfocusedTextColor = Color.White,
                                                                 focusedContainerColor = Color.Transparent,
                                                                 unfocusedContainerColor = Color.Transparent,
                                                                 cursorColor = MaterialTheme.colorScheme.primary,
                                                                 focusedLabelColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                                                 focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                                                             )
                                                         )
                                                        
                                                        if (isLoggingIn) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.Center,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(20.dp),
                                                                    strokeWidth = 2.dp
                                                                )
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Text("Signing in...", style = MaterialTheme.typography.bodyMedium)
                                                            }
                                                        }
                                                        
                                                        loginError?.let { error ->
                                                            Text(
                                                                text = error,
                                                                color = MaterialTheme.colorScheme.error,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        }
                                                    }
                                                    
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { showJellyseerrLoginDialog = false },
                                                            enabled = !isLoggingIn,
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.colors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                        ) {
                                                            Text("Cancel")
                                                        }
                                                        
                                                        Button(
                                                            onClick = {
                                                                if (jellyseerrUrl.isBlank()) {
                                                                    loginError = "Please set Jellyseerr URL first"
                                                                    return@Button
                                                                }
                                                                if (usernameInput.isBlank() || passwordInput.isBlank()) {
                                                                    loginError = "Please enter username and password"
                                                                    return@Button
                                                                }
                                                                
                                                                isLoggingIn = true
                                                                loginError = null
                                                                
                                                                scope.launch {
                                                                    val result = if (useJellyfinAuth) {
                                                                        com.flex.elefin.jellyseerr.JellyseerrApiService.loginWithJellyfin(
                                                                            jellyseerrUrl,
                                                                            usernameInput,
                                                                            passwordInput
                                                                        )
                                                                    } else {
                                                                        com.flex.elefin.jellyseerr.JellyseerrApiService.loginWithEmail(
                                                                            jellyseerrUrl,
                                                                            usernameInput,
                                                                            passwordInput
                                                                        )
                                                                    }
                                                                    
                                                                    isLoggingIn = false
                                                                    
                                                                    result.fold(
                                                                        onSuccess = { cookie ->
                                                                            jellyseerrSessionCookie = cookie
                                                                            jellyseerrUsername = usernameInput
                                                                            settings.jellyseerrSessionCookie = cookie
                                                                            settings.jellyseerrUsername = usernameInput
                                                                            settings.jellyseerrAuthType = "credentials"
                                                                            showJellyseerrLoginDialog = false
                                                                            Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                                                        },
                                                                        onFailure = { error ->
                                                                            loginError = "Login failed: ${error.message}"
                                                                        }
                                                                    )
                                                                }
                                                            },
                                                            enabled = !isLoggingIn,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Sign In")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Enable Jellyseerr Discover Tab toggle (only show if configured)
                            val isJellyseerrConfigured = jellyseerrUrl.isNotBlank() && (
                                (jellyseerrAuthType == "api_key" && jellyseerrApiKey.isNotBlank()) ||
                                (jellyseerrAuthType == "credentials" && jellyseerrSessionCookie.isNotBlank())
                            )
                            
                            if (isJellyseerrConfigured) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                SettingToggle(
                                    title = "Enable Discover Tab",
                                    description = "Show Discover tab with Trending, Popular, and Upcoming content from Jellyseerr",
                                    isEnabled = jellyseerrEnabled,
                                    onToggle = {
                                        jellyseerrEnabled = !jellyseerrEnabled
                                        settings.jellyseerrEnabled = jellyseerrEnabled
                                    }
                                )
                                
                                SettingToggle(
                                    title = "Include in Search",
                                    description = "Show results from Jellyseerr in the main search screen",
                                    isEnabled = jellyseerrSearchEnabled,
                                    onToggle = {
                                        jellyseerrSearchEnabled = !jellyseerrSearchEnabled
                                        settings.jellyseerrSearchEnabled = jellyseerrSearchEnabled
                                    }
                                )
                                
                                // TMDB Direct API Key (Fallback)
                                Spacer(modifier = Modifier.height(16.dp))
                                val tmdbApiKey = settings.tmdbApiKey
                                var showTmdbKeyDialog by remember { mutableStateOf(false) }
                                
                                SettingButton(
                                    title = "TMDB API Key (Trailers)",
                                    description = if (tmdbApiKey.isNotBlank()) 
                                        "TMDB Key Configured ✓" 
                                    else 
                                        "Direct fallback for trailers if Jellyseerr fails",
                                    buttonText = if (tmdbApiKey.isNotBlank()) "Change" else "Set Key",
                                    onClick = { showTmdbKeyDialog = true }
                                )
                                
                                if (showTmdbKeyDialog) {
                                    var apiKeyInput by remember { mutableStateOf(tmdbApiKey) }
                                    Dialog(
                                        onDismissRequest = { showTmdbKeyDialog = false },
                                        properties = DialogProperties(usePlatformDefaultWidth = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.width(500.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = SurfaceDefaults.colors(
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(32.dp),
                                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                                ) {
                                                    Text(
                                                        text = "TMDB API Key",
                                                        style = MaterialTheme.typography.headlineSmall
                                                    )
                                                    
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text(
                                                            text = "Enter your TMDB API Key to fetch trailers directly from The Movie Database.",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                        )
                                                        
                                                        OutlinedTextField(
                                                             value = apiKeyInput,
                                                             onValueChange = { apiKeyInput = it },
                                                             label = { Text("TMDB API Key") },
                                                             singleLine = true,
                                                             modifier = Modifier.fillMaxWidth(),
                                                             colors = TextFieldDefaults.colors(
                                                                 focusedTextColor = Color.White,
                                                                 unfocusedTextColor = Color.White,
                                                                 focusedContainerColor = Color.Transparent,
                                                                 unfocusedContainerColor = Color.Transparent,
                                                                 cursorColor = MaterialTheme.colorScheme.primary,
                                                                 focusedLabelColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                                                 focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                                 unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                                                             )
                                                         )
                                                    }
                                                    
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { showTmdbKeyDialog = false },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.colors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                        ) {
                                                            Text("Cancel")
                                                        }
                                                        
                                                        Button(
                                                            onClick = {
                                                                settings.tmdbApiKey = apiKeyInput
                                                                showTmdbKeyDialog = false
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Save")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        
                        SettingsCategory.APPEARANCE -> {
                            // Dark Mode
                            SettingToggle(
                                title = "Dark Mode",
                                description = "Disable background image and use Material dark background",
                                isEnabled = darkModeEnabled,
                                onToggle = {
                                    darkModeEnabled = !darkModeEnabled
                                    settings.darkModeEnabled = darkModeEnabled
                                }
                            )
                            
                            // Use Logo for Title
                            SettingToggle(
                                title = "Use Logo for Title",
                                description = "Display logo image instead of title text on media screens",
                                isEnabled = useLogoForTitleEnabled,
                                onToggle = {
                                    useLogoForTitleEnabled = !useLogoForTitleEnabled
                                    settings.useLogoForTitle = useLogoForTitleEnabled
                                }
                            )
                            
                            // Animated Play Button
                            SettingToggle(
                                title = "Animated Play Button",
                                description = "Use animated play button with Lottie glow effect",
                                isEnabled = animatedPlayButtonEnabled,
                                onToggle = {
                                    animatedPlayButtonEnabled = !animatedPlayButtonEnabled
                                    settings.useAnimatedPlayButton = animatedPlayButtonEnabled
                                }
                            )
                            
                            // 24-Hour Time Format
                            SettingToggle(
                                title = "24-Hour Time Format",
                                description = "Display time in 24-hour format (HH:mm)",
                                isEnabled = use24HourTimeEnabled,
                                onToggle = {
                                    use24HourTimeEnabled = !use24HourTimeEnabled
                                    settings.use24HourTime = use24HourTimeEnabled
                                }
                            )

                            // 4K Quality Backgrounds
                            SettingToggle(
                                title = "4K Quality Backgrounds",
                                description = "Use 4K resolution (3840x2160) for backdrop images. May impact performance.",
                                isEnabled = use4KBackgrounds,
                                onToggle = {
                                    use4KBackgrounds = !use4KBackgrounds
                                    settings.use4KBackgrounds = use4KBackgrounds
                                }
                            )
                        }
                        
                        SettingsCategory.PERFORMANCE -> {
                            // Use Google TV Cards
                            SettingToggle(
                                title = "Use Google TV Cards",
                                description = "Lightweight cards with subtle scale animation and glow border.",
                                isEnabled = useGoogleTvCards,
                                onToggle = {
                                    useGoogleTvCards = !useGoogleTvCards
                                    settings.useGoogleTvCards = useGoogleTvCards
                                    // Disable simple cards if Google TV cards enabled
                                    if (useGoogleTvCards) {
                                        useSimpleCards = false
                                        settings.useSimpleCards = false
                                    }
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Animations",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                            )
                            
                            // Disable UI Animations
                            SettingToggle(
                                title = "Disable UI Animations",
                                description = "Turn off row scrolling animations for better performance",
                                isEnabled = disableUIAnimations,
                                onToggle = {
                                    disableUIAnimations = !disableUIAnimations
                                    settings.disableUIAnimations = disableUIAnimations
                                }
                            )
                            
                            // Preload Library Images
                            SettingToggle(
                                title = "Preload Library Images",
                                description = "Preload images for smoother scrolling",
                                isEnabled = preloadLibraryImagesEnabled,
                                onToggle = {
                                    preloadLibraryImagesEnabled = !preloadLibraryImagesEnabled
                                    settings.preloadLibraryImages = preloadLibraryImagesEnabled
                                }
                            )
                            
                            // Cache Library Images
                            SettingToggle(
                                title = "Cache Library Images",
                                description = "Cache images to disk and memory for faster loading",
                                isEnabled = cacheLibraryImagesEnabled,
                                onToggle = {
                                    cacheLibraryImagesEnabled = !cacheLibraryImagesEnabled
                                    settings.cacheLibraryImages = cacheLibraryImagesEnabled
                                }
                            )
                            
                            // Use Glide
                            SettingToggle(
                                title = "Use Glide for Images",
                                description = "Use Glide instead of Coil for image loading",
                                isEnabled = useGlideEnabled,
                                onToggle = {
                                    useGlideEnabled = !useGlideEnabled
                                    settings.useGlide = useGlideEnabled
                                }
                            )
                            
                            // Reduce Poster Resolution
                            SettingToggle(
                                title = "Reduce Poster Resolution",
                                description = "Reduce poster images to 600x300px to save bandwidth",
                                isEnabled = reducePosterResolutionEnabled,
                                onToggle = {
                                    reducePosterResolutionEnabled = !reducePosterResolutionEnabled
                                    settings.reducePosterResolution = reducePosterResolutionEnabled
                                }
                            )
                        }
                        
                        SettingsCategory.LIBRARY -> {
                            // Auto-Refresh Media
                            SettingToggle(
                                title = "Auto-Refresh Media",
                                description = "Automatically check for new media (every ${autoRefreshIntervalMinutes} min)",
                                isEnabled = autoRefreshEnabled,
                                onToggle = {
                                    autoRefreshEnabled = !autoRefreshEnabled
                                    settings.autoRefreshEnabled = autoRefreshEnabled
                                }
                            )
                            
                            // Refresh Interval
                            if (autoRefreshEnabled) {
                                SettingCycle(
                                    title = "Refresh Interval",
                                    description = "How often to check for new media",
                                    currentValue = "${autoRefreshIntervalMinutes}m",
                                    onCycle = {
                                        autoRefreshIntervalMinutes = when (autoRefreshIntervalMinutes) {
                                            2 -> 3
                                            3 -> 5
                                            5 -> 10
                                            10 -> 15
                                            15 -> 2
                                            else -> 5
                                        }
                                        settings.autoRefreshIntervalMinutes = autoRefreshIntervalMinutes
                                    }
                                )
                            }
                            
                            // Hide Shows with Zero Episodes
                            SettingToggle(
                                title = "Hide Empty Shows",
                                description = "Hide TV shows with no episodes from home and library",
                                isEnabled = hideShowsWithZeroEpisodesEnabled,
                                onToggle = {
                                    hideShowsWithZeroEpisodesEnabled = !hideShowsWithZeroEpisodesEnabled
                                    settings.hideShowsWithZeroEpisodes = hideShowsWithZeroEpisodesEnabled
                                }
                            )
                        }
                        
                        SettingsCategory.ADVANCED -> {
                            // Debug Outlines
                            SettingToggle(
                                title = "Show Debug Outlines",
                                description = "Show debug borders to visualize layout",
                                isEnabled = debugOutlinesEnabled,
                                onToggle = {
                                    debugOutlinesEnabled = !debugOutlinesEnabled
                                    settings.showDebugOutlines = debugOutlinesEnabled
                                }
                            )
                            
                            // Long Press Duration
                            SettingCycle(
                                title = "Long Press Duration",
                                description = "Duration to hold Enter/OK for episode menu",
                                currentValue = "${longPressDurationSeconds}s",
                                onCycle = {
                                    longPressDurationSeconds = when (longPressDurationSeconds) {
                                        2 -> 3
                                        3 -> 4
                                        4 -> 5
                                        5 -> 2
                                        else -> 2
                                    }
                                    settings.longPressDurationSeconds = longPressDurationSeconds
                                }
                            )
                            
                            // Clear Image Cache
                            SettingButton(
                                title = "Clear Image Cache",
                                description = "Clear all cached images from disk and memory",
                                buttonText = "Clear",
                                onClick = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                val imageLoader: coil.ImageLoader = context.imageLoader
                                                imageLoader.diskCache?.clear()
                                                imageLoader.memoryCache?.clear()
                                                
                                                val coilCacheDir = context.filesDir.resolve("image_cache")
                                                if (coilCacheDir.exists()) {
                                                    coilCacheDir.deleteRecursively()
                                                }
                                                
                                                val glideCacheDir = File(context.cacheDir, "glide_image_cache")
                                                if (glideCacheDir.exists()) {
                                                    glideCacheDir.deleteRecursively()
                                                }
                                                
                                                Glide.get(context).clearDiskCache()
                                            }
                                            
                                            withContext(Dispatchers.Main) {
                                                Glide.get(context).clearMemory()
                                                Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SettingsScreen", "Error clearing cache", e)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Error clearing cache: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        
                        SettingsCategory.UPDATES -> {
                            // Auto-Check for Updates
                            SettingToggle(
                                title = "Auto-Check for Updates",
                                description = "Automatically check for updates when app starts",
                                isEnabled = autoUpdateEnabled,
                                onToggle = {
                                    autoUpdateEnabled = !autoUpdateEnabled
                                    settings.autoUpdateEnabled = autoUpdateEnabled
                                }
                            )
                            
                            // Check for Updates
                            SettingButton(
                                title = "Check for Updates",
                                description = if (checkingForUpdates) {
                                    "Checking for updates..."
                                } else if (updateCheckMessage != null) {
                                    updateCheckMessage!!
                                } else {
                                    "Manually check for app updates from GitHub"
                                },
                                buttonText = if (checkingForUpdates) "Checking..." else "Check",
                                enabled = !checkingForUpdates,
                                onClick = {
                                    scope.launch {
                                        checkingForUpdates = true
                                        updateCheckMessage = null
                                        
                                        try {
                                            val versionCode = try {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                                                }
                                            } catch (e: Exception) { 1 }
                                            
                                            val release = withContext(Dispatchers.IO) {
                                                UpdateService.getLatestRelease()
                                            }
                                            
                                            if (release != null) {
                                                val remoteVersionCode = UpdateService.parseVersion(release.tagName)
                                                if (UpdateService.updateAvailable(remoteVersionCode, versionCode)) {
                                                    latestRelease = release
                                                    showUpdateDialog = true
                                                    updateCheckMessage = "Update available: ${release.name}"
                                                } else {
                                                    updateCheckMessage = "You're on the latest version (${release.name})"
                                                }
                                            } else {
                                                updateCheckMessage = "Failed to check for updates. Please try again later."
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SettingsScreen", "Error checking for updates", e)
                                            updateCheckMessage = "Error checking for updates: ${e.message}"
                                        } finally {
                                            checkingForUpdates = false
                                        }
                                    }
                                }
                            )
                        }
                        
                        SettingsCategory.ACCOUNT -> {
                            // Log Out
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Log Out",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Sign out and return to login screen",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                
                                Button(
                                    onClick = { showLogoutConfirmation = true },
                                    colors = ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Log Out")
                                }
                            }
                        }
                    }
                    
                    // Add some bottom padding
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
        
        // Dialogs
        // Logout confirmation dialog
        if (showLogoutConfirmation) {
            Dialog(
                onDismissRequest = { showLogoutConfirmation = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(500.dp)
                            .heightIn(max = 300.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = SurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                text = "Log Out?",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = "Are you sure you want to log out? You will need to sign in again to access your media.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { showLogoutConfirmation = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text("Cancel")
                                }
                                
                                Button(
                                    onClick = {
                                        showLogoutConfirmation = false
                                        val config = JellyfinConfig(context)
                                        config.clearAuth()
                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        context.startActivity(intent)
                                        (context as? android.app.Activity)?.finish()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Log Out")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Show update dialog if update is found
        latestRelease?.let { release ->
            if (showUpdateDialog) {
                UpdateDialog(
                    release = release,
                    onDismiss = {
                        showUpdateDialog = false
                        latestRelease = null
                    },
                    onUpdate = {
                        showUpdateDialog = false
                        latestRelease = null
                    }
                )
            }
        }

        // ExoPlayer subtitle text color picker dialog
        if (showExoSubtitleColorDialog) {
            SubtitleColorPickerDialog(
                title = "Subtitle Text Color",
                currentColor = settings.exoSubtitleTextColor,
                onColorSelected = { color ->
                    settings.exoSubtitleTextColor = color
                    showExoSubtitleColorDialog = false
                },
                onDismiss = { showExoSubtitleColorDialog = false }
            )
        }

        // ExoPlayer subtitle background color picker dialog
        if (showExoSubtitleBgColorDialog) {
            SubtitleColorPickerDialog(
                title = "Subtitle Background Color",
                currentColor = settings.exoSubtitleBgColor,
                onColorSelected = { color ->
                    settings.exoSubtitleBgColor = color
                    showExoSubtitleBgColorDialog = false
                },
                onDismiss = { showExoSubtitleBgColorDialog = false }
            )
        }
    }
}

@Composable
private fun CategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isFocused -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = ButtonDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp) // Reduced button padding by 20%
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp), // Decreased by 20% (5.1 * 0.8 ≈ 4)
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp) // Reduced icon size by ~17% (24 * 0.83 ≈ 20)
            )
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyMedium, // Smaller text style
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    enabledText: String = "ON",
    disabledText: String = "OFF"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.colors(
                containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(if (isEnabled) enabledText else disabledText)
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    description: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDecrease, enabled = canDecrease) {
                Text("-")
            }
            Button(onClick = onIncrease, enabled = canIncrease) {
                Text("+")
            }
        }
    }
}

@Composable
private fun SettingCycle(
    title: String,
    description: String,
    currentValue: String,
    onCycle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Button(
            onClick = onCycle,
            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(currentValue)
        }
    }
}

@Composable
private fun SettingButton(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(buttonText)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitleColorPickerDialog(
    title: String,
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colorOptions = listOf(
        "White" to 0xFFFFFFFF.toInt(),
        "Black" to 0xFF000000.toInt(),
        "Yellow" to 0xFFFFFF00.toInt(),
        "Cyan" to 0xFF00FFFF.toInt(),
        "Green" to 0xFF00FF00.toInt(),
        "Red" to 0xFFFF0000.toInt(),
        "Blue" to 0xFF0000FF.toInt(),
        "Magenta" to 0xFFFF00FF.toInt()
    )

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
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.7f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val listItemColors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        focusedContentColor = Color.White,
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        selectedContentColor = Color.White
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(colorOptions) { (name, color) ->
                            val isSelected = color == currentColor
                            ListItem(
                                selected = isSelected,
                                onClick = { onColorSelected(color) },
                                headlineContent = {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.9f
                                        )
                                    )
                                },
                                trailingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color(color), RoundedCornerShape(4.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    )
                                },
                                colors = listItemColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
