package com.flex.elefin.subtitles

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OpenSubtitles.com API Client for downloading subtitles on demand
 * 
 * Flow:
 * 1. User presses "Download Subtitles" button
 * 2. User selects a language
 * 3. App searches OpenSubtitles for matching subtitle files
 * 4. User picks a subtitle from the results list
 * 5. App downloads the subtitle file
 * 6. Subtitle is saved locally and can be used with ExoPlayer
 * 
 * Users must configure their own API key in Settings.
 * Get a free API key at: https://www.opensubtitles.com/en/consumers
 * API Documentation: https://opensubtitles.stoplight.io/docs/opensubtitles-api/e3750fd63a100-getting-started
 */
object OpenSubtitlesApi {
    private const val TAG = "OpenSubtitles"
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    
    // API key must be set by user in Settings
    private var apiKey: String = ""
    
    // Authentication token (required for downloads)
    private var authToken: String? = null
    
    // Stored credentials for auto-login
    private var storedUsername: String = ""
    private var storedPassword: String = ""
    
    /**
     * Set the API key (required - must be called before making any API requests)
     */
    fun setApiKey(key: String) {
        apiKey = key
    }
    
    /**
     * Set credentials for authentication (required for downloads)
     */
    fun setCredentials(username: String, password: String) {
        storedUsername = username
        storedPassword = password
        // Clear existing token so we re-authenticate with new credentials
        authToken = null
    }
    
    /**
     * Check if API key is configured
     */
    fun isConfigured(): Boolean = apiKey.isNotBlank()
    
    /**
     * Check if credentials are configured
     */
    fun hasCredentials(): Boolean = storedUsername.isNotBlank() && storedPassword.isNotBlank()
    
    /**
     * Check if we have a valid auth token
     */
    fun isAuthenticated(): Boolean = authToken != null
    
    /**
     * Login to OpenSubtitles to get an authentication token
     * Required for downloading subtitles
     * 
     * @param username OpenSubtitles username
     * @param password OpenSubtitles password
     * @return true if login successful
     */
    suspend fun login(username: String = storedUsername, password: String = storedPassword): Boolean = withContext(Dispatchers.IO) {
        try {
            if (username.isBlank() || password.isBlank()) {
                Log.e(TAG, "❌ Username or password is empty")
                lastError = "Username or password is empty"
                return@withContext false
            }
            
            if (!isConfigured()) {
                Log.e(TAG, "❌ API key not configured")
                lastError = "API key not configured"
                return@withContext false
            }
            
            Log.d(TAG, "🔐 Logging in to OpenSubtitles as: $username")
            
            val loginBody = """{"username": "$username", "password": "$password"}"""
            
            val request = Request.Builder()
                .url("$BASE_URL/login")
                .header("Api-Key", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "ElefinPlayer v1.0")
                .post(loginBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Login failed: HTTP ${response.code}")
                responseBody?.let { Log.e(TAG, "Response: $it") }
                lastError = "Login failed (HTTP ${response.code})"
                return@withContext false
            }
            
            if (responseBody == null) {
                Log.e(TAG, "❌ Empty login response")
                lastError = "Empty login response"
                return@withContext false
            }
            
            Log.d(TAG, "📥 Login response: $responseBody")
            
            val loginResponse = json.decodeFromString<LoginResponse>(responseBody)
            
            if (loginResponse.token.isNullOrBlank()) {
                Log.e(TAG, "❌ No token in login response")
                lastError = "No token received"
                return@withContext false
            }
            
            authToken = loginResponse.token
            storedUsername = username
            storedPassword = password
            
            Log.d(TAG, "✅ Login successful! Downloads allowed: ${loginResponse.user?.allowedDownloads}")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Login error: ${e.message}", e)
            lastError = "Login error: ${e.message}"
            return@withContext false
        }
    }
    
