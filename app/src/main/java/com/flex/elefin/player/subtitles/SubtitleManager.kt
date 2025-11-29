package com.flex.elefin.player.subtitles

import android.content.Context
import android.util.Log
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.MediaStream
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Manages subtitle loading for MPV player
 * Handles both embedded and external subtitles correctly
 */
class SubtitleManager(
    private val context: Context,
    private val api: JellyfinApiService,
) {
    companion object {
        private const val TAG = "SubtitleManager"
    }

    /**
     * Main entry point:
     * Handles both embedded AND external subtitles.
     * 
     * CRITICAL: A subtitle is only TRULY external if ALL THREE conditions are met:
     *   1. IsExternal == true
     *   2. SupportsExternalStream == true
     *   3. DeliveryUrl is not null/blank
     * 
     * If ANY condition fails, the subtitle is embedded (even if Jellyfin labels it "External").
     */
    suspend fun loadSubtitle(
        itemId: String,
        mediaSourceId: String,
        stream: MediaStream,
        mpv: MPVView
    ) {
        Log.d(TAG, "Loading subtitle: ${stream.DisplayTitle} (Index=${stream.Index})")
        Log.d(TAG, "  IsExternal=${stream.IsExternal}")
        Log.d(TAG, "  SupportsExternalStream=${stream.SupportsExternalStream}")
        Log.d(TAG, "  DeliveryUrl=${stream.DeliveryUrl}")
        
        // External subtitle detection:
        // - If IsExternal=true, it's a sidecar file (.srt, .vtt) → must download
        // - DeliveryUrl is OPTIONAL (Jellyfin doesn't always provide it for sidecar files)
        val isTrulyExternal = (stream.IsExternal == true)
        
        if (!isTrulyExternal) {
            // Embedded subtitle (inside video file/container)
            Log.d(TAG, "🔵 EMBEDDED subtitle detected → selecting internally via MPV")
            Log.d(TAG, "   Reason: IsExternal=${stream.IsExternal}")
            Log.d(TAG, "   Embedded subtitles are auto-detected by MPV from the video stream")
            Log.d(TAG, "   No HTTP download needed - MPV will handle internally")
            Log.d(TAG, "   Jellyfin subtitle index: ${stream.Index}")
            Log.d(TAG, "   Subtitle language: ${stream.Language}")
            Log.d(TAG, "   Subtitle codec: ${stream.Codec}")
            
            if (stream.Index != null) {
                // For embedded subtitles, we need to match by language, not by Jellyfin's index
                // MPV numbers tracks starting from 0, but Jellyfin's indices may not align
                Log.d(TAG, "🔍 Looking for MPV subtitle track matching: language=${stream.Language}, codec=${stream.Codec}")
                
                try {
                    // ⭐ CRITICAL: Wait for MPV to detect subtitle tracks
                    // MKV files with many subtitles can take 2-3 seconds to fully parse
                    // Retry up to 6 times (3 seconds total) until we see subtitle tracks
                    var trackCount = 0
                    var subtitleTracksFound = 0
                    var attempts = 0
                    val maxAttempts = 6
                    
                    while (attempts < maxAttempts && subtitleTracksFound == 0) {
                        kotlinx.coroutines.delay(500)
                        attempts++
                        
                        trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                        
                        // Count how many subtitle tracks we have
                        subtitleTracksFound = 0
                        for (i in 0 until trackCount) {
                            val trackType = MPVLib.getPropertyString("track-list/$i/type")
                            if (trackType == "sub") {
                                subtitleTracksFound++
                            }
                        }
                        
                        if (subtitleTracksFound > 0) {
                            Log.d(TAG, "✅ Found $subtitleTracksFound subtitle tracks after ${attempts * 500}ms")
                            break
                        } else {
                            Log.d(TAG, "⏳ Attempt $attempts/$maxAttempts: Only $trackCount tracks, no subtitles yet (waiting...)")
                        }
                    }
                    
                    if (subtitleTracksFound == 0) {
                        Log.w(TAG, "⚠️ No subtitle tracks found after ${maxAttempts * 500}ms - file may not contain embedded subtitles")
                        Log.w(TAG, "   Jellyfin reports 42 subtitles, but MPV only sees $trackCount tracks")
                        Log.w(TAG, "   This file may need subtitle extraction or has metadata issues")
                        return
                    }
                    
                    Log.d(TAG, "📊 MPV has $trackCount total tracks ($subtitleTracksFound subtitles)")
                    
                    // Find the subtitle track that matches our language
                    var mpvTrackId: Int? = null
                    
                    // Normalize Jellyfin's 3-letter code to 2-letter for matching
                    // fra → fr, eng → en, spa → es, etc.
                    val jellyfinLang = stream.Language ?: ""
                    val normalizedLang = when (jellyfinLang.take(3)) {
                        "eng" -> "en"
                        "fra" -> "fr"
                        "spa" -> "es"
                        "ger", "deu" -> "de"
                        "ita" -> "it"
                        "por" -> "pt"
                        "jpn" -> "ja"
                        "chi", "zho" -> "zh"
                        "kor" -> "ko"
                        "rus" -> "ru"
                        "ara" -> "ar"
                        "tur" -> "tr"
                        else -> jellyfinLang.take(2) // Default: use first 2 chars
                    }
                    
                    Log.d(TAG, "   Normalized language: $jellyfinLang → $normalizedLang")
                    
                    for (i in 0 until trackCount) {
                        val trackType = MPVLib.getPropertyString("track-list/$i/type")
                        if (trackType == "sub") {
                            val trackLang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                            val trackId = MPVLib.getPropertyInt("track-list/$i/id")
                            Log.d(TAG, "   MPV track $i: id=$trackId, type=sub, lang=$trackLang")
                            
                            // Match by language prefix (e.g., "fr" matches "fr-CA", "fr-FR", etc.)
                            if (trackLang.startsWith(normalizedLang, ignoreCase = true)) {
                                mpvTrackId = trackId
                                Log.d(TAG, "✅ Found matching track! MPV id=$trackId, lang=$trackLang matches $normalizedLang")
                                break
                            }
                        }
                    }
                    
                    if (mpvTrackId != null) {
                        // ✅ Found embedded subtitle track - select it
                        Log.d(TAG, "🎬 Sending MPV command: sid=$mpvTrackId (language match)")
                        MPVLib.setPropertyInt("sid", mpvTrackId)
                        
                        // Verify selection
                        val currentSid = MPVLib.getPropertyInt("sid")
                        Log.d(TAG, "📊 MPV current subtitle track (sid) = $currentSid")
                        
                        // Ensure visibility is on
                        MPVLib.setPropertyBoolean("sub-visibility", true)
                        val subVisibility = MPVLib.getPropertyBoolean("sub-visibility") ?: false
                        Log.d(TAG, "📊 MPV subtitle visibility = $subVisibility")
                        
                        // Apply codec-specific subtitle settings ONLY for PGS
                        try {
                            // Ensure visibility is on
                            MPVLib.setPropertyBoolean("sub-visibility", true)
                            
                            // Wait a moment for MPV to initialize the subtitle decoder
                            kotlinx.coroutines.delay(200)
                            
                            // Detect if this is PGS/SUP (bitmap) or text subtitle
                            val subCodec = MPVLib.getPropertyString("current-demuxer")
                            val isPGS = subCodec?.contains("pgs", ignoreCase = true) == true ||
                                       subCodec?.contains("sup", ignoreCase = true) == true ||
                                       stream.Codec?.contains("pgs", ignoreCase = true) == true ||
                                       stream.Codec?.contains("hdmv", ignoreCase = true) == true
                            
                            if (isPGS) {
                                // PGS/SUP Bitmap subtitles (Blu-ray) - NEED special settings
                                Log.d(TAG, "🎨 Detected PGS/SUP bitmap subtitle - applying bitmap settings")
                                MPVLib.command(arrayOf("set", "stretch-image-subs-to-screen", "yes"))
                                MPVLib.command(arrayOf("set", "image-subs-video-resolution", "no"))
                                MPVLib.setPropertyDouble("sub-scale", 3.0)
                                MPVLib.setPropertyInt("sub-pos", 90)
                                Log.d(TAG, "   Applied: stretch=yes, scale=3.0, pos=90 (bitmap mode)")
                            } else {
                                // Text subtitles (SRT, VTT, ASS) - use mpv.conf defaults
                                // DO NOT override! mpv.conf has optimal settings
                                Log.d(TAG, "🎨 Detected text subtitle (SRT/VTT/ASS) - using mpv.conf defaults")
                                Log.d(TAG, "   mpv.conf provides: sub-scale=2.4 (readable on TV)")
                                Log.d(TAG, "   NOT overriding (previous overrides shrunk text to invisibility)")
                            }
                            
                            // Log final configuration
                            val finalScale = MPVLib.getPropertyDouble("sub-scale")
                            val finalFontSize = MPVLib.getPropertyInt("sub-font-size")
                            val finalPos = MPVLib.getPropertyInt("sub-pos")
                            Log.d(TAG, "📊 Final subtitle config: scale=$finalScale, font-size=$finalFontSize, pos=$finalPos")
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error applying subtitle styling: ${e.message}", e)
                        }
                        
                        Log.d(TAG, "✅ Selected embedded subtitle track: Jellyfin Index=${stream.Index}, MPV id=$mpvTrackId")
                    } else {
                        // ❌ MPV can't find this subtitle track - it's NOT embedded!
                        // Even though Jellyfin says IsExternal=false, the subtitle doesn't exist in the container
                        // This is common for TV rips where subtitles were stripped during remuxing
                        Log.w(TAG, "⚠️ Subtitle NOT found in MPV tracks - attempting external download")
                        Log.w(TAG, "   Jellyfin Index=${stream.Index}, Language=${stream.Language}")
                        Log.w(TAG, "   This subtitle is NOT embedded despite Jellyfin metadata")
                        Log.w(TAG, "   Attempting to download from Jellyfin server...")
                        
                        // Try to download as external subtitle
                        val downloadedFile = downloadSubtitleFromJellyfin(itemId, mediaSourceId, stream)
                        if (downloadedFile != null) {
                            Log.d(TAG, "✅ Downloaded external subtitle: ${downloadedFile.absolutePath}")
                            // Load subtitle file into MPV
                            try {
                                val title = stream.DisplayTitle ?: stream.Language ?: "Subtitle"
                                MPVLib.command(arrayOf("sub-add", downloadedFile.absolutePath, "select", title))
                                MPVLib.setPropertyBoolean("sub-visibility", true)
                                Log.d(TAG, "🎉 External subtitle loaded into MPV: $title")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error loading downloaded subtitle into MPV: ${e.message}", e)
                            }
                        } else {
                            Log.e(TAG, "❌ Failed to download subtitle from Jellyfin - subtitle unavailable")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error selecting subtitle track: ${e.message}", e)
                }
            }
            return
        }

        // TRULY external subtitle → download from Jellyfin
        Log.d(TAG, "🔴 EXTERNAL subtitle detected (IsExternal=true) → downloading from Jellyfin")
        Log.d(TAG, "   This is a sidecar subtitle file (.srt, .vtt, etc.)")
        Log.d(TAG, "   DeliveryUrl=${stream.DeliveryUrl}")
        
        // Try to download using multiple endpoints (DeliveryUrl may be null for sidecar files)
        val downloadedFile = downloadSubtitleFromJellyfin(itemId, mediaSourceId, stream)
        if (downloadedFile == null) {
            Log.e(TAG, "❌ Failed to download external subtitle from any Jellyfin endpoint")
            return
        }
        
        Log.d(TAG, "✅ Downloaded external subtitle to: ${downloadedFile.absolutePath}")

        // Load subtitle into MPV using sub-add command
        val title = stream.DisplayTitle ?: stream.Language ?: "Subtitle"
        try {
            MPVLib.command(arrayOf("sub-add", downloadedFile.absolutePath, "select", title))
            MPVLib.setPropertyBoolean("sub-visibility", true)
            
            // Wait a moment for subtitle to be loaded
            kotlinx.coroutines.delay(300)
            
            // ⭐⭐⭐ CRITICAL FIX FOR MPV-ANDROID TEXT SUBTITLE VISIBILITY ⭐⭐⭐
            // When a video has BOTH PGS/SUP bitmap tracks AND text SRT/VTT tracks,
            // MPV switches to "bitmap subtitle mode" which DISABLES text rendering on Android!
            // We must FORCE text subtitle rendering mode AFTER loading the subtitle.
            Log.d(TAG, "🔧 FORCING text subtitle rendering mode (fixes PGS+SRT combo invisibility)")
            
            // Force MPV to render text subtitles in video layer (not hardware plane)
            MPVLib.command(arrayOf("set", "blend-subtitles", "video"))
            
            // Disable bitmap subtitle stretching (keeps text mode active)
            MPVLib.command(arrayOf("set", "stretch-image-subs-to-screen", "no"))
            MPVLib.command(arrayOf("set", "image-subs-video-resolution", "no"))
            
            // ⭐ CRITICAL: Force subtitle rendering on top of everything
            MPVLib.command(arrayOf("set", "sub-ass-override", "force"))
            MPVLib.command(arrayOf("set", "sub-fix-timing", "yes"))
            
            // Force text subtitle scaling and positioning
            MPVLib.command(arrayOf("set", "sub-scale-by-window", "no"))  // DISABLE window scaling (causes issues on Android)
            MPVLib.setPropertyDouble("sub-scale", 3.5)  // Even LARGER for visibility testing
            MPVLib.setPropertyInt("sub-pos", 90)
            MPVLib.setPropertyString("sub-color", "#FFFFFF")
            MPVLib.setPropertyInt("sub-border-size", 4)
            MPVLib.setPropertyString("sub-border-color", "#000000")
            
            Log.d(TAG, "📝 Applied text subtitle rendering fix for Android TV")
            Log.d(TAG, "   blend-subtitles=video, sub-scale=2.4, sub-pos=95")
            
            // Verify subtitle is actually selected and visible
            val currentSid = MPVLib.getPropertyInt("sid")
            val subVis = MPVLib.getPropertyBoolean("sub-visibility")
            val subScale = MPVLib.getPropertyDouble("sub-scale")
            val subFontSize = MPVLib.getPropertyInt("sub-font-size")
            val subPos = MPVLib.getPropertyInt("sub-pos")
            
            Log.d(TAG, "📊 EXTERNAL SUBTITLE STATUS:")
            Log.d(TAG, "   sid (current track) = $currentSid")
            Log.d(TAG, "   sub-visibility      = $subVis")
            Log.d(TAG, "   sub-scale           = $subScale")
            Log.d(TAG, "   sub-font-size       = $subFontSize")
            Log.d(TAG, "   sub-pos             = $subPos")
            
            // Check if subtitle file has content
            val subText = MPVLib.getPropertyString("sub-text")
            Log.d(TAG, "   sub-text (current)  = ${subText?.take(50) ?: "(no text at current timestamp)"}")
            
            if (subScale != null && subScale < 2.0) {
                Log.w(TAG, "⚠️ WARNING: sub-scale is too small ($subScale) - subtitles may be invisible on TV!")
                Log.w(TAG, "   Recommended: sub-scale >= 2.4 for 4K TVs")
            }
            
            if (subText.isNullOrBlank()) {
                Log.w(TAG, "⚠️ NO SUBTITLE TEXT at current playback position")
                Log.w(TAG, "   This is NORMAL if:")
                Log.w(TAG, "   - Video is in opening credits (no dialogue)")
                Log.w(TAG, "   - Current timestamp has no subtitle cue")
                Log.w(TAG, "   - SRT file timestamps don't match video")
                Log.w(TAG, "   → Try seeking to 1-2 minutes or to a scene with dialogue")
            } else {
                Log.d(TAG, "✅ Subtitle text IS present at current position - should be visible!")
            }
            
            Log.d(TAG, "🎉 External subtitle loaded into MPV: $title")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading subtitle into MPV: ${e.message}", e)
        }
    }

    /**
     * Download subtitle from Jellyfin using multiple fallback strategies.
     * Handles TV rips where Jellyfin metadata is incorrect about embedded vs external.
     */
    private suspend fun downloadSubtitleFromJellyfin(
        itemId: String,
        mediaSourceId: String,
        stream: MediaStream
    ): File? = withContext(Dispatchers.IO) {
        val index = stream.Index ?: return@withContext null
        
        // Try multiple Jellyfin subtitle endpoints in order of preference
        val urls = mutableListOf<String>()
        
        // 1. DeliveryUrl (if present - highest priority)
        if (!stream.DeliveryUrl.isNullOrBlank()) {
            urls.add(api.resolveDeliveryUrl(stream.DeliveryUrl!!))
        }
        
        // 2. Standard subtitle stream endpoint
        urls.add(api.buildSubtitleUrl(itemId, mediaSourceId, index))
        
        // 3. Subtitle codec download (for external subtitle files)
        val serverBase = api.serverBaseUrl.let { if (it.endsWith("/")) it.removeSuffix("/") else it }
        urls.add("$serverBase/Videos/$itemId/$mediaSourceId/Subtitles/$index/0/Stream.${stream.Codec ?: "srt"}?api_key=${api.apiKey}")
        
        // 4. Alternative: Try without codec extension
        urls.add("$serverBase/Videos/$itemId/Subtitles/$index/Stream.${stream.Codec ?: "srt"}?api_key=${api.apiKey}")
        
        // 5. Try the simple format (some Jellyfin versions use this)
        urls.add("$serverBase/Videos/$itemId/$mediaSourceId/Subtitles/$index?api_key=${api.apiKey}")
        
        Log.d(TAG, "🔄 Attempting to download subtitle from ${urls.size} endpoints...")
        
        for ((attemptNum, url) in urls.withIndex()) {
            Log.d(TAG, "   Attempt ${attemptNum + 1}/${urls.size}: $url")
            val file = downloadSubtitle(url)
            if (file != null) {
                Log.d(TAG, "   ✅ Success on attempt ${attemptNum + 1}")
                return@withContext file
            } else {
                Log.w(TAG, "   ❌ Failed attempt ${attemptNum + 1}")
            }
        }
        
        Log.e(TAG, "❌ All ${urls.size} download attempts failed for subtitle Index=$index")
        return@withContext null
    }
    
    /**
     * Downloads an external subtitle into the local cache.
     * MPV can ONLY load subtitles locally when headers are required.
     */
    private suspend fun downloadSubtitle(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val deviceId = api.getJellyfinConfig()?.deviceId ?: ""
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Elefin/1.0")
                .header("X-Emby-Token", api.apiKey)
                .header(
                    "X-Emby-Authorization",
                    "MediaBrowser Client=\"Elefin\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", Version=\"1.0.0\", Token=\"${api.apiKey}\""
                )
                .build()

            Log.d(TAG, "📥 Downloading subtitle from Jellyfin with auth headers...")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ HTTP error: ${response.code} - ${response.message}")
                return@withContext null
            }

            val extension = when {
                url.contains(".srt", ignoreCase = true) -> "srt"
                url.contains(".ass", ignoreCase = true) -> "ass"
                url.contains(".vtt", ignoreCase = true) -> "vtt"
                else -> "srt"
            }

            val outFile = File(context.cacheDir, "sub_${System.currentTimeMillis()}.$extension")
            response.body?.byteStream()?.use { input ->
                outFile.outputStream().use { out -> input.copyTo(out) }
            }
            
            Log.d(TAG, "✅ Subtitle downloaded: ${outFile.absolutePath} (${outFile.length()} bytes)")
            return@withContext outFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception downloading subtitle: ${e.message}", e)
            return@withContext null
        }
    }
}


