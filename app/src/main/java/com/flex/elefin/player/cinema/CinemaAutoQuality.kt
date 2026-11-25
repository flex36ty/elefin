package com.flex.elefin.player.cinema

/** Buffer-driven quality controller. Called once per second; never treats pause as congestion. */
internal class CinemaAutoQuality {
    var capMbps: Int? = null; private set
    private var bufferingSeconds = 0
    private var stableSeconds = 0
    private var cooldown = 20
    private val levels = listOf(2, 4, 8, 12, 20, 40, 80, 120)
    fun tick(eligible: Boolean, buffering: Boolean, advancing: Boolean, sourceMbps: Int): Int? {
        if (cooldown > 0) cooldown--
        if (!eligible) { bufferingSeconds = 0; stableSeconds = 0; return null }
        if (buffering) { bufferingSeconds++; stableSeconds = 0 }
        else { bufferingSeconds = 0; if (advancing) stableSeconds++ else stableSeconds = 0 }
        if (cooldown > 0) return null
        val current = capMbps ?: sourceMbps.coerceAtLeast(3)
        return when {
            bufferingSeconds >= 8 -> levels.lastOrNull { it < current }
            stableSeconds >= 180 && capMbps != null -> levels.firstOrNull { it > current && it <= sourceMbps }
            else -> null
        }
    }
    fun changed(cap: Int) { capMbps = cap; defer() }
    fun defer() { cooldown = 60; bufferingSeconds = 0; stableSeconds = 0 }
}