    /**
     * Ensure we're authenticated before downloading
     */
    private suspend fun ensureAuthenticated(): Boolean {
        if (authToken != null) return true
        
        if (!hasCredentials()) {
            lastError = "OpenSubtitles login required. Please configure username/password in Settings."
            return false
        }
        
        return login()
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Search for subtitles by IMDB ID and language
     * 
     * @param imdbId IMDB ID (e.g., "tt1234567")
     * @param language Language code (e.g., "en", "es", "fr")
     * @param query Optional search query (movie/show name)
     * @return List of subtitle results
     */
    suspend fun searchSubtitles(
        imdbId: String? = null,
        tmdbId: String? = null,
        query: String? = null,
        language: String = "en",
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): List<SubtitleResult> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$BASE_URL/subtitles?")
            
            // Add search parameters
            if (!imdbId.isNullOrBlank()) {
                // Remove "tt" prefix if present for API
                val cleanImdbId = imdbId.removePrefix("tt")
                urlBuilder.append("imdb_id=$cleanImdbId&")
            }
            if (!tmdbId.isNullOrBlank()) {
                urlBuilder.append("tmdb_id=$tmdbId&")
            }
            if (!query.isNullOrBlank()) {
                urlBuilder.append("query=${java.net.URLEncoder.encode(query, "UTF-8")}&")
            }
            urlBuilder.append("languages=$language&")
            
            // For TV shows
            if (seasonNumber != null) {
                urlBuilder.append("season_number=$seasonNumber&")
            }
            if (episodeNumber != null) {
                urlBuilder.append("episode_number=$episodeNumber&")
            }
            
            // Order by download count (most popular first)
            urlBuilder.append("order_by=download_count&order_direction=desc")
            
            val url = urlBuilder.toString()
            Log.d(TAG, "🔍 Searching subtitles: $url")
            
            if (!isConfigured()) {
                Log.e(TAG, "❌ API key not configured")
                return@withContext emptyList()
            }
            
            val request = Request.Builder()
                .url(url)
                .header("Api-Key", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "ElefinPlayer v1.0")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Search failed: HTTP ${response.code}")
                response.body?.string()?.let { Log.e(TAG, "Response: $it") }
                return@withContext emptyList()
            }
            
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            Log.d(TAG, "📥 Search response: ${responseBody.take(500)}...")
            
            val searchResponse = json.decodeFromString<SubtitleSearchResponse>(responseBody)
            Log.d(TAG, "✅ Found ${searchResponse.data.size} subtitle(s)")
            
            return@withContext searchResponse.data
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching subtitles: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    // Last error message for UI display
    var lastError: String? = null
        private set
    
