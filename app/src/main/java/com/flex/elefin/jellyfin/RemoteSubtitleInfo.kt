package com.flex.elefin.jellyfin

import kotlinx.serialization.Serializable

@Serializable
data class RemoteSubtitleInfo(
    val Id: String? = null,
    val Name: String? = null,
    val ProviderName: String? = null,
    val ThreeLetterISOLanguageName: String? = null,
    val Format: String? = null,
    val IsHashMatch: Boolean? = null,
    val HearingImpaired: Boolean? = null,
    val Forced: Boolean? = null
)
