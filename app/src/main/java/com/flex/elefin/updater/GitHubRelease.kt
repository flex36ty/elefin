package com.flex.elefin.updater

import com.google.gson.annotations.SerializedName

/**
 * Data class for GitHub Release API response
 */
data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    
    val name: String,
    
    val body: String?,
    
    val assets: List<GitHubAsset>
)

/**
 * Data class for GitHub Release Asset
 */
data class GitHubAsset(
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String
)

