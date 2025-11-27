package com.flex.elefin.updater

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service for checking GitHub releases for app updates
 */
object UpdateService {
    private const val TAG = "UpdateService"
    
    private const val GITHUB_USERNAME = "flex36ty"
    private const val GITHUB_REPO = "elefin"
    
    private val apiUrl = "https://api.github.com/repos/$GITHUB_USERNAME/$GITHUB_REPO/releases/latest"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Fetches the latest release from GitHub
     * @return GitHubRelease if successful, null otherwise
     */
    suspend fun getLatestRelease(): GitHubRelease? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                
                if (!response.isSuccessful || body == null) {
                    Log.w(TAG, "Failed to fetch latest release: ${response.code}")
                    return@withContext null
                }
                
                val release = gson.fromJson(body, GitHubRelease::class.java)
                Log.d(TAG, "Fetched latest release: ${release.name} (${release.tagName})")
                release
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching latest release", e)
                null
            }
        }
    }
    
    /**
     * Parses version tag (e.g., "v1.0.5") to numeric version code (e.g., 10005)
     */
    fun parseVersion(tag: String): Int {
        return try {
            tag.replace("v", "", ignoreCase = true)
                .split(".")
                .map { it.toInt() }
                .let { parts ->
                    val major = parts.getOrNull(0) ?: 0
                    val minor = parts.getOrNull(1) ?: 0
                    val patch = parts.getOrNull(2) ?: 0
                    major * 10000 + minor * 100 + patch
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing version tag: $tag", e)
            0
        }
    }
    
    /**
     * Checks if an update is available
     * @param remoteVersionCode Version code from GitHub release
     * @param localVersionCode Current app version code
     * @return true if remote version is newer
     */
    fun updateAvailable(remoteVersionCode: Int, localVersionCode: Int): Boolean {
        return remoteVersionCode > localVersionCode
    }
}

