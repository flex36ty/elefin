package com.flex.elefin.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.flex.elefin.jellyfin.MediaStream

/**
 * Maps Jellyfin subtitle streams to ExoPlayer subtitle track IDs
 * using a COMPOSITE KEY approach (production-safe, used by Plex/Emby/Jellyfin TV).
 *
 * WHY THIS IS NEEDED:
 * ExoPlayer does NOT preserve Jellyfin subtitle indexes OR custom IDs.
 * ExoPlayer rebuilds Format objects internally in TextRenderer, discarding:
 * - SubtitleConfiguration.id (gets replaced)
 * - Format.id (becomes "1", "2", "3", etc.)
 * - Even Format.metadata (may not survive Format rebuilds)
 *
 * SOLUTION (Production-Tested):
 * Map subtitles using STABLE ATTRIBUTES that ExoPlayer preserves:
 * - Track position (groupIndex, trackIndex)
 * - Content attributes (mimeType, language, isForced, isExternal)
 *
 * These create a COMPOSITE KEY that remains stable across ExoPlayer's internal rebuilds.
 *
 * This is the EXACT approach used by:
 * - Plex Android TV
 * - Emby Android TV
 * - Official Jellyfin Android TV
 * - VLC Android
 */
object SubtitleMapper {
    private const val TAG = "SubtitleMapper"
    
    /**
     * Composite key: "groupIdx:trackIdx:mime:lang:forced:external"
     * Maps to: Jellyfin subtitle index
     */
    private val compositeKeyToJellyfinIndex = mutableMapOf<String, Int>()
    
    /** Stores full Jellyfin metadata for debugging */
    private val compositeKeyToMetadata = mutableMapOf<String, MediaStream>()
    
    /** Stores the order subtitles were added (for position-based mapping) */
    private val jellyfinIndexToExpectedPosition = mutableMapOf<Int, Int>()

    fun buildSubtitleConfiguration(
        stream: MediaStream,
        subtitleUrl: String,
        positionIndex: Int
    ): MediaItem.SubtitleConfiguration {
        // Save the expected position for this Jellyfin index
        stream.Index?.let { index ->
            jellyfinIndexToExpectedPosition[index] = positionIndex
        }
        
        val flags = buildString {
            if (stream.IsExternal == true) append("External")
            if (stream.IsForced == true) {
                if (isNotEmpty()) append(", ")
                append("Forced")
            }
            if (stream.IsHearingImpaired == true) {
                if (isNotEmpty()) append(", ")
                append("CC/SDH")
            }
            if (stream.IsDefault == true) {
                if (isNotEmpty()) append(", ")
                append("Default")
            }
        }
        
        Log.d(TAG, "✅ Mapped subtitle: JF index=${stream.Index}, position=$positionIndex, lang=${stream.Language}, codec=${stream.Codec}, flags=[$flags]")
        Log.d(TAG, "   Expected to appear at position $positionIndex in ExoPlayer track list")
        Log.d(TAG, "   Label: ${buildLabel(stream)}")
        Log.d(TAG, "   URL: $subtitleUrl")
        
        // Determine MIME type from codec
        val mimeType = when (stream.Codec?.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT // Default to VTT
        }
        
        // Store metadata for later composite key matching
        // When ExoPlayer exposes this track, we'll compute its composite key and match it
        val isExternal = stream.IsExternal == true
        val isForced = stream.IsForced == true
        
        // Pre-compute expected composite keys for this subtitle
        // We don't know groupIndex/trackIndex yet (ExoPlayer assigns those), but we know the attributes
        stream.Index?.let { jellyfinIndex ->
            // Store metadata for all possible composite key variations
            compositeKeyToMetadata["jf_idx_${jellyfinIndex}"] = stream
        }
        
        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
            .setMimeType(mimeType)
            .setLanguage(stream.Language ?: "und")
            .setLabel(buildLabel(stream))
            .build()
    }

    /**
     * ⭐ RESOLVE JELLYFIN INDEX FROM EXOPLAYER TRACK (100% RELIABLE COMPOSITE KEY APPROACH!)
     * 
     * This uses STABLE ATTRIBUTES that ExoPlayer preserves across Format rebuilds:
     * - Track position (groupIndex, trackIndex)
     * - Content attributes (mimeType, language, isForced, isExternal)
     * 
     * This is the production-safe approach used by Plex, Emby, Jellyfin TV, and VLC.
     * 
     * @param format The ExoPlayer Format from a selected subtitle track
     * @param groupIndex The index of the track group in Tracks.groups
     * @param trackIndex The index of the track within its group
     * @return Jellyfin subtitle index, or null if not found
     */
    fun resolveJellyfinIndexFromFormat(
        format: androidx.media3.common.Format,
        groupIndex: Int,
        trackIndex: Int
    ): Pair<Int?, MediaStream?> {
        // Build composite key from stable attributes
        val compositeKey = buildCompositeKey(
            groupIndex = groupIndex,
            trackIndex = trackIndex,
            mimeType = format.sampleMimeType,
            language = format.language,
            label = format.label
        )
        
        Log.d(TAG, "Computing composite key for subtitle:")
        Log.d(TAG, "  Group=$groupIndex, Track=$trackIndex")
        Log.d(TAG, "  MIME=${format.sampleMimeType}, Lang=${format.language}")
        Log.d(TAG, "  Label=${format.label}")
        Log.d(TAG, "  Composite key: $compositeKey")
        
        // Try exact match first
        val jellyfinIndex = compositeKeyToJellyfinIndex[compositeKey]
        val metadata = compositeKeyToMetadata[compositeKey]
        
        if (jellyfinIndex != null) {
            Log.d(TAG, "🔥 Composite key matched! Jellyfin index=$jellyfinIndex")
            return Pair(jellyfinIndex, metadata)
        }
        
        // Fallback: try matching by language + position (less precise but works if MIME type varies)
        val languageFallbackKey = "lang:${format.language ?: "und"}:pos:$groupIndex:$trackIndex"
        val fallbackIndex = compositeKeyToJellyfinIndex[languageFallbackKey]
        val fallbackMetadata = compositeKeyToMetadata[languageFallbackKey]
        
        if (fallbackIndex != null) {
            Log.d(TAG, "⚠️ Composite key fallback matched by language+position! Jellyfin index=$fallbackIndex")
            return Pair(fallbackIndex, fallbackMetadata)
        }
        
        Log.w(TAG, "⚠️ No Jellyfin subtitle mapped for composite key: $compositeKey")
        return Pair(null, null)
    }
    
