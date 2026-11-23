package com.flex.elefin.player.cinema
import org.junit.Assert.*
import org.junit.Test
class CinemaShieldSupportTest {
    @Test fun supportsOnly2019ShieldModels() {
        assertTrue(supportsShieldAiUpscaling("NVIDIA", "mdarcy"))
        assertTrue(supportsShieldAiUpscaling("nvidia", "sif"))
        assertFalse(supportsShieldAiUpscaling("NVIDIA", "darcy"))
        assertFalse(supportsShieldAiUpscaling("NVIDIA", "foster"))
        assertFalse(supportsShieldAiUpscaling("TCL", "mdarcy"))
        assertFalse(supportsShieldAiUpscaling("NVIDIA", "unknown"))
    }
}
