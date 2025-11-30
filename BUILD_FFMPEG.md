# Building Media3 FFmpeg Extension for Elefin

This guide explains how to build the FFmpeg extension for Media3 to enable:
- **PGS subtitles** (.sup) - Image-based subtitles from Blu-ray rips
- **DTS audio** - High-quality surround sound
- **TrueHD audio** - Lossless audio codec
- **VobSub** (.sub/.idx) - DVD subtitles
- **30+ additional codecs**

## Prerequisites

### Required Tools
1. **Android NDK** (r21e or later)
2. **Git**
3. **FFmpeg source** (4.4 or later)
4. **Linux/macOS** (or WSL2 on Windows)
5. **Android Studio** with SDK installed

### Check Your NDK Version
```bash
ls $ANDROID_SDK_ROOT/ndk/
# Should show version like: 25.2.9519653
```

If not installed:
```bash
# In Android Studio: Tools → SDK Manager → SDK Tools → NDK (Side by side)
# Or via command line:
sdkmanager --install "ndk;25.2.9519653"
```

---

## Method 1: Pre-Built AAR (Recommended - Fast)

### Option A: Use Androidx Media3 Official Build

The Media3 team provides pre-built FFmpeg extensions for common architectures.

1. **Add the FFmpeg extension to your project**

Edit `app/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // FFmpeg decoder extension
    implementation("androidx.media3:media3-decoder:1.8.0")
    implementation("androidx.media3:media3-decoder-ffmpeg:1.8.0")
}
```

2. **Download the native libraries**

The extension needs native `.so` files. Download from:
https://github.com/androidx/media/releases

Look for: `media3-decoder-ffmpeg-1.8.0-all.aar`

3. **Extract and add to project**

```bash
# Create libs directory
mkdir -p app/libs

# Download AAR
curl -L -o app/libs/media3-decoder-ffmpeg.aar \
  "https://github.com/androidx/media/releases/download/1.8.0/media3-decoder-ffmpeg-1.8.0-all.aar"
```

4. **Update build.gradle.kts**

```kotlin
dependencies {
    // Point to local AAR
    implementation(files("libs/media3-decoder-ffmpeg.aar"))
}
```

---

## Method 2: Build from Source (Advanced)

This gives you full control and latest FFmpeg features.

### Step 1: Clone Media3 Repository

```bash
cd ~/Projects  # or your preferred directory
git clone https://github.com/androidx/media.git
cd media
git checkout release  # Use stable release branch
```

### Step 2: Set Environment Variables

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"  # Adjust path
export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/25.2.9519653"  # Adjust version
export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
```

For **Windows WSL2**:
```bash
export ANDROID_SDK_ROOT="/mnt/c/Users/YOUR_USERNAME/AppData/Local/Android/Sdk"
export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/25.2.9519653"
```

### Step 3: Run FFmpeg Build Script

Media3 provides a script to download and compile FFmpeg:

```bash
cd libraries/decoder_ffmpeg/src/main
./build_ffmpeg.sh
```

**Build targets** (customize in script if needed):
- `armeabi-v7a` - 32-bit ARM (older devices)
- `arm64-v8a` - 64-bit ARM (Shield TV, modern devices) ← **REQUIRED**
- `x86` - 32-bit Intel (emulators)
- `x86_64` - 64-bit Intel (emulators)

**For Shield TV only** (faster build):
```bash
# Edit build_ffmpeg.sh and change:
ENABLED_ABIS=("arm64-v8a")
```

This takes **15-30 minutes** depending on your CPU.

### Step 4: Build the AAR

```bash
cd ~/Projects/media
./gradlew :media3-decoder-ffmpeg:assembleRelease
```

Output will be at:
```
libraries/decoder_ffmpeg/build/outputs/aar/media3-decoder-ffmpeg-release.aar
```

### Step 5: Copy to Your Project

```bash
cp libraries/decoder_ffmpeg/build/outputs/aar/media3-decoder-ffmpeg-release.aar \
   /path/to/elefin/app/libs/media3-decoder-ffmpeg.aar
```

### Step 6: Update Elefin build.gradle.kts

```kotlin
dependencies {
    implementation("androidx.media3:media3-decoder:1.8.0")
    implementation(files("libs/media3-decoder-ffmpeg.aar"))
}
```

---

## Method 3: Quick Script (Automated)

Save this as `build_ffmpeg_extension.sh`:

```bash
#!/bin/bash
set -e

echo "🔧 Building Media3 FFmpeg Extension for Elefin"

# Configuration
MEDIA3_REPO="https://github.com/androidx/media.git"
WORK_DIR="$HOME/media3_build"
ELEFIN_DIR="/path/to/elefin"  # CHANGE THIS
ENABLED_ABIS=("arm64-v8a")    # Shield TV only

