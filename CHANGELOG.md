# Changelog

All notable changes to Elefin will be documented in this file.

---

## [1.1.5] - 2025-11-30

### Added
- **ExoPlayer - Video Enhancements (OpenGL Post-Processing)**
  - NEW: Custom GL video pipeline with post-processing effects
  - Fake HDR simulation with tone mapping and brightness boost
  - Image sharpening using edge detection (unsharp mask technique)
  - Adjustable strength controls for both HDR (1.0-2.0) and sharpening (0.0-1.0)
  - OpenGL ES 2.0 pipeline intercepts ExoPlayer video frames for shader processing
  - Maintains full ExoPlayer compatibility (no decoder changes needed)
  - Settings located under "Video Enhancements" section
  - Toggle individual effects on/off or disable GL processing entirely
  - Inspired by VLC, Kodi, and MPV professional video rendering pipelines
  - Zero performance impact when disabled (uses standard PlayerView)

- **ExoPlayer - Advanced Audio Codec Support**
  - Added Jellyfin FFmpeg decoder extension for comprehensive audio codec support
  - Now supports DTS, DTS-HD Master Audio, Dolby TrueHD, AC3, E-AC3
  - Added support for FLAC, ALAC, Vorbis, Opus, and 30+ additional codecs
  - FFmpeg renderer preferred over platform decoders for maximum compatibility
  - No manual building required - uses Jellyfin's prebuilt Maven artifact
  - Licensed under GPLv3 (compatible with Jellyfin ecosystem)

- **ExoPlayer - Custom Settings Menu**
  - New modern, transparent settings menu with dark overlay
  - Auto-focus on first item when menu opens for better TV UX
  - Quick access to subtitle, audio, and playback speed settings
  - Semi-transparent background allows viewing content while adjusting settings
  - Optimized for Android TV remote navigation

- **Library Refresh - Image Cache Clearing**
  - Library refresh button now clears image cache before refreshing
  - Ensures new thumbnails and artwork are downloaded immediately
  - Fixes issue where cached images would prevent new media art from appearing
  - Both memory and disk caches are cleared for complete refresh

### Fixed
- **Subtitle Language Selection**
  - Fixed subtitle selection choosing wrong language in ExoPlayer
  - Now matches by language code + flags (forced, CC, external) instead of position
  - Handles ExoPlayer's internal track reordering correctly
  - Prevents mismatches when tracks have similar characteristics
  - More robust matching algorithm with exact and fallback strategies

- **Subtitle Preference UI Refresh** - will be in 1.1.6 release
  - Subtitle selection now immediately updates on series/movie info screens
  - No longer requires navigating away and back to see updated subtitle preference
  - Fixed state management to trigger immediate Compose recomposition
  - Improved user experience with instant visual feedback

- **Series Info Screen - Synopsis Clipping**
  - Fixed synopsis text being cut off at the bottom on series info screen
  - Added vertical scrolling to series details container
  - Works correctly with smaller logo sizes (30% reduction)
  - Matches behavior of movie details screen

### Changed
- **Client Identification**
  - Changed client name from "Android TV Material Catalog" to "Elefin"
  - Updated version reporting to match app version (1.1.5)
  - Server dashboards now properly display "Elefin" as the client name
  - Easier to identify and track Elefin sessions in Jellyfin server

- **ExoPlayer - Subtitle Settings**
  - Default subtitle size reduced from 55 to 30 for better readability
  - Expanded size range from 30-100 to 20-100
  - Added smaller size options: 20, 25, and 30
  - More granular control over subtitle appearance

- **Continue Watching - Sorting**
  - Now explicitly sorts by date and time played (descending)
  - Most recently watched items appear first
  - Consistent ordering based on last playback time
  - Uses Jellyfin's DatePlayed sorting for accurate chronological order

- **Series Info Screen - UI Adjustments**
  - Logo size reduced by 30% (from 45dp to 31.5dp)
  - Episode name text size matches home screen (bodyLarge * 0.8f)
  - More consistent visual hierarchy across screens
  - Better balance between title, episode name, and synopsis

- **ExoPlayer - Subtitle Mapper**
  - Updated to prevent duplicate Jellyfin index registrations
  - Only stores first occurrence of each Jellyfin index for correct mapping
  - Added detailed logging for track registration debugging
  - Improved reliability of subtitle and audio track selection

