/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.core

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.schabi.newpipe.engine.model.YpVideoInfo

/**
 * Media3 playback core — the central, engine-independent playback
 * infrastructure of YewPlay.
 *
 * Pipeline: engines → [EngineStreamResolver] → [MediaItemFactory] →
 * [MediaPlaybackCore] → Media3 → UI. This class owns the Media3 [ExoPlayer]
 * instance and exposes a small deliberate surface; it never references engine
 * types beyond the standardized [YpVideoInfo].
 *
 * It is added side-by-side with the legacy ExoPlayer2 `Player` during the
 * migration (FASE 10). Existing playback keeps working untouched until
 * FASE 11 rewires the UI/service layer onto this core.
 */
class MediaPlaybackCore(
    context: Context,
    userAgent: String = DEFAULT_USER_AGENT
) {
    /** Listener surface for the UI layer; mirrors the Media3 events we surface. */
    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onPlaybackStateChanged(playbackState: Int)
        fun onPlayerError(error: PlaybackException)
    }

    private val appContext = context.applicationContext

    private val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(appContext)
                .setDataSourceFactory(
                    DefaultDataSource.Factory(
                        appContext,
                        DefaultHttpDataSource.Factory().setUserAgent(userAgent)
                    )
                )
        )
        .build()

    private val listeners = mutableListOf<Listener>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            listeners.forEach { it.onPlaybackStateChanged(playbackState) }
        }

        override fun onPlayerError(error: PlaybackException) {
            listeners.forEach { it.onPlayerError(error) }
        }
    }

    init {
        player.addListener(playerListener)
    }

    /** The underlying Media3 player, for surfaces (PlayerView) and the session layer. */
    fun media3Player(): ExoPlayer = player

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Prepares playback of an engine-resolved video. */
    fun setVideo(info: YpVideoInfo, maxHeight: Int = Int.MAX_VALUE, startPositionMs: Long = 0L) {
        val item = MediaItemFactory.fromVideoInfo(info, maxHeight)
            ?: throw IllegalArgumentException("no playable format in ${info.url}")
        setMediaItem(item, startPositionMs)
    }

    fun setMediaItem(item: MediaItem, startPositionMs: Long = 0L) {
        player.setMediaItem(item, startPositionMs)
        player.prepare()
    }

    fun play() = player.play()

    fun pause() = player.pause()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun isPlaying(): Boolean = player.isPlaying

    fun currentPosition(): Long = player.currentPosition

    fun duration(): Long = player.duration

    /** Releases the player. The core must not be used afterwards. */
    fun release() {
        player.removeListener(playerListener)
        listeners.clear()
        player.release()
    }

    companion object {
        private const val DEFAULT_USER_AGENT = "YewPlay (Media3)"
    }
}
