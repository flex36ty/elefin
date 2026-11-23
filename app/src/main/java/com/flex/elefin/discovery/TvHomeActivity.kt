package com.flex.elefin.discovery

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.flex.elefin.*
import com.flex.elefin.jellyfin.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException

/** Exposes only resized launcher art belonging to the active account. */
class TvArtworkProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/jpeg"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val c = requireNotNull(context)
        val parts = uri.pathSegments
        if (mode != "r" || parts.size != 2 || parts[0] != TvHome.account(c) ||
            !AppSettings(c).tvHomeRecommendations || !JellyfinConfig(c).isConfigured() ||
            !parts[1].matches(Regex("[a-f0-9]{64}\\.jpg"))) throw FileNotFoundException()
        return ParcelFileDescriptor.open(File(File(c.filesDir, "tv-art"), parts[1]), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

/** Resolve IDs using the current authenticated account, never caller-supplied server URLs. */
class TvHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val config = JellyfinConfig(this@TvHomeActivity)
            val uri = intent.data
            val parts = uri?.pathSegments.orEmpty()
            val destination = try {
                if (uri?.scheme != "elefin-tv" || uri.host != "item" || parts.size != 2 ||
                    parts[0] != TvHome.account(this@TvHomeActivity) || !parts[1].matches(Regex("[A-Za-z0-9_-]{1,128}")) || !config.isConfigured() ||
                    !AppSettings(this@TvHomeActivity).tvHomeRecommendations) null
                else {
                    val api = JellyfinApiService(config.serverUrl, config.accessToken, config.userId, config)
                    val item = try { withTimeout(15000) { api.getItemDetails(parts[1]) } } finally { api.close() }
                    if (parts[0] != TvHome.account(this@TvHomeActivity)) null
                    else when(item?.Type) {
                        "Movie" -> MovieDetailsActivity.createIntent(this@TvHomeActivity, item)
                        "Episode" -> item.SeriesId?.let { SeriesDetailsActivity.createIntent(this@TvHomeActivity,
                            JellyfinItem(Id = it, Name = item.SeriesName.orEmpty()), episodeId = item.Id) }
                        "Series" -> SeriesDetailsActivity.createIntent(this@TvHomeActivity, item)
                        else -> null
                    }
                }
            } catch(e: TimeoutCancellationException) { null }
              catch(e: CancellationException) { throw e }
              catch(e: Exception) { null }
            startActivity(destination ?: Intent(this@TvHomeActivity, MainActivity::class.java))
            finish()
        }
    }
}
