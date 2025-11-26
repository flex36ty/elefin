package com.flex.elefin

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule
import com.flex.elefin.jellyfin.AppSettings

@GlideModule
class ElefinGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val settings = AppSettings(context)
        
        // Configure memory cache - use 40% of available memory when caching is enabled
        val calculator = MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(2f)
            .build()
        
        if (settings.cacheLibraryImages) {
            builder.setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong() * 2))
        }
        
        // Configure disk cache - use InternalCacheDiskCacheFactory which stores in filesDir
        // This persists across app restarts and is less likely to be cleared by the system
        if (settings.cacheLibraryImages) {
            val diskCacheSizeBytes = 1024 * 1024 * 500 // 500MB (increased from 250MB for better persistence)
            builder.setDiskCache(
                InternalCacheDiskCacheFactory(
                    context,
                    "glide_image_cache",
                    diskCacheSizeBytes.toLong()
                )
            )
        }
        
        // Use higher quality decoding for better image quality
        builder.setDefaultRequestOptions(
            com.bumptech.glide.request.RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565) // Use RGB_565 for better performance while maintaining quality
                .diskCacheStrategy(if (settings.cacheLibraryImages) DiskCacheStrategy.AUTOMATIC else DiskCacheStrategy.NONE)
                .skipMemoryCache(!settings.cacheLibraryImages)
        )
    }
    
    override fun isManifestParsingEnabled(): Boolean {
        // Disable manifest parsing to improve performance
        return false
    }
}

