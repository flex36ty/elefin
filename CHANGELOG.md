# Changelog v1.1

All notable changes to Elefin will be documented in this file.

## Unreleased / Upcoming Release

### Changed
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
  - Updated Search screen to use matching Jellyfin AndroidTV color scheme
  - Added `TvSearchTextField` component for search-specific styling with pill shape

### Added
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
  - Added setting to transcode AAC audio to AC3 for better device compatibility
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
- AAC to AC3 transcoding toggle (enabled by default)
- Audio track preference storage

---

*For older changes, see git history*

