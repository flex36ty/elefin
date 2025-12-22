package com.flex.elefin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyseerr.JellyseerrApiService
import com.flex.elefin.screens.JellyseerrDetailsScreen

class JellyseerrDetailsActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_MEDIA_TYPE = "media_type" // "movie" or "tv"

        fun createIntent(
            context: Context,
            tmdbId: Int,
            mediaType: String
        ): Intent {
            return Intent(context, JellyseerrDetailsActivity::class.java).apply {
                putExtra(EXTRA_TMDB_ID, tmdbId)
                putExtra(EXTRA_MEDIA_TYPE, mediaType)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1)
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "movie"

        if (tmdbId == -1) {
            finish()
            return
        }

        val settings = AppSettings(this)
        
        // Create Jellyseerr API service
        val apiService = if (settings.jellyseerrEnabled && settings.jellyseerrUrl.isNotBlank() && settings.jellyseerrApiKey.isNotBlank()) {
            try {
                JellyseerrApiService.withApiKey(
                    baseUrl = settings.jellyseerrUrl,
                    apiKey = settings.jellyseerrApiKey
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        setContent {
            JellyfinAppTheme {
                JellyseerrDetailsScreen(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    apiService = apiService,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}
