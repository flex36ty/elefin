# Elefin

An Android TV client for Jellyfin built with Jetpack Compose. This is not a fork of the Jellyfin Android TV. Big credits to them for everything nonetheless.

## Description

Elefin is a modern Android TV Jellyfin client built from the ground up using Jetpack Compose for TV. It offers a polished Material Design 3 interface optimized for television viewing with professional-grade performance on all Android TV devices including NVIDIA Shield, ONN 4K, and budget boxes.

## ✨ Latest Features (v1.1.6)

### 🚀 Performance Optimizations
- **UI Performance for Android TV**: Optimized for smooth 60fps scrolling on all devices including older NVIDIA Shield models (Tegra X1)
  - `@Stable` annotations on all data classes to prevent unnecessary recomposition
  - LazyRow optimization with proper `key` and `contentType` parameters
  - Significantly reduced layout invalidations and memory overhead
  - Based on Google TV and streaming app best practices
  
- **Background Image Debouncing**: 1-second delay before changing backgrounds
  - Prevents excessive server requests during fast scrolling
  - Reduces network traffic by 80-90% when browsing
  - Smoother scrolling without background flickering
  - Applied to all screens: home rows, library grids, and collection grids

- **Background Image Optimization**: All backgrounds reduced from 4K to 1080p
  - 75% smaller file size (~4MB → ~1MB per backdrop)
  - Aggressive memory and disk caching for instant loading
  - Smooth 300ms crossfade animations with hardware acceleration
  - Perfect for 1080p TVs and still great on 4K displays

- **Disable UI Animations Setting**: Optional performance mode for weaker devices
  - Disables row scrolling animations for instant navigation
  - Automatically reduces image resolution from 4K to 600×900
  - Reduces GPU load, memory usage, and bandwidth by ~85%
  - UI refreshes immediately when toggled (no restart required)

## Features

### Home Screen
- **Periodic auto-refresh** mechanism for detecting new media
- **Checks for new content** at configurable intervals (2-15 minutes)
- **Automatic row updates** when new media is detected (Continue Watching, Next Up, Recently Added)
- **Manual refresh button** with image cache clearing
- **Dynamic backdrop images** that change based on focused content
- **Debounced background loading** for efficient resource usage

### Media Browsing
- **Continue Watching** row sorted by last played date (most recent first)
- **Next Up** row with intelligent image fallback (Thumb → Backdrop → Primary)
- **Recently Released Movies** section
- **Recently Added** sections for Movies, TV Shows, and Episodes (per library)
- **Library Browsing** with grid layouts for Movies, TV Shows, and Episodes
- **Collection Browsing** with support for custom collections
- **Sorting Options**: Alphabetically, Date Added, Date Released
- **Filter Options**: Hide shows with zero episodes
- **Debounced background images** for smooth browsing experience

### Content Discovery
- Beautiful **movie and TV show details** screens with scrollable synopsis
- **Single-line synopsis** with clickable popup for full text
- **Logo display** (30% smaller for better layout)
- **Cast information** with focusable cards and actor photos
- **Similar content** recommendations (movies only)
- **Episode name sizing** matches home screen for consistency
- **Metadata display** including:
  - Year, Runtime, Genre
  - Maturity ratings
  - Review ratings (Rotten Tomatoes Fresh/Rotten/Audience, IMDb)
  - Audio language information
  - Video resolution and HDR/SDR indicators
- **Client identification** as "Elefin" on Jellyfin server dashboards

### Video Playback
- **ExoPlayer with FFmpeg** support (default, recommended) for comprehensive codec support
  - **Advanced Audio Codecs**: DTS, DTS-HD Master Audio, Dolby TrueHD, AC3, E-AC3
  - **Additional Codecs**: FLAC, ALAC, Vorbis, Opus, and 30+ more formats
  - Powered by Jellyfin's prebuilt FFmpeg decoder extension (GPLv3)
  - Automatic hardware acceleration when available
  - Preferred over platform decoders for maximum compatibility
  
