package com.flex.elefin.player.cinema

import com.flex.elefin.jellyfin.MediaStream
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CinemaDolbyRoutingTest {
    @Test fun preservesDolbyVisionMetadataFromJellyfin() {
        val stream = Json.decodeFromString<MediaStream>("""{"Type":"Video","Codec":"hevc","VideoRangeType":"DOVI","VideoDoViTitle":"Dolby Vision Profile 5","DvProfile":5}""")
        assertTrue(requiresNativeDolbyVision(stream))
        assertEquals(5, stream.DvProfile)
    }
    @Test fun recognizesMetadataVariantsWithoutTreatingAllHevcAsDolbyVision() {
        assertTrue(requiresNativeDolbyVision(MediaStream(VideoRangeType = "DOVIWithHDR10")))
        assertTrue(requiresNativeDolbyVision(MediaStream(VideoDoViTitle = "Dolby Vision Profile 5")))
        assertTrue(requiresNativeDolbyVision(MediaStream(DvProfile = 8)))
        assertFalse(requiresNativeDolbyVision(MediaStream(Codec = "hevc", VideoRangeType = "HDR10")))
        assertFalse(requiresNativeDolbyVision(MediaStream(Codec = "h264")))
        assertFalse(requiresNativeDolbyVision(null))
    }
}
