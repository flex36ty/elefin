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
     * Reset for next video - call this before loading a new file
     */
    fun resetForNextVideo() {
        hasLoadedOnce = false
        Log.d(TAG, "✅ Reset for next video (hasLoadedOnce = false)")
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
            Log.d(TAG, "✅ Surface ready, loading file (ONCE): $url")
            MPVLib.command(arrayOf("loadfile", url))
            hasLoadedOnce = true  // Mark as loaded
            this.filePath = null
            this.pendingUrl = null
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
        
        // Attach surface AFTER format is set - this is critical for video output
        MPVLib.attachSurface(holder.surface)
        Log.d(TAG, "✅ Surface attached via MPVLib.attachSurface() with HDR format (RGBA_1010102)")
        
        // CRITICAL: Configure video output properties AFTER attaching surface
        // These must be set as PROPERTIES (after init and attachSurface) for HDR to work
        Log.d(TAG, "Configuring video output properties for HDR (MUST be after attachSurface)")
        
        // GPU context and API (required for video rendering)
        MPVLib.setPropertyString("gpu-context", "android")
        MPVLib.setPropertyString("gpu-api", "opengl")
        MPVLib.setPropertyString("opengl-es", "yes")
        MPVLib.setPropertyString("hwdec", "mediacodec-copy")
        
        // HDR output configuration - MUST be set as properties AFTER attachSurface
        // Use gpu-next VO for better HDR support (already set in initOptions, but ensure it's active)
        MPVLib.setPropertyString("vo", "gpu-next")
        Log.d(TAG, "✅ VO set to gpu-next for HDR support")
        
        // HDR processing properties
        MPVLib.setPropertyString("hdr-compute-peak", "yes")
        MPVLib.setPropertyString("hdr-peak-percentile", "99")
        MPVLib.setPropertyString("target-colorspace-hint", "yes")
        
        // CRITICAL: Enable native HDR passthrough (must be set as properties)
        MPVLib.setPropertyString("gpu-hdr", "yes")
        MPVLib.setPropertyString("native-hdr", "yes")
        Log.d(TAG, "✅ Native HDR passthrough enabled: gpu-hdr=yes, native-hdr=yes")
        
        // Tone mapping - use "auto" for better HDR handling (clip was too aggressive)
        MPVLib.setPropertyString("tone-mapping", "auto")
        Log.d(TAG, "✅ Tone mapping set to auto for HDR")
        
        // Enable 10-bit framebuffer format for HDR output (critical to prevent dithering to 8-bit)
        try {
            MPVLib.setPropertyString("fbo-format", "rgb10_a2")
            Log.d(TAG, "✅ HDR framebuffer format set to rgb10_a2 (10-bit) - prevents dithering to 8-bit")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set fbo-format (may not be supported)", e)
        }
        
        Log.d(TAG, "✅ HDR support fully configured: Surface=RGBA_1010102, vo=gpu-next, fbo-format=rgb10_a2, gpu-hdr=yes, native-hdr=yes, tone-mapping=bt2390")
        
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
        Log.w(TAG, "⚠️ Surface destroyed - this should NOT happen during video playback!")
        Log.w(TAG, "   If this happens between videos, you'll get crashes!")
        
        // ⭐ CRITICAL: DO NOT detach surface or set vo=null during normal operation!
        // This breaks MPV's VO pipeline and causes "current-vo=null" + crashes
        // Surface should only be destroyed when:
        // 1. App is backgrounded
        // 2. Activity is finishing
        // NOT between video loads!
        
        surfaceReady = false
        // DO NOT set vo=null or detach surface - MPV needs the surface to stay alive!
        // The only exception is in destroy() when we're truly shutting down
    }
}

