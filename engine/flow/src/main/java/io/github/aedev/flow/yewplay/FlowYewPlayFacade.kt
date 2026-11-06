/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * YewPlay integration facade — this file is NOT part of upstream Flow-Tube/Flow.
 * It exposes the minimal public surface the YewPlay FlowEngine adapter needs,
 * so upstream helpers that are `internal` (SignatureCipher, YouTubeSearchParams)
 * stay untouched and hidden inside this module.
 */

package io.github.aedev.flow.yewplay

import android.content.Context
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.YouTubeSearchParams
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.SignatureCipher
import io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder

object FlowYewPlayFacade {
    /** Must be called once with an application context before stream resolution. */
    fun initialize(context: Context) {
        PipePipeNsigDecoder.initialize(context)
    }

    /** `params` token restricting a `/youtubei/v1/search` to one content type. */
    fun searchParams(contentType: ContentType): String? = YouTubeSearchParams.build(
        contentType =
            when (contentType) {
                ContentType.VIDEO -> YouTubeSearchParams.ContentType.VIDEO
                ContentType.CHANNEL -> YouTubeSearchParams.ContentType.CHANNEL
                ContentType.PLAYLIST -> YouTubeSearchParams.ContentType.PLAYLIST
            }
    )

    enum class ContentType {
        VIDEO,
        CHANNEL,
        PLAYLIST
    }

    data class ResolvedStreams(
        val response: PlayerResponse,
        /** Progressive (muxed audio+video) formats with solved URLs. */
        val progressive: List<PlayerResponse.StreamingData.Format>,
        /** Adaptive (audio-only or video-only) formats with solved URLs. */
        val adaptive: List<PlayerResponse.StreamingData.Format>,
        val hlsManifestUrl: String?,
        val dashManifestUrl: String?
    )

    /**
     * Streams from [YouTubeClient.TV_TIZEN], the client that keeps serving visitors Google Video
     * has walled on the app clients. Every format it returns is signed, so signatures and `n`
     * parameters are solved through the decoder. Null when any step is unavailable; callers fall
     * back to another engine through YewPlay's fallback policy.
     */
    suspend fun resolveStreams(videoId: String): ResolvedStreams? {
        val signatureTimestamp = PipePipeNsigDecoder.signatureTimestamp() ?: return null
        val response =
            YouTube
                .player(
                    videoId,
                    client = YouTubeClient.TV_TIZEN,
                    signatureTimestamp = signatureTimestamp,
                    localeOverride = YouTubeLocale.EXTRACTION,
                    apiUrl = YouTubeClient.API_URL_YOUTUBE
                ).getOrNull() ?: return null
        if (response.playabilityStatus.status != "OK") return null
        val streamingData = response.streamingData ?: return null
        val progressive = solveFormats(streamingData.formats.orEmpty())
        val adaptive = solveFormats(streamingData.adaptiveFormats)
        if (progressive.isEmpty() && adaptive.isEmpty()) return null
        return ResolvedStreams(
            response = response,
            progressive = progressive,
            adaptive = adaptive,
            hlsManifestUrl = streamingData.hlsManifestUrl,
            dashManifestUrl = streamingData.dashManifestUrl
        )
    }

    private suspend fun solveFormats(
        formats: List<PlayerResponse.StreamingData.Format>
    ): List<PlayerResponse.StreamingData.Format> {
        val ciphers = formats.associateWith { SignatureCipher.parse(it.signatureCipher ?: it.cipher) }
        val solved = PipePipeNsigDecoder.decodeSignatures(ciphers.values.mapNotNull { it?.signature })
        val signed =
            formats.mapNotNull { format ->
                val cipher = ciphers[format] ?: return@mapNotNull format.takeIf { !it.url.isNullOrEmpty() }
                solved[cipher.signature]?.let { format.copy(url = cipher.signedUrl(it)) }
            }
        PipePipeNsigDecoder.prefetch(signed.mapNotNull { it.url })
        return signed.mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            if (PipePipeNsigDecoder.rawN(url) == null) return@mapNotNull format
            PipePipeNsigDecoder.deobfuscateUrl(url)?.let { format.copy(url = it) }
        }
    }
}
