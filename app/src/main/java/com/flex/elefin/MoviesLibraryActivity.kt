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
import com.flex.elefin.screens.MoviesLibraryScreen

/**
 * Activity for the Movies Library screen.
 * Shows a home-screen-like layout focused on movies only.
 */
class MoviesLibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID) ?: ""
        val libraryName = intent.getStringExtra(EXTRA_LIBRARY_NAME) ?: "Movies"

        setContent {
            JellyfinAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BackHandler {
                        finish()
                    }
                    
                    MoviesLibraryScreen(
                        libraryId = libraryId,
                        libraryName = libraryName,
                        onItemClick = { item: JellyfinItem, resumePositionMs: Long ->
                            // Route to appropriate details screen based on item type
                            val intent = when (item.Type) {
                                "Movie" -> {
                                    MovieDetailsActivity.createIntent(
                                        context = this@MoviesLibraryActivity,
                                        item = item,
                                        fromLibrary = true
                                    )
                                }
                                else -> {
                                    // Fallback to movie details for any other type
                                    MovieDetailsActivity.createIntent(
                                        context = this@MoviesLibraryActivity,
                                        item = item,
                                        fromLibrary = true
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
            return Intent(context, MoviesLibraryActivity::class.java).apply {
                putExtra(EXTRA_LIBRARY_ID, libraryId)
                putExtra(EXTRA_LIBRARY_NAME, libraryName)
            }
        }
    }
}

