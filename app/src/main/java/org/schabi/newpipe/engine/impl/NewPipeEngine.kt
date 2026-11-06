/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.engine.EngineCapability
import org.schabi.newpipe.engine.EngineDescriptor
import org.schabi.newpipe.engine.EngineException
import org.schabi.newpipe.engine.EngineManager
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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * NewPipe engine — wraps the bundled NewPipeExtractor without touching its
 * internals. This is the default engine and the always-available fallback.
 *
 * Scope: YouTube service of the extractor, matching the other engines' scope.
 */
class NewPipeEngine : ExtractionEngine {

    override val descriptor = EngineDescriptor(
        id = EngineManager.NEWPIPE_ID,
        displayName = "NewPipe",
        summary = "NewPipeExtractor (YouTube, SoundCloud, PeerTube, Bandcamp, media.ccc.de)",
        version = "0.29.1-bundled",
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

    private val youtube get() = NewPipe.getService(ServiceList.YouTube.serviceId)

    override fun checkAvailability(): EngineState = if (runCatching { youtube }.isSuccess) {
        EngineState.AVAILABLE
    } else {
        EngineState.UNAVAILABLE
    }

    override suspend fun search(query: String, filter: SearchFilter, pageKey: String?): YpSearchPage = withContext(Dispatchers.IO) {
        try {
            val service = youtube
            val handler = service.searchQHFactory.fromQuery(query, listOf(filter.toContentFilter()), "")
            if (pageKey == null) {
                val info = SearchInfo.getInfo(service, handler)
                info.toYpSearchPage()
            } else {
                val info = SearchInfo.getMoreItems(
                    service,
                    handler,
                    org.schabi.newpipe.extractor.Page(pageKey)
                )
                YpSearchPage(
                    items = info.items.filterIsInstance<StreamInfoItem>().map { it.toYpMediaItem() },
                    channels = emptyList(),
                    playlists = emptyList(),
                    nextPageKey = info.nextPage?.url
                )
            }
        } catch (e: Exception) {
            throw EngineException("search failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            youtube
                .suggestionExtractor
                .suggestionList(query)
        } catch (e: Exception) {
            throw EngineException("suggestions failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getVideoInfo(url: String): YpVideoInfo = withContext(Dispatchers.IO) {
        try {
            StreamInfo.getInfo(youtube, url).toYpVideoInfo()
        } catch (e: Exception) {
            throw EngineException("video info failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getChannelInfo(url: String, pageKey: String?): YpChannelInfo = withContext(Dispatchers.IO) {
        try {
            val service = youtube
            if (pageKey == null) {
                // ChannelInfo in this extractor carries metadata only; the items
                // live in channel tabs, so the first tab is fetched as page one.
                val info = ChannelInfo.getInfo(service, url)
                val firstTab = info.tabs.firstOrNull()
                val tabInfo = firstTab?.let { ChannelTabInfo.getInfo(service, it) }
                YpChannelInfo(
                    url = info.url,
                    name = info.name,
                    avatarUrl = info.avatars.lastOrNull()?.url,
                    description = info.description ?: "",
                    subscriberCount = info.subscriberCount,
                    items = tabInfo?.relatedItems?.filterIsInstance<StreamInfoItem>()
                        ?.map { it.toYpMediaItem() }.orEmpty(),
                    nextPageKey = encodeChannelPageKey(firstTab?.url, tabInfo?.nextPage?.url)
                )
            } else {
                val (tabUrl, pageUrl) = decodeChannelPageKey(pageKey)
                val tabHandler = ListLinkHandler(tabUrl, tabUrl, tabUrl, emptyList(), "")
                val more = ChannelTabInfo.getMoreItems(
                    service,
                    tabHandler,
                    org.schabi.newpipe.extractor.Page(pageUrl)
                )
                YpChannelInfo(
                    url = url,
                    name = "",
                    avatarUrl = null,
                    description = "",
                    subscriberCount = -1,
                    items = more.items.filterIsInstance<StreamInfoItem>().map { it.toYpMediaItem() },
                    nextPageKey = encodeChannelPageKey(tabUrl, more.nextPage?.url)
                )
            }
        } catch (e: Exception) {
            throw EngineException("channel info failed: ${e.message}", e, descriptor.id)
        }
    }

    // A channel page key must carry the tab handler alongside the continuation
    // URL; both are URLs, joined by a newline (never present in URLs).
    private fun encodeChannelPageKey(tabUrl: String?, pageUrl: String?): String? = if (tabUrl == null || pageUrl == null) null else "$tabUrl\n$pageUrl"

    private fun decodeChannelPageKey(pageKey: String): Pair<String, String> {
        val parts = pageKey.split("\n", limit = 2)
        require(parts.size == 2) { "malformed channel page key" }
        return parts[0] to parts[1]
    }

    override suspend fun getPlaylistInfo(url: String, pageKey: String?): YpPlaylistInfo = withContext(Dispatchers.IO) {
        try {
            val service = youtube
            if (pageKey == null) {
                PlaylistInfo.getInfo(service, url).toYpPlaylistInfo()
            } else {
                val more = PlaylistInfo.getMoreItems(
                    service,
                    url,
                    org.schabi.newpipe.extractor.Page(pageKey)
                )
                YpPlaylistInfo(
                    url = url,
                    name = "",
                    thumbnailUrl = null,
                    authorName = null,
                    itemCount = -1,
                    items = more.items.filterIsInstance<StreamInfoItem>().map { it.toYpMediaItem() },
                    nextPageKey = more.nextPage?.url
                )
            }
        } catch (e: Exception) {
            throw EngineException("playlist info failed: ${e.message}", e, descriptor.id)
        }
    }

    // region mapping
    private fun SearchFilter.toContentFilter(): String = when (this) {
        SearchFilter.ALL -> "all"
        SearchFilter.VIDEOS -> "videos"
        SearchFilter.CHANNELS -> "channels"
        SearchFilter.PLAYLISTS -> "playlists"
        SearchFilter.MUSIC -> "music_songs"
    }

    private fun SearchInfo.toYpSearchPage(): YpSearchPage {
        val videos = mutableListOf<YpMediaItem>()
        val channels = mutableListOf<YpChannelItem>()
        val playlists = mutableListOf<YpPlaylistItem>()
        for (item in relatedItems) {
            when (item) {
                is StreamInfoItem -> videos.add(item.toYpMediaItem())

                is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> channels.add(
                    YpChannelItem(
                        url = item.url,
                        name = item.name,
                        avatarUrl = item.thumbnails.lastOrNull()?.url,
                        subscriberCount = item.subscriberCount,
                        description = item.description
                    )
                )

                is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> playlists.add(
                    YpPlaylistItem(
                        url = item.url,
                        name = item.name,
                        thumbnailUrl = item.thumbnails.lastOrNull()?.url,
                        authorName = item.uploaderName,
                        itemCount = item.streamCount
                    )
                )
            }
        }
        return YpSearchPage(videos, channels, playlists, nextPage?.url)
    }

    private fun StreamInfoItem.toYpMediaItem() = YpMediaItem(
        url = url,
        title = name,
        thumbnailUrl = thumbnails.lastOrNull()?.url,
        authorName = uploaderName,
        authorUrl = uploaderUrl,
        durationSeconds = duration,
        viewCount = viewCount,
        uploadDate = textualUploadDate,
        isLive = streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
            streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
    )

    private fun StreamInfo.toYpVideoInfo(): YpVideoInfo {
        val formats = mutableListOf<YpMediaFormat>()
        videoStreams?.forEach { formats.add(it.toYpFormat(muxed = true, audioOnly = false, videoOnly = false)) }
        videoOnlyStreams?.forEach { formats.add(it.toYpFormat(muxed = false, audioOnly = false, videoOnly = true)) }
        audioStreams?.forEach { formats.add(it.toYpAudioFormat()) }
        dashMpdUrl?.takeIf { it.isNotBlank() }?.let {
            formats.add(YpMediaFormat(it, "application/dash+xml", "DASH", isAdaptive = true, muxed = false))
        }
        hlsUrl?.takeIf { it.isNotBlank() }?.let {
            formats.add(YpMediaFormat(it, "application/x-mpegURL", "HLS", isAdaptive = true, muxed = false))
        }
        return YpVideoInfo(
            url = url,
            title = name,
            description = description?.content ?: "",
            thumbnailUrl = thumbnails.lastOrNull()?.url,
            author = uploaderName,
            authorUrl = uploaderUrl,
            durationSeconds = duration,
            viewCount = viewCount,
            uploadDate = textualUploadDate,
            formats = formats,
            subtitles = subtitles?.map { sub ->
                YpSubtitleTrack(
                    url = sub.url ?: "",
                    languageCode = sub.languageTag ?: "",
                    displayName = sub.displayLanguageName ?: sub.languageTag ?: "",
                    isAutoGenerated = sub.isAutoGenerated
                )
            } ?: emptyList(),
            likeCount = likeCount
        )
    }

    private fun VideoStream.toYpFormat(muxed: Boolean, audioOnly: Boolean, videoOnly: Boolean) = YpMediaFormat(
        url = url ?: manifestUrl ?: "",
        mimeType = format?.mimeType ?: "video/mp4",
        qualityLabel = resolution ?: "",
        height = height,
        fps = fps,
        bitrate = bitrate,
        muxed = muxed,
        isAudioOnly = audioOnly,
        isVideoOnly = videoOnly
    )

    private fun AudioStream.toYpAudioFormat() = YpMediaFormat(
        url = url ?: manifestUrl ?: "",
        mimeType = format?.mimeType ?: "audio/mp4",
        qualityLabel = "${bitrate / 1000}kbps",
        bitrate = bitrate,
        muxed = false,
        isAudioOnly = true
    )

    private fun PlaylistInfo.toYpPlaylistInfo() = YpPlaylistInfo(
        url = url,
        name = name,
        thumbnailUrl = thumbnails.lastOrNull()?.url,
        authorName = uploaderName,
        itemCount = streamCount,
        items = relatedItems.filterIsInstance<StreamInfoItem>().map { it.toYpMediaItem() },
        nextPageKey = nextPage?.url
    )
    // endregion
}
