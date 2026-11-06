/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.engine.model.YpMediaFormat
import org.schabi.newpipe.engine.model.YpVideoInfo

class FormatSelectorTest {

    private fun videoInfo(
        formats: List<YpMediaFormat>,
        durationSeconds: Long = 600
    ) = YpVideoInfo(
        url = "https://www.youtube.com/watch?v=abcdefghijk",
        title = "t",
        description = "",
        thumbnailUrl = null,
        author = "a",
        authorUrl = null,
        durationSeconds = durationSeconds,
        viewCount = 0,
        uploadDate = null,
        formats = formats,
        subtitles = emptyList()
    )

    private fun muxed(height: Int, bitrate: Int = height * 1000) = YpMediaFormat("https://v/$height", "video/mp4", "${height}p", height = height, bitrate = bitrate, muxed = true)

    private fun videoOnly(height: Int, bitrate: Int = height * 2000) = YpMediaFormat(
        "https://vo/$height",
        "video/mp4",
        "${height}p",
        height = height,
        bitrate = bitrate,
        muxed = false,
        isVideoOnly = true,
        isAdaptive = true
    )

    private fun audioOnly(bitrate: Int) = YpMediaFormat(
        "https://a/$bitrate",
        "audio/mp4",
        "${bitrate / 1000}kbps",
        bitrate = bitrate,
        muxed = false,
        isAudioOnly = true,
        isAdaptive = true
    )

    private fun manifest(mime: String, url: String) = YpMediaFormat(url, mime, "MANIFEST", muxed = false, isAdaptive = true)

    @Test
    fun `empty formats yield no selection`() {
        assertNull(FormatSelector.select(videoInfo(emptyList())))
    }

    @Test
    fun `best muxed at or under max height wins`() {
        val info = videoInfo(listOf(muxed(360), muxed(720), muxed(1080)))
        val selection = FormatSelector.select(info, maxHeight = 720)
        assertEquals(720, selection!!.videoFormat.height)
        assertNull(selection.audioFormat)
    }

    @Test
    fun `closest above is used when nothing fits max height`() {
        val info = videoInfo(listOf(muxed(720), muxed(1080)))
        val selection = FormatSelector.select(info, maxHeight = 360)
        assertEquals(720, selection!!.videoFormat.height)
    }

    @Test
    fun `video-only pairs with highest bitrate audio`() {
        val info = videoInfo(listOf(videoOnly(720), videoOnly(1080), audioOnly(128_000), audioOnly(256_000)))
        val selection = FormatSelector.select(info, maxHeight = 1080)
        assertEquals(1080, selection!!.videoFormat.height)
        assertEquals(256_000, selection.audioFormat!!.bitrate)
    }

    @Test
    fun `live content prefers DASH manifest`() {
        val info = videoInfo(
            listOf(
                muxed(720),
                manifest("application/x-mpegURL", "https://hls"),
                manifest("application/dash+xml", "https://dash")
            ),
            durationSeconds = 0
        )
        val selection = FormatSelector.select(info)
        assertTrue(selection!!.isManifest)
        assertEquals("https://dash", selection.videoFormat.url)
    }

    @Test
    fun `non-live content ignores manifests when muxed exists`() {
        val info = videoInfo(listOf(muxed(720), manifest("application/dash+xml", "https://dash")))
        val selection = FormatSelector.select(info)
        assertEquals(720, selection!!.videoFormat.height)
    }

    @Test
    fun `blank urls are never selected`() {
        val info = videoInfo(listOf(muxed(720).copy(url = ""), muxed(360)))
        assertEquals(360, FormatSelector.select(info)!!.videoFormat.height)
    }

    @Test
    fun `last resort picks any playable url`() {
        val only = manifest("application/dash+xml", "https://dash")
        val selection = FormatSelector.select(videoInfo(listOf(only)))
        assertNotNull(selection)
        assertEquals("https://dash", selection!!.videoFormat.url)
    }
}
