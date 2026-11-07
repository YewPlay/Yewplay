/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine.bridge

import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.rxSingle
import org.schabi.newpipe.engine.EngineCapability
import org.schabi.newpipe.engine.EngineManager
import org.schabi.newpipe.engine.SearchFilter
import org.schabi.newpipe.engine.model.YpChannelItem
import org.schabi.newpipe.engine.model.YpMediaFormat
import org.schabi.newpipe.engine.model.YpMediaItem
import org.schabi.newpipe.engine.model.YpPlaylistItem
import org.schabi.newpipe.engine.model.YpSearchPage
import org.schabi.newpipe.engine.model.YpSubtitleTrack
import org.schabi.newpipe.engine.model.YpVideoInfo
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.InfoCache

/**
 * YewPlay MultiEngine — integration-layer bridge between the active [EngineManager] engine and
 * the classic NewPipe call sites, which speak NewPipeExtractor types ([StreamInfo], [SearchInfo],
 * [InfoItemsPage]).
 *
 * Routing rule: when the YouTube service is requested and the active engine is NOT the bundled
 * NewPipe engine, the operation runs on the active engine (with the manager's bounded fallback)
 * and the standardized engine models are converted into extractor model objects. Otherwise the
 * call is delegated to [ExtractorHelper] untouched, so the NewPipe code path keeps its exact
 * upstream behavior.
 *
 * Engine-produced [StreamInfo]s are stored in the same [InfoCache] used by [ExtractorHelper],
 * so cache lookups (e.g. `ExtractorHelper.isCached`) work uniformly for every engine.
 */
object EngineExtractorBridge {

    private fun activeManagerOrNull(): EngineManager? = runCatching { EngineManager.get() }.getOrNull()

