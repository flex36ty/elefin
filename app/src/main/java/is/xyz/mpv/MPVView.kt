package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log

/**
 * Simple MPVView implementation for Compose integration
 * Extends BaseMPVView with minimal Jellyfin-specific configuration
 */
class MPVView(context: Context, attrs: AttributeSet?) : BaseMPVView(context, attrs) {
    
    companion object {
        private const val TAG = "MPVView"
    }
    
    override fun initOptions() {
        // Video output
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        
        // Hardware decoding
        MPVLib.setOptionString("hwdec", "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        
        // Audio output
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        
        // Disable ytdl
        MPVLib.setOptionString("ytdl", "no")
        
        // Keep window open
        MPVLib.setOptionString("keep-open", "yes")
        
        Log.d(TAG, "MPV options initialized")
    }
    
    override fun postInitOptions() {
        // Set properties after init
        MPVLib.setPropertyBoolean("keep-open", true)
        
        Log.d(TAG, "MPV post-init options configured")
    }
    
    override fun observeProperties() {
        // Observe playback properties if needed
        // For now, keep it simple
        Log.d(TAG, "MPV property observation configured")
    }
}