    /**
     * Download a subtitle file with retry logic for temporary failures
     * REQUIRES authentication - will auto-login if credentials are stored
     * 
     * @param fileId OpenSubtitles file ID
     * @param maxRetries Maximum number of retry attempts for 503 errors
     * @return Subtitle file content as ByteArray, or null if failed
     */
    suspend fun downloadSubtitle(fileId: Int, maxRetries: Int = 3): ByteArray? = withContext(Dispatchers.IO) {
        lastError = null
        
        try {
            Log.d(TAG, "📥 Requesting download link for file ID: $fileId")
            
            if (!isConfigured()) {
                Log.e(TAG, "❌ API key not configured")
                lastError = "API key not configured"
                return@withContext null
            }
            
            // Ensure we're authenticated (login if needed)
            if (!ensureAuthenticated()) {
                Log.e(TAG, "❌ Not authenticated - cannot download")
                // lastError is set by ensureAuthenticated
                return@withContext null
            }
            
            var attempt = 0
            var downloadResponse: okhttp3.Response? = null
            
            // Retry loop for handling 503 Service Unavailable
            while (attempt < maxRetries) {
                attempt++
                
                // Request download link - MUST include Authorization Bearer token
                val downloadRequest = Request.Builder()
                    .url("$BASE_URL/download")
                    .header("Api-Key", apiKey)
                    .header("Authorization", "Bearer $authToken")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "ElefinPlayer v1.0")
                    .post("""{"file_id": $fileId}""".toRequestBody("application/json".toMediaType()))
                    .build()
                
                downloadResponse = client.newCall(downloadRequest).execute()
                
                if (downloadResponse.isSuccessful) {
                    break // Success, exit retry loop
                }
                
                val code = downloadResponse.code
                
                // If we get 401, our token might be expired - try to re-login once
                if (code == 401 && attempt == 1) {
                    Log.w(TAG, "⚠️ Token expired, attempting re-login...")
                    authToken = null
                    downloadResponse.close()
                    if (login()) {
                        continue // Retry with new token
                    } else {
                        return@withContext null
                    }
                }
                
                // Only retry on 503 (Service Unavailable) or 429 (Rate Limited)
                if (code == 503 || code == 429) {
                    Log.w(TAG, "⚠️ Server temporarily unavailable (HTTP $code), attempt $attempt/$maxRetries")
                    downloadResponse.close()
                    
                    if (attempt < maxRetries) {
                        // Wait before retrying (exponential backoff: 1s, 2s, 4s)
                        val delayMs = (1000L * (1 shl (attempt - 1)))
                        Log.d(TAG, "⏳ Waiting ${delayMs}ms before retry...")
                        kotlinx.coroutines.delay(delayMs)
                    }
                } else {
                    // Non-retryable error
                    break
                }
            }
            
            if (downloadResponse == null || !downloadResponse.isSuccessful) {
                val code = downloadResponse?.code ?: 0
                Log.e(TAG, "❌ Download request failed: HTTP $code")
                downloadResponse?.body?.string()?.let { body ->
                    // Check for HTML error page vs JSON response
                    if (body.contains("Error 503") || body.contains("Service Temporarily Unavailable")) {
                        lastError = "OpenSubtitles is temporarily unavailable. Please try again later."
                        Log.e(TAG, "OpenSubtitles service is down (503)")
                    } else {
                        Log.e(TAG, "Response: $body")
                        lastError = "Download failed (HTTP $code)"
                    }
                }
                return@withContext null
            }
            
            val downloadBody = downloadResponse.body?.string() ?: return@withContext null
            Log.d(TAG, "📥 Download response: $downloadBody")
            
            val dlResponse = json.decodeFromString<SubtitleDownloadResponse>(downloadBody)
            val downloadLink = dlResponse.link
            
            if (downloadLink.isNullOrBlank()) {
                Log.e(TAG, "❌ No download link in response")
                lastError = "No download link received"
                return@withContext null
            }
            
            Log.d(TAG, "📥 Downloading from: $downloadLink")
            
            // Download the actual subtitle file - User-Agent required
            val fileRequest = Request.Builder()
                .url(downloadLink)
                .header("User-Agent", "ElefinPlayer v1.0")
                .get()
                .build()
            
            val fileResponse = client.newCall(fileRequest).execute()
            
            if (!fileResponse.isSuccessful) {
                Log.e(TAG, "❌ File download failed: HTTP ${fileResponse.code}")
                lastError = "File download failed (HTTP ${fileResponse.code})"
                return@withContext null
            }
            
            val bytes = fileResponse.body?.bytes()
            Log.d(TAG, "✅ Downloaded ${bytes?.size ?: 0} bytes")
            
            return@withContext bytes
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading subtitle: ${e.message}", e)
            lastError = e.message ?: "Unknown error"
            return@withContext null
        }
    }
    
    /**
     * Download and save subtitle to local storage
     * 
     * @param context Android context
     * @param itemId Jellyfin item ID (for organizing files)
     * @param subtitle Subtitle result to download
     * @return Local file path if successful, null if failed
     */
    suspend fun downloadAndSaveSubtitle(
        context: Context,
        itemId: String,
        subtitle: SubtitleResult
    ): String? = withContext(Dispatchers.IO) {
        try {
            val fileId = subtitle.attributes.files.firstOrNull()?.fileId
            if (fileId == null) {
                Log.e(TAG, "❌ No file ID in subtitle result")
                return@withContext null
            }
            
            val bytes = downloadSubtitle(fileId) ?: return@withContext null
            
            // Create directory for downloaded subtitles
            val subtitleDir = File(context.filesDir, "downloaded_subtitles/$itemId")
            subtitleDir.mkdirs()
            
            // Determine file extension from the original filename
            val originalFileName = subtitle.attributes.files.firstOrNull()?.fileName
            val extension = originalFileName
                ?.substringAfterLast(".", "srt")
                ?.lowercase()
                ?.takeIf { it in listOf("srt", "sub", "ass", "ssa", "vtt") }
                ?: "srt"
            
            // Create unique filename with GUARANTEED extension
            val language = subtitle.attributes.language ?: "unknown"
            // Clean release name - remove special chars, limit length, ensure no dots that could confuse extension
            val release = subtitle.attributes.release
                ?.take(40)
                ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                ?.replace(Regex("_+"), "_")  // Collapse multiple underscores
                ?.trimEnd('_')
                ?: "subtitle"
            
            // IMPORTANT: Always ensure .srt extension is present for ExoPlayer to detect MIME type
            val fileName = "${language}_${release}.${extension}"
            
            val file = File(subtitleDir, fileName)
            file.writeBytes(bytes)
            
            Log.d(TAG, "✅ Saved subtitle to: ${file.absolutePath}")
            Log.d(TAG, "📁 File name: $fileName, Extension: $extension, Size: ${bytes.size} bytes")
            
            return@withContext file.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving subtitle: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Get list of downloaded subtitles for an item
     */
    fun getDownloadedSubtitles(context: Context, itemId: String): List<DownloadedSubtitle> {
        val subtitleDir = File(context.filesDir, "downloaded_subtitles/$itemId")
        if (!subtitleDir.exists()) return emptyList()
        
        return subtitleDir.listFiles()?.mapNotNull { file ->
            val parts = file.nameWithoutExtension.split("_", limit = 2)
            val language = parts.getOrNull(0) ?: "unknown"
            val release = parts.getOrNull(1)?.replace("_", " ") ?: file.name
            
            DownloadedSubtitle(
                filePath = file.absolutePath,
                language = language,
                release = release,
                fileName = file.name
            )
        } ?: emptyList()
    }
    
    /**
     * Delete a downloaded subtitle
     */
    fun deleteDownloadedSubtitle(filePath: String): Boolean {
        return try {
            File(filePath).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting subtitle: ${e.message}")
            false
        }
    }
    
    /**
     * Clear all downloaded subtitles for an item
     */
    fun clearDownloadedSubtitles(context: Context, itemId: String) {
        val subtitleDir = File(context.filesDir, "downloaded_subtitles/$itemId")
        if (subtitleDir.exists()) {
            subtitleDir.deleteRecursively()
        }
    }
}

// Data classes for OpenSubtitles API responses

@Serializable
data class SubtitleSearchResponse(
    val data: List<SubtitleResult> = emptyList(),
    @SerialName("total_count")
    val totalCount: Int = 0
)

@Serializable
data class SubtitleResult(
    val id: String = "",
    val type: String = "",
    val attributes: SubtitleAttributes = SubtitleAttributes()
)

@Serializable
data class SubtitleAttributes(
    val language: String? = null,
    val release: String? = null,
    @SerialName("download_count")
    val downloadCount: Int = 0,
    @SerialName("hearing_impaired")
    val hearingImpaired: Boolean = false,
    @SerialName("machine_translated")
    val machineTranslated: Boolean = false,
    val fps: Double = 0.0,
    val votes: Int = 0,
    val ratings: Double = 0.0,
    val uploader: SubtitleUploader? = null,
    val files: List<SubtitleFile> = emptyList(),
    @SerialName("feature_details")
    val featureDetails: FeatureDetails? = null
)

@Serializable
data class SubtitleUploader(
    val name: String? = null,
    val rank: String? = null
)

@Serializable
data class SubtitleFile(
    @SerialName("file_id")
    val fileId: Int = 0,
    @SerialName("file_name")
    val fileName: String? = null,
    @SerialName("cd_number")
    val cdNumber: Int = 1
)

@Serializable
data class FeatureDetails(
    @SerialName("feature_id")
    val featureId: Int = 0,
    @SerialName("feature_type")
    val featureType: String? = null,
    val title: String? = null,
    val year: Int? = null,
    @SerialName("imdb_id")
    val imdbId: Int? = null
)

@Serializable
data class SubtitleDownloadResponse(
    val link: String? = null,
    @SerialName("file_name")
    val fileName: String? = null,
    val requests: Int = 0,
    val remaining: Int = 0,
    val message: String? = null,
    @SerialName("reset_time")
    val resetTime: String? = null,
    @SerialName("reset_time_utc")
    val resetTimeUtc: String? = null
)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val status: Int = 0,
    val user: LoginUser? = null
)