    /** True when this call must be served by the active non-NewPipe engine. */
    private fun shouldUseEngine(serviceId: Int): Boolean {
        if (serviceId != ServiceList.YouTube.serviceId) return false
        val manager = activeManagerOrNull() ?: return false
        return manager.active().descriptor.id != EngineManager.NEWPIPE_ID
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Stream info                                                                               */
    /* ---------------------------------------------------------------------------------------- */

    @JvmStatic
    fun getStreamInfo(serviceId: Int, url: String, forceLoad: Boolean): Single<StreamInfo> {
        if (!shouldUseEngine(serviceId)) {
            return ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
        }
        val cache = InfoCache.getInstance()
        val loadFromEngine = rxSingle {
            EngineManager.get()
                .execute(EngineCapability.STREAM_EXTRACTION) { it.getVideoInfo(url) }
                .value
                .toStreamInfo(serviceId)
        }.doOnSuccess { info -> cache.putInfo(serviceId, url, info, InfoCache.Type.STREAM) }

        // Same semantics as ExtractorHelper.checkCache: forceLoad bypasses and evicts the cache,
        // otherwise a cached entry wins over a network load.
        return if (forceLoad) {
            cache.removeInfo(serviceId, url, InfoCache.Type.STREAM)
            loadFromEngine
        } else {
            Maybe.concat(
                Maybe.defer {
                    val cached = cache.getFromKey(serviceId, url, InfoCache.Type.STREAM)
                    if (cached is StreamInfo) Maybe.just(cached) else Maybe.empty()
                },
                loadFromEngine.toMaybe()
            ).firstElement().toSingle()
        }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Search                                                                                    */
    /* ---------------------------------------------------------------------------------------- */

    @JvmStatic
    fun searchFor(
        serviceId: Int,
        searchString: String,
        contentFilter: List<String>,
        sortFilter: String
    ): Single<SearchInfo> {
        if (!shouldUseEngine(serviceId)) {
            return ExtractorHelper.searchFor(serviceId, searchString, contentFilter, sortFilter)
        }
        val filter = contentFilter.toSearchFilter()
        return rxSingle {
            val page = EngineManager.get()
                .execute(EngineCapability.SEARCH_VIDEOS) { it.search(searchString, filter, null) }
                .value
            page.toSearchInfo(serviceId, searchString, contentFilter, sortFilter)
        }
    }

    @JvmStatic
    fun getMoreSearchItems(
        serviceId: Int,
        searchString: String,
        contentFilter: List<String>,
        sortFilter: String,
        page: Page
    ): Single<InfoItemsPage<InfoItem>> {
        if (!shouldUseEngine(serviceId)) {
            return ExtractorHelper.getMoreSearchItems(
                serviceId,
                searchString,
                contentFilter,
                sortFilter,
                page
            )
        }
        val filter = contentFilter.toSearchFilter()
        return rxSingle {
            val result = EngineManager.get()
                .execute(EngineCapability.SEARCH_VIDEOS) { it.search(searchString, filter, page.url) }
                .value
            InfoItemsPage(result.toInfoItems(serviceId), result.nextPageKey?.let { Page(it) }, emptyList())
        }
    }

    @JvmStatic
    fun suggestionsFor(serviceId: Int, query: String): Single<List<String>> {
        if (!shouldUseEngine(serviceId)) {
            return ExtractorHelper.suggestionsFor(serviceId, query)
        }
        return rxSingle {
            EngineManager.get()
                .execute(EngineCapability.SEARCH_SUGGESTIONS) { it.suggestions(query) }
                .value
        }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Conversion: engine models -> extractor models                                             */
    /* ---------------------------------------------------------------------------------------- */

    private fun List<String>.toSearchFilter(): SearchFilter = when {
        any { it.contains("music") } -> SearchFilter.MUSIC
        any { it == "videos" } -> SearchFilter.VIDEOS
        any { it == "channels" } -> SearchFilter.CHANNELS
        any { it == "playlists" } -> SearchFilter.PLAYLISTS
        else -> SearchFilter.ALL
    }

    private fun thumbnailsOf(url: String?): List<Image> = listOfNotNull(
        url?.takeIf { it.isNotBlank() }?.let {
            Image(it, Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.MEDIUM)
        }
    )

    private fun mediaFormatOf(mimeType: String): MediaFormat? = runCatching {
        MediaFormat.getFromMimeType(mimeType.substringBefore(";").trim())
    }.getOrNull()

    private fun YpVideoInfo.toStreamInfo(serviceId: Int): StreamInfo {
        val streamType = if (isLive) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM
        val id = runCatching {
            NewPipe.getService(serviceId).streamLHFactory.fromUrl(url).id
        }.getOrNull() ?: url
        val info = StreamInfo(serviceId, url, url, streamType, id, title, NO_AGE_LIMIT)
        info.thumbnails = thumbnailsOf(thumbnailUrl)
        info.duration = durationSeconds
        info.viewCount = viewCount
        info.likeCount = likeCount
        info.uploaderName = author
        info.uploaderUrl = authorUrl ?: ""
        info.description = Description.of(description, Description.Type.PLAIN_TEXT)
        if (uploadDate != null) {
            info.textualUploadDate = uploadDate
        }

        val videoStreams = mutableListOf<VideoStream>()
        val videoOnlyStreams = mutableListOf<VideoStream>()
        val audioStreams = mutableListOf<AudioStream>()
        var dashMpdUrl: String? = null
        var hlsUrl: String? = null
        for (format in formats) {
            val mime = format.mimeType.lowercase()
            when {
                // Manifest entries are exposed through the dedicated fields, not as streams.
                format.isAdaptive && mime.contains("dash") ->
                    if (dashMpdUrl == null) dashMpdUrl = format.url

                format.isAdaptive && (mime.contains("mpegurl") || mime.contains("m3u8")) ->
                    if (hlsUrl == null) hlsUrl = format.url

                format.isAudioOnly -> audioStreams.add(format.toAudioStream())

                format.muxed -> videoStreams.add(format.toVideoStream(videoOnly = false))

                else -> videoOnlyStreams.add(format.toVideoStream(videoOnly = true))
            }
        }
        info.videoStreams = videoStreams
        info.videoOnlyStreams = videoOnlyStreams
        info.audioStreams = audioStreams
        info.dashMpdUrl = dashMpdUrl
        info.hlsUrl = hlsUrl
        info.subtitles = subtitles.mapNotNull { it.toSubtitlesStream() }
        return info
    }

    private fun YpMediaFormat.toVideoStream(videoOnly: Boolean): VideoStream = VideoStream.Builder()
        .setId(Stream.ID_UNKNOWN)
        .setContent(url, true)
        .setMediaFormat(mediaFormatOf(mimeType))
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setIsVideoOnly(videoOnly)
        .setResolution(qualityLabel)
        .build()

    private fun YpMediaFormat.toAudioStream(): AudioStream = AudioStream.Builder()
        .setId(Stream.ID_UNKNOWN)
        .setContent(url, true)
        .setMediaFormat(mediaFormatOf(mimeType))
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setAverageBitrate(bitrate)
        .build()

    private fun YpSubtitleTrack.toSubtitlesStream(): SubtitlesStream? {
        if (url.isBlank() || languageCode.isBlank()) return null
        return runCatching {
            SubtitlesStream.Builder()
                .setContent(url, true)
                // YouTube caption tracks are served as TTML; the engine contract is YouTube-scoped.
                .setMediaFormat(MediaFormat.TTML)
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setLanguageCode(languageCode)
                .setName(displayName)
                .setAutoGenerated(isAutoGenerated)
                .build()
        }.getOrNull()
    }

    private fun YpMediaItem.toStreamInfoItem(serviceId: Int): StreamInfoItem {
        val type = if (isLive) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM
        val item = StreamInfoItem(serviceId, url, title, type)
        item.duration = durationSeconds
        item.uploaderName = authorName
        item.uploaderUrl = authorUrl
        item.viewCount = viewCount
        if (uploadDate != null) {
            item.textualUploadDate = uploadDate
        }
        item.thumbnails = thumbnailsOf(thumbnailUrl)
        return item
    }

    private fun YpChannelItem.toChannelInfoItem(serviceId: Int): ChannelInfoItem {
        val item = ChannelInfoItem(serviceId, url, name)
        item.description = description
        item.subscriberCount = subscriberCount
        item.thumbnails = thumbnailsOf(avatarUrl)
        return item
    }

    private fun YpPlaylistItem.toPlaylistInfoItem(serviceId: Int): PlaylistInfoItem {
        val item = PlaylistInfoItem(serviceId, url, name)
        item.uploaderName = authorName
        item.streamCount = itemCount
        item.thumbnails = thumbnailsOf(thumbnailUrl)
        return item
    }

    private fun YpSearchPage.toInfoItems(serviceId: Int): List<InfoItem> = items.map { it.toStreamInfoItem(serviceId) } +
        channels.map { it.toChannelInfoItem(serviceId) } +
        playlists.map { it.toPlaylistInfoItem(serviceId) }

    private fun YpSearchPage.toSearchInfo(
        serviceId: Int,
        searchString: String,
        contentFilter: List<String>,
        sortFilter: String
    ): SearchInfo {
        // The handler only carries query metadata (filters, url) for the UI; building it from the
        // YouTube service factory is integration-layer usage, not engine internals.
        val handler = runCatching {
            NewPipe.getService(serviceId).searchQHFactory.fromQuery(searchString, contentFilter, sortFilter)
        }.getOrElse {
            NewPipe.getService(serviceId).searchQHFactory.fromQuery(searchString, emptyList(), "")
        }
        val info = SearchInfo(serviceId, handler, searchString)
        info.relatedItems = toInfoItems(serviceId)
        info.nextPage = nextPageKey?.let { Page(it) }
        return info
    }
}
