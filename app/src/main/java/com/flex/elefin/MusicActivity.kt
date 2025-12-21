package com.flex.elefin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.flex.elefin.music.MusicNav
import com.flex.elefin.music.ui.MusicHomeScreen
// Apple Music-style UI screens
import com.flex.elefin.music.ui.apple.AppleAlbumScreen
import com.flex.elefin.music.ui.apple.AppleArtistScreen
import com.flex.elefin.music.ui.apple.AppleNowPlayingScreen

/**
 * Activity for the Music module.
 * Handles navigation between music screens using a simple state-based approach.
 */
class MusicActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JellyfinAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DisposableEffect(Unit) {
                        onDispose {
                            // Disconnect when activity is destroyed
                            com.flex.elefin.music.player.PlayerConnection.disconnect()
                        }
                    }
                    MusicNavHost(
                        onBackToHome = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, MusicActivity::class.java)
        }
    }
}

/**
 * Simple navigation host for music screens.
 */
@Composable
private fun MusicNavHost(
    onBackToHome: () -> Unit
) {
    // Navigation state
    var currentScreen by remember { mutableStateOf<MusicScreen>(MusicScreen.Home) }
    val navigationStack = remember { mutableStateListOf<MusicScreen>() }

    // Handle back navigation
    val onBack: () -> Unit = {
        if (navigationStack.isNotEmpty()) {
            // Use removeAt instead of removeLast for compatibility with older Android versions
            currentScreen = navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            onBackToHome()
        }
    }

    // Navigate to a new screen
    val navigateTo: (MusicScreen) -> Unit = { screen ->
        navigationStack.add(currentScreen)
        currentScreen = screen
    }

    when (val screen = currentScreen) {
        is MusicScreen.Home -> {
            MusicHomeScreen(
                onArtistClick = { artistId ->
                    navigateTo(MusicScreen.Artist(artistId))
                },
                onAlbumClick = { albumId ->
                    navigateTo(MusicScreen.Album(albumId))
                },
                onNowPlayingClick = {
                    navigateTo(MusicScreen.NowPlaying)
                },
                onBackPress = onBack
            )
        }
        is MusicScreen.Artist -> {
            AppleArtistScreen(
                artistId = screen.artistId,
                onAlbumClick = { albumId ->
                    navigateTo(MusicScreen.Album(albumId))
                },
                onBackPress = onBack
            )
        }
        is MusicScreen.Album -> {
            AppleAlbumScreen(
                albumId = screen.albumId,
                onArtistClick = { artistId ->
                    navigateTo(MusicScreen.Artist(artistId))
                },
                onBackPress = onBack
            )
        }
        is MusicScreen.NowPlaying -> {
            AppleNowPlayingScreen(
                onBackPress = onBack,
                onAlbumClick = { albumId ->
                    navigateTo(MusicScreen.Album(albumId))
                },
                onArtistClick = { artistId ->
                    navigateTo(MusicScreen.Artist(artistId))
                }
            )
        }
    }
}

/**
 * Sealed class representing music screens for navigation
 */
sealed class MusicScreen {
    object Home : MusicScreen()
    data class Artist(val artistId: String) : MusicScreen()
    data class Album(val albumId: String) : MusicScreen()
    object NowPlaying : MusicScreen()
}

