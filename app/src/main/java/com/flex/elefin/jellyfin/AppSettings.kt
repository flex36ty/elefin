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
        // ExoPlayer GL enhancements
        private const val KEY_USE_GL_ENHANCEMENTS = "use_gl_enhancements"
        
        // MPV Shader support
        private const val KEY_MPV_SHADER_PROFILE = "mpv_shader_profile"
        
        // Legacy/ExoPlayer settings
        private const val KEY_ENABLE_FAKE_HDR = "enable_fake_hdr"
        private const val KEY_ENABLE_SHARPENING = "enable_sharpening"
        private const val KEY_HDR_STRENGTH = "hdr_strength"
        private const val KEY_SHARPEN_STRENGTH = "sharpen_strength"
        private const val KEY_ENABLE_FRAME_BLENDING = "enable_frame_blending"
        private const val KEY_FRAME_BLEND_STRENGTH = "frame_blend_strength"
        private const val KEY_ENABLE_DENOISE = "enable_denoise"
        private const val KEY_DENOISE_STRENGTH = "denoise_strength"
        private const val KEY_ENABLE_DEBAND = "enable_deband"
        private const val KEY_DEBAND_STRENGTH = "deband_strength"
        private const val KEY_ENABLE_FXAA = "enable_fxaa"
        private const val KEY_VIDEO_BRIGHTNESS = "video_brightness"
        private const val KEY_VIDEO_CONTRAST = "video_contrast"
        private const val KEY_VIDEO_SATURATION = "video_saturation"
        private const val KEY_VIDEO_COLOR_TEMPERATURE = "video_color_temperature"
        
        // UI performance settings
        private const val KEY_DISABLE_UI_ANIMATIONS = "disable_ui_animations"
        private const val KEY_USE_SIMPLE_CARDS = "use_simple_cards"
        private const val KEY_USE_GOOGLE_TV_CARDS = "use_google_tv_cards"
        private const val KEY_LOW_POWER_MODE = "low_power_mode"
        private const val KEY_USE_4K_BACKGROUNDS = "use_4k_backgrounds"
        
        // OpenSubtitles settings
        private const val KEY_OPENSUBTITLES_API_KEY = "opensubtitles_api_key"
        private const val KEY_OPENSUBTITLES_USERNAME = "opensubtitles_username"
        private const val KEY_OPENSUBTITLES_PASSWORD = "opensubtitles_password"
        
        // TMDB settings (deprecated - use Jellyseerr instead)
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_TMDB_TRENDING_ENABLED = "tmdb_trending_enabled"
        
        // Jellyseerr settings
        private const val KEY_JELLYSEERR_URL = "jellyseerr_url"
        private const val KEY_JELLYSEERR_API_KEY = "jellyseerr_api_key"
        private const val KEY_JELLYSEERR_ENABLED = "jellyseerr_enabled"
        private const val KEY_JELLYSEERR_AUTH_TYPE = "jellyseerr_auth_type" // "api_key" or "credentials"
        private const val KEY_JELLYSEERR_USERNAME = "jellyseerr_username"
        private const val KEY_JELLYSEERR_SESSION_COOKIE = "jellyseerr_session_cookie"
        
        // Server-side transcoding settings
        private const val KEY_SERVER_TRANSCODING_ENABLED = "server_transcoding_enabled"
        private const val KEY_TRANSCODE_AV1 = "transcode_av1"
        private const val KEY_TRANSCODE_HEVC = "transcode_hevc"
        private const val KEY_TRANSCODE_TARGET_CODEC = "transcode_target_codec"
        private const val KEY_TRANSCODE_MAX_BITRATE = "transcode_max_bitrate"
        private const val KEY_AUTO_TRANSCODE_ON_ERROR = "auto_transcode_on_error"
        private const val KEY_FALLBACK_TO_MPV = "fallback_to_mpv"
        private const val KEY_ROW_CARD_COUNT = "row_card_count"
        private const val KEY_NAVIGATION_SOUNDS_ENABLED = "navigation_sounds_enabled"
        private const val KEY_THEME_COLOR_HEX = "theme_color_hex"
    }

    var isMpvEnabled: Boolean
        get() = prefs.getBoolean(KEY_MPV_ENABLED, false) // Disabled by default (ExoPlayer is default)
        set(value) = prefs.edit().putBoolean(KEY_MPV_ENABLED, value).apply()

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

    // MPV Shader Profile: None, Cinema, HdrBoost, Sports, Sharp
    var mpvShaderProfile: String
        get() = prefs.getString(KEY_MPV_SHADER_PROFILE, "None") ?: "None"
        set(value) = prefs.edit().putString(KEY_MPV_SHADER_PROFILE, value).apply()

    // Enable Dynamic Tone Mapping (Pseudo-HDR++) - default false
    var enableDynamicToneMapping: Boolean
        get() = prefs.getBoolean("enable_dynamic_tone_mapping", false)
        set(value) = prefs.edit().putBoolean("enable_dynamic_tone_mapping", value).apply()
    
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
    
    // Denoise filter (reduces noise/grain in video)
    var enableDenoise: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_DENOISE, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_DENOISE, value).apply()
    
    // Denoise strength (0.0 - 1.0, default: 0.5)
    var denoiseStrength: Float
        get() = prefs.getFloat(KEY_DENOISE_STRENGTH, 0.5f).coerceIn(0.0f, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_DENOISE_STRENGTH, value.coerceIn(0.0f, 1.0f)).apply()
    
    // Debanding filter (reduces color banding artifacts)
    var enableDeband: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_DEBAND, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_DEBAND, value).apply()
    
    // Deband strength (0.0 - 1.0, default: 0.5)
    var debandStrength: Float
        get() = prefs.getFloat(KEY_DEBAND_STRENGTH, 0.5f).coerceIn(0.0f, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_DEBAND_STRENGTH, value.coerceIn(0.0f, 1.0f)).apply()
    
    // FXAA anti-aliasing
    var enableFXAA: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FXAA, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_FXAA, value).apply()
    
    // Video brightness adjustment (-1.0 to 1.0, default: 0.0)
    var videoBrightness: Float
        get() = prefs.getFloat(KEY_VIDEO_BRIGHTNESS, 0.0f).coerceIn(-1.0f, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_VIDEO_BRIGHTNESS, value.coerceIn(-1.0f, 1.0f)).apply()
    
    // Video contrast adjustment (0.0 to 2.0, default: 1.0)
    var videoContrast: Float
        get() = prefs.getFloat(KEY_VIDEO_CONTRAST, 1.0f).coerceIn(0.0f, 2.0f)
        set(value) = prefs.edit().putFloat(KEY_VIDEO_CONTRAST, value.coerceIn(0.0f, 2.0f)).apply()
    
    // Video saturation adjustment (0.0 to 2.0, default: 1.0)
    var videoSaturation: Float
        get() = prefs.getFloat(KEY_VIDEO_SATURATION, 1.0f).coerceIn(0.0f, 2.0f)
        set(value) = prefs.edit().putFloat(KEY_VIDEO_SATURATION, value.coerceIn(0.0f, 2.0f)).apply()
    
    // Video color temperature adjustment (-1.0 to 1.0, default: 0.0)
    // -1.0 = cool/blue, 0.0 = neutral, 1.0 = warm/orange
    var videoColorTemperature: Float
        get() = prefs.getFloat(KEY_VIDEO_COLOR_TEMPERATURE, 0.0f).coerceIn(-1.0f, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_VIDEO_COLOR_TEMPERATURE, value.coerceIn(-1.0f, 1.0f)).apply()
    
    // UI performance settings
    var disableUIAnimations: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_UI_ANIMATIONS, false) // Disabled by default (animations enabled)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_UI_ANIMATIONS, value).apply()
    
    // Use simple cards without zoom animation (better for low-spec devices)
    var useSimpleCards: Boolean
        get() = prefs.getBoolean(KEY_USE_SIMPLE_CARDS, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_USE_SIMPLE_CARDS, value).apply()
    
    // Use Google TV style cards (lightweight with subtle scale animation)
    var useGoogleTvCards: Boolean
        get() = prefs.getBoolean(KEY_USE_GOOGLE_TV_CARDS, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_USE_GOOGLE_TV_CARDS, value).apply()
    
    // Low power mode - enables all performance optimizations for budget devices
    var lowPowerMode: Boolean
        get() = prefs.getBoolean(KEY_LOW_POWER_MODE, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_LOW_POWER_MODE, value).apply()
    
    // Use 4K background images (disabled by default)
    var use4KBackgrounds: Boolean
        get() = prefs.getBoolean(KEY_USE_4K_BACKGROUNDS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_4K_BACKGROUNDS, value).apply()
    
    // OpenSubtitles API key - users need to get their own key from opensubtitles.com
    var openSubtitlesApiKey: String
        get() = prefs.getString(KEY_OPENSUBTITLES_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUBTITLES_API_KEY, value).apply()
    
    // OpenSubtitles username - required for downloading subtitles
    var openSubtitlesUsername: String
        get() = prefs.getString(KEY_OPENSUBTITLES_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUBTITLES_USERNAME, value).apply()
    
    // OpenSubtitles password - required for downloading subtitles
    var openSubtitlesPassword: String
        get() = prefs.getString(KEY_OPENSUBTITLES_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUBTITLES_PASSWORD, value).apply()
    
    // TMDB API key - for trending content discovery (deprecated - use Jellyseerr instead)
    var tmdbApiKey: String
        get() = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TMDB_API_KEY, value).apply()
    
    // TMDB Trending tab enabled - shows trending content from TMDB in library screens (deprecated)
    var tmdbTrendingEnabled: Boolean
        get() = prefs.getBoolean(KEY_TMDB_TRENDING_ENABLED, false) // Disabled by default (use Jellyseerr instead)
        set(value) = prefs.edit().putBoolean(KEY_TMDB_TRENDING_ENABLED, value).apply()
    
    // Jellyseerr URL - base URL for Jellyseerr/Overseerr instance
    var jellyseerrUrl: String
        get() = prefs.getString(KEY_JELLYSEERR_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_JELLYSEERR_URL, value).apply()
    
    // Jellyseerr authentication type: "api_key" or "credentials"
    var jellyseerrAuthType: String
        get() = prefs.getString(KEY_JELLYSEERR_AUTH_TYPE, "api_key") ?: "api_key"
        set(value) = prefs.edit().putString(KEY_JELLYSEERR_AUTH_TYPE, value).apply()
    
    // Jellyseerr API key - for API key authentication
    var jellyseerrApiKey: String
        get() = prefs.getString(KEY_JELLYSEERR_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_JELLYSEERR_API_KEY, value).apply()
    
    // Jellyseerr username - for display purposes when using credentials auth
    var jellyseerrUsername: String
        get() = prefs.getString(KEY_JELLYSEERR_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_JELLYSEERR_USERNAME, value).apply()
    
    // Jellyseerr session cookie - from username/password login
    var jellyseerrSessionCookie: String
        get() = prefs.getString(KEY_JELLYSEERR_SESSION_COOKIE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_JELLYSEERR_SESSION_COOKIE, value).apply()
    
    // Jellyseerr enabled - shows Discover tab with trending/popular/upcoming content
    var jellyseerrEnabled: Boolean
        get() = prefs.getBoolean(KEY_JELLYSEERR_ENABLED, true) // Enabled by default when configured
        set(value) = prefs.edit().putBoolean(KEY_JELLYSEERR_ENABLED, value).apply()

    // Jellyseerr search integration enabled - shows Jellyseerr results in search
    var jellyseerrSearchEnabled: Boolean
        get() = prefs.getBoolean("jellyseerr_search_enabled", true) // Enabled by default
        set(value) = prefs.edit().putBoolean("jellyseerr_search_enabled", value).apply()
    
    // Check if Jellyseerr is properly configured
    val isJellyseerrConfigured: Boolean
        get() {
            if (!jellyseerrEnabled || jellyseerrUrl.isBlank()) return false
            return when (jellyseerrAuthType) {
                "api_key" -> jellyseerrApiKey.isNotBlank()
                "credentials" -> jellyseerrSessionCookie.isNotBlank()
                else -> false
            }
        }
    
    // Clear Jellyseerr credentials (for logout)
    fun clearJellyseerrCredentials() {
        jellyseerrApiKey = ""
        jellyseerrUsername = ""
        jellyseerrSessionCookie = ""
        jellyseerrAuthType = "api_key"
    }
    
    // Server-side transcoding settings
    // Master switch for server-side transcoding
    var serverTranscodingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVER_TRANSCODING_ENABLED, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_SERVER_TRANSCODING_ENABLED, value).apply()
    
    // Transcode AV1 content (device doesn't support AV1 hardware decoding)
    var transcodeAV1: Boolean
        get() = prefs.getBoolean(KEY_TRANSCODE_AV1, true) // Enabled by default when transcoding is on
        set(value) = prefs.edit().putBoolean(KEY_TRANSCODE_AV1, value).apply()
    
    // Transcode HEVC/H.265 content (for older devices without HEVC support)
    var transcodeHEVC: Boolean
        get() = prefs.getBoolean(KEY_TRANSCODE_HEVC, false) // Disabled by default
        set(value) = prefs.edit().putBoolean(KEY_TRANSCODE_HEVC, value).apply()
    
    // Target codec for transcoding: "h264" or "hevc"
    var transcodeTargetCodec: String
        get() = prefs.getString(KEY_TRANSCODE_TARGET_CODEC, "h264") ?: "h264"
        set(value) = prefs.edit().putString(KEY_TRANSCODE_TARGET_CODEC, value).apply()
    
    // Maximum bitrate for transcoded video in Mbps (default: 40 Mbps for high quality, range: 5-120)
    var transcodeMaxBitrateMbps: Int
        get() = prefs.getInt(KEY_TRANSCODE_MAX_BITRATE, 40).coerceIn(5, 120)
        set(value) = prefs.edit().putInt(KEY_TRANSCODE_MAX_BITRATE, value.coerceIn(5, 120)).apply()
    
    // Automatically switch to transcoding when direct play fails (codec not supported, decoder error, etc.)
    var autoTranscodeOnError: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TRANSCODE_ON_ERROR, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRANSCODE_ON_ERROR, value).apply()
    
    // Fallback to MPV player when ExoPlayer fails and transcoding is disabled/unavailable
    var fallbackToMpv: Boolean
        get() = prefs.getBoolean(KEY_FALLBACK_TO_MPV, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_FALLBACK_TO_MPV, value).apply()

    // Number of cards to fetch/display per row (25, 50, 75, 100)
    var rowCardCount: Int
        get() = prefs.getInt(KEY_ROW_CARD_COUNT, 25).coerceIn(25, 100) // Default 25
        set(value) = prefs.edit().putInt(KEY_ROW_CARD_COUNT, value.coerceIn(25, 100)).apply()

    var navigationSoundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NAVIGATION_SOUNDS_ENABLED, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_NAVIGATION_SOUNDS_ENABLED, value).apply()

    var themeColorHex: String
        get() = prefs.getString(KEY_THEME_COLOR_HEX, "#FFFFFF") ?: "#FFFFFF"
        set(value) = prefs.edit().putString(KEY_THEME_COLOR_HEX, value).apply()
}

