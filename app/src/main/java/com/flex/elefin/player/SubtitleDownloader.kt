package com.flex.elefin.player

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
 * Unified Subtitle Downloader for both ExoPlayer and MPV
 * 
 * Downloads subtitles to local cache when selected from the list,
 * then provides file:// URLs to both players.
 * 
 * Benefits:
 * - Fixes MPV authentication issues
 * - Makes ExoPlayer faster (no network delay)
 * - Enables offline subtitle switching
 * - Unified approach for both players
 */
object SubtitleDownloader {
    private const val TAG = "SubtitleDownloader"
    
    // Cache of downloaded subtitles: key = "itemId_streamIndex", value = local file path
    private val downloadedSubtitles = mutableMapOf<String, String>()
    
    /**
     * Download a subtitle to local cache (or return cached path if already downloaded)
     * 
     * @param context Android context for cache directory
     * @param apiService Jellyfin API service
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
            val cacheKey = "${itemId}_${streamIndex}"
            
            // Check if already downloaded
            downloadedSubtitles[cacheKey]?.let { cachedPath ->
                if (File(cachedPath).exists()) {
                    Log.d(TAG, "✅ Using cached subtitle: $cachedPath")
                    return@withContext cachedPath
                } else {
                    // Cached file deleted, remove from cache
                    downloadedSubtitles.remove(cacheKey)
                }
            }
            
            val isExternal = stream.IsExternal == true
            
            // Build correct Jellyfin subtitle URL
            val subtitleUrl = apiService.buildJellyfinSubtitleUrl(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                streamIndex = streamIndex,
                isExternal = isExternal,
                codec = stream.Codec,
                path = stream.Path
            )
            
            Log.d(TAG, "📥 Downloading subtitle: ${stream.DisplayTitle}")
            Log.d(TAG, "   URL: $subtitleUrl")
            Log.d(TAG, "   Index: $streamIndex, External: $isExternal, Codec: ${stream.Codec}")
            
            // Create HTTP client with longer timeouts
            // Jellyfin may need time to extract/transcode embedded subtitles
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)  // 1 minute for subtitle extraction
                .writeTimeout(30, TimeUnit.SECONDS)
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
                Log.e(TAG, "❌ Content-Length too large: $contentLength bytes")
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
            
            // Create cache directory
            val cacheDir = File(context.cacheDir, "subtitles")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // Create cache file with unique name
            val fileName = "sub_${itemId}_${streamIndex}.$extension"
            val subtitleFile = File(cacheDir, fileName)
            
            // Stream response to file
            var bytesDownloaded = 0L
            responseBody.byteStream().use { input ->
                java.io.FileOutputStream(subtitleFile).use { output ->
                    bytesDownloaded = input.copyTo(output)
                }
            }
            
            val localPath = subtitleFile.absolutePath
            Log.d(TAG, "✅ Downloaded $bytesDownloaded bytes to: $localPath")
            
            // Cache the result
            downloadedSubtitles[cacheKey] = localPath
            
            return@withContext localPath
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading subtitle: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Pre-download all subtitles for an item
     * This can be called in the background when an item is loaded
     * 
     * @param context Android context
     * @param apiService Jellyfin API service
     * @param itemId Jellyfin item ID
     * @param mediaSourceId Media source ID
     * @param streams List of subtitle streams to download
     * @return Map of stream index to local file path
     */
    suspend fun downloadAllSubtitles(
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
     * Get cached subtitle path if available
     */
    fun getCachedSubtitle(itemId: String, streamIndex: Int): String? {
        val cacheKey = "${itemId}_${streamIndex}"
        return downloadedSubtitles[cacheKey]?.let { path ->
            if (File(path).exists()) path else {
                downloadedSubtitles.remove(cacheKey)
                null
            }
        }
    }
    
    /**
     * Clear subtitle cache for a specific item
     */
    fun clearItemCache(context: Context, itemId: String) {
        val cacheDir = File(context.cacheDir, "subtitles")
        if (!cacheDir.exists()) return
        
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.contains(itemId)) {
                file.delete()
                Log.d(TAG, "Deleted cached subtitle: ${file.name}")
                
                // Remove from memory cache
                downloadedSubtitles.entries.removeIf { it.value == file.absolutePath }
            }
        }
    }
    
    /**
     * Clear all subtitle caches
     */
    fun clearAllCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "subtitles")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
                cacheDir.delete()
            }
            downloadedSubtitles.clear()
            Log.d(TAG, "✅ Cleared all subtitle caches")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing subtitle cache", e)
        }
    }
}

