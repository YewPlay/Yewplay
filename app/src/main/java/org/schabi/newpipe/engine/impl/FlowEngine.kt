/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine.impl

import android.content.Context
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.search.SearchResultsPage
import io.github.aedev.flow.yewplay.FlowYewPlayFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.engine.EngineCapability
import org.schabi.newpipe.engine.EngineDescriptor
import org.schabi.newpipe.engine.EngineException
import org.schabi.newpipe.engine.EngineState
import org.schabi.newpipe.engine.ExtractionEngine
import org.schabi.newpipe.engine.SearchFilter
import org.schabi.newpipe.engine.model.YpChannelInfo
import org.schabi.newpipe.engine.model.YpChannelItem
import org.schabi.newpipe.engine.model.YpMediaFormat
import org.schabi.newpipe.engine.model.YpMediaItem
import org.schabi.newpipe.engine.model.YpPlaylistInfo
import org.schabi.newpipe.engine.model.YpPlaylistItem
import org.schabi.newpipe.engine.model.YpSearchPage
import org.schabi.newpipe.engine.model.YpSubtitleTrack
import org.schabi.newpipe.engine.model.YpVideoInfo

/**
 * FlowTube engine — wraps the vendored InnerTube client from Flow-Tube/Flow
 * without touching its internals. All access goes through the public `YouTube`
 * facade plus [FlowYewPlayFacade], a YewPlay-only addition that keeps upstream
 * `internal` helpers (search params, signature solving) encapsulated.
 *
 * Scope: YouTube only. SponsorBlock/DeArrow, SABR playback and account features
 * are out of scope for the engine contract.
 */
