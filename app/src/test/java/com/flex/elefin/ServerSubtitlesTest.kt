package com.flex.elefin.subtitles

import com.flex.elefin.jellyfin.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ServerSubtitlesTest {
    @Test fun encodesOpaqueProviderIdsAndPreservesServerPrefix() {
        val url = remoteSubtitleUrl("https://example.test/jellyfin", "item", "provider/a+b?x#y%z")
        assertEquals("https://example.test/jellyfin/Items/item/RemoteSearch/Subtitles/provider%2Fa+b%3Fx%23y%25z", url)
    }

    @Test fun mapsLanguagePickerToServerLanguageCodes() {
        assertEquals("eng", subtitleServerLanguage("en"))
        assertEquals("fra", subtitleServerLanguage("fr"))
        assertEquals("pob", subtitleServerLanguage("pt-br"))
        assertEquals("zht", subtitleServerLanguage("zh-tw"))
    }

    @Test fun doesNotConfuseExistingOrAmbiguousTracksWithADownload() {
        val old = MediaStream(Index = 3, Type = "Subtitle", IsExternal = true, Path = "old.srt", Language = "eng")
        val added = old.copy(Index = 4, Path = "new.srt")
        val before = MediaSource(Id = "source", MediaStreams = listOf(old))
        assertNull(newlyAddedSubtitle(before, before))
        assertEquals(added, newlyAddedSubtitle(before, before.copy(MediaStreams = listOf(old, added))))
        assertNull(newlyAddedSubtitle(before, before.copy(MediaStreams = listOf(old, added, added.copy(Index = 5)))))
        assertNull(newlyAddedSubtitle(before, before.copy(MediaStreams = listOf(old, added.copy(IsExternal = false)))))
    }

    @Test fun handlesNullableProviderFieldsAndUnknownMetadata() {
        val result = Json { ignoreUnknownKeys = true }.decodeFromString<RemoteSubtitleInfo>(
            """{"Id":"opaque-id","Name":null,"ProviderName":"OpenSubtitles","IsHashMatch":true,"Extra":1}""")
        assertNull(result.Name)
        assertTrue(result.IsHashMatch == true)
        assertEquals("opaque-id", result.Id)
    }

    @Test fun distinguishesDeniedAccessFromAnEmptySearch() {
        checkSubtitleResponse(204)
        try {
            checkSubtitleResponse(403)
            fail("Access denied must surface to the user")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("permission"))
        }
    }
}
