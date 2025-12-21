# Elefin

An Android TV client for Jellyfin built with Jetpack Compose. This is not a fork of the Jellyfin Android TV. Big credits to them for everything nonetheless.

## Description

Elefin is a modern Android TV Jellyfin client built from the ground up using Jetpack Compose for TV. It offers a polished Material Design 3 interface optimized for television viewing with professional-grade performance on all Android TV devices including NVIDIA Shield, ONN 4K, and budget boxes.

## Features

Plex inspired home screen navigation
Auto play next episode
Download subtitles via the app from open Subtitles
Jellyseer integration to request movies and tv shows directly from the app itself
MPV player support / Exoplayer is still preferred
Subtitles size and color customization
Dark mode support
Image and poster caching for smoother navigation
Autoupdates directly from github when new releases are available
Fake HDR and video sharpening options
Auto refresh media
Hide shows with empty folders (maintainneerr users will love this)

and a lot more (see changelog for detailed features list)

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

