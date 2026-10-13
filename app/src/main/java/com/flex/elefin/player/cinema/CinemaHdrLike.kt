package com.flex.elefin.player.cinema

/** Accept only explicitly identified SDR transfer functions; never guess for unknown content. */
internal fun cinemaSupportsHdrLike(transfer: String?, primaries: String?): Boolean =
    transfer?.lowercase(java.util.Locale.ROOT) in setOf("bt.1886", "srgb", "gamma1.8", "gamma2.0", "gamma2.2", "gamma2.4", "gamma2.6", "gamma2.8") &&
        primaries?.lowercase(java.util.Locale.ROOT) in setOf("bt.709", "bt.601-525", "bt.601-625")

internal fun cinemaHdrLikeShader(strength: Int): String {
    val amount = String.format(java.util.Locale.ROOT, "%.3f", strength.coerceIn(0, 100) / 100.0)
    return """
        //!HOOK MAIN
        //!BIND HOOKED
        //!DESC Elefin Cinema SDR highlight contrast
        vec4 hook() {
            vec4 color = HOOKED_tex(HOOKED_pos);
            vec3 x = clamp(color.rgb, 0.0, 1.0);
            // Keep black/white endpoints fixed and use a mild, bounded tonal curve.
            vec3 lift = 0.22 * x * (1.0 - x) * (2.0 * x - 0.55);
            return vec4(clamp(x + $amount * lift, 0.0, 1.0), color.a);
        }
    """.trimIndent()
}