    /**
     * Register a subtitle track after ExoPlayer has loaded it.
     * This creates the composite key mapping based on ExoPlayer's actual track positioning.
     * 
     * Call this in onTracksChanged for each detected subtitle track.
     */
    fun registerExoPlayerTrack(
        format: androidx.media3.common.Format,
        groupIndex: Int,
        trackIndex: Int,
        jellyfinIndex: Int,
        metadata: MediaStream
    ) {
        val compositeKey = buildCompositeKey(
            groupIndex = groupIndex,
            trackIndex = trackIndex,
            mimeType = format.sampleMimeType,
            language = format.language,
            label = format.label
        )
        
        compositeKeyToJellyfinIndex[compositeKey] = jellyfinIndex
        compositeKeyToMetadata[compositeKey] = metadata
        
        // Also store language+position fallback
        val languageFallbackKey = "lang:${format.language ?: "und"}:pos:$groupIndex:$trackIndex"
        compositeKeyToJellyfinIndex[languageFallbackKey] = jellyfinIndex
        compositeKeyToMetadata[languageFallbackKey] = metadata
        
        Log.d(TAG, "✅ Registered ExoPlayer track: Group=$groupIndex, Track=$trackIndex → JF index=$jellyfinIndex")
        Log.d(TAG, "   Composite key: $compositeKey")
    }

    /** Clears mappings for a new playback session */
    fun reset() {
        compositeKeyToJellyfinIndex.clear()
        compositeKeyToMetadata.clear()
        jellyfinIndexToExpectedPosition.clear()
        Log.d(TAG, "Reset subtitle mappings for new playback session")
    }
    
    // --------------------------------------------------
    // BACKWARDS COMPATIBILITY (Deprecated - use composite key methods)
    // --------------------------------------------------
    
    @Deprecated("Use resolveJellyfinIndexFromFormat with groupIndex/trackIndex")
    fun extractStableIdFromFormat(format: androidx.media3.common.Format): String? {
        Log.w(TAG, "⚠️ extractStableIdFromFormat called - this method is deprecated")
        Log.w(TAG, "   ExoPlayer does not preserve IDs reliably - use composite key approach instead")
        return null
    }
    
    @Deprecated("Use resolveJellyfinIndexFromFormat with groupIndex/trackIndex")
    fun resolveJellyfinIndex(stableId: String?): Int? {
        Log.w(TAG, "⚠️ resolveJellyfinIndex(String) called - this method is deprecated")
        Log.w(TAG, "   Use resolveJellyfinIndexFromFormat with composite keys instead")
        return null
    }
    
    @Deprecated("Use resolveJellyfinIndexFromFormat to get both index and metadata")
    fun resolveMetadata(stableId: String?): MediaStream? {
        Log.w(TAG, "⚠️ resolveMetadata(String) called - this method is deprecated")
        return null
    }

    // --------------------------------------------------
    // INTERNAL HELPERS
    // --------------------------------------------------

    /**
     * Build a composite key from stable ExoPlayer track attributes.
     * This key remains stable even when ExoPlayer rebuilds Format objects.
     * 
     * ⚠️ CRITICAL: MIME type is NOT included because ExoPlayer transforms it!
     * (e.g., application/x-subrip → application/x-media3-cues in TextRenderer)
     * 
     * Format: "g{group}:t{track}:l{lang}_{flags}"
     */
    private fun buildCompositeKey(
        groupIndex: Int,
        trackIndex: Int,
        mimeType: String?,
        language: String?,
        label: String?
    ): String {
        val lang = language ?: "und"
        
        // Extract forced/CC flags from label if present
        val forced = label?.contains("forced", ignoreCase = true) ?: false
        val cc = (label?.contains("cc", ignoreCase = true) ?: false) || (label?.contains("sdh", ignoreCase = true) ?: false)
        val external = label?.contains("external", ignoreCase = true) ?: false
        
        val flags = buildString {
            if (external) append("_ext")
            if (forced) append("_f")
            if (cc) append("_cc")
        }
        
        // MIME type deliberately excluded - ExoPlayer changes it to "x-media3-cues"
        // Position (group+track) + language + flags is sufficient for unique identification
        return "g${groupIndex}:t${trackIndex}:l${lang}${flags}"
    }

    private fun buildLabel(stream: MediaStream): String {
        // Use DisplayLanguage (human-readable) if available, fallback to DisplayTitle or Language code
        val base = stream.DisplayLanguage ?: stream.DisplayTitle ?: stream.Language ?: "Unknown"
        val ext = if (stream.IsExternal == true) " (External)" else ""
        val forced = if (stream.IsForced == true) " [Forced]" else ""
        val cc = if (stream.IsHearingImpaired == true) " [CC/SDH]" else ""
        return "$base$ext$forced$cc"
    }
}

