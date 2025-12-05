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

        // Version code: major * 10000 + minor * 100 + patch
        versionCode = 10111
        versionName = "1.1.11"

        ndk {
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

    androidComponents {
        onVariants { variant ->
            if (variant.buildType == "release") {
                variant.outputs.forEach { output ->
                    val out = output as com.android.build.api.variant.impl.VariantOutputImpl
                    out.outputFileName.set("elefin-release.apk")
                }
            }
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
    
}

dependencies {

    // -------------------------------------------------------------
    // AndroidX Core + Leanback
    // -------------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)

    // -------------------------------------------------------------
    // Images - Glide + Coil
    // -------------------------------------------------------------
    implementation(libs.glide)
    kapt("com.github.bumptech.glide:compiler:4.11.0")
    implementation(libs.coil.compose)

    // -------------------------------------------------------------
    // Compose BOM + UI
    // -------------------------------------------------------------
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.foundation)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")

    // TV Compose
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)

    // -------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------
    implementation(libs.navigation.compose)

    // -------------------------------------------------------------
    // JSON / Networking - Ktor + Gson
    // -------------------------------------------------------------
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.code.gson:gson:2.11.0")

    // GitHub updater
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // -------------------------------------------------------------
    // Lottie
    // -------------------------------------------------------------
    implementation(libs.lottieCompose)

    // -------------------------------------------------------------
    // Media3 / ExoPlayer - COMPLETE JELLYFIN CLIENT EXTENSIONS
    // -------------------------------------------------------------
    
    // Core ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)
    
    // Adaptive Streaming (HLS, DASH, SmoothStreaming)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
    
    // UI Components
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.ui.leanback) // Android TV optimized UI
    
    // Extractors (Subtitle support: SRT, ASS, VTT, TTML, MKV internal subs)
    implementation(libs.media3.extractor)
    
    // MediaSession (TV remote controls, Next Episode, Media keys)
    implementation(libs.media3.session)
    
    // Network DataSources
    implementation(libs.media3.datasource.okhttp) // REQUIRED for Jellyfin API reliability
    implementation(libs.media3.datasource.cronet) // Optional: Ultra-low latency streaming
    
    // Transformer (Optional: Advanced media processing)
    implementation(libs.media3.transformer)
    
    // -------------------------------------------------------------
    // FFmpeg Audio Decoder (Jellyfin-provided)
    // -------------------------------------------------------------
    // Adds support for: DTS, DTS-HD, TrueHD, AC3, E-AC3, and 30+ codecs
    // Licensed under GPLv3 (compatible with Jellyfin ecosystem)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)
    
    // -------------------------------------------------------------
    // MPV Player (Optional - can be enabled in settings)
    // -------------------------------------------------------------
    // Uses embedded libmpv.so and libplayer.so from jniLibs folder
    // Requires .so files to be placed in app/src/main/jniLibs/{abi}/
}
