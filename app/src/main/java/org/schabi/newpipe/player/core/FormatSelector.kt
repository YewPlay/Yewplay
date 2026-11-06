/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.core

import org.schabi.newpipe.engine.model.YpMediaFormat
import org.schabi.newpipe.engine.model.YpVideoInfo

/**
 * Pure, engine-agnostic playback-format selection over the standardized
 * [YpMediaFormat] list. No Android or Media3 references so the policy is unit
 * testable on the JVM.
 *
 * Policy (deterministic):
 * 1. Adaptive manifests (DASH, then HLS) win for live content.
 * 2. Otherwise the best muxed format at or under [maxHeight] (or the closest
 *    above when nothing fits) plays without track merging.
 * 3. When no muxed format exists, the best video-only at/under [maxHeight]
 *    pairs with the highest-bitrate audio-only track.
 */
object FormatSelector {

    data class PlaybackSelection(
        val videoFormat: YpMediaFormat,
        /** Non-null only for video-only selections that need a separate audio track. */
        val audioFormat: YpMediaFormat? = null,
        /** True when [videoFormat] is an adaptive manifest (DASH/HLS), not a file. */
        val isManifest: Boolean = false
    )

    fun select(info: YpVideoInfo, maxHeight: Int = Int.MAX_VALUE): PlaybackSelection? {
        val formats = info.formats
        if (formats.isEmpty()) return null

        if (info.durationSeconds <= 0L) {
            // Live/unknown duration: prefer manifests when offered.
            selectManifest(formats, "application/dash+xml")?.let { return it }
            selectManifest(formats, "application/x-mpegURL")?.let { return it }
        }

        val muxed = formats.filter { it.muxed && !it.isAdaptive && it.url.isNotBlank() }
        if (muxed.isNotEmpty()) {
            return PlaybackSelection(videoFormat = bestAtHeight(muxed, maxHeight))
        }

        val videos = formats.filter { it.isVideoOnly && it.url.isNotBlank() }
        val audios = formats.filter { it.isAudioOnly && it.url.isNotBlank() }
        if (videos.isNotEmpty()) {
            val video = bestAtHeight(videos, maxHeight)
            val audio = audios.maxByOrNull { it.bitrate }
            return PlaybackSelection(videoFormat = video, audioFormat = audio)
        }

        // Last resort: any adaptive manifest, then any playable URL at all.
        selectManifest(formats, "application/dash+xml")?.let { return it }
        selectManifest(formats, "application/x-mpegURL")?.let { return it }
        return formats.firstOrNull { it.url.isNotBlank() }?.let { PlaybackSelection(videoFormat = it) }
    }

    private fun selectManifest(formats: List<YpMediaFormat>, mimeType: String): PlaybackSelection? = formats
        .filter { it.isAdaptive && it.mimeType == mimeType && it.url.isNotBlank() }
        .maxByOrNull { it.bitrate }
        ?.let { PlaybackSelection(videoFormat = it, isManifest = true) }

    /** Highest-resolution format at or under [maxHeight]; closest above when none fits. */
    private fun bestAtHeight(formats: List<YpMediaFormat>, maxHeight: Int): YpMediaFormat {
        val fitting = formats.filter { it.height in 1..maxHeight }
        return when {
            fitting.isNotEmpty() -> fitting.maxWith(compareBy({ it.height }, { it.bitrate }))
            else -> formats.minByOrNull { it.height } ?: formats.first()
        }
    }
}
