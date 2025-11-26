package com.flex.elefin.screens

import coil.annotation.ExperimentalCoilApi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import java.io.File
import android.widget.Toast

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    var mpvEnabled by remember { mutableStateOf(settings.isMpvEnabled) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            // Settings options
            // MPV Player setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Use MPV Player",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use MPV player instead of ExoPlayer for video playback",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        mpvEnabled = !mpvEnabled
                        settings.isMpvEnabled = mpvEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (mpvEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (mpvEnabled) "ON" else "OFF")
                }
            }

            // Debug Outlines setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Show Debug Outlines",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Show debug borders on media info screen to visualize layout",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        debugOutlinesEnabled = !debugOutlinesEnabled
                        settings.showDebugOutlines = debugOutlinesEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (debugOutlinesEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (debugOutlinesEnabled) "ON" else "OFF")
                }
            }

            // Preload Library Images setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Preload Library Images",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Preload images in library views for smoother scrolling",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        preloadLibraryImagesEnabled = !preloadLibraryImagesEnabled
                        settings.preloadLibraryImages = preloadLibraryImagesEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (preloadLibraryImagesEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (preloadLibraryImagesEnabled) "ON" else "OFF")
                }
            }

            // Cache Library Images setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Cache Library Images",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cache library images to disk and memory for faster loading",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        cacheLibraryImagesEnabled = !cacheLibraryImagesEnabled
                        settings.cacheLibraryImages = cacheLibraryImagesEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (cacheLibraryImagesEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (cacheLibraryImagesEnabled) "ON" else "OFF")
                }
            }

            // Use Glide setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Use Glide for Image Loading",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use Glide instead of Coil for image loading with disk and memory caching",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        useGlideEnabled = !useGlideEnabled
                        settings.useGlide = useGlideEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (useGlideEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (useGlideEnabled) "ON" else "OFF")
                }
            }

            // Reduce Poster Resolution setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Reduce Poster Resolution",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reduce poster image resolution to 600x300px to save bandwidth and storage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        reducePosterResolutionEnabled = !reducePosterResolutionEnabled
                        settings.reducePosterResolution = reducePosterResolutionEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (reducePosterResolutionEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (reducePosterResolutionEnabled) "ON" else "OFF")
                }
            }

            // Animated Play Button setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Animated Play Button",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use animated play button with Lottie glow effect and icon morphing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        animatedPlayButtonEnabled = !animatedPlayButtonEnabled
                        settings.useAnimatedPlayButton = animatedPlayButtonEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (animatedPlayButtonEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (animatedPlayButtonEnabled) "ON" else "OFF")
                }
            }

            // 24-Hour Time Format setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "24-Hour Time Format",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Display time in 24-hour format (HH:mm) instead of 12-hour format",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        use24HourTimeEnabled = !use24HourTimeEnabled
                        settings.use24HourTime = use24HourTimeEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (use24HourTimeEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (use24HourTimeEnabled) "ON" else "OFF")
                }
            }
            
            // Long Press Duration setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Long Press Duration",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Duration to hold Enter/OK button to open episode menu (${longPressDurationSeconds}s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cycle through options: 2, 3, 4, 5 seconds
                    Button(
                        onClick = {
                            longPressDurationSeconds = when (longPressDurationSeconds) {
                                2 -> 3
                                3 -> 4
                                4 -> 5
                                5 -> 2
                                else -> 2
                            }
                            settings.longPressDurationSeconds = longPressDurationSeconds
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("${longPressDurationSeconds}s")
                    }
                }
            }

            // Remote Theming setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Remote Theming",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Load custom theme from server's Custom CSS (Admin → General → Custom CSS)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        remoteThemingEnabled = !remoteThemingEnabled
                        settings.remoteThemingEnabled = remoteThemingEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (remoteThemingEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (remoteThemingEnabled) "ON" else "OFF")
                }
            }

            // Dark Mode setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Dark Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Disable background image and use Material dark background instead",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        darkModeEnabled = !darkModeEnabled
                        settings.darkModeEnabled = darkModeEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (darkModeEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (darkModeEnabled) "ON" else "OFF")
                }
            }

            // Auto-Refresh setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Auto-Refresh Media",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically check for new media and refresh rows (every ${autoRefreshIntervalMinutes} minutes)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        autoRefreshEnabled = !autoRefreshEnabled
                        settings.autoRefreshEnabled = autoRefreshEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (autoRefreshEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (autoRefreshEnabled) "ON" else "OFF")
                }
            }

            // Auto-Refresh Interval setting
            if (autoRefreshEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Refresh Interval",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "How often to check for new media (${autoRefreshIntervalMinutes} minutes)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cycle through options: 2, 3, 5, 10, 15 minutes
                        Button(
                            onClick = {
                                autoRefreshIntervalMinutes = when (autoRefreshIntervalMinutes) {
                                    2 -> 3
                                    3 -> 5
                                    5 -> 10
                                    10 -> 15
                                    15 -> 2
                                    else -> 5
                                }
                                settings.autoRefreshIntervalMinutes = autoRefreshIntervalMinutes
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("${autoRefreshIntervalMinutes}m")
                        }
                    }
                }
            }

            // Hide Shows with Zero Episodes setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Hide Shows with Zero Episodes",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hide TV shows with no episodes from the home screen and library view",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        hideShowsWithZeroEpisodesEnabled = !hideShowsWithZeroEpisodesEnabled
                        settings.hideShowsWithZeroEpisodes = hideShowsWithZeroEpisodesEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (hideShowsWithZeroEpisodesEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (hideShowsWithZeroEpisodesEnabled) "ON" else "OFF")
                }
            }

            // Minimal Buffer for 4K setting - Hidden for now
            /*
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Minimal Buffer for 4K",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Start playing 4K content with minimal buffering (reduces wait time for large files)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        minimalBuffer4KEnabled = !minimalBuffer4KEnabled
                        settings.minimalBuffer4K = minimalBuffer4KEnabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (minimalBuffer4KEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (minimalBuffer4KEnabled) "ON" else "OFF")
                }
            }
            */
            
            // Transcode AAC to AC3 setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Transcode AAC to AC3",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Transcode all AAC audio to AC3 (5.1 max). AC3 is universally supported on all devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        transcodeAacToAc3Enabled = !transcodeAacToAc3Enabled
                        settings.transcodeAacToAc3 = transcodeAacToAc3Enabled
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (transcodeAacToAc3Enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(if (transcodeAacToAc3Enabled) "ON" else "OFF")
                }
            }
            
            // Clear Cache button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Clear Image Cache",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Clear all cached images from disk and memory",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    // Clear Coil cache
                                    val imageLoader: coil.ImageLoader = context.imageLoader
                                    imageLoader.diskCache?.clear()
                                    imageLoader.memoryCache?.clear()
                                    
                                    // Clear Coil disk cache directory manually (in case)
                                    val coilCacheDir = context.filesDir.resolve("image_cache")
                                    if (coilCacheDir.exists()) {
                                        coilCacheDir.deleteRecursively()
                                    }
                                    
                                    // Clear Glide cache directory manually
                                    val glideCacheDir = File(context.cacheDir, "glide_image_cache")
                                    if (glideCacheDir.exists()) {
                                        glideCacheDir.deleteRecursively()
                                    }
                                    
                                    // Clear Glide disk cache (must be done on IO thread)
                                    Glide.get(context).clearDiskCache()
                                }
                                
                                // Clear Glide memory cache on main thread
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
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