# Check prerequisites
command -v git >/dev/null 2>&1 || { echo "❌ Git required"; exit 1; }
[ -z "$ANDROID_SDK_ROOT" ] && { echo "❌ Set ANDROID_SDK_ROOT"; exit 1; }
[ -z "$ANDROID_NDK_ROOT" ] && { echo "❌ Set ANDROID_NDK_ROOT"; exit 1; }

# Clone Media3
echo "📦 Cloning Media3..."
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"
[ ! -d "media" ] && git clone "$MEDIA3_REPO"
cd media
git checkout release

# Customize build
echo "⚙️ Configuring for Shield TV (arm64-v8a only)..."
sed -i 's/ENABLED_ABIS=.*/ENABLED_ABIS=("arm64-v8a")/' \
    libraries/decoder_ffmpeg/src/main/build_ffmpeg.sh

# Build FFmpeg
echo "🔨 Building FFmpeg (this takes 15-30 minutes)..."
cd libraries/decoder_ffmpeg/src/main
./build_ffmpeg.sh

# Build AAR
echo "📦 Building AAR..."
cd "$WORK_DIR/media"
./gradlew :media3-decoder-ffmpeg:assembleRelease

# Copy to Elefin
echo "📋 Copying to Elefin..."
mkdir -p "$ELEFIN_DIR/app/libs"
cp libraries/decoder_ffmpeg/build/outputs/aar/media3-decoder-ffmpeg-release.aar \
   "$ELEFIN_DIR/app/libs/media3-decoder-ffmpeg.aar"

echo "✅ Done! Add to build.gradle.kts:"
echo "   implementation(files(\"libs/media3-decoder-ffmpeg.aar\"))"
```

Run it:
```bash
chmod +x build_ffmpeg_extension.sh
./build_ffmpeg_extension.sh
```

---

## Step 7: Enable FFmpeg in ExoPlayer

After adding the AAR, update your player code:

### In `JellyfinVideoPlayerScreen.kt`:

```kotlin
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary

// Before creating ExoPlayer
@UnstableApi
fun buildElefinPlayer(context: Context): ExoPlayer {
    // Check if FFmpeg is available
    val ffmpegAvailable = try {
        FfmpegLibrary.isAvailable()
    } catch (e: Exception) {
        Log.w("ExoPlayer", "FFmpeg not available: ${e.message}")
        false
    }
    
    if (ffmpegAvailable) {
        Log.i("ExoPlayer", "✅ FFmpeg decoder available - DTS/TrueHD/PGS supported")
    } else {
        Log.w("ExoPlayer", "⚠️ FFmpeg decoder not available - limited codec support")
    }
    
    val renderersFactory = DefaultRenderersFactory(context).apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        setEnableDecoderFallback(true)
    }
    
    return ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(DefaultTrackSelector(context))
        .build()
}
```

The `EXTENSION_RENDERER_MODE_PREFER` setting you already have will automatically use FFmpeg when needed!

---

## Verify Installation

### Test PGS Subtitles
1. Play a Blu-ray rip with PGS subtitles (.sup)
2. Check logcat:
```bash
adb logcat | grep -i "ffmpeg\|pgs\|subtitle"
```

Expected output:
```
ExoPlayer: ✅ FFmpeg decoder available
ExoPlayer: Using FFmpeg renderer for PGS subtitles
```

### Test DTS Audio
1. Play a movie with DTS audio
2. Check logcat:
```bash
adb logcat | grep -i "dts\|audio"
```

Expected output:
```
ExoPlayer: Using FFmpeg audio renderer for DTS
```

---

## Troubleshooting

### "FFmpeg not available"
- Ensure `.so` files are in the AAR's `jni/` folders
- Check ABI matches your device (arm64-v8a for Shield TV)
- Verify AAR is in `app/libs/` directory

### "UnsatisfiedLinkError"
- FFmpeg native libraries not included
- Rebuild AAR with correct NDK version
- Check `build.gradle.kts` has `implementation(files("libs/..."))`

### Build fails on Windows
- Use WSL2 (Ubuntu)
- Install build-essential: `sudo apt install build-essential`

### "ndk-build not found"
```bash
export PATH="$ANDROID_NDK_ROOT:$PATH"
```

---

## File Size Impact

Adding FFmpeg extension increases APK size:
- **arm64-v8a only**: ~8-10 MB
- **All ABIs**: ~35-40 MB

**Recommendation**: Only include arm64-v8a for Shield TV release.

---

## Summary

**Fastest method**: Use pre-built AAR from Media3 releases
**Most control**: Build from source with custom FFmpeg flags
**Best for Elefin**: Build arm64-v8a only for Shield TV

After setup, ExoPlayer will automatically use FFmpeg for:
✅ PGS subtitles (Blu-ray)
✅ DTS/DTS-HD audio
✅ TrueHD audio
✅ VobSub subtitles (DVD)
✅ 30+ additional codecs

Your Elefin client will match **native Jellyfin clients** in codec support! 🎯

