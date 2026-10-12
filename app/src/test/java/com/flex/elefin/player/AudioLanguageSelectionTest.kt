package com.flex.elefin.player

import com.flex.elefin.jellyfin.MediaStream
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AudioLanguageSelectionTest {
    @Test fun deviceLanguageOverridesForeignDefault() {
        val tracks = listOf(MediaStream(Index=1, Type="Audio", Language="spa", IsDefault=true), MediaStream(Index=2, Type="Audio", Language="eng"))
        assertEquals(2, preferredAudioIndex(tracks, Locale.US))
    }
    @Test fun acceptsBibliographicAndRegionalCodes() {
        val tracks = listOf(MediaStream(Index=1, Type="Audio", Language="eng", IsDefault=true), MediaStream(Index=2, Type="Audio", Language="fre"))
        assertEquals(2, preferredAudioIndex(tracks, Locale.CANADA_FRENCH))
        assertEquals(2, preferredAudioIndex(listOf(MediaStream(Index=2, Type="Audio", Language="fr-CA")), Locale.FRANCE))
    }
    @Test fun missingLanguageFallsBackToDefault() {
        val tracks = listOf(MediaStream(Index=1, Type="Audio", Language="und"), MediaStream(Index=2, Type="Audio", Language="eng", IsDefault=true))
        assertEquals(2, preferredAudioIndex(tracks, Locale.JAPAN))
        assertNull(preferredAudioIndex(emptyList(), Locale.US))
    }
}
