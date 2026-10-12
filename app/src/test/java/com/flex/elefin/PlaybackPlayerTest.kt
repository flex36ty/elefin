package com.flex.elefin

import com.flex.elefin.jellyfin.PlaybackPlayer
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPlayerTest {
    @Test fun preservesLegacyPlayerAndDefault() {
        assertEquals(PlaybackPlayer.CINEMA, PlaybackPlayer.fromPreference(null, false))
        assertEquals(PlaybackPlayer.MPV, PlaybackPlayer.fromPreference(null, true))
    }

    @Test fun explicitSelectionTakesPriorityOverLegacyFlag() {
        for (player in PlaybackPlayer.entries) {
            for (legacy in listOf(false, true)) {
                assertEquals(player, PlaybackPlayer.fromPreference(player.name, legacy))
            }
        }
    }

    @Test fun unknownSelectionFallsBackToLegacyPreference() {
        assertEquals(PlaybackPlayer.MPV, PlaybackPlayer.fromPreference("unknown", true))
        assertEquals(PlaybackPlayer.CINEMA, PlaybackPlayer.fromPreference("unknown", false))
    }
}