- **OpenGL Video Enhancements** (optional):
  - Custom GL pipeline for real-time video post-processing
  - **Fake HDR** simulation with tone mapping and brightness boost (strength 1.0-2.0)
  - **Image Sharpening** using edge detection unsharp mask technique (strength 0.0-1.0)
  - Adjustable strength controls for both effects
  - Zero performance impact when disabled (uses standard PlayerView)
  - OpenGL ES 2.0 pipeline intercepts video frames for shader processing
  - Inspired by VLC, Kodi, and MPV rendering techniques
  - Full ExoPlayer compatibility maintained
  
- **MPV Player** option (experimental, for advanced users)
  
- **Enhanced Subtitle Support**:
  - Language-aware selection with forced/CC/external flag detection
  - Fixed subtitle language selection (matches by language + flags, not position)
  - Handles ExoPlayer's internal track reordering correctly
  - Support for SRT, VTT, ASS, PGS, and embedded formats
  - Customizable appearance with new size options (20-100, default 30)
  - Robust matching system for accurate subtitle selection
  - Immediate UI updates when subtitle preference changes
  
- **Custom Settings Menu** with modern transparent overlay:
  - Quick access to subtitle, audio, and playback speed settings
  - Auto-focus on first item for better TV navigation
  - Semi-transparent dark background for viewing content while adjusting
  - Optimized for Android TV remote D-pad navigation
  - Purple focus highlights on control buttons
  
- **Playback Controls**:
  - Play/pause button focused by default when controller appears
  - Automatic pause when home button pressed (app minimized)
  - Seek controls (15 seconds forward/backward with D-pad)
  - Title overlay showing movie/show name and episode information
  - Automatic playback position tracking and resume functionality
  - Automatic error handling and fallback to transcoding

### Customization
- **Dark Mode** - Disable background images and use Material dark background
- **Image Caching** options with memory and disk cache support
- **Poster Resolution** options - use lower resolution images (600×900 vs 4K) to save bandwidth
- **Performance Mode** - Disable UI animations for better performance on weaker devices
- **Auto-refresh Intervals** - Configure home screen refresh timing (2-15 minutes)
- **Hide Shows with Zero Episodes** - Filter out empty series from library views

### User Interface
- **Material Design 3** components optimized for TV
- **Smooth 60fps scrolling** on all Android TV devices (NVIDIA Shield, ONN 4K, budget boxes)
- Focus-aware navigation with optimized animations
- **Circular progress bars** for Continue Watching cards
- **Thumbnail images** for episodes with horizontal cards (16:9 aspect ratio)
- **Debounced background changes** (1-second delay) for efficient browsing
- **Hardware-accelerated image rendering** with 300ms crossfade animations
- Responsive layouts that adapt to content
- Touch-optimized controls for Android TV remote D-pad navigation
- **GL pipeline warmup** to prevent initial UI stutter

### Settings
- **Player Selection**: ExoPlayer with FFmpeg (recommended) / MPV (experimental)
- **Video Enhancements**: Fake HDR and sharpening with adjustable strength controls
- **Subtitle Customization**: Size (20-100, default 30), color, background transparency
- **Performance Options**: Disable UI animations for weaker devices
- **Image Preferences**: Coil/Glide selection, caching, poster resolution
- **Auto-refresh Intervals**: Configure home screen refresh timing
- **Dark Mode**: Disable background images for pure dark theme
- **Debug Options**: Outlines for development
- Multiple customization options for personalized experience

## Screenshots

