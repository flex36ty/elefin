package com.flex.elefin.player.mpv

import android.content.Context
import android.util.Log
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.MediaStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MPV Subtitle Downloader
 * 
 * WHY THIS IS NEEDED:
 * MPV has a known bug where http-header-fields does NOT apply to sub-add commands.
 * This means MPV cannot load Jellyfin subtitles via HTTP because it won't send
 * authentication headers.
 * 
 * SOLUTION:
 * Pre-download subtitles to local cache, then load via file:// URLs.
 * This bypasses the authentication issue and makes subtitles work reliably.
 */
object MPVSubtitleDownloader {
    private const val TAG = "MPVSubtitleDownloader"
    
    /**
     * Download a single subtitle to local cache
     * 
     * @param context Android context for cache directory
     * @param apiService JellyfinApiService for building URLs
     * @param itemId Jellyfin item ID
     * @param mediaSourceId Media source ID
     * @param stream Subtitle stream metadata
     * @return Local file path if successful, null if failed
     */
    suspend fun downloadSubtitle(
        context: Context,
        apiService: JellyfinApiService,
        itemId: String,
        mediaSourceId: String,
        stream: MediaStream
    ): String? = withContext(Dispatchers.IO) {
        try {
            val streamIndex = stream.Index ?: return@withContext null
            val isExternal = stream.IsExternal == true
            
            // Build correct Jellyfin subtitle URL
            val subtitleUrl = apiService.buildJellyfinSubtitleUrl(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                streamIndex = streamIndex,
                isExternal = isExternal,
                codec = stream.Codec,
                path = stream.Path  // Include path for external subtitle detection
            )
            
            Log.d(TAG, "📥 Downloading subtitle for MPV: ${stream.DisplayTitle}")
            Log.d(TAG, "   URL: $subtitleUrl")
            Log.d(TAG, "   Index: $streamIndex, External: $isExternal, Codec: ${stream.Codec}")
            
            // Create HTTP client with reasonable timeouts
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
            
            // Build request
            val request = Request.Builder()
                .url(subtitleUrl)
                .build()
            
            // Execute download
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Failed to download subtitle: HTTP ${response.code}")
                Log.e(TAG, "   URL: $subtitleUrl")
                response.body?.close()
                return@withContext null
            }
            
            val responseBody = response.body
            if (responseBody == null) {
                Log.e(TAG, "❌ Subtitle download returned null body")
                return@withContext null
            }
            
            val contentLength = responseBody.contentLength()
            Log.d(TAG, "   Content-Length: $contentLength bytes")
            
            // Sanity check: subtitle files should be < 5MB
            if (contentLength > 5_000_000) {
                Log.e(TAG, "❌ Content-Length too large: $contentLength bytes (${contentLength / 1024 / 1024} MB)")
                Log.e(TAG, "   This might be the video file, not a subtitle!")
                responseBody.close()
                return@withContext null
            }
            
            // Determine file extension from codec
            val extension = when (stream.Codec?.lowercase()) {
                "srt", "subrip" -> "srt"
                "vtt", "webvtt" -> "vtt"
                "ass", "ssa" -> "ass"
                "ttml" -> "ttml"
                else -> "srt"
            }
            
            // Create cache file with unique name based on item ID and stream index
            val cacheDir = File(context.cacheDir, "mpv_subtitles")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val fileName = "sub_${itemId}_${streamIndex}.$extension"
            val subtitleFile = File(cacheDir, fileName)
            
            // Stream response to file
            var bytesDownloaded = 0L
            responseBody.byteStream().use { input ->
                java.io.FileOutputStream(subtitleFile).use { output ->
                    bytesDownloaded = input.copyTo(output)
                }
            }
            
            Log.d(TAG, "✅ Downloaded $bytesDownloaded bytes to: ${subtitleFile.absolutePath}")
            return@withContext subtitleFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading subtitle: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Download multiple subtitles in parallel
     * 
     * @param context Android context for cache directory
     * @param apiService JellyfinApiService instance
     * @param itemId Jellyfin item ID
     * @param mediaSourceId Media source ID
     * @param streams List of subtitle streams to download
     * @return Map of stream index to local file path
     */
    suspend fun downloadSubtitles(
        context: Context,
        apiService: JellyfinApiService,
        itemId: String,
        mediaSourceId: String,
        streams: List<MediaStream>
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<Int, String>()
        
        streams.forEach { stream ->
            val streamIndex = stream.Index ?: return@forEach
            val localPath = downloadSubtitle(
                context = context,
                apiService = apiService,
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                stream = stream
            )
            
            if (localPath != null) {
                results[streamIndex] = localPath
            }
        }
        
        Log.d(TAG, "✅ Downloaded ${results.size}/${streams.size} subtitles successfully")
        return@withContext results
    }
    
    /**
     * Clear old subtitle cache files to free up space
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "mpv_subtitles")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    file.delete()
                    Log.d(TAG, "Deleted cached subtitle: ${file.name}")
                }
            }
            Log.d(TAG, "✅ Cleared subtitle cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing subtitle cache", e)
        }
    }
}

