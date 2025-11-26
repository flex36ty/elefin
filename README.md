# Elefin

An Android TV client for Jellyfin built with Jetpack Compose. This is not a fork of the Jellyfin Android TV. Big credits to them for everything nonetheless

## Description

Elefin is an Android TV jellyfin client. It's built from the ground up using Jetpack Compose for TV, it offers a modern Material Design 3 interface optimized for television viewing.

## Features

### Home screen
- **Added periodic auto-refresh mechanism using LaunchedEffect
- **Checks for new media at the configured interval
- **Refreshes all rows (Continue Watching, Next Up, Recently Added, etc.) when new content is detected
- **Respects the enabled/disabled setting and interval changes

### How it works
- **Periodically (every 2–15 minutes, configurable) checks for new media
- **Compares recent items with current lists to detect new content
- **If new media is found, refreshes all media rows
- **Logs refresh activity for debugging
- **The feature is enabled by default with a 5-minute interval. Users can adjust or disable it in Settings.

### Media Browsing
- **Home Screen** with featured content and dynamic backdrop images
- **Library Browsing** with support for Movies, TV Shows, and Episodes
- **Sorting Options**: Alphabetically, Date Added, Date Released
- **Recently Added** sections for Movies, TV Shows, and Episodes
- **Continue Watching** row with progress tracking
- **Next Up** row for tracking your next episodes to watch
- **Recently Released Movies** section

### Content Discovery
- Beautiful **movie and TV show details** screens
- **Cast information** with focusable cards
- **Similar content** recommendations
- **Metadata display** including:
  - Year, Runtime, Genre
  - Maturity ratings
  - Review ratings (Rotten Tomatoes Fresh/Rotten/Audience, IMDb)
  - Audio language information
  - Video resolution and HDR/SDR indicators

### Video Playback
- **ExoPlayer** support (default) for reliable video playback
- **MPV Player** option for advanced video codec support
- Automatic playback position tracking and resume functionality
- **Subtitle selection** with language detection
- **Seek controls** (15 seconds forward/backward with D-pad)
- Title overlay showing movie/show name and episode information
- Automatic error handling and fallback to transcoding

### Customization
- **Dark Mode** - Disable background images and use Material dark background
- **Image caching** options (Coil or Glide)
- **Poster resolution** use lower res images to save bandwidth and make browsing smoother

### User Interface
- **Material Design 3** components optimized for TV
- Focus-aware navigation with smooth animations
- **Circular progress bars** for Continue Watching cards
- **Thumbnail images** for episodes with horizontal cards
- Responsive layouts that adapt to content
- Touch-optimized controls for Android TV remote

### Settings
- Player selection (ExoPlayer/MPV)
- Image loading preferences
- Debug outlines for development
- Multiple customization options

## Screenshots

![carousel](https://github.com/flex36ty/elefin/blob/master/screenshots/carousel.png?raw=true)
![dark2](https://github.com/flex36ty/elefin/blob/master/screenshots/dark2.png?raw=true)
![darkmode1](https://github.com/flex36ty/elefin/blob/master/screenshots/darkmode1.png?raw=true)
![exit](https://github.com/flex36ty/elefin/blob/master/screenshots/exit.png?raw=true)
![homescreen](https://github.com/flex36ty/elefin/blob/master/screenshots/homescreen.png?raw=true)
![library](https://github.com/flex36ty/elefin/blob/master/screenshots/library.png?raw=true)
![movieviewpage](https://github.com/flex36ty/elefin/blob/master/screenshots/moveviewpage.png?raw=true)

## Requirements

- **Android TV** device or Android TV emulator
- **Android 5.0 (API 21)** or higher
- **Jellyfin Server** (any version with API support)
- Network connection to Jellyfin server

## Building

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/elefin.git
   cd elefin
   ```

2. Open the project in Android Studio or build from command line:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on your Android TV device:
   ```bash
   ./gradlew installDebug
   ```

## Configuration

### Server Connection

1. Launch the app on your Android TV
2. Enter your Jellyfin server URL
3. Login with your credentials or use Quick Connect

### Settings

Access settings from the home screen to configure:
- Player preferences
- Image loading and caching
- Dark mode
- Remote theming
- Time format
- And more...

## Screenshots

_Add screenshots here to showcase the app interface_

## Technical Details

### Built With

- **Kotlin** - Programming language
- **Jetpack Compose for TV** - Modern UI framework
- **Material Design 3** - Design system
- **ExoPlayer/Media3** - Video playback engine
- **MPV** - Alternative video player (optional)
- **Ktor** - HTTP client for Jellyfin API
- **Coil/Glide** - Image loading libraries
- **Kotlin Serialization** - JSON parsing

### Architecture

- MVVM pattern with Repository pattern
- Jetpack Compose for declarative UI
- Coroutines for asynchronous operations
- StateFlow for reactive state management

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

