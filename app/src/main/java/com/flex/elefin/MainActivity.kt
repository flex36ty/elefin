package com.flex.elefin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.screens.JellyfinHomeScreen
import com.flex.elefin.screens.UpdateDialog
import com.flex.elefin.MovieDetailsActivity
import com.flex.elefin.SeriesDetailsActivity
import com.flex.elefin.JellyfinVideoPlayerActivity
import com.flex.elefin.updater.GitHubRelease
import com.flex.elefin.updater.UpdateService

/**
 * Main entry point that loads the Jellyfin home screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if this is the first launch - if not, remove splash screen background
        val settings = AppSettings(this)
        val isFirstLaunch = settings.isFirstLaunch
        if (!isFirstLaunch) {
            // Remove splash screen background for subsequent launches
            window.setBackgroundDrawableResource(android.R.color.transparent)
        } else {
            // Mark that we've launched at least once
            settings.isFirstLaunch = false
        }
        
        // Check MPV availability (initialization happens lazily when class is first accessed)
        // This is expected to fail on emulators/devices without MPV libraries - app will use ExoPlayer instead
        try {
            val available = `is`.xyz.mpv.MPVLib.isAvailable()
            if (available) {
                android.util.Log.d("MainActivity", "MPV libraries available")
            } else {
                android.util.Log.d("MainActivity", "MPV libraries not available - will use ExoPlayer")
            }
        } catch (e: Exception) {
            android.util.Log.d("MainActivity", "MPV check failed - will use ExoPlayer: ${e.message}")
        }
        
        val appSettings = AppSettings(this)
        
        // Get version code from package manager
        val versionCode = try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting version code", e)
            1 // Fallback to 1
        }
        
        setContent {
            JellyfinAppTheme {
                // Update checker (only if auto-update is enabled)
                val settings = AppSettings(this)
                if (settings.autoUpdateEnabled) {
                    UpdateChecker(
                        localVersionCode = versionCode
                    )
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    JellyfinHomeScreen(
                        onItemClick = { item: JellyfinItem, resumePositionMs: Long ->
                            // Route to appropriate details screen based on item type
                            val intent = when (item.Type) {
                                "Series" -> {
                                    SeriesDetailsActivity.createIntent(
                                        context = this@MainActivity,
                                        item = item,
                                        fromLibrary = false // From home screen
                                    )
                                }
                                "Episode" -> {
                                    // Episodes navigate to series details screen, focused on that episode
                                    if (item.SeriesId != null) {
                                        // Fetch series details first to get the series item
                                        val seriesItem = JellyfinItem(
                                            Id = item.SeriesId,
                                            Name = item.SeriesName ?: ""
                                        )
                                        SeriesDetailsActivity.createIntent(
                                            context = this@MainActivity,
                                            item = seriesItem,
                                            fromLibrary = false,
                                            episodeId = item.Id // Pass episode ID to focus on it
                                        )
                                    } else {
                                        // Fallback: go directly to video player if no SeriesId
                                        JellyfinVideoPlayerActivity.createIntent(
                                            context = this@MainActivity,
                                            itemId = item.Id,
                                            resumePositionMs = resumePositionMs
                                        )
                                    }
                                }
                                else -> {
                                    // Movies and other types go to movie details screen
                                    MovieDetailsActivity.createIntent(
                                        context = this@MainActivity,
                                        item = item,
                                        fromLibrary = false // From home screen
                                    )
                                }
                            }
                            startActivity(intent)
                        },
                        showDebugOutlines = appSettings.showDebugOutlines,
                        preloadLibraryImages = appSettings.preloadLibraryImages,
                        cacheLibraryImages = appSettings.cacheLibraryImages,
                        reducePosterResolution = appSettings.reducePosterResolution
                    )
                }
            }
        }
    }
}

/**
 * Composable that checks for app updates on startup
 */
@Composable
private fun UpdateChecker(localVersionCode: Int) {
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    
    LaunchedEffect(Unit) {
        // Check for updates in the background
        try {
            val release = UpdateService.getLatestRelease() ?: return@LaunchedEffect
            val remoteVersionCode = UpdateService.parseVersion(release.tagName)
            
            if (UpdateService.updateAvailable(remoteVersionCode, localVersionCode)) {
                android.util.Log.d("UpdateChecker", "Update available: ${release.name} (remote: $remoteVersionCode, local: $localVersionCode)")
                latestRelease = release
                showUpdateDialog = true
            } else {
                android.util.Log.d("UpdateChecker", "No update available (remote: $remoteVersionCode, local: $localVersionCode)")
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "Error checking for updates", e)
            // Silently fail - don't interrupt user experience
        }
    }
    
    // Show update dialog if update is available
    latestRelease?.let { release ->
        if (showUpdateDialog) {
            UpdateDialog(
                release = release,
                onDismiss = { showUpdateDialog = false },
                onUpdate = { showUpdateDialog = false }
            )
        }
    }
}