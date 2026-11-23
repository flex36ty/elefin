package com.flex.elefin.player.cinema

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.ui.SubtitleView
import java.io.File

/** Subtitle-only clock follower: never opens a video or creates an audio decoder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CinemaTopSubtitles(context: Context, private val main: ExoPlayer) {
    val view = SubtitleView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var offsetMs = 0
    private var released = false
    private var active = false
    private val player = ExoPlayer.Builder(context, RenderersFactory { eventHandler, _, _, textOutput, _ ->
        arrayOf(TextRenderer(textOutput, eventHandler.looper).apply { experimentalSetLegacyDecodingEnabled(true) })
    }).build()
    init {
        val settings = com.flex.elefin.jellyfin.AppSettings(context)
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.exoSubtitleTextSize.toFloat())
        view.setStyle(androidx.media3.ui.CaptionStyleCompat(settings.exoSubtitleTextColor,
            if (settings.exoSubtitleBgTransparent) android.graphics.Color.TRANSPARENT else settings.exoSubtitleBgColor,
            android.graphics.Color.TRANSPARENT, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK, null))
        player.addListener(object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                if (!active) return
                view.setCues(cueGroup.cues.map { it.buildUpon().setLine(.06f, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_START).build() })
            }
            override fun onPlayerError(error: PlaybackException) {
                off()
                android.util.Log.w("CinemaPlayer", "Top subtitle failed: ${error.errorCodeName}")
                android.widget.Toast.makeText(context, "Could not display the top subtitle. Try another track.", android.widget.Toast.LENGTH_LONG).show()
            }
        })
        handler.post(object : Runnable {
            override fun run() {
                if (released) return
                if (active) {
                    val target = (main.currentPosition - offsetMs).coerceAtLeast(0)
                    if (kotlin.math.abs(player.currentPosition - target) > 200) player.seekTo(target)
                    player.playbackParameters = main.playbackParameters
                    player.playWhenReady = main.isPlaying
                }
                handler.postDelayed(this, 100)
            }
        })
    }
    fun setOffset(milliseconds: Int) { offsetMs = milliseconds }
    fun load(path: String, mime: String) {
        active = false
        view.setCues(emptyList())
        val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(path)))
            .setMimeType(mime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val durationUs = main.duration.takeIf { it > 0 }?.times(1000) ?: C.TIME_UNSET
        player.setMediaSource(SingleSampleMediaSource.Factory(DefaultDataSource.Factory(view.context))
            .createMediaSource(subtitle, durationUs), (main.currentPosition - offsetMs).coerceAtLeast(0))
        active = true
        player.prepare()
        player.playWhenReady = main.isPlaying
    }
    fun off() { active = false; player.stop(); player.clearMediaItems(); view.setCues(emptyList()) }
    fun release() { released = true; handler.removeCallbacksAndMessages(null); player.release(); view.setCues(emptyList()) }
}
