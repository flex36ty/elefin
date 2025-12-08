package com.flex.elefin.music

/**
 * Navigation routes for the Music module
 */
sealed class MusicNav(val route: String) {
    object Home : MusicNav("music_home")
    object Artist : MusicNav("music_artist/{artistId}") {
        fun createRoute(artistId: String) = "music_artist/$artistId"
    }
    object Album : MusicNav("music_album/{albumId}") {
        fun createRoute(albumId: String) = "music_album/$albumId"
    }
    object NowPlaying : MusicNav("music_now_playing")
}

