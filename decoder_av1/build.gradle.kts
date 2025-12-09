plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "androidx.media3.decoder.av1"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        ndk {
            // Only build for ARM architectures (Shield TV, most Android TV devices)
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                // Release build for better performance
                arguments("-DCMAKE_BUILD_TYPE=Release")
                arguments("-DBUILD_TESTING=OFF")
                targets("gav1JNI")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Configure native build with CMake
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // Media3 decoder base
    api("androidx.media3:media3-decoder:1.8.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.annotation:annotation:1.9.1")
}

