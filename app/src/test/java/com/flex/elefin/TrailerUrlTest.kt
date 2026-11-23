package com.flex.elefin

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.flex.elefin.jellyfin.JellyfinItem

class TrailerUrlTest {
    @Test fun supportsServerYouTubeLinks() {
        for (url in listOf(
            "https://www.youtube.com/watch?v=pf4sOGLOV9Y&feature=share",
            "https://youtu.be/pf4sOGLOV9Y?si=test",
            "https://m.youtube.com/shorts/pf4sOGLOV9Y",
            "https://www.youtube-nocookie.com/embed/pf4sOGLOV9Y"
        )) assertEquals("pf4sOGLOV9Y", TrailerUrl.youtubeId(url))
    }

    @Test fun rejectsUnsupportedOrMisleadingLinks() {
        for (url in listOf(
            "https://youtube.com.attacker.test/watch?v=pf4sOGLOV9Y",
            "https://youtube.com@attacker.test/watch?v=pf4sOGLOV9Y",
            "file:///watch?v=pf4sOGLOV9Y",
            "https://youtube.com/watch?v=bad",
            "https://youtube.com/watch?v=pf4sOGLOV9Y%2Fbad"
        )) assertNull(TrailerUrl.youtubeId(url))
        for (url in listOf("file:///trailer.mp4", "content://media/trailer.mp4",
            "https://example.com/watch", "https://user:password@example.com/trailer.mp4")) {
            assertNull(TrailerUrl.directMedia(url))
        }
    }

    @Test fun preservesSignedMediaUrls() {
        val url = "https://example.com/trailer.M3U8?signature=abc&expires=123"
        assertEquals(url, TrailerUrl.directMedia(url))
    }

    @Test fun readsRemoteTrailersAndAcceptsMissingMetadata() {
        val json = Json { ignoreUnknownKeys = true }
        val item = json.decodeFromString<JellyfinItem>("""{"Id":"1","Name":"Movie","RemoteTrailers":[{"Url":"https://youtu.be/pf4sOGLOV9Y","Name":"Trailer","Extra":true}]}""")
        assertEquals("pf4sOGLOV9Y", TrailerUrl.youtubeId(item.RemoteTrailers!!.first().Url!!))
        assertNull(json.decodeFromString<JellyfinItem>("""{"Id":"1","Name":"Movie"}""").RemoteTrailers)
    }
}