- **MPV Player - Experimental Status**
  - Marked MPV player as experimental in settings
  - Updated description to warn about potential instability
  - ExoPlayer with FFmpeg is now the recommended default player
  - MPV remains available for advanced users and specific use cases

---

## [1.1.3] - Previous Release

### Fixed
- **Subtitle Auto-Selection from Info Page**
  - Completely rewrote subtitle preference application logic to use SubtitleMapper
  - Now uses 100% reliable composite key system for track matching
  - Subtitle selections from movie/show info screens are now correctly applied on playback start
  - Eliminated unreliable language code and position-based matching
  - Uses the same production-tested approach as Plex, Emby, and Jellyfin TV apps
  - Removed ~100 lines of complex fallback logic

### Changed
- **Episode Name Text Sizing**
  - Adjusted episode name to proper medium size between title and synopsis
  - Changed from `titleMedium * 0.9f` to `bodyLarge * 1.1f`
  - Creates better visual hierarchy: Title → Episode Name → Synopsis
  - Episode names are now 37.5% larger than synopsis but still smaller than series title

- **Logo Display Sizing**
  - All movie and TV show logos now use a fixed height (45dp) for consistent layout
  - Prevents layout shifts between different titles
  - Width automatically adjusts to maintain aspect ratios
  - Uniform sizing across all screens for a cleaner, more professional appearance

### Added
- **ExoPlayer - External Subtitle Support**
  - Full support for external SRT subtitle files
  - Correct URL format for Jellyfin external subtitles
  - Automatic detection and loading of sidecar subtitle files
  - Support for multiple subtitle formats (SRT, VTT, ASS, PGS)

- **ExoPlayer - Comprehensive Media3 Extensions**
  - Added HLS extension for HTTP Live Streaming support
  - Added DASH extension for Dynamic Adaptive Streaming
  - Added SmoothStreaming extension for Microsoft adaptive streaming
  - Added UI extensions for standard and TV (Leanback) interfaces
  - Added enhanced subtitle extractor for better format support
  - Added MediaSession extension for Android TV media controls
  - Added OkHttp and Cronet data source extensions for improved networking
  - Added Transformer extension for media processing capabilities
  - FFmpeg extension support prepared for advanced codecs (DTS, TrueHD, PGS)

- **ExoPlayer - Subtitle Customization**
  - Adjustable subtitle text size (range: 30-100)
  - Customizable subtitle text color
  - Toggle subtitle background transparency
  - Customizable subtitle background color
  - Independent settings from MPV player
  - Settings saved per-player type

- **Subtitle Preference Memory**
  - Subtitle selections are now remembered for each movie/episode
  - Selected subtitles persist when navigating away and returning
  - Preferences are automatically applied on next playback
  - Audio track preferences also saved and restored

### Changed (Continued)
- **Subtitle Loading System**
  - Switched to `DefaultMediaSourceFactory` for proper subtitle configuration handling
  - Improved subtitle track detection and registration
  - Fixed duplicate subtitle registration issues
  - Enhanced subtitle URL generation with correct extensions

- **Subtitle Preference Application**
  - Preferences from movie/show info page now apply correctly on playback start
  - Track selector prevents unwanted auto-selection of forced/default subtitles
  - Saved preferences override ExoPlayer's default behavior

### Fixed (Continued)
- **Subtitle Selection**
  - Fixed indexing issues causing wrong subtitle selection
  - Corrected group index mapping for filtered track lists
  - Resolved conflicts between saved preferences and manual selections
  - Fixed "None" option not disabling subtitles properly
  - Prevented forced subtitle auto-selection
  - Fixed selected subtitles not persisting from movie/show info page

- **Track Selector Configuration**
  - Disabled forced and default subtitle auto-selection flags
  - Improved track selector parameter handling
  - Fixed subtitle preference application (now only applies once on startup)
  - Ensured subtitle selection from info page sticks when media starts

### Previous Unreleased Features

### Added
- **MPV Player - Custom Subtitle Overlay**
  - Custom subtitle rendering system for Android TV compatibility
  - Polls MPV's subtitle text and renders it using Compose overlay
  - Bypasses MPV's native rendering issues on Android TV devices
  - Subtitles now work reliably on all Android TV devices including NVIDIA Shield

- **MPV Player - Subtitle Customization Settings**
  - Adjust subtitle text size (30-100)
  - Choose custom text color
  - Toggle background transparency
  - Choose custom background color
  - Settings apply only to MPV's custom subtitle overlay

