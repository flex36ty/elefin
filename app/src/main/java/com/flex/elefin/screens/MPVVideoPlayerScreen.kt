package com.flex.elefin.screens

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.player.mpv.MpvUrlSelector
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MPV Video Player Screen for Jellyfin
 * Built from scratch using official mpv-android source
 * https://github.com/mpv-android/mpv-android
 */
@Composable
fun MPVVideoPlayerScreen(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    onBack: () -> Unit,
    resumePositionMs: Long = 0L,
    subtitleStreamIndex: Int? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var playbackUrl by remember { mutableStateOf<String?>(null) }
    var mpvView by remember { mutableStateOf<MPVView?>(null) }
    var initialized by remember { mutableStateOf(false) }
    
    // Fetch video URL
    LaunchedEffect(item.Id) {
        try {
            val details = withContext(Dispatchers.IO) {
                apiService.getItemDetails(item.Id)
            }
            
            if (details != null) {
                val mediaSource = details.MediaSources?.firstOrNull()
                val mediaSourceId = mediaSource?.Id
                
                // Build MPV URL
                val deviceId = try {
                    android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "android-tv"
                } catch (e: Exception) {
                    "android-tv"
                }
                
                val selector = MpvUrlSelector(
                    server = apiService.serverBaseUrl.let { if (it.endsWith("/")) it.removeSuffix("/") else it },
                    userId = apiService.getUserId(),
                    accessToken = apiService.apiKey,
                    deviceId = deviceId
                )
                
                val result = selector.buildDirectPlay(item.Id, mediaSourceId)
                playbackUrl = result.url
                
                Log.d("MPVPlayer", "Playback URL: ${result.url}")
                isLoading = false
            } else {
                errorMessage = "Failed to load video details"
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("MPVPlayer", "Error loading video", e)
            errorMessage = "Error: ${e.message}"
            isLoading = false
        }
    }
    
    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (initialized) {
                        try {
                            MPVLib.setPropertyBoolean("pause", true)
                            Log.d("MPVPlayer", "Paused on lifecycle pause")
                        } catch (e: Exception) {
                            Log.e("MPVPlayer", "Error pausing", e)
                        }
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d("MPVPlayer", "Destroying MPV")
                    mpvView?.destroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mpvView?.destroy()
        }
    }
    
    BackHandler { onBack() }
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()
            }
            errorMessage != null -> {
                Text(text = errorMessage!!, color = Color.Red, style = MaterialTheme.typography.bodyLarge)
            }
            playbackUrl != null -> {
                AndroidView(
                    factory = { ctx ->
                        val view = MPVView(ctx, null)
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        // Initialize MPV
                        val configDir = File(ctx.filesDir, "mpv").absolutePath
                        val cacheDir = ctx.cacheDir.absolutePath
                        
                        File(configDir).mkdirs()
                        
                        view.initialize(configDir, cacheDir)
                        initialized = true
                        
                        // Set HTTP headers for Jellyfin
                        try {
                            val deviceId = android.provider.Settings.Secure.getString(
                                ctx.contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID
                            ) ?: "android-tv"
                            
                            val accessToken = apiService.apiKey
                            val headers = buildString {
                                append("User-Agent: MPV-Android\r\n")
                                append("Authorization: MediaBrowser Token=\"$accessToken\"\r\n")
                                append("X-Emby-Authorization: MediaBrowser Client=\"Elefin\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", Token=\"$accessToken\", Version=\"1.1.10\"\r\n")
                                append("Accept: */*")
                            }
                            
                            MPVLib.setPropertyString("http-header-fields", headers)
                            Log.d("MPVPlayer", "HTTP headers set")
                        } catch (e: Exception) {
                            Log.e("MPVPlayer", "Error setting headers", e)
                        }
                        
                        // Load video
                        view.playFile(playbackUrl!!)
                        
                        // Seek to resume position if needed
                        if (resumePositionMs > 0) {
                            scope.launch {
                                kotlinx.coroutines.delay(500) // Wait for video to load
                                try {
                                    val seekSeconds = resumePositionMs / 1000.0
                                    MPVLib.command(arrayOf("seek", seekSeconds.toString(), "absolute"))
                                    Log.d("MPVPlayer", "Seeking to $seekSeconds seconds")
                                } catch (e: Exception) {
                                    Log.e("MPVPlayer", "Error seeking", e)
                                }
                            }
                        }
                        
                        mpvView = view
                        Log.d("MPVPlayer", "MPV initialized and playing")
                        
                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


