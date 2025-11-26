package com.flex.elefin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.screens.SeriesDetailsScreen

class SeriesDetailsActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_ITEM_NAME = "item_name"
        private const val EXTRA_FROM_LIBRARY = "from_library"
        private const val EXTRA_EPISODE_ID = "episode_id"
        
        const val SOURCE_HOME = "home"
        const val SOURCE_LIBRARY = "library"

        fun createIntent(
            context: Context,
            item: JellyfinItem,
            fromLibrary: Boolean = false,
            episodeId: String? = null
        ): Intent {
            return Intent(context, SeriesDetailsActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, item.Id)
                putExtra(EXTRA_ITEM_NAME, item.Name)
                putExtra(EXTRA_FROM_LIBRARY, fromLibrary)
                episodeId?.let { putExtra(EXTRA_EPISODE_ID, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: ""
        val fromLibrary = intent.getBooleanExtra(EXTRA_FROM_LIBRARY, false)
        val episodeId = intent.getStringExtra(EXTRA_EPISODE_ID)

        // Get Jellyfin configuration and API service
        val config = JellyfinConfig(this)
        val apiService = if (config.isConfigured()) {
            JellyfinApiService(
                baseUrl = config.serverUrl,
                accessToken = config.accessToken,
                userId = config.userId,
                config = config
            )
        } else {
            finish()
            return
        }

        // Create a minimal item object (details will be fetched in the screen)
        val item = JellyfinItem(
            Id = itemId,
            Name = itemName
        )

        val settings = AppSettings(this)
        
        setContent {
            JellyfinAppTheme {
                SeriesDetailsScreen(
                    item = item,
                    apiService = apiService,
                    showDebugOutlines = settings.showDebugOutlines,
                    initialEpisodeId = episodeId,
                    onBackPressed = {
                        if (fromLibrary) {
                            // Go back to library view (which is still MainActivity with selectedLibraryId)
                            finish()
                        } else {
                            // Go back to home screen (MainActivity)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

