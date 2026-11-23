package com.flex.elefin.jellyfin

enum class PlaybackPlayer(val label: String) {
    EXOPLAYER("ExoPlayer"),
    MPV("MPV"),
    CINEMA("Elefin Cinema (MPV)");

    companion object {
        /** Respect saved selections; use Elefin MPV for an unconfigured player. */
        fun fromPreference(value: String?, legacyMpvEnabled: Boolean): PlaybackPlayer =
            entries.firstOrNull { it.name == value }
                ?: if (legacyMpvEnabled) MPV else CINEMA
    }
}
