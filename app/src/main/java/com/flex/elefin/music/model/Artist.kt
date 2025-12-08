package com.flex.elefin.music.model

import androidx.compose.runtime.Stable

@Stable
data class Artist(
    val id: String,
    val name: String,
    val overview: String?,
    val imageUrl: String?,
    val albumCount: Int = 0,
    val songCount: Int = 0
)

