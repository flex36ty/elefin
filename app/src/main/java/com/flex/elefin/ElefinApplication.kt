package com.flex.elefin

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

import org.schabi.newpipe.extractor.NewPipe
import com.flex.elefin.networking.ElefinDownloader

class ElefinApplication : Application(), ImageLoaderFactory {
    private var startedActivities = 0
    private var changingConfiguration = false
    private val launcherAccountListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "server_url" || key == "user_id" || key == "access_token")
            com.flex.elefin.discovery.TvHome.accountChanged(this)
    }
    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("jellyfin_config", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(launcherAccountListener)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) changingConfiguration = activity.isChangingConfigurations
                com.flex.elefin.discovery.TvHome.schedule(this@ElefinApplication)
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) {
                if (startedActivities++ == 0 && !changingConfiguration) {
                    com.flex.elefin.jellyfin.LibraryNewContent.newForegroundSession()
                }
                changingConfiguration = false
                com.flex.elefin.jellyfin.LibraryNewContent.account(this@ElefinApplication)?.let {
                    com.flex.elefin.jellyfin.LibraryNewContent.baseline(this@ElefinApplication, it)
                }
            }
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        NewPipe.init(ElefinDownloader())
        // Include HLS manifests when YouTube does not return direct media streams.
        org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
            .setFetchVisionosClient(true)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.4) // Use 40% of available memory (increased for better caching)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    // Use filesDir instead of cacheDir for better persistence across restarts
                    // filesDir is less likely to be cleared by the system
                    .directory(filesDir.resolve("image_cache"))
                    // Increased to 5% of available disk space (was 2%)
                    // This gives more room for caching while still being reasonable
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false) // Always cache images regardless of HTTP headers
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

