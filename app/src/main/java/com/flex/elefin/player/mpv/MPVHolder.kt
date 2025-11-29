package com.flex.elefin.player.mpv

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import `is`.xyz.mpv.MPVView
import java.io.File

/**
 * ⭐ CRITICAL: MPV Singleton Holder
 * 
 * MPVLib can only be initialized ONCE per process lifetime.
 * This object ensures we create ONE MPVView instance and reuse it
 * across all video playbacks.
 * 
 * DO NOT create multiple MPVView instances - it will crash!
 */
object MPVHolder {
    private const val TAG = "MPVHolder"
    
    @Volatile
    private var mpvInstance: MPVView? = null
    
    @Volatile
    private var initialized = false
    
    // ⭐ CRITICAL: MPV is only truly ready after file-loaded event
    // Use Compose State so Composables can react to changes
    var ready by mutableStateOf(false)
        private set
    
    /**
     * Get or create the singleton MPVView instance.
     * This is safe to call multiple times - it returns the same instance.
     */
    fun getOrCreateMPV(context: Context): MPVView {
        if (mpvInstance != null) {
            Log.d(TAG, "Returning existing MPV singleton instance")
            return mpvInstance!!
        }
        
        synchronized(this) {
            // Double-check after acquiring lock
            if (mpvInstance != null) {
                return mpvInstance!!
            }
            
            Log.d(TAG, "Creating MPV singleton instance (FIRST TIME ONLY)")
            
            // Create the MPVView
            val mpv = MPVView(context.applicationContext, null)
            
            // Initialize MPV if not already done
            if (!initialized) {
                val configDir = File(context.filesDir, "mpv_config").apply {
                    if (!exists()) mkdirs()
                    
                    // Copy mpv.conf from assets
                    val mpvConfFile = File(this, "mpv.conf")
                    if (!mpvConfFile.exists()) {
                        try {
                            context.assets.open("mpv.conf").use { input ->
                                mpvConfFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "✅ Copied mpv.conf from assets")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not copy mpv.conf", e)
                        }
                    }
                }
                
                val cacheDir = context.cacheDir
                
                Log.d(TAG, "Initializing MPVLib singleton...")
                val success = mpv.initialize(configDir.absolutePath, cacheDir.absolutePath)
                
                if (!success) {
                    Log.e(TAG, "❌ Failed to initialize MPV")
                    throw Exception("MPV initialization failed")
                }
                
                initialized = true
                Log.d(TAG, "✅ MPV singleton initialized successfully")
            }
            
            mpvInstance = mpv
            return mpv
        }
    }
    
    /**
     * ⭐ CRITICAL: Switch to new video using loadfile replace
     * DO NOT stop/destroy VO - just replace the file
     */
    fun loadNewVideo(url: String, resumePosition: Double = 0.0) {
        try {
            ready = false  // Reset ready state for new video
            mpvInstance?.let { mpv ->
                // ⭐ CRITICAL: Use loadfile "replace" mode, NOT stop + loadfile!
                // This preserves the VO pipeline and prevents crashes
                `is`.xyz.mpv.MPVLib.command(arrayOf("loadfile", url, "replace"))
                Log.d(TAG, "✅ Loading new video with replace mode (VO pipeline preserved)")
                
                // Reset the hasLoadedOnce flag so next play() works
                mpv.resetForNextVideo()
                
                // If resume position is provided, MPVView will handle it in the file-loaded event
                if (resumePosition > 0) {
                    Log.d(TAG, "Resume position will be set after file-loaded: ${resumePosition}s")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading new video: ${e.message}")
        }
    }
    
    /**
     * Prepare for new video - ONLY resets flags, does NOT stop playback
     */
    fun prepareForNewVideo() {
        ready = false  // Reset ready state for new video
        mpvInstance?.resetForNextVideo()
        Log.d(TAG, "Prepared for new video (ready=false, hasLoadedOnce reset)")
    }
    
    /**
     * Called by MPV event observer when file is loaded and ready
     */
    fun onFileLoaded() {
        ready = true
        Log.d(TAG, "✅ MPV file loaded - ready for property queries")
    }
    
    /**
     * Check if MPV is initialized
     */
    fun isInitialized(): Boolean = initialized && mpvInstance != null
}

