package com.flex.elefin

import `is`.xyz.mpv.encodeMpvHttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Test

class MpvHttpHeadersTest {
    @Test fun authorizationCommasStayInsideOneHeader() {
        assertEquals(
            "Authorization: MediaBrowser Client=\"Elefin\"\\, Device=\"TV\"\\, Token=\"example\"",
            encodeMpvHttpHeaders("Authorization: MediaBrowser Client=\"Elefin\", Device=\"TV\", Token=\"example\"")
        )
    }
    @Test fun crlfHeadersBecomeSeparateListEntries() {
        assertEquals("Accept: video/*\\, */*,X-Test: one\\\\two",
            encodeMpvHttpHeaders("Accept: video/*, */*\r\nX-Test: one\\two\r\n"))
    }
    @Test fun emptyHeadersDoNotCreateBlankRequestLines() {
        assertEquals("", encodeMpvHttpHeaders("\r\n\n"))
    }
}
