# Changelog

All notable changes to Elefin will be documented in this file.

## [Unreleased]

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

