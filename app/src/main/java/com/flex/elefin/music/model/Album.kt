package com.flex.elefin.music.model

import androidx.compose.runtime.Stable

@Stable
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String? = null,
    val year: Int?,
    val imageUrl: String?,
    val overview: String?,
    val trackCount: Int = 0,
    val durationTicks: Long = 0
)

