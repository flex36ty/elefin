package com.flex.elefin

/** Only supported web video URLs are handed to a player; never file/content URIs. */
internal object TrailerUrl {
    fun youtubeId(value: String): String? {
        val uri = runCatching { java.net.URI(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val id = when (host) {
            "youtu.be" -> parts.singleOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
            "youtube-nocookie.com", "www.youtube-nocookie.com" -> when {
                parts.firstOrNull() in setOf("embed", "shorts", "live") -> parts.getOrNull(1)
                uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&')
                    .firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
                else -> null
            }
            else -> null
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    fun directMedia(value: String): String? {
        val url = value.trim()
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return url.takeIf {
            uri.path.orEmpty().substringAfterLast('.').lowercase() in
                setOf("mp4", "m4v", "webm", "mov", "m3u8", "mpd")
        }
    }
}
