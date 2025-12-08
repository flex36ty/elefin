package com.flex.elefin.music.model

import androidx.compose.runtime.Stable

@Stable
data class Track(
    val id: String,
    val name: String,
    val album: String,
    val albumId: String? = null,
    val artist: String,
    val artistId: String? = null,
    val trackNumber: Int,
    val discNumber: Int = 1,
    val durationMs: Long,
    val imageUrl: String?,
    val streamUrl: String,
    val codec: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int? = null
)