![home](https://raw.githubusercontent.com/flex36ty/elefin/master/screenshots/home.png)
![watched](https://raw.githubusercontent.com/flex36ty/elefin/master/screenshots/watched.png)
![series](https://raw.githubusercontent.com/flex36ty/elefin/master/screenshots/series.png)
![home](https://raw.githubusercontent.com/flex36ty/elefin/master/screenshots/home.png)

### Dark mode

![darkmode1](https://raw.githubusercontent.com/flex36ty/elefin/master/screenshots/darkmode.png)

## Requirements

- **Android TV** device or Android TV emulator
- **Android 5.0 (API 21)** or higher
- **Jellyfin Server** (any version with API support)
- Network connection to Jellyfin server

### Recommended Devices
- ✅ **NVIDIA Shield** (2015/2017/2019/Pro) - Optimized for Tegra X1
- ✅ **Chromecast with Google TV** (4K/HD)
- ✅ **ONN 4K Streaming Box**
- ✅ Budget Android TV boxes - Performance mode available
- ✅ Android TV built into smart TVs

## Configuration

### Server Connection

1. Launch the app on your Android TV
2. Enter your Jellyfin server URL
3. Login with your credentials or use Quick Connect

### Settings

Access settings from the home screen to configure:
- **Player Preferences**: ExoPlayer (FFmpeg) vs MPV
- **Video Enhancements**: Fake HDR, sharpening with strength controls
- **Subtitle Options**: Size, color, background transparency
- **Performance Mode**: Disable animations for weaker devices
- **Image Loading**: Caching, resolution, library preferences
- **Dark Mode**: Pure dark theme without background images
- **Auto-refresh**: Configure new media detection intervals
- **Time Format**: 12/24 hour display
- And more customization options...

## Performance

Elefin is optimized for smooth performance on all Android TV devices:

### Optimization Highlights
- **60fps scrolling** even on older NVIDIA Shield (Tegra X1) hardware
- **@Stable data classes** prevent unnecessary UI recomposition
- **Debounced background loading** reduces server requests by 80-90%
- **Optimized image caching** with memory and disk cache for instant loading
- **1080p backgrounds** (75% smaller than 4K) with hardware acceleration
- **Performance mode** available for budget devices (disables animations, reduces image resolution)
- **LazyRow/LazyColumn optimization** with proper keys and contentType
- **GL pipeline warmup** prevents initial UI stutter

### Based on Industry Best Practices
- Google TV UI optimization techniques
- Netflix/Disney+/Plex-style debounced carousels
- Professional streaming app performance patterns
- Optimized for both high-end and budget Android TV hardware

## Screenshots

_Add screenshots here to showcase the app interface_

## Technical Details

### Built With

- **Kotlin** - Programming language
- **Jetpack Compose for TV** - Modern UI framework
- **Material Design 3** - Design system
- **ExoPlayer/Media3** - Video playback engine with full codec support
- **Jellyfin FFmpeg Decoder** - Advanced audio codec support (DTS, TrueHD, AC3, etc.)
- **OpenGL ES 2.0** - Custom video post-processing pipeline (fake HDR, sharpening)
- **MPV** - Alternative video player (experimental)
- **Ktor** - HTTP client for Jellyfin API
- **Coil/Glide** - Image loading libraries
- **Kotlin Serialization** - JSON parsing

### Architecture

- **MVVM pattern** with Repository pattern for clean separation
- **Jetpack Compose for TV** for declarative, reactive UI
- **Kotlin Coroutines** for asynchronous operations and background tasks
- **StateFlow** for reactive state management
- **@Stable annotations** for optimized recomposition
- **LazyRow/LazyColumn** with proper keys and contentType for efficient scrolling
- **Debounced state updates** for performance optimization
- **Hardware-accelerated rendering** with OpenGL ES 2.0 (optional)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- The Jellyfin team for working hard on delivering an awesome Open Source software
- Built for the [Jellyfin](https://jellyfin.org/) media server
- Uses Material Design components from AndroidX
- Inspired by modern TV streaming interfaces

## Disclaimer

Elefin is an independent client application and is not affiliated with or endorsed by the Jellyfin project.