@Serializable
data class LoginUser(
    @SerialName("user_id")
    val userId: Int = 0,
    @SerialName("allowed_downloads")
    val allowedDownloads: Int = 0,
    val level: String? = null,
    val vip: Boolean = false
)

/**
 * Represents a locally downloaded subtitle file
 */
data class DownloadedSubtitle(
    val filePath: String,
    val language: String,
    val release: String,
    val fileName: String
)

/**
 * Supported subtitle languages
 */
object SubtitleLanguages {
    val languages = listOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "pt-br" to "Portuguese (Brazil)",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh-cn" to "Chinese (Simplified)",
        "zh-tw" to "Chinese (Traditional)",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "nl" to "Dutch",
        "pl" to "Polish",
        "tr" to "Turkish",
        "sv" to "Swedish",
        "da" to "Danish",
        "fi" to "Finnish",
        "no" to "Norwegian",
        "cs" to "Czech",
        "hu" to "Hungarian",
        "ro" to "Romanian",
        "el" to "Greek",
        "he" to "Hebrew",
        "th" to "Thai",
        "vi" to "Vietnamese",
        "id" to "Indonesian",
        "ms" to "Malay"
    )
    
    fun getDisplayName(code: String): String {
        return languages.find { it.first == code }?.second ?: code.uppercase()
    }
}

