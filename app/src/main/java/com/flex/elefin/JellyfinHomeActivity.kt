package com.flex.elefin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.screens.JellyfinHomeScreen

class JellyfinHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JellyfinAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    JellyfinHomeScreen(
                        onItemClick = { item: JellyfinItem, resumePositionMs: Long ->
                            // TODO: Navigate to video player with item and resume position
                            // For now, just finish the activity
                            finish()
                        },
                        showDebugOutlines = false,
                        preloadLibraryImages = false
                    )
                }
            }
        }
    }
}
