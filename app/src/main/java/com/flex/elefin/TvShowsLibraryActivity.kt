package com.flex.elefin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.screens.TvShowsLibraryScreen

/**
 * Activity for the TV Shows Library screen.
 * Shows a home-screen-like layout focused on TV shows only.
 */
class TvShowsLibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID) ?: ""
        val libraryName = intent.getStringExtra(EXTRA_LIBRARY_NAME) ?: "TV Shows"

        setContent {
            JellyfinAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BackHandler {
                        finish()
                    }
                    
                    TvShowsLibraryScreen(
                        libraryId = libraryId,
                        libraryName = libraryName,
                        onItemClick = { item: JellyfinItem, resumePositionMs: Long ->
                            // Route to appropriate details screen based on item type
                            val intent = when (item.Type) {
                                "Series" -> {
                                    // Auto-focus on the next episode the user needs to watch
                                    SeriesDetailsActivity.createIntent(
                                        context = this@TvShowsLibraryActivity,
                                        item = item,
                                        fromLibrary = true,
                                        autoFocusNextUp = true
                                    )
                                }
                                "Episode" -> {
                                    // Episodes navigate to series details screen, focused on that episode
                                    if (item.SeriesId != null) {
                                        val seriesItem = JellyfinItem(
                                            Id = item.SeriesId,
                                            Name = item.SeriesName ?: ""
                                        )
                                        SeriesDetailsActivity.createIntent(
                                            context = this@TvShowsLibraryActivity,
                                            item = seriesItem,
                                            fromLibrary = true,
                                            episodeId = item.Id
                                        )
                                    } else {
                                        // Fallback: go directly to video player if no SeriesId
                                        JellyfinVideoPlayerActivity.createIntent(
                                            context = this@TvShowsLibraryActivity,
                                            itemId = item.Id,
                                            resumePositionMs = resumePositionMs
                                        )
                                    }
                                }
                                else -> {
                                    // Fallback to series details for any other type
                                    SeriesDetailsActivity.createIntent(
                                        context = this@TvShowsLibraryActivity,
                                        item = item,
                                        fromLibrary = true,
                                        autoFocusNextUp = true
                                    )
                                }
                            }
                            startActivity(intent)
                        },
                        onBackPressed = { finish() }
                        // Note: onSearchClick and onSettingsClick are handled internally as dialogs
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_LIBRARY_ID = "library_id"
        private const val EXTRA_LIBRARY_NAME = "library_name"
        
        fun createIntent(context: Context, libraryId: String, libraryName: String): Intent {
            return Intent(context, TvShowsLibraryActivity::class.java).apply {
                putExtra(EXTRA_LIBRARY_ID, libraryId)
                putExtra(EXTRA_LIBRARY_NAME, libraryName)
            }
        }
    }
}

