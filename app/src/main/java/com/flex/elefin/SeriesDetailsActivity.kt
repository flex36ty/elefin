package com.flex.elefin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.screens.SeriesDetailsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SeriesDetailsActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_ITEM_NAME = "item_name"
        private const val EXTRA_FROM_LIBRARY = "from_library"
        private const val EXTRA_EPISODE_ID = "episode_id"
        private const val EXTRA_AUTO_FOCUS_NEXT_UP = "auto_focus_next_up"
        
        const val SOURCE_HOME = "home"
        const val SOURCE_LIBRARY = "library"

        fun createIntent(
            context: Context,
            item: JellyfinItem,
            fromLibrary: Boolean = false,
            episodeId: String? = null,
            autoFocusNextUp: Boolean = false
        ): Intent {
            return Intent(context, SeriesDetailsActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, item.Id)
                putExtra(EXTRA_ITEM_NAME, item.Name)
                putExtra(EXTRA_FROM_LIBRARY, fromLibrary)
                episodeId?.let { putExtra(EXTRA_EPISODE_ID, it) }
                putExtra(EXTRA_AUTO_FOCUS_NEXT_UP, autoFocusNextUp)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: ""
        val fromLibrary = intent.getBooleanExtra(EXTRA_FROM_LIBRARY, false)
        val explicitEpisodeId = intent.getStringExtra(EXTRA_EPISODE_ID)
        val autoFocusNextUp = intent.getBooleanExtra(EXTRA_AUTO_FOCUS_NEXT_UP, false)
        
        android.util.Log.d("SeriesDetailsActivity", "onCreate: seriesId=$itemId, explicitEpisodeId=$explicitEpisodeId, autoFocusNextUp=$autoFocusNextUp, fromLibrary=$fromLibrary")

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
                // State for the resolved episode ID (may come from NextUp API)
                var resolvedEpisodeId by remember { mutableStateOf(explicitEpisodeId) }
                var hasResolvedNextUp by remember { mutableStateOf(explicitEpisodeId != null) }
                
                // If coming from library without explicit episode ID, fetch NextUp for this series
                // This auto-focuses on the next episode the user should watch
                // Fallback: if NextUp returns nothing (series not started), find the first unwatched episode
                LaunchedEffect(itemId, autoFocusNextUp) {
                    if (explicitEpisodeId == null && autoFocusNextUp && !hasResolvedNextUp) {
                        withContext(Dispatchers.IO) {
                            try {
                                // First try NextUp API (for series that have been started)
                                var nextUpEpisode = apiService.getNextUpForSeries(itemId)
                                
                                if (nextUpEpisode != null) {
                                    android.util.Log.d("SeriesDetailsActivity", "✅ Auto-focusing on NextUp episode: ${nextUpEpisode.Name} (ID: ${nextUpEpisode.Id})")
                                    resolvedEpisodeId = nextUpEpisode.Id
                                } else {
                                    // Fallback: find the first unwatched episode (for series not started yet)
                                    android.util.Log.d("SeriesDetailsActivity", "No NextUp found, searching for first unwatched episode...")
                                    val firstUnwatched = apiService.getFirstUnwatchedEpisode(itemId)
                                    if (firstUnwatched != null) {
                                        android.util.Log.d("SeriesDetailsActivity", "✅ Auto-focusing on first unwatched episode: ${firstUnwatched.Name} (ID: ${firstUnwatched.Id})")
                                        resolvedEpisodeId = firstUnwatched.Id
                                    } else {
                                        // All episodes watched or error - fallback to Season 1 Episode 1
                                        android.util.Log.d("SeriesDetailsActivity", "No unwatched episodes found, getting S1E1...")
                                        val firstEpisode = apiService.getFirstEpisode(itemId)
                                        if (firstEpisode != null) {
                                            android.util.Log.d("SeriesDetailsActivity", "✅ Auto-focusing on S1E1: ${firstEpisode.Name} (ID: ${firstEpisode.Id})")
                                            resolvedEpisodeId = firstEpisode.Id
                                        } else {
                                            android.util.Log.d("SeriesDetailsActivity", "Could not find any episodes, will focus on Season 1 button")
                                        }
                                    }
                                }
                                hasResolvedNextUp = true
                            } catch (e: Exception) {
                                android.util.Log.e("SeriesDetailsActivity", "Error fetching NextUp for series", e)
                                hasResolvedNextUp = true
                            }
                        }
                    } else if (explicitEpisodeId == null && !autoFocusNextUp) {
                        // No auto-focus requested, mark as resolved immediately
                        hasResolvedNextUp = true
                    }
                }
                
                // Only show screen after we've resolved the next up episode (or decided not to)
                if (hasResolvedNextUp) {
                    SeriesDetailsScreen(
                        item = item,
                        apiService = apiService,
                        showDebugOutlines = settings.showDebugOutlines,
                        initialEpisodeId = resolvedEpisodeId,
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
}