class FlowEngine(
    private val appContext: Context
) : ExtractionEngine {

    override val descriptor = EngineDescriptor(
        id = ID,
        displayName = "FlowTube",
        summary = "Flow InnerTube client (YouTube only)",
        version = "vendored-7ecb476",
        capabilities = setOf(
            EngineCapability.SEARCH_VIDEOS,
            EngineCapability.SEARCH_CHANNELS,
            EngineCapability.SEARCH_PLAYLISTS,
            EngineCapability.SEARCH_MUSIC,
            EngineCapability.SEARCH_SUGGESTIONS,
            EngineCapability.STREAM_EXTRACTION,
            EngineCapability.ADAPTIVE_STREAMS,
            EngineCapability.SUBTITLES,
            EngineCapability.CHANNEL_INFO,
            EngineCapability.PLAYLIST_INFO,
            EngineCapability.PAGINATION
        )
    )

    // Lazily initialized: the vendored client keeps process-wide state (visitor
    // data, decoder caches) that needs a Context before first use.
    private val youtube by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FlowYewPlayFacade.initialize(appContext.applicationContext)
        YouTube.cacheDirectory = appContext.cacheDir
        YouTube
    }

    override fun checkAvailability(): EngineState = try {
        youtube
        EngineState.AVAILABLE
    } catch (e: Exception) {
        EngineState.UNAVAILABLE
    }

    override suspend fun search(query: String, filter: SearchFilter, pageKey: String?): YpSearchPage = withContext(Dispatchers.IO) {
        try {
            if (filter == SearchFilter.MUSIC) {
                val result = if (pageKey == null) {
                    youtube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                } else {
                    youtube.searchContinuation(pageKey).getOrThrow()
                }
                return@withContext YpSearchPage(
                    items = result.items.mapNotNull { it.toYpMediaItem() },
                    channels = emptyList(),
                    playlists = emptyList(),
                    nextPageKey = result.continuation
                )
            }
            val params = when (filter) {
                SearchFilter.VIDEOS -> FlowYewPlayFacade.searchParams(FlowYewPlayFacade.ContentType.VIDEO)
                SearchFilter.CHANNELS -> FlowYewPlayFacade.searchParams(FlowYewPlayFacade.ContentType.CHANNEL)
                SearchFilter.PLAYLISTS -> FlowYewPlayFacade.searchParams(FlowYewPlayFacade.ContentType.PLAYLIST)
                else -> null
            }
            val page = if (pageKey == null) {
                youtube.videoSearch(query, params = params).getOrThrow()
            } else {
                youtube.videoSearch(query, continuation = pageKey).getOrThrow()
            }
            page.toYpSearchPage()
        } catch (e: Exception) {
            throw EngineException("search failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            youtube.searchSuggestions(query).getOrThrow().queries
        } catch (e: Exception) {
            throw EngineException("suggestions failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getVideoInfo(url: String): YpVideoInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
            ?: throw EngineException("not a YouTube video url: $url", engineId = descriptor.id, recoverable = false)
        try {
            val mediaInfo = youtube.getMediaInfo(videoId).getOrNull()
            val streams = FlowYewPlayFacade.resolveStreams(videoId)
                ?: throw EngineException("no playable streams for $videoId", engineId = descriptor.id)

            val ypFormats = mutableListOf<YpMediaFormat>()
            streams.progressive.forEach { ypFormats.add(it.toYpFormat(muxed = true)) }
            streams.adaptive.forEach { ypFormats.add(it.toYpFormat(muxed = false)) }
            streams.dashManifestUrl?.takeIf { it.isNotBlank() }?.let {
                ypFormats.add(YpMediaFormat(it, "application/dash+xml", "DASH", isAdaptive = true, muxed = false))
            }
            streams.hlsManifestUrl?.takeIf { it.isNotBlank() }?.let {
                ypFormats.add(YpMediaFormat(it, "application/x-mpegURL", "HLS", isAdaptive = true, muxed = false))
            }

            val details = streams.response.videoDetails
            val microformat = streams.response.microformat?.playerMicroformatRenderer
            val captions = streams.response.captions
                ?.playerCaptionsTracklistRenderer
                ?.captionTracks
                .orEmpty()

            YpVideoInfo(
                url = "https://www.youtube.com/watch?v=$videoId",
                title = mediaInfo?.title ?: details?.title ?: "",
                description = mediaInfo?.description ?: details?.shortDescription ?: "",
                thumbnailUrl = mediaInfo?.authorThumbnail,
                author = mediaInfo?.author ?: details?.author ?: "",
                authorUrl = (mediaInfo?.authorId ?: microformat?.externalChannelId)?.let { "https://www.youtube.com/channel/$it" },
                durationSeconds = (mediaInfo?.durationSeconds ?: details?.lengthSeconds?.toIntOrNull() ?: 0).toLong(),
                viewCount = (mediaInfo?.viewCount ?: details?.viewCount?.toIntOrNull() ?: 0).toLong(),
                uploadDate = mediaInfo?.uploadDate ?: microformat?.uploadDate ?: microformat?.publishDate,
                formats = ypFormats,
                subtitles = captions.mapNotNull { track ->
                    val baseUrl = track.baseUrl ?: return@mapNotNull null
                    YpSubtitleTrack(
                        url = baseUrl,
                        languageCode = track.languageCode ?: "",
                        displayName = track.name?.text ?: track.languageCode ?: "",
                        isAutoGenerated = track.kind == "asr"
                    )
                },
                likeCount = mediaInfo?.like?.toLong() ?: microformat?.likeCount?.toLongOrNull() ?: -1,
                isLive = details?.isLiveContent == true || details?.isLive == true
            )
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException("getVideoInfo failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getChannelInfo(url: String, pageKey: String?): YpChannelInfo = withContext(Dispatchers.IO) {
        try {
            if (pageKey != null) {
                val (channelId, continuation) = decodeChannelPageKey(pageKey)
                val page = youtube.channelVideosContinuation(continuation, channelId, "", "").getOrThrow()
                return@withContext YpChannelInfo(
                    url = "https://www.youtube.com/channel/$channelId",
                    name = "",
                    avatarUrl = null,
                    description = "",
                    subscriberCount = -1,
                    items = page.videos.map { it.toYpMediaItem() },
                    nextPageKey = page.continuation?.let { encodeChannelPageKey(channelId, it) }
                )
            }
            val channelId = youtube.resolveChannelId(url).getOrThrow()
            val channel = youtube.channel(channelId).getOrThrow()
            val header = channel.header
            val videos = youtube.channelVideos(channelId, header.title, header.avatarUrl).getOrThrow()
            YpChannelInfo(
                url = header.canonicalUrl ?: "https://www.youtube.com/channel/$channelId",
                name = header.title,
                avatarUrl = header.avatarUrl.takeIf { it.isNotBlank() },
                description = header.description ?: "",
                subscriberCount = header.subscriberCount ?: -1,
                items = videos.videos.map { it.toYpMediaItem() },
                nextPageKey = videos.continuation?.let { encodeChannelPageKey(channelId, it) }
            )
        } catch (e: Exception) {
            throw EngineException("getChannelInfo failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getPlaylistInfo(url: String, pageKey: String?): YpPlaylistInfo = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url)
            ?: throw EngineException("not a YouTube playlist url: $url", engineId = descriptor.id, recoverable = false)
        try {
            val page = youtube.videoPlaylistPage(playlistId, continuation = pageKey).getOrThrow()
            YpPlaylistInfo(
                url = "https://www.youtube.com/playlist?list=$playlistId",
                name = page.title ?: "",
                thumbnailUrl = page.thumbnailUrl,
                authorName = page.ownerName,
                itemCount = page.videoCount?.toLong() ?: page.videos.size.toLong(),
                items = page.videos.map { it.toYpMediaItem() },
                nextPageKey = page.continuation
            )
        } catch (e: Exception) {
            throw EngineException("getPlaylistInfo failed: ${e.message}", e, descriptor.id)
        }
    }

    private fun SearchResultsPage.toYpSearchPage(): YpSearchPage {
        val videos = mutableListOf<YpMediaItem>()
        val channels = mutableListOf<YpChannelItem>()
        val playlists = mutableListOf<YpPlaylistItem>()
        sections.forEach { section ->
            val item = (section as? io.github.aedev.flow.innertube.pages.search.SearchSection.Result)?.item ?: return@forEach
            when (item) {
                is FeedItem.VideoItem -> videos.add(item.video.toYpMediaItem())
                is FeedItem.ShortItem -> videos.add(item.video.toYpMediaItem())
                is FeedItem.RelatedChannelItem -> channels.add(item.channel.toYpChannelItem())
                is FeedItem.PlaylistItem -> playlists.add(item.playlist.toYpPlaylistItem())
                is FeedItem.PostItem -> Unit // community posts are out of the engine contract
            }
        }
        return YpSearchPage(videos, channels, playlists, continuation)
    }

    private fun YTItem.toYpMediaItem(): YpMediaItem? = (this as? SongItem)?.let { song ->
        YpMediaItem(
            url = "https://www.youtube.com/watch?v=${song.id}",
            title = song.title,
            thumbnailUrl = song.thumbnail,
            authorName = song.artists.joinToString(", ") { it.name },
            authorUrl = song.artists.firstOrNull()?.id?.let { "https://www.youtube.com/channel/$it" },
            durationSeconds = song.duration?.toLong() ?: 0,
            viewCount = 0,
            uploadDate = null
        )
    }

    private fun Video.toYpMediaItem(): YpMediaItem = YpMediaItem(
        url = "https://www.youtube.com/watch?v=$id",
        title = title,
        thumbnailUrl = thumbnailUrl.takeIf { it.isNotBlank() },
        authorName = channelName.takeIf { it.isNotBlank() },
        authorUrl = channelId.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/channel/$it" },
        durationSeconds = duration.toLong(),
        viewCount = viewCount,
        uploadDate = uploadDate.takeIf { it.isNotBlank() },
        isLive = isLive
    )

    private fun io.github.aedev.flow.data.model.Channel.toYpChannelItem(): YpChannelItem = YpChannelItem(
        url = url.ifBlank { "https://www.youtube.com/channel/$id" },
        name = name,
        avatarUrl = thumbnailUrl.takeIf { it.isNotBlank() },
        subscriberCount = subscriberCount,
        description = description.takeIf { it.isNotBlank() }
    )

    private fun Playlist.toYpPlaylistItem(): YpPlaylistItem = YpPlaylistItem(
        url = "https://www.youtube.com/playlist?list=$id",
        name = name,
        thumbnailUrl = thumbnailUrl.takeIf { it.isNotBlank() },
        authorName = null,
        itemCount = videoCount.toLong()
    )

    private fun io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format.toYpFormat(
        muxed: Boolean
    ): YpMediaFormat {
        val audio = isAudio
        return YpMediaFormat(
            url = url.orEmpty(),
            mimeType = mimeType.substringBefore(';'),
            qualityLabel = qualityLabel ?: audioQuality ?: "${bitrate / 1000}kbps",
            height = height ?: 0,
            fps = fps ?: 0,
            bitrate = bitrate,
            muxed = muxed,
            isAudioOnly = audio,
            isVideoOnly = !audio && !muxed,
            isAdaptive = !muxed
        )
    }

    companion object {
        const val ID = "flow"

        private val VIDEO_ID_REGEXES = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})")
        )
        private val PLAYLIST_ID_REGEX = Regex("[?&]list=([A-Za-z0-9_-]+)")

        fun extractVideoId(url: String): String? = VIDEO_ID_REGEXES.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

        fun extractPlaylistId(url: String): String? = PLAYLIST_ID_REGEX.find(url)?.groupValues?.get(1)

        private fun encodeChannelPageKey(channelId: String, continuation: String): String = "$channelId\n$continuation"

        private fun decodeChannelPageKey(key: String): Pair<String, String> {
            val parts = key.split("\n", limit = 2)
            return parts[0] to (parts.getOrNull(1) ?: "")
        }
    }
}
