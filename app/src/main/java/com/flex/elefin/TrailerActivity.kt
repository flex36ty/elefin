package com.flex.elefin

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.flex.elefin.player.mpv.MpvTvPlayerActivity
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
class TrailerActivity {
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
                             Log.d("TrailerActivity", "Selected split FHD streams: Video=${fhdVideo.id}, Audio=${bestAudio.id}")
                             Log.d("TrailerActivity", "Audio URL: $audioUrl")
                        } else {
                             Log.w("TrailerActivity", "Found 1080p video (${fhdVideo.id}) but NO suitable audio stream found.")
                        }
                    } else {
                        Log.d("TrailerActivity", "No 1080p video-only stream found.")
                    }
                    
                    // Fallback: 720p Muxed (itag 22)
                    if (streamUrl.isEmpty()) {
                        val hdStream = muxedStreams.find { it.id == "22" }
                        if (hdStream != null) {
                            streamUrl = hdStream.content
                            Log.d("TrailerActivity", "Selected 720p progressive stream (itag 22)")
                        }
                    }

                    // Fallback: 360p Muxed (itag 18)
                    if (streamUrl.isEmpty()) {
                        val sdStream = muxedStreams.find { it.id == "18" }
                        if (sdStream != null) {
                            streamUrl = sdStream.content
                            Log.d("TrailerActivity", "Selected 360p progressive stream (itag 18)")
                        }
                    }
                    
                    // Final Fallback: Best Available (revert to original sort logic, avoiding 4K if possible)
                    if (streamUrl.isEmpty()) {
                        // ... existing fallback or DASH check
                        val dashUrl = extractor.dashMpdUrl
                        if (dashUrl.isNotEmpty()) {
                             streamUrl = dashUrl
                             Log.d("TrailerActivity", "Fallback to DASH manifest")
                        } else {
                            // Last resort
                            val bestMux = muxedStreams.firstOrNull()
                            streamUrl = bestMux?.content ?: ""
                        }
                    }

                    if (streamUrl.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            Log.d("TrailerActivity", "Launching trailer: $streamUrl")
                            val intent = MpvTvPlayerActivity.createIntent(
                                context,
                                streamUrl,
                                "",
                                "Trailer: $title",
                                "trailer-$key"
                            ).apply {
                                if (audioUrl != null) {
                                    putExtra("audio_url", audioUrl)
                                    Log.d("TrailerActivity", "Added audio_url to intent")
                                } else {
                                    Log.d("TrailerActivity", "No audio_url to add to intent")
                                }
                                putExtra("is_trailer", true)
                            }
                            context.startActivity(intent)
                        }
                        return@launch
                    }
                    
                    /* REMOVED PREVIOUS LOGIC */
                        
                    // Removed: if (bestStream != null) ... logic replaced above
                        
                } catch (e: Exception) {
                    Log.e("TrailerActivity", "Error extracting trailer", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error playing trailer: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
