package `is`.xyz.mpv

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "BaseMPVView"
        
        // ⭐ CRITICAL: Global flags to track MPVLib singleton initialization
        // MPVLib is a singleton - create() and init() can only be called ONCE per process lifetime
        @Volatile
        private var mpvLibCreated = false
        
        @Volatile
        private var mpvLibInitialized = false
    }
    
    /**
     * Initialize libmpv.
     *
     * Call this once before the view is shown.
     */
    
    fun initialize(configDir: String, cacheDir: String): Boolean {
        if (!MPVLib.isAvailable()) {
            Log.e(TAG, "MPV libraries not available, cannot initialize")
            initialized = false
            return false
        }
        
        // ⭐ CRITICAL: Don't initialize the same view instance twice
        if (initialized) {
            Log.d(TAG, "This MPVView instance already initialized, skipping")
            return true
        }
        
        try {
            // Add surface callback BEFORE initialization
            // This ensures we catch surface creation immediately
            holder.addCallback(this)
            Log.d(TAG, "Surface callback added to holder")
            
            // ⭐ MPVLib is a singleton - only create() and init() once globally
            if (!mpvLibCreated) {
                Log.d(TAG, "First MPVView - calling MPVLib.create()")
                MPVLib.create(context)
                mpvLibCreated = true
            } else {
                Log.d(TAG, "MPVLib already created, reusing")
            }

            // ⭐ Options must be set BEFORE init(), and only once
            if (!mpvLibInitialized) {
                Log.d(TAG, "Setting MPVLib options and calling init()")
                
                /* set normal options (user-supplied config can override) */
                MPVLib.setOptionString("config", "yes")
                MPVLib.setOptionString("config-dir", configDir)
                for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
                    MPVLib.setOptionString(opt, cacheDir)
                initOptions()

                MPVLib.init()
                mpvLibInitialized = true
                Log.d(TAG, "✅ MPVLib.init() completed")
            } else {
                Log.d(TAG, "MPVLib already initialized, skipping init()")
            }

            /* set hardcoded options */
            postInitOptions()
            // could mess up VO init before surfaceCreated() is called
            MPVLib.setOptionString("force-window", "no")
            // need to idle at least once for playFile() logic to work
            MPVLib.setOptionString("idle", "once")

            observeProperties()
            initialized = true
            
            // Check if surface already exists (might be created before callback is set)
            if (holder.surface != null && holder.surface.isValid) {
                Log.d(TAG, "Surface already exists during initialization, triggering surfaceCreated")
                // Manually trigger surface creation logic
                surfaceCreated(holder)
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            initialized = false
            return false
        }
    }

    /**
     * Deinitialize libmpv.
     *
     * Call this once before the view is destroyed.
     */
    fun destroy() {
        if (!initialized) return
        
        Log.d(TAG, "Destroying MPV player...")
        
        // Disable surface callbacks to avoid using unintialized mpv state
        holder.removeCallback(this)

        try {
            // CRITICAL: Stop playback and detach surface BEFORE destroying MPV
            // This prevents crashes when MediaCodec decoder is still active
            
            // 1. Stop playback first (pause and stop the file)
            try {
                MPVLib.setPropertyBoolean("pause", true)
                MPVLib.command(arrayOf("stop"))
                Log.d(TAG, "Playback stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping playback", e)
            }
            
            // 2. Detach surface using the same logic as surfaceDestroyed
            // This ensures proper cleanup of the MediaCodec decoder
            if (surfaceReady) {
                try {
                    Log.w(TAG, "detaching surface before destroy")
                    surfaceReady = false
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.setOptionString("force-window", "no")
                    // Note: There could be a race condition, but we try to ensure
                    // MPV is done using the surface by stopping playback first
                    MPVLib.detachSurface()
                    Log.d(TAG, "Surface detached")
                } catch (e: Exception) {
                    Log.w(TAG, "Error detaching surface", e)
                }
            }
            
            // 3. Now safe to destroy MPV
            MPVLib.destroy()
            Log.d(TAG, "MPV destroyed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying MPV", e)
        }
        initialized = false
    }

    protected abstract fun initOptions()
    protected abstract fun postInitOptions()

    protected abstract fun observeProperties()

    private var filePath: String? = null
    protected var initialized: Boolean = false
    @Volatile
    var surfaceReady: Boolean = false
        private set
    
    // ⭐ CRITICAL: Prevent double load - MPV will crash if loadfile() is called twice
    @Volatile
    private var hasLoadedOnce: Boolean = false
    
    // Pending URL to load once surface is ready
    // NOTE: This is set by playFile(), don't load from here - let playFile() handle it
    @Volatile
    var pendingUrl: String? = null

    /**
     * Reset for next video - just clear the loaded flag
     * Do NOT call stop here - it must be done BEFORE surface attach!
     */
    fun resetForNextVideo() {
        hasLoadedOnce = false
        Log.d(TAG, "✅ Reset hasLoadedOnce flag for next video")
    }
    
    /**
     * Stop playback - call this BEFORE surface initialization
     * This must be called before any GPU/VO configuration
     */
    fun stopPlayback() {
        try {
            if (initialized) {
                Log.d(TAG, "Stopping MPV playback...")
                MPVLib.command(arrayOf("stop"))
                Thread.sleep(50) // Wait for stop to complete
                Log.d(TAG, "✅ MPV stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MPV", e)
        }
    }
    
    /**
     * Set the first file to be played once the player is ready.
     * This will wait for surface to be created and VO to be configured.
     */
    fun playFile(filePath: String) {
        this.filePath = filePath
        this.pendingUrl = filePath
        loadFileIfReady(filePath)
    }
    
    private fun loadFileIfReady(url: String) {
        // ⭐ CRITICAL: Only load ONCE per video - double load causes MPV to crash
        if (hasLoadedOnce) {
            Log.d(TAG, "⚠️ File already loaded once, ignoring duplicate load request")
            return
        }
        
        // Only load file if surface is ready and VO is configured
        if (initialized && surfaceReady && holder.surface != null && holder.surface.isValid) {
            try {
                // ⭐ NO STOP HERE - already done in resetForNextVideo()
                // Double stop kills MPV's command queue!
                Log.d(TAG, "✅ Surface ready, loading file: $url")
                MPVLib.command(arrayOf("loadfile", url))
                hasLoadedOnce = true  // Mark as loaded
                this.filePath = null
                this.pendingUrl = null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading file", e)
            }
        } else {
            Log.d(TAG, "Waiting for surface to be ready before loading file. initialized=$initialized, surfaceReady=$surfaceReady, surface=${holder.surface?.isValid}")
        }
    }

    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        if (!initialized) return
        voInUse = vo
        MPVLib.setOptionString("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!initialized) return
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!initialized) return
        Log.w(TAG, "attaching surface - SurfaceHolder callback triggered")
        
        // CRITICAL: Configure Surface for HDR output BEFORE attaching
        // This must be done FIRST, before attachSurface() or super, to enable 10-bit HDR output
        try {
            // Set surface format to 10-bit HDR (RGBA_1010102) - Required for HDR10 output
            // MUST be set before attachSurface() or Android gives you an 8-bit SDR surface
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.setFormat(PixelFormat.RGBA_1010102)
                Log.d(TAG, "✅ Creating surface with PixelFormat.RGBA_1010102 for 10-bit HDR output")
            } else {
                holder.setFormat(PixelFormat.RGBX_8888)
                Log.d(TAG, "Surface format set to RGBX_8888 (API < 26, HDR not supported)")
            }
            
            // Ensure proper SurfaceView configuration for HDR
            setWillNotDraw(false)
            setZOrderOnTop(false)
            setZOrderMediaOverlay(false)
            Log.d(TAG, "SurfaceView configured for HDR: willNotDraw=false, zOrderOnTop=false, zOrderMediaOverlay=false")
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring surface format for HDR", e)
        }
        
        // ⭐ ONLY attach surface here - do NOT configure VO/HDR/GPU properties!
        // Configuration MUST happen ONCE in the app code (MPVVideoPlayerScreen), NOT here
        // Double configuration causes MPV to enter reconfig loop and discard loadfile commands
        MPVLib.attachSurface(holder.surface)
        Log.d(TAG, "✅ Surface attached (RGBA_1010102 format for HDR)")
        
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")
        
        // Mark surface as ready - this allows playFile() to work
        surfaceReady = true
        Log.d(TAG, "Surface attached and VO configured for HDR, surfaceReady=true")
        
        // Test if VO is loaded (debug - should appear if VO is working)
        try {
            MPVLib.command(arrayOf("show-text", "VO Loaded - HDR Ready", "3000"))
        } catch (e: Exception) {
            Log.w(TAG, "Could not show VO test text", e)
        }

        // ⭐ REMOVED: Don't load file here - let playFile() handle it
        // This was causing double-load issues where file was loaded before play() was called
        // The pendingUrl will be processed by playFile() -> loadFileIfReady()
        Log.d(TAG, "Surface ready - file will be loaded by playFile() when called")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!initialized) return
        
        // Surface destroyed - this should only happen when:
        // 1. App is backgrounded
        // 2. Activity is finishing
        // 3. Configuration change (rotation, etc.)
        // It should NOT happen between normal video loads (MPVView is now persistent)
        Log.d(TAG, "Surface destroyed (app backgrounded or activity finishing)")
        
        // ⭐ CRITICAL: DO NOT detach surface or set vo=null during normal operation!
        // This breaks MPV's VO pipeline and causes "current-vo=null" + crashes
        // We only mark surfaceReady as false, but don't destroy the VO
        surfaceReady = false
        
        // DO NOT set vo=null or detach surface - MPV needs the surface to stay alive!
        // The only exception is in destroy() when we're truly shutting down
    }
}

