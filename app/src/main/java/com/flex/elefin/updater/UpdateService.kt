package com.flex.elefin.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // 5 minutes for large APK files
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
    
    /**
     * Downloads the APK file from the given URL and returns a File URI for installation
     * @param context Application context
     * @param apkUrl URL of the APK to download
     * @param progressCallback Optional callback for download progress (0-100) - will be called on Main dispatcher
     * @return File URI for the downloaded APK, or null if download failed
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        progressCallback: ((Int) -> Unit)? = null
    ): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting APK download from: $apkUrl")
                
                // Create download directory in app-specific external storage
                val downloadsDir = File(context.getExternalFilesDir(null), "updates")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                
                // Create file name from URL
                val fileName = "elefin-update.apk"
                val apkFile = File(downloadsDir, fileName)
                
                // Delete old file if exists
                if (apkFile.exists()) {
                    apkFile.delete()
                }
                
                // Download the file
                val request = Request.Builder()
                    .url(apkUrl)
                    .build()
                
                val response = downloadClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download APK: ${response.code}")
                    return@withContext null
                }
                
                val body = response.body ?: run {
                    Log.e(TAG, "Response body is null")
                    return@withContext null
                }
                
                val contentLength = body.contentLength()
                val input = body.byteStream()
                val output = FileOutputStream(apkFile)
                
                try {
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    var lastProgress = -1
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress if callback provided (on main thread for UI updates)
                        // Only update every 1% to avoid excessive callbacks
                        if (contentLength > 0 && progressCallback != null) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                withContext(Dispatchers.Main) {
                                    progressCallback(progress)
                                }
                            }
                        }
                    }
                    
                    // Ensure 100% is reported
                    if (contentLength > 0 && progressCallback != null) {
                        withContext(Dispatchers.Main) {
                            progressCallback(100)
                        }
                    }
                    
                    output.flush()
                    Log.d(TAG, "APK downloaded successfully: ${apkFile.absolutePath}")
                    
                    // Create FileProvider URI
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "com.flex.elefin.fileprovider",
                        apkFile
                    )
                    
                    Log.d(TAG, "APK URI created: $fileUri")
                    fileUri
                } finally {
                    input.close()
                    output.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                null
            }
        }
    }
    
    /**
     * Attempts to install an APK file. First tries Intent.ACTION_VIEW, 
     * then falls back to PackageInstaller if needed.
     * @param context Application context
     * @param apkUri URI of the APK file to install
     * @return true if installation started successfully, false otherwise
     */
    suspend fun installApk(context: Context, apkUri: Uri): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Attempting to launch installer with URI: $apkUri")
                
                // Grant URI permissions to allow the installer to access the file
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.grantUriPermission(
                    "android",
                    apkUri,
                    flags
                )
                
                // Try Intent.ACTION_VIEW with the APK file
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = apkUri
                    type = "application/vnd.android.package-archive"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(flags)
                }
                
                // Try to find any activity that can handle this
                val resolveInfo = context.packageManager.queryIntentActivities(installIntent, 0)
                if (resolveInfo.isNotEmpty()) {
                    try {
                        context.startActivity(installIntent)
                        Log.d(TAG, "Installer activity launched successfully")
                        return@withContext true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start installer activity", e)
                    }
                }
                
                // If ACTION_VIEW doesn't work, try ACTION_INSTALL_PACKAGE (API 14+)
                try {
                    val installPackageIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        data = apkUri
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(flags)
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    }
                    
                    val resolveInfo2 = context.packageManager.queryIntentActivities(installPackageIntent, 0)
                    if (resolveInfo2.isNotEmpty()) {
                        try {
                            context.startActivity(installPackageIntent)
                            Log.d(TAG, "Installer launched via ACTION_INSTALL_PACKAGE")
                            return@withContext true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start installer via ACTION_INSTALL_PACKAGE", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_INSTALL_PACKAGE not available", e)
                }
                
                Log.e(TAG, "No installer activity found")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error launching installer", e)
                false
            }
        }
    }
}

