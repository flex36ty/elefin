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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
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
    
    // Check if mpv-elefin is installed
    LaunchedEffect(Unit) {
        isMpvInstalled = try {
            context.packageManager.getPackageInfo("com.flex.mpvelefin", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
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
    
    // Logout confirmation
    var showLogoutConfirmation by remember { mutableStateOf(false) }

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
                                                    val mpvApkUrl = "https://github.com/nowsci/mpv-elefin/releases/latest/download/mpv-elefin.apk"
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
                            
                            // Remote Theming
                            SettingToggle(
                                title = "Remote Theming",
                                description = "Load custom theme from server's Custom CSS",
                                isEnabled = remoteThemingEnabled,
                                onToggle = {
                                    remoteThemingEnabled = !remoteThemingEnabled
                                    settings.remoteThemingEnabled = remoteThemingEnabled
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
                        }
                        
                        SettingsCategory.PERFORMANCE -> {
                            // Disable UI Animations
                            SettingToggle(
                                title = "Disable UI Animations",
                                description = "Turn off card zoom animations for better performance",
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
            AlertDialog(
                onDismissRequest = { showLogoutConfirmation = false },
                title = { Text("Log Out?") },
                text = { Text("Are you sure you want to log out? You will need to sign in again to access your media.") },
                confirmButton = {
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
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Log Out")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showLogoutConfirmation = false },
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Cancel")
                    }
                }
            )
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
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(48.dp),
            shape = SurfaceDefaults.shape,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                colorOptions.forEach { (name, color) ->
                    val isSelected = color == currentColor
                    Button(
                        onClick = { onColorSelected(color) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(color), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
