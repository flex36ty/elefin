package com.flex.elefin.subtitles

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.player.SubtitleDownloader
import com.flex.elefin.player.SubtitleMapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Reprepare at the same position with the existing headers, tracks and play/pause state. */
suspend fun attachServerSubtitle(context: Context, api: JellyfinApiService, player: ExoPlayer, selection: ServerSubtitleSelection) {
    val file = selection.localPath ?: SubtitleDownloader.downloadSubtitle(context, api, selection.item.Id, selection.sourceId, selection.stream)
        ?: error("Subtitle saved on the server, but could not be loaded into the player. Select it again from the subtitle menu.")
    val current = player.currentMediaItem ?: error("Playback has ended.")
    val id = "server-subtitle-${selection.sourceId}-${selection.localPath?.hashCode() ?: selection.stream.Index}"
    val config = SubtitleMapper.buildLocalSubtitleConfiguration(file, selection.stream.Language ?: "und", selection.stream.DisplayTitle ?: "Downloaded subtitle")
        .buildUpon().setId(id).build()
    val existing = current.localConfiguration?.subtitleConfigurations.orEmpty().filter { it.id != id }
    val updated = current.buildUpon().setSubtitleConfigurations(existing + config).build()
    val headers = DefaultHttpDataSource.Factory().setUserAgent("Jellyfin Android TV")
        .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(api.getVideoRequestHeaders().toMutableMap())
    val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, headers)).createMediaSource(updated)
    val ready = CompletableDeferred<Unit>()
    val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }) {
                for (index in 0 until group.length) {
                    if (group.getTrackFormat(index).id == id) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
                        ready.complete(Unit)
                        return
                    }
                }
            }
        }
        override fun onPlayerError(error: PlaybackException) { ready.completeExceptionally(error) }
    }
    player.addListener(listener)
    try {
        val position = player.currentPosition
        val play = player.playWhenReady
        player.setMediaSource(source, position)
        player.prepare()
        player.playWhenReady = play
        withTimeoutOrNull(20_000) { ready.await() }
            ?: error("Subtitle was saved, but the player did not load its track. Reopen playback and select it from the subtitle menu.")
    } finally { player.removeListener(listener) }
}
