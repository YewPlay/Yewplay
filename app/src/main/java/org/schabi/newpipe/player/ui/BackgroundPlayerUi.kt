package org.schabi.newpipe.player.ui

import org.schabi.newpipe.player.Player

/**
 * This is not a "graphical" UI for the background player, but it is used to disable fetching
 * video and text tracks with it.
 *
 * This allows reducing data usage for manifest sources with demuxed audio and video,
 * such as livestreams.
 */
class BackgroundPlayerUi(player: Player) : PlayerUi(player) {

    override fun initPlayback() {
        super.initPlayback()

        // Make sure to disable video and subtitles track types
        player.useVideoAndSubtitles(false)
    }
}
