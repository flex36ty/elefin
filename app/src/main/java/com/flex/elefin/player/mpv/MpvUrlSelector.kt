package com.flex.elefin.player.mpv

data class MpvUrlResult(
    val url: String,
    val headers: String
)

class MpvUrlSelector(
    private val server: String,
    private val userId: String,
    private val accessToken: String,
    private val deviceId: String = ""
) {

    private fun buildHeaders(): String {
        return buildString {
            append("User-Agent: MPV-Android\r\n")
            append("Authorization: MediaBrowser Token=\"$accessToken\"\r\n")
            val authHeader = if (deviceId.isNotEmpty()) {
                "MediaBrowser Client=\"Elefin\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", Token=\"$accessToken\", Version=\"1.0\""
            } else {
                "MediaBrowser Client=\"Elefin\", Device=\"AndroidTV\", DeviceId=\"\", Version=\"1.0\""
            }
            append("X-Emby-Authorization: $authHeader\r\n")
            append("Accept: */*\r\n")
        }
    }

    /** ----------------------------
     *  DIRECT PLAY (BEST)
     *  ---------------------------- */
    fun buildDirectPlay(itemId: String, mediaSourceId: String? = null): MpvUrlResult {
        val msId = mediaSourceId ?: itemId
        val url = "$server/Videos/$itemId/stream" +
                "?static=true" +
                "&mediaSourceId=$msId" +
                "&api_key=$accessToken"

        return MpvUrlResult(url = url, headers = buildHeaders())
    }

    /** ----------------------------
     *  DIRECT PLAY (ORIGINAL)
     *  ---------------------------- */
    fun buildDirectPlayOriginal(itemId: String, mediaSourceId: String? = null): MpvUrlResult {
        val msId = mediaSourceId ?: itemId
        // ⭐ CRITICAL: Use lowercase "static" - MPV lowercases query params, must match Jellyfin's expectation
        // Using capital "Static=false" causes MPV to rewrite to "static=false" which Jellyfin treats differently
        val url = "$server/Videos/$itemId/stream" +
                "?static=false" +
                "&mediaSourceId=$msId" +
                "&api_key=$accessToken"

        return MpvUrlResult(url = url, headers = buildHeaders())
    }

    /** ----------------------------
     *  DIRECT STREAM (HLS)
     *  ---------------------------- */
    fun buildHls(itemId: String, mediaSourceId: String? = null): MpvUrlResult {
        val msId = mediaSourceId ?: itemId
        val url = "$server/Videos/$itemId/master.m3u8" +
                "?mediaSourceId=$msId" +
                "&api_key=$accessToken"

        return MpvUrlResult(url = url, headers = buildHeaders())
    }

    /** ----------------------------
     *  TRANSCODE (HLS)
     *  ---------------------------- */
    fun buildTranscodeHls(
        itemId: String,
        mediaSourceId: String? = null,
        maxBitrate: Int = 20000000,
        audioStream: Int? = null,
        subtitleStream: Int? = null
    ): MpvUrlResult {
        val msId = mediaSourceId ?: itemId
        val sb = StringBuilder()
        sb.append("$server/Videos/$itemId/master.m3u8")
        sb.append("?api_key=$accessToken")
        sb.append("&mediaSourceId=$msId")
        sb.append("&transcodingProtocol=hls")
        sb.append("&maxVideoBitrate=$maxBitrate")

        audioStream?.let { sb.append("&audioStreamIndex=$it") }
        subtitleStream?.let { sb.append("&subtitleStreamIndex=$it") }

        return MpvUrlResult(url = sb.toString(), headers = buildHeaders())
    }

    /** ----------------------------
     *  UNIVERSAL AUTO-SELECTOR
     *  (DirectPlay → DirectStream → Transcode)
     *  ---------------------------- */
    fun auto(itemId: String, mediaSourceId: String? = null): MpvUrlResult {
        // Use DirectPlay Stream by default (more reliable than /original)
        // The /original endpoint may not exist for all media items and can return 404
        // Fallback logic will handle trying /original if needed
        return buildDirectPlay(itemId, mediaSourceId)
    }
}
