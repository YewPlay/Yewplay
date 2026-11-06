/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.core

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import org.schabi.newpipe.engine.model.YpVideoInfo

/**
 * Converts the engine-agnostic [YpVideoInfo] into Media3 [MediaItem]s. This is
 * the single seam where engine output enters the Media3 playback
 * infrastructure — engines never see Media3 types and Media3 never sees
 * engine types.
 */
object MediaItemFactory {

    /** The single video to play, selected by [FormatSelector]. */
    fun fromVideoInfo(info: YpVideoInfo, maxHeight: Int = Int.MAX_VALUE): MediaItem? {
        val selection = FormatSelector.select(info, maxHeight) ?: return null
        val builder = MediaItem.Builder()
            .setUri(selection.videoFormat.url)
            .setMediaId(info.url)

        when {
            selection.isManifest && selection.videoFormat.mimeType.contains("dash") ->
                builder.setMimeType(MimeTypes.APPLICATION_MPD)

            selection.isManifest && selection.videoFormat.mimeType.contains("mpegURL", ignoreCase = true) ->
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        if (info.subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                info.subtitles.map { track ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(track.languageCode.ifBlank { null })
                        .setLabel(track.displayName.ifBlank { track.languageCode })
                        .build()
                }
            )
        }
        return builder.build()
    }

    /**
     * A separate audio track for video-only selections. Callers merge it with
     * [fromVideoInfo]'s item via a `MergingMediaSource` when
     * `FormatSelector.PlaybackSelection.audioFormat` is non-null.
     */
    fun audioTrackFor(info: YpVideoInfo, maxHeight: Int = Int.MAX_VALUE): MediaItem? = FormatSelector
        .select(info, maxHeight)
        ?.audioFormat
        ?.takeIf { it.url.isNotBlank() }
        ?.let { audio ->
            MediaItem.Builder()
                .setUri(audio.url)
                .setMediaId("${info.url}#audio")
                .build()
        }
}
