package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_INT64

/**
 * MPVView - A SurfaceView that renders video using libmpv.
 * Based on mpv-android source code, simplified for Elefin.
 */
class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    companion object {
        private const val TAG = "mpv"
        // mpv option `hwdec` is set to this
        private const val HWDECS = "mediacodec,mediacodec-copy"
    }
    
    private var httpHeaders: String? = null
    
    /**
     * Set HTTP headers for network streams.
     * Must be called BEFORE initialize().
     */
    fun setHttpHeaders(headers: String) {
        httpHeaders = headers
    }

    override fun initOptions() {
        // Apply phone-optimized defaults
        MPVLib.setOptionString("profile", "fast")

        // Video output
        setVo("gpu")

        // Hardware decoding
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", HWDECS)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("tls-verify", "no")
        MPVLib.setOptionString("input-default-bindings", "yes")
        
        // Disable ytdl to prevent it from interfering
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("load-scripts", "no")
        
        // Set HTTP headers if provided
        httpHeaders?.let { headers ->
            MPVLib.setOptionString("http-header-fields", headers)
            Log.d(TAG, "HTTP headers set")
        }
        
        // Limit demuxer cache for mobile devices
        val cacheMegs = 64
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
    }

    override fun postInitOptions() {
        // We need to call write-watch-later manually
        MPVLib.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        // Observe essential properties for playback control
        MPVLib.observeProperty("time-pos", MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration", MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("paused-for-cache", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPV_FORMAT_FLAG)
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }
    
    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    // Property getters/setters

    var paused: Boolean?
        get() = MPVLib.getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

    val timePos: Double?
        get() = MPVLib.getPropertyDouble("time-pos")

    val duration: Double?
        get() = MPVLib.getPropertyDouble("duration")

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed")
        set(speed) = MPVLib.setPropertyDouble("speed", speed!!)

    // Commands

    fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))
    
    fun pause() = MPVLib.setPropertyBoolean("pause", true)
    
    fun play() = MPVLib.setPropertyBoolean("pause", false)
    
    fun seek(delta: Int) = MPVLib.command(arrayOf("seek", delta.toString(), "relative"))
    
    fun seekTo(position: Double) = MPVLib.command(arrayOf("seek", position.toString(), "absolute"))
    
    fun adjustVolume(delta: Int) = MPVLib.command(arrayOf("add", "volume", delta.toString()))
}
