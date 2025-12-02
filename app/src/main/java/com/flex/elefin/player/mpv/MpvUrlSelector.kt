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
                "MediaBrowser Client=\"Elefin\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", Token=\"$accessToken\", Version=\"1.1.9\""
            } else {
                "MediaBrowser Client=\"Elefin\", Device=\"AndroidTV\", DeviceId=\"\", Version=\"1.1.9\""
            }
            append("X-Emby-Authorization: $authHeader\r\n")
            append("Accept: */*\r\n")
        }
    }

    /** ----------------------------
     *  DIRECT PLAY (BEST)
     *  Uses /Videos/{id}/stream?static=true
     *  This is the CORRECT URL format for MPV
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
     *  Fallback option
     *  ---------------------------- */
    fun buildDirectPlayOriginal(itemId: String, mediaSourceId: String? = null): MpvUrlResult {
        val msId = mediaSourceId ?: itemId
        val url = "$server/Videos/$itemId/original" +
                "?mediaSourceId=$msId" +
                "&api_key=$accessToken"

        return MpvUrlResult(url = url, headers = buildHeaders())
    }

    /** ----------------------------
     *  UNIVERSAL AUTO-SELECTOR
     *  (DirectPlay by default)
     *  ---------------------------- */
    fun auto(itemId: String, mediaSourceId: String? = null): MpvUrlResult {
        return buildDirectPlay(itemId, mediaSourceId)
    }
}
