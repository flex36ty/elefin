package com.flex.elefin.player.cinema

/** 2019 Shield devices; older Tegra X1 models do not provide AI upscaling. */
internal fun supportsShieldAiUpscaling(manufacturer: String, device: String): Boolean =
    manufacturer.equals("NVIDIA", ignoreCase = true) &&
        device.lowercase(java.util.Locale.ROOT) in setOf("mdarcy", "sif")
