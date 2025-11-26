plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.flex.elefin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flex.elefin"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Include all ABIs that have native libraries
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("lib")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.glide)
    kapt("com.github.bumptech.glide:compiler:4.11.0")
    
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.foundation)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    
    // TV Compose
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)
    
    // Material Icons Extended (includes Language icon and more)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Coil for image loading
    implementation(libs.coil.compose)
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // Ktor for HTTP client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    
    // Media3/ExoPlayer for video playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls) // HLS streaming support (better subtitle track detection)
    implementation(libs.media3.exoplayer.dash) // DASH streaming support (better subtitle track detection)
    // Note: FFmpeg extension is not available as a pre-built Maven dependency
    // Extension renderer mode (EXTENSION_RENDERER_MODE_PREFER) is already configured in JellyfinVideoPlayerScreen
    // which enables software decoding for additional codecs. For full FFmpeg support (DTS, TrueHD),
    // the extension would need to be built from source or use a third-party build.
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.common)
    
    // Lottie for animations
    implementation(libs.lottieCompose)
}