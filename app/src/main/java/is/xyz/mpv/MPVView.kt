package `is`.xyz.mpv

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_INT64
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_NONE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_STRING
import java.io.File

/**
 * MPV SurfaceView for video rendering.
 * 
 * Handles the MPV lifecycle and provides playback controls.
 */
class MPVView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "MPVView"
        private const val HWDECS = "mediacodec,mediacodec-copy"
    }

    private var filePath: String? = null
    private var voInUse: String = "gpu"
    private var httpHeaders: String? = null
    private var isInitialized = false

    constructor(context: Context) : this(context, null)

    /**
     * Set HTTP headers before calling initialize().
     * Headers should be in CRLF format: "Header1: value1\r\nHeader2: value2\r\n"
     */
    fun setHttpHeaders(headers: String?) {
        this.httpHeaders = headers
    }

    /**
     * Initialize MPV. Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        if (isInitialized) {
            Log.w(TAG, "MPV already initialized")
            return
        }
        
        // Copy bundled fonts from assets to internal storage for libass
        val fontsDir = File(context.filesDir, "fonts")
        copyFontsFromAssets(fontsDir)

        MPVLib.create(context)

        // Set config options
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        
        // Cache directories
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)
        
        // Font directory for libass - CRITICAL for text subtitle rendering
        MPVLib.setOptionString("sub-fonts-dir", fontsDir.absolutePath)
        MPVLib.setOptionString("osd-fonts-dir", fontsDir.absolutePath)

        // Initialize options before MPVLib.init()
        initOptions()

        // Set HTTP headers if provided (must be before init)
        httpHeaders?.let { headers ->
            if (headers.isNotEmpty()) {
                MPVLib.setOptionString("http-header-fields", headers)
                Log.d(TAG, "HTTP headers set")
            }
        }

        // Disable ytdl to prevent interference with direct URLs
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("load-scripts", "no")

        MPVLib.init()

        // Post-init options
        postInitOptions()

        // Surface management options - keep window alive for subtitle rendering
        MPVLib.setOptionString("force-window", "yes")  // Keep window even without surface
        MPVLib.setOptionString("keep-open", "yes")  // Keep player open after playback ends
        MPVLib.setOptionString("idle", "yes")  // Stay idle instead of exiting

        holder.addCallback(this)
        observeProperties()
        
        isInitialized = true
        Log.d(TAG, "MPV initialized successfully")
        
        // Log subtitle-related properties for debugging
        val subVis = MPVLib.getPropertyBoolean("sub-visibility")
        val sid = MPVLib.getPropertyString("sid")
        Log.d(TAG, "Initial subtitle state: sub-visibility=$subVis, sid=$sid")
    }

    private fun initOptions() {
        // Use fast profile for mobile
        MPVLib.setOptionString("profile", "fast")

        // Video output - Initialize as null to prevent "Missing surface pointer" error
        // We will enable it in surfaceCreated
        MPVLib.setOptionString("vo", "null")

        // Hardware decoding
        MPVLib.setOptionString("hwdec", HWDECS)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")

        // Audio output
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        
        // Subtitle settings - ensure subtitles are visible and rendered
        MPVLib.setOptionString("sub-visibility", "yes")
        MPVLib.setOptionString("sub-auto", "fuzzy")  // Auto-load external subtitles
        MPVLib.setOptionString("sid", "auto")  // Auto-select first subtitle track
        MPVLib.setOptionString("sub-forced-events-only", "no")  // Show all subtitle events, not just forced
        
        // Font settings - CRITICAL for subtitle rendering
        MPVLib.setOptionString("embeddedfonts", "yes")  // Use fonts embedded in video files
        MPVLib.setOptionString("sub-font", "Roboto")  // Use bundled Roboto font
        MPVLib.setOptionString("sub-font-provider", "none")  // Don't use system font provider (broken on Android)
        
        // Subtitle rendering - CRITICAL for Android GPU output
        MPVLib.setOptionString("sub-ass", "yes")  // Enable ASS/SSA subtitle rendering
        MPVLib.setOptionString("sub-ass-force-margins", "no")  // Don't force margins
        
        // CRITICAL: blend-subtitles must be set correctly for GPU output on Android
        // "video" blends into the video frame which is required for hw decoding
        MPVLib.setOptionString("blend-subtitles", "video")
        
        // Secondary subtitle (for dual subtitle display) - disabled
        MPVLib.setOptionString("secondary-sid", "no")
        
        // Subtitle styling for SRT and other text subtitles  
        MPVLib.setOptionString("sub-font-size", "55")  // Larger font for TV visibility
        MPVLib.setOptionString("sub-color", "#FFFFFFFF")  // White text
        MPVLib.setOptionString("sub-border-color", "#FF000000")  // Black border
        MPVLib.setOptionString("sub-border-size", "3")  // Border thickness
        MPVLib.setOptionString("sub-shadow-color", "#80000000")  // Semi-transparent shadow
        MPVLib.setOptionString("sub-shadow-offset", "2")  // Shadow offset
        MPVLib.setOptionString("sub-pos", "95")  // Position from top (95% = near bottom)
        
        // Ensure subtitles are rendered
        MPVLib.setOptionString("sub-scale", "1.0")
        MPVLib.setOptionString("sub-scale-with-window", "yes")
        MPVLib.setOptionString("sub-use-margins", "yes")  // Use margins for positioning
        
        // OSD settings - required for subtitle display
        MPVLib.setOptionString("osd-level", "3")  // Full OSD including subtitles
        MPVLib.setOptionString("osd-bar", "yes")
        
        // Log level for debugging subtitle and VO issues
        MPVLib.setOptionString("msg-level", "all=v,vo=v,sub=v,osd=v")

        // Display FPS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val disp = ContextCompat.getDisplayOrDefault(context)
            val refreshRate = disp.mode.refreshRate
            Log.v(TAG, "Display reports FPS of $refreshRate")
            MPVLib.setOptionString("display-fps-override", refreshRate.toString())
        }

        // GPU context for Android - CRITICAL for subtitle rendering
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("gpu-api", "opengl")  // Required for libass subtitle overlay
        MPVLib.setOptionString("opengl-es", "yes")

        // TLS settings - allow self-signed certs for local servers
        MPVLib.setOptionString("tls-verify", "no")

        // Demuxer cache settings for mobile
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
    }

    private fun postInitOptions() {
        // Don't auto-save position, we handle this ourselves for Jellyfin
        MPVLib.setOptionString("save-position-on-quit", "no")
    }

    private fun observeProperties() {
        data class Property(val name: String, val format: Int = MPV_FORMAT_NONE)
        val properties = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("duration/full", MPV_FORMAT_DOUBLE),
            Property("pause", MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("speed", MPV_FORMAT_STRING),
            Property("track-list"),
            Property("video-params/aspect", MPV_FORMAT_DOUBLE),
            Property("playlist-pos", MPV_FORMAT_INT64),
            Property("playlist-count", MPV_FORMAT_INT64),
            Property("media-title", MPV_FORMAT_STRING),
            Property("hwdec-current"),
            Property("eof-reached", MPV_FORMAT_FLAG)
        )

        for ((name, format) in properties)
            MPVLib.observeProperty(name, format)
    }

    /**
     * Destroy MPV. Call this when done with playback.
     */
    fun destroy() {
        if (!isInitialized) return
        
        holder.removeCallback(this)
        MPVLib.destroy()
        isInitialized = false
        Log.d(TAG, "MPV destroyed")
    }

    /**
     * Load and play a file.
     */
    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    /**
     * Set the video output to use.
     */
    fun setVo(vo: String) {
        voInUse = vo
        MPVLib.setOptionString("vo", vo)
    }

    // SurfaceHolder.Callback implementation

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface created, attaching to MPV")
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        
        // Enable VO now that surface is ready
        MPVLib.setPropertyString("vo", voInUse)

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath as String))
            filePath = null
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface destroyed, detaching from MPV")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
    }

    // Observer management

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }

    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    // Playback control properties

    var paused: Boolean?
        get() = MPVLib.getPropertyBoolean("pause")
        set(value) = MPVLib.setPropertyBoolean("pause", value!!)

    var timePos: Double?
        get() = MPVLib.getPropertyDouble("time-pos/full")
        set(value) = MPVLib.setPropertyDouble("time-pos", value!!)

    val duration: Double?
        get() = MPVLib.getPropertyDouble("duration/full")

    val hwdecActive: String
        get() = MPVLib.getPropertyString("hwdec-current") ?: "no"

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed")
        set(value) = MPVLib.setPropertyDouble("speed", value!!)

    val eofReached: Boolean?
        get() = MPVLib.getPropertyBoolean("eof-reached")

    // Playback control methods

    fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))
    
    fun pause() {
        paused = true
    }
    
    fun play() {
        paused = false
    }

    fun seek(seconds: Int) {
        MPVLib.command(arrayOf("seek", seconds.toString(), "relative"))
    }

    fun seekTo(position: Double) {
        timePos = position
    }

    fun cycleAudio() = MPVLib.command(arrayOf("cycle", "audio"))
    
    fun cycleSub() = MPVLib.command(arrayOf("cycle", "sub"))
    
    fun cycleHwdec() = MPVLib.command(arrayOf("cycle-values", "hwdec", HWDECS, "no"))

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    // Track information

    data class Track(val mpvId: Int, val name: String, val lang: String? = null)
    
    var tracks = mapOf<String, MutableList<Track>>(
        "audio" to arrayListOf(),
        "video" to arrayListOf(),
        "sub" to arrayListOf()
    )

    fun loadTracks() {
        for (list in tracks.values) {
            list.clear()
            list.add(Track(-1, "Off"))
        }
        
        val count = MPVLib.getPropertyInt("track-list/count") ?: return
        
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!tracks.containsKey(type)) continue
            
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val title = MPVLib.getPropertyString("track-list/$i/title")
            val codec = MPVLib.getPropertyString("track-list/$i/codec")

            // Build track name
            val trackName = when {
                !title.isNullOrEmpty() && !lang.isNullOrEmpty() -> "$title ($lang)"
                !title.isNullOrEmpty() -> title
                !lang.isNullOrEmpty() -> lang.uppercase()
                else -> "Track $mpvId"
            }
            
            tracks.getValue(type).add(Track(mpvId = mpvId, name = trackName, lang = lang))
            
            // Log subtitle codec info for debugging
            if (type == "sub") {
                Log.d(TAG, "Subtitle track $mpvId: $trackName, codec=$codec")
            }
        }
        
        Log.d(TAG, "Loaded ${tracks["audio"]?.size ?: 0} audio tracks, ${tracks["sub"]?.size ?: 0} subtitle tracks")
    }

    /**
     * Copy bundled fonts from assets to internal storage for libass.
     * This is required because libass cannot read from Android assets directly.
     */
    private fun copyFontsFromAssets(fontsDir: File) {
        try {
            if (!fontsDir.exists()) {
                fontsDir.mkdirs()
            }
            
            val assetManager = context.assets
            val fontFiles = assetManager.list("fonts") ?: return
            
            for (fontFile in fontFiles) {
                val destFile = File(fontsDir, fontFile)
                if (!destFile.exists()) {
                    Log.d(TAG, "Copying font: $fontFile to ${destFile.absolutePath}")
                    assetManager.open("fonts/$fontFile").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Font copied successfully: $fontFile")
                } else {
                    Log.d(TAG, "Font already exists: $fontFile")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy fonts from assets", e)
        }
    }

    // Track selection

    var vid: Int
        get() = MPVLib.getPropertyString("vid")?.toIntOrNull() ?: -1
        set(value) {
            if (value == -1) MPVLib.setPropertyString("vid", "no")
            else MPVLib.setPropertyInt("vid", value)
        }

    var sid: Int
        get() = MPVLib.getPropertyString("sid")?.toIntOrNull() ?: -1
        set(value) {
            if (value == -1) MPVLib.setPropertyString("sid", "no")
            else MPVLib.setPropertyInt("sid", value)
        }

    var aid: Int
        get() = MPVLib.getPropertyString("aid")?.toIntOrNull() ?: -1
        set(value) {
            if (value == -1) MPVLib.setPropertyString("aid", "no")
            else MPVLib.setPropertyInt("aid", value)
        }
}

