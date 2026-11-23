package com.flex.elefin

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.flex.elefin.player.cinema.CinemaPlayerActivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

import org.schabi.newpipe.extractor.ServiceList



/**
 * Utility to launch trailers using NewPipe Extractor to play in-app.
 */
class TrailerLauncher {
    companion object {
        private val loading = java.util.concurrent.atomic.AtomicBoolean(false)
        private val findingLocalTrailer = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Prefer the user's server-indexed trailer; retain YouTube as a fallback. */
        fun launchTrailer(
            context: Context,
            apiService: com.flex.elefin.jellyfin.JellyfinApiService?,
            itemId: String,
            title: String,
            youtubeKey: String?,
            callerScope: CoroutineScope? = null
        ) {
            if (loading.get() || !findingLocalTrailer.compareAndSet(false, true)) return
            val scope = callerScope ?: (context as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.IO)
            scope.launch(Dispatchers.IO) {
                val localTrailer = try {
                    apiService?.getLocalTrailers(itemId)?.firstOrNull { it.Id.isNotBlank() }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("TrailerLauncher", "Local trailer lookup failed; trying online trailer")
                    null
                }
                val remoteTrailers = if (localTrailer == null) try {
                    apiService?.getItemDetails(itemId)?.RemoteTrailers.orEmpty()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("TrailerLauncher", "Remote trailer lookup failed; trying optional fallback")
                    emptyList()
                } else emptyList()
                withContext(Dispatchers.Main) {
                    if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                        return@withContext
                    }
                    if (localTrailer != null && apiService != null) {
                        val url = apiService.getLocalTrailerPlaybackUrl(
                            localTrailer.Id, localTrailer.MediaSources?.firstOrNull()?.Id
                        )
                        // MPVView forwards this directly to MPV's string-list option.
                        // Escape commas inside MediaBrowser authorization parameters.
                        val headers = apiService.getVideoRequestHeaders().entries.joinToString(
                            separator = ","
                        ) {
                            "${it.key}: ${it.value}".replace("\\", "\\\\").replace(",", "\\,")
                        }
                        try {
                            context.startActivity(CinemaPlayerActivity.createIntent(
                                context, url, headers, "Trailer: $title", "trailer-${localTrailer.Id}"
                            ).apply {
                                putExtra("is_trailer", true)
                                if (context !is android.app.Activity) {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            })
                            return@withContext
                        } catch (e: Exception) {
                            Log.w("TrailerLauncher", "Could not open local trailer player")
                        }
                    }
                    for (trailer in remoteTrailers) {
                        val remoteUrl = trailer.Url ?: continue
                        val remoteKey = TrailerUrl.youtubeId(remoteUrl)
                        if (remoteKey != null) {
                            launchTmdbTrailer(context, remoteKey, title, scope)
                            return@withContext
                        }
                        val mediaUrl = TrailerUrl.directMedia(remoteUrl) ?: continue
                        try {
                            // Remote hosts must never receive the Jellyfin access token.
                            context.startActivity(CinemaPlayerActivity.createIntent(
                                context, mediaUrl, "", "Trailer: $title", "remote-trailer-$itemId"
                            ).apply {
                                putExtra("is_trailer", true)
                                if (context !is android.app.Activity) addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            return@withContext
                        } catch (e: Exception) {
                            Log.w("TrailerLauncher", "Could not open remote trailer player")
                        }
                    }
                    if (!youtubeKey.isNullOrBlank()) {
                        launchTmdbTrailer(context, youtubeKey, title, scope)
                    } else {
                        Toast.makeText(context, "No supported trailer found on Jellyfin. Add a local trailer or remote trailer URL, or optionally configure TMDB in settings.", Toast.LENGTH_LONG).show()
                    }
                }
            }.invokeOnCompletion { findingLocalTrailer.set(false) }
        }

        fun launchTmdbTrailer(context: Context, key: String, title: String, callerScope: CoroutineScope? = null) {
            if (!key.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                Toast.makeText(context, "This trailer link is invalid.", Toast.LENGTH_SHORT).show()
                return
            }
            if (!loading.compareAndSet(false, true)) return
            Toast.makeText(context, "Loading trailer…", Toast.LENGTH_SHORT).show()
            val youtubeUrl = "https://www.youtube.com/watch?v=$key"
            
            // Leaving the hosting screen cancels extraction/download rather than opening
            // a trailer unexpectedly after the user has left the screen.
            val scope = callerScope ?: (context as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.IO)
            scope.launch(Dispatchers.IO) {
                val cachedFiles = mutableListOf<java.io.File>()
                var handedToPlayer = false
                try {
                    // Use ServiceList.YouTube (id 0)
                    val streamingService = ServiceList.YouTube
                    val extractor = streamingService.getStreamExtractor(youtubeUrl)
                    runInterruptible { extractor.fetchPage() }
                    

                    
                    // PRODUCTION FIX: Force 1080p (FHD). Avoid 4K.
                    // Priority:
                    // 1. 1080p Video-Only (itag 137/299) + Audio (itag 140/141)
                    // 2. 720p Muxed (itag 22)
                    // 3. 360p Muxed (itag 18)
                    
                    var streamUrl = ""
                    var audioUrl: String? = null
                    
                    val videoOnlyStreams = extractor.videoOnlyStreams
                    val audioStreams = extractor.audioStreams
                    val muxedStreams = extractor.videoStreams
                    
                    // Try 1080p Video-Only (137 = 1080p AVC, 299 = 1080p60 AVC)
                    val fhdVideo = videoOnlyStreams.find { it.id == "137" } 
                        ?: videoOnlyStreams.find { it.id == "299" }
                        
                    if (fhdVideo != null) {
                        // Find best M4A audio
                        val preferredAudio = audioStreams.filter {
                            it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
                        }.ifEmpty { audioStreams }
                        val bestAudio = preferredAudio.find { it.id == "141" }
                            ?: preferredAudio.find { it.id == "140" }
                            
                        if (bestAudio != null) {
                             streamUrl = fhdVideo.content
                             audioUrl = bestAudio.content
                             Log.d("TrailerLauncher", "Selected split FHD streams: Video=${fhdVideo.id}, Audio=${bestAudio.id}")
                        } else {
                             Log.w("TrailerLauncher", "Found 1080p video (${fhdVideo.id}) but NO suitable audio stream found.")
                        }
                    } else {
                        Log.d("TrailerLauncher", "No 1080p video-only stream found.")
                    }
                    
                    Log.d("TrailerLauncher", "Available Video-Only Streams: ${videoOnlyStreams.map { it.id }}")
                    Log.d("TrailerLauncher", "Available Audio Streams: ${audioStreams.map { it.id }}")
                    Log.d("TrailerLauncher", "Available Muxed Streams: ${muxedStreams.map { it.id }}")

                    // Fallback: 720p Muxed (itag 22)
                    if (streamUrl.isEmpty()) {
                        val hdStream = muxedStreams.find { it.id == "22" }
                        if (hdStream != null) {
                            streamUrl = hdStream.content
                            Log.d("TrailerLauncher", "Selected 720p progressive stream (itag 22)")
                        }
                    }

                    // Fallback: 360p Muxed (itag 18)
                    if (streamUrl.isEmpty()) {
                        val sdStream = muxedStreams.find { it.id == "18" }
                        if (sdStream != null) {
                            streamUrl = sdStream.content
                            Log.d("TrailerLauncher", "Selected 360p progressive stream (itag 18)")
                        }
                    }
                    
                    // Final Fallback: Best Available (revert to original sort logic, avoiding 4K if possible)
                    if (streamUrl.isEmpty()) {
                        // ... existing fallback or DASH check
                        val dashUrl = extractor.dashMpdUrl
                        if (dashUrl.isNotEmpty()) {
                            streamUrl = dashUrl
                            Log.d("TrailerLauncher", "Fallback to DASH manifest")
                        } else {
                            // Last resort
                            val bestMux = muxedStreams.firstOrNull()
                            streamUrl = bestMux?.content ?: ""
                            Log.d("TrailerLauncher", "Last resort muxed stream: ${bestMux?.id}")
                        }
                    }

                    if (streamUrl.isEmpty()) {
                        streamUrl = extractor.hlsUrl
                        if (streamUrl.isNotEmpty()) {
                            Log.d("TrailerLauncher", "Selected HLS manifest for $key")
                        }
                    }

                    if (streamUrl.isNotEmpty()) {
                        // Direct YouTube URLs reject MPV's open-ended Range requests.
                        // Download complete, bounded chunks before handing media to MPV.
                        if (android.net.Uri.parse(streamUrl).host?.endsWith(".googlevideo.com") == true
                            && android.net.Uri.parse(streamUrl).path == "/videoplayback") {
                            suspend fun cacheMedia(mediaUrl: String, suffix: String): String {
                                val file = java.io.File.createTempFile("elefin-trailer-", suffix, context.cacheDir)
                                cachedFiles += file
                                runInterruptible {
                                    com.flex.elefin.networking.TrailerMediaDownloader.download(mediaUrl, file)
                                }
                                return file.absolutePath
                            }
                            streamUrl = cacheMedia(streamUrl, ".mp4")
                            audioUrl = audioUrl?.let { cacheMedia(it, ".m4a") }
                        }
                        withContext(Dispatchers.Main) {
                            if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                                return@withContext
                            }
                            Log.d("TrailerLauncher", "Launching in-app trailer for $key")
                            val intent = CinemaPlayerActivity.createIntent(
                                context,
                                streamUrl,
                                "",
                                "Trailer: $title",
                                "trailer-$key"
                            ).apply {
                                if (audioUrl != null) {
                                    putExtra("audio_url", audioUrl)
                                    Log.d("TrailerLauncher", "Added audio_url to intent")
                                } else {
                                    Log.d("TrailerLauncher", "No audio_url to add to intent")
                                }
                                putExtra("is_trailer", true)
                                if (context !is android.app.Activity) {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            context.startActivity(intent)
                            handedToPlayer = true
                        }
                        return@launch
                    }
                    throw Exception("No playable YouTube stream found")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("TrailerLauncher", "In-app trailer playback failed for $key", e)
                    withContext(Dispatchers.Main) {
                        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                            return@withContext
                        }
                        if (e !is org.schabi.newpipe.extractor.exceptions.PrivateContentException) {
                            context.startActivity(TrailerWebPlayerActivity.createIntent(context, key, "Trailer: $title").apply {
                                if (context !is android.app.Activity) addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            return@withContext
                        }
                        Toast.makeText(context,
                            "This trailer is private and cannot be played.",
                            Toast.LENGTH_LONG).show()
                    }
                } finally {
                    if (!handedToPlayer) cachedFiles.forEach { it.delete() }
                }
            }.invokeOnCompletion {
                // Also reset if the lifecycle was already destroyed before launch ran.
                loading.set(false)
            }
        }
    }
}
