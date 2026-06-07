package com.flex.elefin

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.flex.elefin.player.mpv.MpvTvPlayerActivity
import com.flex.elefin.ui.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Utility to launch trailers using NewPipe Extractor to play in-app.
 */
class TrailerLauncher {
    companion object {
        fun launchTmdbTrailer(context: Context, key: String, title: String) {
            val youtubeUrl = "https://www.youtube.com/watch?v=$key"
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Use ServiceList.YouTube (id 0)
                    val streamingService = ServiceList.YouTube
                    val extractor = streamingService.getStreamExtractor(youtubeUrl)
                    extractor.fetchPage()
                    
                    val streamInfo = extractor as? YoutubeStreamExtractor
                    
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
                        val bestAudio = audioStreams.find { it.id == "141" } // 256k
                            ?: audioStreams.find { it.id == "140" } // 128k
                            
                        if (bestAudio != null) {
                             streamUrl = fhdVideo.content
                             audioUrl = bestAudio.content
                             Log.d("TrailerLauncher", "Selected split FHD streams: Video=${fhdVideo.id}, Audio=${bestAudio.id}")
                             Log.d("TrailerLauncher", "Audio URL: $audioUrl")
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

                    if (streamUrl.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            Log.d("TrailerLauncher", "Launching trailer: $streamUrl")
                            val intent = MpvTvPlayerActivity.createIntent(
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
                            }
                            context.startActivity(intent)
                        }
                        return@launch
                    }
                    throw Exception("No playable YouTube stream found")
                } catch (e: Exception) {
                    Log.e("TrailerLauncher", "Error extracting trailer, falling back to YouTube App", e)
                    withContext(Dispatchers.Main) {
                        val appIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$key"))
                        val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$key"))
                        try {
                            if (context !is android.app.Activity) {
                                appIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(appIntent)
                        } catch (ex: Exception) {
                            try {
                                if (context !is android.app.Activity) {
                                    webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(webIntent)
                            } catch (exc: Exception) {
                                Toast.makeText(context, "Error playing trailer: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}
