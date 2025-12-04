package com.flex.elefin.jellyfin

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_MPV_ENABLED = "mpv_enabled"
        private const val KEY_DEBUG_OUTLINES = "debug_outlines"
        private const val KEY_PRELOAD_LIBRARY_IMAGES = "preload_library_images"
        private const val KEY_CACHE_LIBRARY_IMAGES = "cache_library_images"
        private const val KEY_USE_GLIDE = "use_glide"
        private const val KEY_REDUCE_POSTER_RESOLUTION = "reduce_poster_resolution"
        private const val KEY_ANIMATED_PLAY_BUTTON = "animated_play_button"
        private const val KEY_USE_24_HOUR_TIME = "use_24_hour_time"
        private const val KEY_LONG_PRESS_DURATION = "long_press_duration"
        private const val KEY_REMOTE_THEMING_ENABLED = "remote_theming_enabled"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        private const val KEY_AUTO_REFRESH_INTERVAL = "auto_refresh_interval_minutes"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_SORT_TYPE = "library_sort_type"
        private const val KEY_HIDE_SHOWS_WITH_ZERO_EPISODES = "hide_shows_with_zero_episodes"
        private const val KEY_MINIMAL_BUFFER_4K = "minimal_buffer_4k"
        private const val KEY_TRANSCODE_AAC_TO_AC3 = "transcode_aac_to_ac3"
        private const val KEY_USE_LOGO_FOR_TITLE = "use_logo_for_title"
        private const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
        private const val KEY_AUTOPLAY_COUNTDOWN_SECONDS = "autoplay_countdown_seconds"
        private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
        private const val KEY_SKIP_INTRO_ENABLED = "skip_intro_enabled"
        private const val KEY_SKIP_CREDITS_ENABLED = "skip_credits_enabled"
        
        // Subtitle customization
        private const val KEY_SUBTITLE_TEXT_COLOR = "subtitle_text_color"
        private const val KEY_SUBTITLE_BG_COLOR = "subtitle_bg_color"
        private const val KEY_SUBTITLE_BG_TRANSPARENT = "subtitle_bg_transparent"
        private const val KEY_SUBTITLE_TEXT_SIZE = "subtitle_text_size"
        private const val KEY_EXO_SUBTITLE_TEXT_COLOR = "exo_subtitle_text_color"
        private const val KEY_EXO_SUBTITLE_BG_COLOR = "exo_subtitle_bg_color"
        private const val KEY_EXO_SUBTITLE_BG_TRANSPARENT = "exo_subtitle_bg_transparent"
        private const val KEY_EXO_SUBTITLE_TEXT_SIZE = "exo_subtitle_text_size"
        
        // Video enhancement settings
        private const val KEY_USE_GL_ENHANCEMENTS = "use_gl_enhancements"
        private const val KEY_ENABLE_FAKE_HDR = "enable_fake_hdr"
        private const val KEY_ENABLE_SHARPENING = "enable_sharpening"
        private const val KEY_HDR_STRENGTH = "hdr_strength"
        private const val KEY_SHARPEN_STRENGTH = "sharpen_strength"
        private const val KEY_ENABLE_FRAME_BLENDING = "enable_frame_blending"
        private const val KEY_FRAME_BLEND_STRENGTH = "frame_blend_strength"
        
        // UI performance settings
        private const val KEY_DISABLE_UI_ANIMATIONS = "disable_ui_animations"
    }

    var isMpvEnabled: Boolean
        get() = false // Temporarily disabled
        set(value) { /* No-op - MPV temporarily disabled */ }

    var showDebugOutlines: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_OUTLINES, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_OUTLINES, value).apply()
    
    var preloadLibraryImages: Boolean
        get() = prefs.getBoolean(KEY_PRELOAD_LIBRARY_IMAGES, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_PRELOAD_LIBRARY_IMAGES, value).apply()
    
    var cacheLibraryImages: Boolean
        get() = prefs.getBoolean(KEY_CACHE_LIBRARY_IMAGES, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_CACHE_LIBRARY_IMAGES, value).apply()
    
    var useGlide: Boolean
        get() = prefs.getBoolean(KEY_USE_GLIDE, false) // Disabled by default (Coil is default)
        set(value) = prefs.edit().putBoolean(KEY_USE_GLIDE, value).apply()
    
    var reducePosterResolution: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_POSTER_RESOLUTION, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_REDUCE_POSTER_RESOLUTION, value).apply()
    
    var useAnimatedPlayButton: Boolean
        get() = prefs.getBoolean(KEY_ANIMATED_PLAY_BUTTON, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_ANIMATED_PLAY_BUTTON, value).apply()
    
    var use24HourTime: Boolean
        get() = prefs.getBoolean(KEY_USE_24_HOUR_TIME, false) // Disabled by default (12-hour format)
        set(value) = prefs.edit().putBoolean(KEY_USE_24_HOUR_TIME, value).apply()
    
    // Long press duration in seconds (2, 3, 4, or 5)
    var longPressDurationSeconds: Int
        get() = prefs.getInt(KEY_LONG_PRESS_DURATION, 2) // Default 2 seconds
        set(value) = prefs.edit().putInt(KEY_LONG_PRESS_DURATION, value).apply()

    var remoteThemingEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_THEMING_ENABLED, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_REMOTE_THEMING_ENABLED, value).apply()
    
    var darkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE_ENABLED, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, value).apply()
    
    var autoRefreshEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REFRESH_ENABLED, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REFRESH_ENABLED, value).apply()
    
    // Auto-refresh interval in minutes (default: 5 minutes)
    var autoRefreshIntervalMinutes: Int
        get() = prefs.getInt(KEY_AUTO_REFRESH_INTERVAL, 5) // Default 5 minutes
        set(value) = prefs.edit().putInt(KEY_AUTO_REFRESH_INTERVAL, value).apply()
    
    // First launch flag - used to show splash screen only on first app launch
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true) // Default true (first launch)
        set(value) = prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, value).apply()
    
    // Subtitle preferences per episode/item
    fun getSubtitlePreference(itemId: String): Int? {
        val index = prefs.getInt("subtitle_$itemId", -1)
        return if (index >= 0) index else null
    }
    
    fun setSubtitlePreference(itemId: String, subtitleIndex: Int?) {
        prefs.edit().apply {
            if (subtitleIndex != null) {
                putInt("subtitle_$itemId", subtitleIndex)
            } else {
                remove("subtitle_$itemId")
            }
            apply()
        }
    }
    
    // Audio track preferences per episode/item
    fun getAudioPreference(itemId: String): Int? {
        val index = prefs.getInt("audio_$itemId", -1)
        return if (index >= 0) index else null
    }
    
    fun setAudioPreference(itemId: String, audioIndex: Int?) {
        prefs.edit().apply {
            if (audioIndex != null) {
                putInt("audio_$itemId", audioIndex)
            } else {
                remove("audio_$itemId")
            }
            apply()
        }
    }
    
    // Sort preference for library views
    fun getSortType(): String {
        return prefs.getString(KEY_SORT_TYPE, "Alphabetically") ?: "Alphabetically"
    }
    
    fun setSortType(sortType: String) {
        prefs.edit().putString(KEY_SORT_TYPE, sortType).apply()
    }
    
    var hideShowsWithZeroEpisodes: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SHOWS_WITH_ZERO_EPISODES, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SHOWS_WITH_ZERO_EPISODES, value).apply()
    
    var minimalBuffer4K: Boolean
        get() = prefs.getBoolean(KEY_MINIMAL_BUFFER_4K, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_MINIMAL_BUFFER_4K, value).apply()
    
    var transcodeAacToAc3: Boolean
        get() = prefs.getBoolean(KEY_TRANSCODE_AAC_TO_AC3, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_TRANSCODE_AAC_TO_AC3, value).apply()
    
    var useLogoForTitle: Boolean
        get() = prefs.getBoolean(KEY_USE_LOGO_FOR_TITLE, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_USE_LOGO_FOR_TITLE, value).apply()
    
    var autoplayNextEpisode: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, value).apply()
    
    // Autoplay countdown duration in seconds (10-120 seconds, default: 10)
    var autoplayCountdownSeconds: Int
        get() = prefs.getInt(KEY_AUTOPLAY_COUNTDOWN_SECONDS, 10).coerceIn(10, 120) // Default 10 seconds, range 10-120
        set(value) = prefs.edit().putInt(KEY_AUTOPLAY_COUNTDOWN_SECONDS, value.coerceIn(10, 120)).apply()
    
    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, value).apply()
    
    // Skip intro/credits settings (enabled by default)
    var skipIntroEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_INTRO_ENABLED, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_SKIP_INTRO_ENABLED, value).apply()
    
    var skipCreditsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CREDITS_ENABLED, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_SKIP_CREDITS_ENABLED, value).apply()
    
    // Subtitle customization settings
    // Text color as ARGB int (default: White = 0xFFFFFFFF)
    var subtitleTextColor: Int
        get() = prefs.getInt(KEY_SUBTITLE_TEXT_COLOR, 0xFFFFFFFF.toInt())
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_TEXT_COLOR, value).apply()
    
    // Background color as ARGB int (default: Black = 0xFF000000)
    var subtitleBgColor: Int
        get() = prefs.getInt(KEY_SUBTITLE_BG_COLOR, 0xFF000000.toInt())
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_BG_COLOR, value).apply()
    
    // Background transparency (true = transparent, false = opaque)
    var subtitleBgTransparent: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE_BG_TRANSPARENT, false) // Opaque by default
        set(value) = prefs.edit().putBoolean(KEY_SUBTITLE_BG_TRANSPARENT, value).apply()
    
    // Text size (default: 55, range: 30-100)
    var subtitleTextSize: Int
        get() = prefs.getInt(KEY_SUBTITLE_TEXT_SIZE, 55).coerceIn(30, 100)
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_TEXT_SIZE, value.coerceIn(30, 100)).apply()
    
    // ExoPlayer-specific subtitle settings
    var exoSubtitleTextColor: Int
        get() = prefs.getInt(KEY_EXO_SUBTITLE_TEXT_COLOR, 0xFFFFFFFF.toInt())
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_TEXT_COLOR, value).apply()
    
    var exoSubtitleBgColor: Int
        get() = prefs.getInt(KEY_EXO_SUBTITLE_BG_COLOR, 0xFF000000.toInt())
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_BG_COLOR, value).apply()
    
    var exoSubtitleBgTransparent: Boolean
        get() = prefs.getBoolean(KEY_EXO_SUBTITLE_BG_TRANSPARENT, false)
        set(value) = prefs.edit().putBoolean(KEY_EXO_SUBTITLE_BG_TRANSPARENT, value).apply()
    
    var exoSubtitleTextSize: Int
        get() = prefs.getInt(KEY_EXO_SUBTITLE_TEXT_SIZE, 30).coerceIn(20, 100)
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_TEXT_SIZE, value.coerceIn(20, 100)).apply()
    
    // Video enhancement settings
    var useGLEnhancements: Boolean
        get() = prefs.getBoolean(KEY_USE_GL_ENHANCEMENTS, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_USE_GL_ENHANCEMENTS, value).apply()
    
    var enableFakeHDR: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FAKE_HDR, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_FAKE_HDR, value).apply()
    
    var enableSharpening: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SHARPENING, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SHARPENING, value).apply()
    
    // HDR strength (1.0 - 2.0, default: 1.3)
    var hdrStrength: Float
        get() = prefs.getFloat(KEY_HDR_STRENGTH, 1.3f).coerceIn(1.0f, 2.0f)
        set(value) = prefs.edit().putFloat(KEY_HDR_STRENGTH, value.coerceIn(1.0f, 2.0f)).apply()
    
    // Sharpening strength (0.0 - 1.0, default: 0.5)
    var sharpenStrength: Float
        get() = prefs.getFloat(KEY_SHARPEN_STRENGTH, 0.5f).coerceIn(0.0f, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SHARPEN_STRENGTH, value.coerceIn(0.0f, 1.0f)).apply()
    
    // Frame blending (fake soap opera effect) - disabled by default
    var enableFrameBlending: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FRAME_BLENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_FRAME_BLENDING, value).apply()
    
    // Frame blend strength (0.0 - 1.0, default: 0.5)
    // 0.0 = no blending (current frame only), 1.0 = full blend (50/50 with previous frame)
    var frameBlendStrength: Float
        get() = prefs.getFloat(KEY_FRAME_BLEND_STRENGTH, 0.5f).coerceIn(0.0f, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_FRAME_BLEND_STRENGTH, value.coerceIn(0.0f, 1.0f)).apply()
    
    // UI performance settings
    var disableUIAnimations: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_UI_ANIMATIONS, false) // Disabled by default (animations enabled)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_UI_ANIMATIONS, value).apply()
}