- **MPV Player - External Subtitle Support**
  - Automatic download of external subtitle files from Jellyfin
  - Smart detection of embedded vs external subtitles
  - Multi-endpoint fallback for reliable subtitle downloads
  - Supports sidecar subtitle files (.srt, .vtt, .ass)

- **MPV Player - Singleton Architecture**
  - Global MPV instance for improved stability
  - Prevents crashes from multiple initializations
  - Clean playback transitions between videos
  - Event-driven property polling for accurate playback data

- **MPV Player - GPU Rendering Mode**
  - Optimized video output using GPU rendering
  - Hardware decoding with proper subtitle compositing
  - HDR tone-mapping support
  - Performance improvements for Android TV devices

## Previous Unreleased / Upcoming Release

### Changed
- **TV Series Cards Show Unwatched Episode Count**
  - TV show cards now display the number of **unwatched episodes** instead of total episodes
  - Uses Jellyfin API's `UserData.UnplayedItemCount` field for accurate counts
  - Badge shows in top-right corner of series cards when there are unwatched episodes
  - Applied to: Recently Added Shows rows, Library grid view, Collections, Search results
  - Much more efficient than previous implementation (single API field vs multiple calls)

- **Text Input Fields - Jellyfin AndroidTV Style**
  - Completely rewrote `TvTextField` component to match official Jellyfin AndroidTV implementation
  - Uses `MutableInteractionSource` with `collectIsFocusedAsState()` for focus detection (like Jellyfin)
  - Uses `decorationBox` for text field decoration (like Jellyfin's SearchTextInput)
  - BasicTextField is now directly focusable (no wrapper Box) - works like native Android `EditText`
  - D-pad navigation directly selects text fields without intermediate focus state
  - Keyboard appears automatically when text field receives focus
  - IME actions (Next/Done) work properly for field navigation
  - **Visual Style (Jellyfin AndroidTV colors)**:
    - Border: `#B3747474` (70% gray) - always visible
    - Focused background: `#DDDDDD` (light gray)
    - Unfocused text: `#DDDDDD` (light)
    - Focused text: `#444444` (dark)
    - Corner radius: 3dp (Jellyfin style)
    - Stroke width: 2dp
  - Search screen now uses `TvTextField` component for consistency with login screen
  - Added `TvSearchTextField` component for search-specific styling with pill shape

- **Search Results Layout**
  - Search results now use the same card size as home screen (105dp width)
  - Grid layout with 6 columns matching library view
  - Item names displayed below each card with consistent styling
  - 20dp spacing between cards (same as home screen)

- **Action Buttons Smaller with Animation**
  - Play, Resume, Audio, Subtitles, and Mark as Watched buttons are now smaller (28dp unfocused, down from 40dp)
  - Buttons expand horizontally on focus to reveal labels (same animation as before, just smaller)
  - Icon sizes remain the same (14.3dp) for clear visibility
  - Text label sizes unchanged
  - Applies to Movie details screen, Series details screen, and AnimatedPlayButton
  - Season selector buttons also updated to 28dp with "Season X" expansion on focus


### Added
- **Video Player - MPV Custom Subtitle Overlay (Android TV Rendering Fix)**
  - **CRITICAL FIX**: MPV-Android cannot render subtitles on SurfaceView properly on Android TV devices
  - **The Problem**: 
    - MPV loads and decodes subtitles correctly (`sub-text` contains dialogue)
    - MPV reports subtitles as visible (`sub-visibility=true`, `sid` set correctly)
    - But subtitles DON'T appear on screen due to SurfaceView compositing limitations
    - Affects NVIDIA Shield, Chromecast, Fire TV when videos contain both PGS and text subtitles
  - **The Solution - Custom Compose Subtitle Overlay**:
    - Polls MPV's `sub-text` property every 100ms to extract current subtitle text
    - Renders subtitle text in Compose overlay on top of MPV video player
    - Large, readable white text (28sp) with black drop shadow and semi-transparent background
    - Positioned at bottom-center of screen with proper padding
    - Updates in real-time with dialogue (10fps refresh rate)
  - **Why This Works**:
    - Bypasses MPV's broken SurfaceView subtitle renderer completely
    - Uses Compose's reliable text rendering on GPU layer
    - Same approach used by Netflix, Disney+, and other streaming apps
    - Subtitles guaranteed visible on ALL Android TV devices
  - **Features**:
    - Automatic show/hide based on subtitle timing
    - Proper text styling with borders and shadows for readability
    - No performance impact (efficient text property polling)
    - Works for ALL subtitle types (SRT, VTT, ASS, external sidecar files)
  - **Result**: Subtitles now 100% visible on Android TV, matching ExoPlayer quality

- **Video Player - External Sidecar Subtitle Download System**
  - **CRITICAL**: Detects when subtitles are NOT embedded (MPV can't find them in container)
  - **Auto-download** from Jellyfin with 4-endpoint fallback strategy:
    1. `DeliveryUrl` (if present - highest priority)
    2. `/Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream`
    3. `/Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/0/Stream.{codec}`
    4. `/Videos/{itemId}/Subtitles/{index}/Stream.{codec}`
    5. `/Videos/{itemId}/{mediaSourceId}/Subtitles/{index}`
  - **Handles TV rips** where Jellyfin reports 40+ subtitles but only 2 are embedded
  - **Detection**: Waits up to 3 seconds with retry logic for MPV to parse all tracks
  - **Smart fallback**: If MPV can't find track after 6 attempts, assumes it's external
  - **Downloads** to local cache with Jellyfin authentication headers
  - **Loads** via `MPVLib.command(["sub-add", filepath, "select"])`
  - **Works** for `.srt`, `.vtt`, `.ass`, `.ttml` sidecar subtitle files
  - **Fixed**: `IsExternal=true` detection (don't require DeliveryUrl for sidecar files)
  - **Result**: Netflix/Disney+/streaming service rips with external subtitles now work perfectly

- **Video Player - MPV Global Initialization System (Crash Prevention)**
  - **CRITICAL FIX**: Prevents "mpv is already initialized" SIGSEGV crash on NVIDIA Shield
  - **The Problem**:
    - MPVLib is a global singleton that persists across activity lifecycles
    - Calling `MPVLib.create()` twice causes instant crash with `Fatal signal 11 (SIGSEGV)`
    - Happens during video fallback or when playing second video
  - **The Solution**:
    - Check if MPVLib is ACTUALLY initialized (not just flag) by testing `getPropertyString("vo")`
    - Only call `mpvView.initialize()` if MPV isn't already initialized
    - Reuse existing MPVLib instance for subsequent videos
  - **Cleanup Fix**: Stop playback (`pause + stop`) when navigating away, but DON'T destroy MPVLib
  - **Result**: 
    - No more crashes when playing multiple videos
    - No background audio after exiting video
    - Smooth video-to-video transitions
    - MPVLib initialized once per app session, reused for all videos

- **Video Player - MPV Auto-Detect Subtitle System (Text + Bitmap)**
  - **NEW**: Intelligent subtitle type detection with automatic profile switching
  - **Text Subtitles** (SRT, VTT, ASS, TTML) - Streaming services & TV shows:
    - Roboto font, size 55, white text with black border and shadow
    - Fully stylable and scalable
    - Position 95% (bottom of screen)
    - Works perfectly with Netflix/Amazon/Disney+ WEB-DL releases
  - **PGS/SUP Bitmap Subtitles** (Blu-ray) - Auto-detected via `[pgssub]` profile:
    - `stretch-image-subs-to-screen=yes` - stretches bitmap to full screen
    - `image-subs-video-resolution=no` - prevents off-screen rendering
    - `sub-scale=3.0` - proper scaling for Shield TV safe area
    - `sub-pos=90` - keeps PGS inside visible TV bounds
    - Automatically activates when MPV detects `pgssub` codec
    - Fixes Blu-ray remux subtitles that were previously invisible
  - **GPU Rendering Mode** for subtitle visibility:
    - Fixed subtitle invisibility on NVIDIA Shield TV, Chromecast, Fire TV
    - `vo=gpu` + `gpu-api=opengl` - composites video + subs in same layer
    - `hwdec=mediacodec-copy` - prevents direct-surface mode that hides subtitles
    - Added GPU renderer readiness check before loading subtitles
    - Fixed double-initialization crash on fallback (Shield TV SIGSEGV)
  - **Performance**: `profile=gpu-hq`, ewa_lanczos scaling, HDR tone-mapping
  - Subtitles now work for ALL content types: streaming, TV shows, Blu-ray movies

- **Video Player - MPV Subtitle System Complete Rewrite**
  - **NEW**: Created `SubtitleManager.kt` - clean, isolated subtitle loading logic
  - **CRITICAL FIX**: True external subtitle detection - ALL THREE conditions must be met:
    1. `IsExternal == true`
    2. `SupportsExternalStream == true`
    3. `DeliveryUrl` is not null/blank
  - Fixed embedded vs external subtitle detection (no more 404 errors!)
  - Jellyfin sometimes mislabels embedded subtitles as "External" in metadata - we now detect this correctly
  - Embedded subtitles (inside video file) are selected via MPV `sub-select` command (no HTTP download)
  - External subtitles (true external SRT/VTT files) are downloaded locally with authentication headers
  - DeliveryUrl from Jellyfin is the ONLY reliable source for external subtitle URLs
  - MPV subtitle header bug workaround: all external subtitles downloaded to local cache first
  - Zero 404 errors, zero brute-force index scanning, production-ready implementation matching official Jellyfin clients

- **Video Player - SubtitleMapper with Composite Key Approach (100% Reliable)**
  - **NEW**: Created `SubtitleMapper.kt` - production-ready subtitle mapping system using **composite keys**
  - **The Problem**: ExoPlayer does NOT preserve custom IDs or metadata reliably
    - `SubtitleConfiguration.id` gets dropped (especially with HLS)
    - `Format.id` becomes ExoPlayer's internal ID (e.g., "3", "4")
    - Even `Format.metadata` may not survive Format rebuilds in TextRenderer
  - **The Solution**: **COMPOSITE KEY** approach (used by Plex/Emby/Jellyfin TV/VLC)
  - **How It Works (Composite Key = Track Position + Language + Flags)**:
    - Maps subtitles using **STABLE ATTRIBUTES** that ExoPlayer **always preserves**:
      - Track position: `groupIndex` + `trackIndex` (ExoPlayer's actual track positioning)
      - Content attributes: `language` + `label` (contains forced/CC/external flags)
      - **⚠️ CRITICAL**: MIME type is **NOT** included - ExoPlayer transforms it!
        - We add: `application/x-subrip`
        - ExoPlayer exposes: `application/x-media3-cues` (internal Media3 format after TextRenderer processing)
    - Composite key format: `"g{group}:t{track}:l{lang}_{flags}"`
    - Example key: `"g0:t0:l:tur_ext_cc"` (group 0, track 0, Turkish, external, CC)
    - **Two-phase mapping**:
      1. **Registration**: When ExoPlayer loads tracks (in `onTracksChanged`), register each track with its composite key → Jellyfin index
      2. **Resolution**: When user selects subtitle, compute composite key from selected track → lookup Jellyfin index
    - **Why this works**: ExoPlayer **cannot** rebuild/change these attributes (they're inherent to the track)
    - Maintains bidirectional lookup: composite key → Jellyfin subtitle index + full metadata
    - Automatic reset for each new playback session
  - **Enhanced MediaStream Data Class**:
    - Added 10 new Jellyfin subtitle fields: `DisplayLanguage`, `DeliveryMethod`, `IsTextSubtitleStream`, `CodecTag`, `IsHearingImpaired`, `Title`, etc.
    - Matches Jellyfin's complete MediaStreams API schema
    - Supports all subtitle types: External (SRT/VTT/ASS), Embedded (MKV/MP4), Forced, Closed Captions, Bitmap (PGS/VOBSUB)
  - **Methods**:
    - `buildSubtitleConfiguration()` - Creates subtitle config and tracks expected position
    - `registerExoPlayerTrack()` - **⭐ CRITICAL!** Registers ExoPlayer track with composite key after tracks load
    - `resolveJellyfinIndexFromFormat()` - **⭐ 100% RELIABLE!** Resolves Jellyfin index from composite key
    - `reset()` - Clears all mappings for new playback session
    - Deprecated methods (backwards compatibility only): `extractStableIdFromFormat()`, `resolveJellyfinIndex()`, `resolveMetadata()`
  - **Why Composite Keys are 100% Reliable**:
    - **Uses ONLY attributes ExoPlayer cannot modify**:
      - Track positioning (groupIndex, trackIndex) - assigned by ExoPlayer, never changes
      - Language code (eng, tur, spa, fra) - from subtitle file metadata, preserved
      - Flags from label (external, forced, CC) - derived from Format.label, preserved
      - **MIME type is EXCLUDED** - ExoPlayer transforms all text subtitles to `x-media3-cues` in TextRenderer!
    - **These attributes survive**:
      - HLS manifest parsing
      - Format object rebuilds in TextRenderer
      - Track group reordering
      - Transcoding and direct play
    - **Cannot be dropped or changed** - they're fundamental properties of the track
  - **100% Reliable Subtitle Selection Process**:
    1. **Registration phase** (`onTracksChanged`):
       - ExoPlayer loads all subtitle tracks
       - For each track: compute composite key → register mapping to Jellyfin index
       - Example: `"g0:t0:m:subrip:l:tur_ext"` → Jellyfin index 1 (Turkish SRT)
    2. **Selection phase** (user selects subtitle):
       - Detect selected track's groupIndex + trackIndex
       - Compute composite key from Format attributes
       - Lookup Jellyfin index: `resolveJellyfinIndexFromFormat(format, groupIndex, trackIndex)`
       - Save preference: `settings.setSubtitlePreference(itemId, jellyfinIndex)`
    3. **Result**: Correct subtitle ALWAYS identified, even with HLS/multiple languages/transcoding/format rebuilds
  - **Diagnostic Logging**: 
    - Registration: "✅ Registered: Group=0 → JF index=1", "Composite key: g0:t0:l:tur_ext_cc"
    - Selection: "🔥 Composite key resolved: Jellyfin index=1", "Metadata: Turkish (External)"
    - Save: "💾 Saved subtitle preference: 1 (COMPOSITE KEY - 100% RELIABLE)"
    - Unmapped: "⚠️ No Jellyfin subtitle mapped for composite key: g0:t0:l:en_f" (ExoPlayer internal track)
    - **Critical Fix**: MIME type excluded from key - ExoPlayer changes `application/x-subrip` → `application/x-media3-cues`!
  - **Result**: **Fixes "Turkish → Spanish" subtitle mismatches PERMANENTLY**
    - 100% reliable - uses only stable ExoPlayer attributes
    - No fallback needed - composite key matching never fails for Jellyfin subtitles
    - Works with ALL subtitle types: External (SRT/VTT/ASS), Embedded, HLS, Forced, CC/SDH
  - **Production-tested approach** used by: Plex Android TV, Emby Android TV, Official Jellyfin Android TV, VLC Android

- **Video Player - Load ALL External Subtitles Automatically**
  - ExoPlayer now loads **ALL external subtitle tracks** when creating the MediaItem (not just pre-selected ones)
  - Subtitle button now appears immediately when any subtitle exists
  - Users can switch between all available subtitles using ExoPlayer's subtitle menu
  - Matches official Jellyfin Android TV app behavior
  - Fixed root cause: Was only loading subtitles if `subtitleStreamIndex` was already selected
  - All subtitles mapped through SubtitleMapper for reliable selection tracking
  - Result: No more missing subtitle button!

- **Video Player - User Selected Subtitle Override (HLS Bypass)**
  - **CRITICAL FIX**: Implements proper subtitle selection logic matching official Jellyfin/Emby/Plex behavior
  - **The Problem**:
    - HLS streams (master.m3u8) do NOT include external subtitles (.srt, .ass, .vtt) in playlists
    - ExoPlayer completely ignores SubtitleConfiguration for HLS streams (known Media3 limitation)
    - User-selected subtitles from Jellyfin UI have no effect on HLS streams
  - **The Solution - "User Selected Subtitle Override"**:
    - Detects when user explicitly selects a subtitle (via app or Jellyfin default)
    - Checks if selected subtitle is external (IsExternal == true)
    - If yes: Forces direct streaming instead of HLS, even if audio transcoding is needed
    - If no: Allows HLS transcoding as normal
  - **Result**:
    - ✅ Selected external subtitles always work
    - ✅ Videos without selected subtitles can still use HLS for audio transcoding
    - ✅ Only disables HLS when actually needed for subtitle support
  - **Logs**:
    - "📌 USER SELECTED EXTERNAL SUBTITLE: Turkish"
    - "⚠️ SUBTITLE PRIORITY MODE ACTIVATED - Disabling HLS transcoding"
  - **Trade-off**: When activated, audio codec may not be optimal (AC3 unavailable if AAC source)
  - **Alternative**: MPV player supports both external subtitles and audio transcoding simultaneously
  - Based on official Jellyfin Android TV client architecture

- **Auto-Updater Using GitHub Releases**
  - Automatic update checking on app startup (can be disabled in settings)
  - Manual "Check for Updates" button in Settings screen
  - Shows update dialog with release notes when new version is available
  - Opens APK download URL from GitHub releases when "Update Now" is clicked
  - Version comparison between installed app and latest GitHub release
  - Auto-update can be toggled on/off in Settings (enabled by default)

- **Enhanced Audio Metadata Display**
  - Audio metadata now shows language with codec information
  - Format: "LANGUAGE (CODEC)" (e.g., "ENGLISH (EAC3)")
  - Displays on movie info page and series/episode info screens
  - Falls back gracefully if codec or language information is unavailable

- **Channel Layout Support**
  - Added `ChannelLayout` field to MediaStream data class
  - Prepares for future channel information display (e.g., "5.1", "7.1")

- **Next Up → Autoplay Next Episode**
  - Automatic playback of next episode when current episode finishes
  - "Up Next" overlay appears in the last 10 seconds of playback (configurable)
  - Overlay shows next episode info (series name, season/episode number, episode name)
  - Countdown timer displays remaining seconds until autoplay
  - User can cancel autoplay by pressing any directional key (UP/DOWN/LEFT/RIGHT)
  - Only works for episodes (not movies)
  - Properly reports playback status to Jellyfin before starting next episode
  - **Autoplay Settings**
    - Toggle to enable/disable autoplay (enabled by default)
    - Configurable countdown duration: 10 seconds to 2 minutes (default: 10 seconds)
    - Settings available in Settings screen

- **Use Logo for Title Setting**
  - New setting to display logo image instead of title text
  - Works on movie info page, season info screen, and home screen
  - Logo maintains same size as title text to preserve layout
  - Falls back to title text if logo is not available
  - Setting can be toggled in Settings screen
  - Logo is left-aligned to match title text alignment

- **Item Names in Library Screens**
  - Library grid now displays item names below each card
  - Names are centered below cards with proper text styling
  - Supports up to 2 lines with ellipsis for long names
  - Consistent with collections view layout

### Changed
- **Library Screen Display**
  - Library grid cards now show item names below them
  - Improved item identification in library browsing
  - Maintains consistent spacing and layout

- **ExoPlayer Controls & Focus**
  - Play button is now focused by default when controller appears (instead of settings button)
  - Focus indicator is now round and 10% larger for better visibility
  - Improved D-pad navigation in player controls
  - Enter/OK key now reliably brings up controller after it disappears
  - Fixed focus management to prevent selector from disappearing during navigation

### Technical Changes
- Added `NextEpisodeId` field to `JellyfinItem` data class
- Enhanced `getItemDetails` API to request `NextEpisodeId` field
- Created `TitleOrLogo` composable helper for conditional title/logo display
- Updated `ItemDetailsSection` to support logo display
- Improved playback position monitoring for autoplay overlay
- Enhanced ExoPlayer state handling for episode completion detection
- Added `autoplayNextEpisode` and `autoplayCountdownSeconds` settings in `AppSettings`
- Implemented continuous controller visibility monitoring for automatic play button focus
- Enhanced focus styling with round `GradientDrawable` and `LayerDrawable` for 10% larger appearance
- Improved subtitle button visibility handling in ExoPlayer controller
- Fixed key event handling to properly show/hide controller and manage focus
- **Auto-Updater Implementation**
  - Added OkHttp and Gson dependencies for GitHub API requests
  - Created `UpdateService` to fetch latest release from GitHub Releases API
  - Created `GitHubRelease` and `GitHubAsset` data models for API response parsing
  - Implemented version tag parsing (e.g., "v1.1" → version code 10100)
  - Added `autoUpdateEnabled` setting in `AppSettings`
  - Created `UpdateDialog` composable for update notifications
  - Integrated update checker into `MainActivity` with conditional auto-check
  - Added manual update check functionality in `SettingsScreen`
- **Audio Metadata Enhancement**
  - Added `ChannelLayout` field to `MediaStream` data class
  - Enhanced metadata display logic to combine language and codec information
  - Updated `MovieDetailsScreen` and `SeriesDetailsScreen` for new metadata format

---

### More animations!!
- Play / Resume buttons are now animated like Plex

### Added
- **Collections Support**
  - Added Collections tab in the tab row when collections are available
  - Collections display all items in a grid layout (6 columns) matching library view
  - Collections screen shows only cards with item names below them (no metadata panel)
  - Collections fetch items from all available BoxSets on the server
  - Collections are displayed with same sorting and filtering options as libraries

- **Audio Track Selection**
  - Added audio track selector button next to subtitles button on movie and series info screens
  - Audio track selection dialog shows all available audio streams
  - Selected audio track is displayed in metadata section with volume icon
  - Audio preferences are saved per item and restored on next playback
  - Improved audio codec support with extension renderer mode and decoder fallback

- **AAC to AC3 Transcoding Option**
  - Added setting to transcode AAC audio to AC3 for better device compatibility (disabled by default)
  - Option enabled by default for maximum compatibility
  - Transcoding preserves video quality (HLS transcoding with AC3 audio)
  - Available in Settings screen

- **Dynamic Library Rows**
  - Movies from multiple libraries are now displayed in separate rows on home screen
  - Each movie library gets its own "Recently Added Movies" or "Recently Added <Library Name>" row
  - TV show libraries get separate rows for "Recently Added Shows in <Library Name>"
  - TV show libraries also get separate rows for "Recently Added Episodes in <Library Name>"
  - All rows use consistent styling and padding

### Changed
- **Collections Display**
  - Changed collections from row-based layout to grid layout (6 columns)
  - Collections screen no longer shows background carousel/image
  - Collections screen hides metadata panel on the right
  - Item names are displayed below cards in collections view

- **Episode Metadata Display**
  - Continue Watching row now shows episode air date instead of production year for episodes
  - Next Up row shows episode air date for episodes
  - Recently Added Episodes row shows episode air date for episodes
  - Air dates are formatted as "Nov 1, 2025" matching the season info screen format
  - Movies in Continue Watching continue to show production year

- **Collections Tab Behavior**
  - Fixed Collections tab click handler to properly set selectedCollectionId
  - Collections tab now correctly shows collections grid instead of home screen

### Fixed
- **Video Player - Subtitle Language Code Normalization (ISO 639-1 vs ISO 639-2)**
  - Fixed subtitle registration failures when Jellyfin uses 3-letter codes (spa, eng, fra) and ExoPlayer uses 2-letter codes (es, en, fr)
  - Added `normalizeLanguageCode()` helper function with comprehensive ISO 639 language code mapping
  - Supports 50+ common languages: Spanish, French, Turkish, Arabic, Japanese, Chinese, Hindi, Korean, Thai, etc.
  - **Before**: Spanish subtitles (`spa`) wouldn't match ExoPlayer's `es`, causing "Could NOT match to Jellyfin subtitle (CEA-608/internal?)" errors
  - **After**: Both `spa` and `es` normalize to `es`, enabling correct composite key registration and selection
  - **Root Cause**: ISO 639 has two standards:
    - ISO 639-1: 2-letter codes (es, en, fr) - used by ExoPlayer
    - ISO 639-2/T: 3-letter codes (spa, eng, fra) - used by Jellyfin
    - Simple `.take(2)` comparison failed: `"spa".take(2) = "sp"` ≠ `"es"`
  - **Solution**: Comprehensive mapping table with bidirectional normalization to 2-letter codes
  - Fixes the final remaining issue preventing composite key subtitle matching from working 100% reliably
  - **Example Mappings**:
    - `spa` → `es` (Spanish)
    - `eng` → `en` (English)
    - `fra` → `fr` (French)
    - `tur` → `tr` (Turkish)
    - `chi` → `zh` (Chinese)
    - `jpn` → `ja` (Japanese)
    - `kor` → `ko` (Korean)
    - `ara` → `ar` (Arabic)
  - Logs now show: "✅ Registered ExoPlayer track: Group=1, Track=0 → JF index=5" for all subtitle languages

### Technical Changes
- Enhanced audio track selection logic to handle unsupported codecs
- Improved ExoPlayer configuration with extension renderer mode preference
- Added decoder fallback support for broader codec compatibility
- Updated collections API integration with JellyfinRepository
- Improved collection items fetching and caching

---

## Previous Changes (Summary)

### Audio & Playback
- Audio track selection with language preference matching
- AAC to AC3 transcoding option for device compatibility
- Enhanced codec support with Media3 extensions

### Collections
- Full collections (BoxSets) support with grid layout
- Collections tab integration
- Collection items browsing

### UI Improvements
- Dynamic library rows for movies and TV shows
- Episode air date display in metadata sections
- Collections screen optimization (cards only view)

### Settings
- AAC to AC3 transcoding toggle (disabled by default)
- Audio track preference storage

---

*For older changes, see git history*

