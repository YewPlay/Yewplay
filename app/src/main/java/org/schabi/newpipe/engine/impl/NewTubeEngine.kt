/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine.impl

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.data.FeedbackEndpoint
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
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
 * NewTube engine — wraps the vendored MediaServiceCore (InnerTube client from
 * the NewTube/SmartTube lineage) without touching its internals.
 *
 * Scope: YouTube only. Subtitles are exposed; SponsorBlock/DeArrow and account
 * features are out of scope for the engine contract.
 */
class NewTubeEngine(
    private val appContext: Context
) : ExtractionEngine {

    override val descriptor = EngineDescriptor(
        id = ID,
        displayName = "NewTube",
        summary = "MediaServiceCore InnerTube client (YouTube only)",
        version = "vendored-52fecea",
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

    // Lazily initialized: MediaServiceCore keeps process-wide state behind
    // GlobalPreferences, which requires a Context before first use.
    private val service by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GlobalPreferences.instance(appContext.applicationContext)
        YouTubeServiceManager.instance()
    }

    override fun checkAvailability(): EngineState = try {
        service
        EngineState.AVAILABLE
    } catch (e: Exception) {
        EngineState.UNAVAILABLE
    }

    override suspend fun search(query: String, filter: SearchFilter, pageKey: String?): YpSearchPage = withContext(Dispatchers.IO) {
        try {
            val groups = if (pageKey == null) {
                service.contentService.getSearch(query) ?: emptyList()
            } else {
                listOfNotNull(service.contentService.getGroup(pageKey))
            }
            groups.toYpSearchPage(filter)
        } catch (e: Exception) {
            throw EngineException("search failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            service.contentService.getSearchTags(query) ?: emptyList()
        } catch (e: Exception) {
            throw EngineException("suggestions failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getVideoInfo(url: String): YpVideoInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
            ?: throw EngineException("not a YouTube video url: $url", engineId = descriptor.id, recoverable = false)
        try {
            val formats = service.mediaItemService.getFormatInfo(videoId)
            val metadata = runCatching { service.mediaItemService.getMetadata(videoId) }.getOrNull()

            val ypFormats = mutableListOf<YpMediaFormat>()
            formats.urlFormats?.forEach { ypFormats.add(it.toYpFormat(muxed = true, adaptive = false)) }
            formats.adaptiveFormats?.forEach { ypFormats.add(it.toYpFormat(muxed = false, adaptive = true)) }
            formats.dashManifestUrl?.takeIf { it.isNotBlank() }?.let {
                ypFormats.add(YpMediaFormat(it, "application/dash+xml", "DASH", isAdaptive = true, muxed = false))
            }
            formats.hlsManifestUrl?.takeIf { it.isNotBlank() }?.let {
                ypFormats.add(YpMediaFormat(it, "application/x-mpegURL", "HLS", isAdaptive = true, muxed = false))
            }

            YpVideoInfo(
                url = "https://www.youtube.com/watch?v=$videoId",
                title = formats.title ?: metadata?.title ?: "",
                description = formats.description ?: metadata?.description ?: "",
                thumbnailUrl = metadata?.authorImageUrl,
                author = formats.author ?: metadata?.author ?: "",
                authorUrl = formats.channelId?.let { "https://www.youtube.com/channel/$it" },
                durationSeconds = formats.lengthSeconds?.toLongOrNull()
                    ?: (metadata?.durationMs ?: 0L) / 1000,
                viewCount = formats.viewCount?.toLongOrNull()
                    ?: metadata?.viewCount?.toLongOrNull() ?: -1,
                uploadDate = formats.uploadDate ?: metadata?.publishedDate,
                formats = ypFormats,
                subtitles = formats.subtitles?.map {
                    YpSubtitleTrack(
                        url = it.baseUrl ?: "",
                        languageCode = it.languageCode ?: "",
                        displayName = it.name ?: it.languageCode ?: "",
                        isAutoGenerated = it.vssId?.startsWith("a.") == true ||
                            it.type.equals("asr", ignoreCase = true)
                    )
                } ?: emptyList(),
                likeCount = metadata?.likeCount?.toLongOrNull() ?: -1,
                isLive = formats.isLive || formats.isLiveContent
            )
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException("video info failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getChannelInfo(url: String, pageKey: String?): YpChannelInfo = withContext(Dispatchers.IO) {
        try {
            if (pageKey != null) {
                val group = service.contentService.getGroup(pageKey)
                return@withContext YpChannelInfo(
                    url = url,
                    name = "",
                    avatarUrl = null,
                    description = "",
                    subscriberCount = -1,
                    items = group?.mediaItems.orEmpty()
                        .filter { it.type == MediaItem.TYPE_VIDEO || it.type == MediaItem.TYPE_MUSIC }
                        .map { it.toYpMediaItem() },
                    nextPageKey = group?.nextPageKey
                )
            }
            val channelId = extractChannelId(url)
                ?: throw EngineException(
                    "not a YouTube channel url: $url",
                    engineId = descriptor.id,
                    recoverable = false
                )
            val rows = service.contentService.getChannelObserve(channelId).blockingFirst()
            val uploads = rows.firstOrNull {
                it.type == MediaGroup.TYPE_CHANNEL_UPLOADS && !it.isEmpty
            } ?: rows.firstOrNull { !it.isEmpty }
            YpChannelInfo(
                url = url,
                name = uploads?.title ?: "",
                avatarUrl = runCatching {
                    service.contentService.getChannelAvatarUrl(channelId)
                }.getOrNull(),
                description = "",
                // Anonymous InnerTube browse does not expose a subscriber count.
                subscriberCount = -1,
                items = uploads?.mediaItems.orEmpty().map { it.toYpMediaItem() },
                nextPageKey = uploads?.nextPageKey
            )
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException("channel info failed: ${e.message}", e, descriptor.id)
        }
    }

    override suspend fun getPlaylistInfo(url: String, pageKey: String?): YpPlaylistInfo = withContext(Dispatchers.IO) {
        try {
            if (pageKey != null) {
                val group = service.contentService.getGroup(pageKey)
                return@withContext YpPlaylistInfo(
                    url = url,
                    name = "",
                    thumbnailUrl = null,
                    authorName = null,
                    itemCount = -1,
                    items = group?.mediaItems.orEmpty()
                        .filter { it.type == MediaItem.TYPE_VIDEO || it.type == MediaItem.TYPE_MUSIC }
                        .map { it.toYpMediaItem() },
                    nextPageKey = group?.nextPageKey
                )
            }
            val playlistId = extractQueryParam(url, "list")
                ?: throw EngineException(
                    "not a YouTube playlist url: $url",
                    engineId = descriptor.id,
                    recoverable = false
                )
            val group = service.contentService.getGroup(PlaylistMediaItem(playlistId))
            YpPlaylistInfo(
                url = url,
                name = group?.title ?: "",
                thumbnailUrl = null,
                authorName = null,
                itemCount = group?.mediaItems?.size?.toLong() ?: -1,
                items = group?.mediaItems.orEmpty()
                    .filter { it.type == MediaItem.TYPE_VIDEO || it.type == MediaItem.TYPE_MUSIC }
                    .map { it.toYpMediaItem() },
                nextPageKey = group?.nextPageKey
            )
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException("playlist info failed: ${e.message}", e, descriptor.id)
        }
    }

    // region mapping
    private fun List<MediaGroup>.toYpSearchPage(filter: SearchFilter): YpSearchPage {
        val videos = mutableListOf<YpMediaItem>()
        val channels = mutableListOf<YpChannelItem>()
        val playlists = mutableListOf<YpPlaylistItem>()
        for (item in flatMap { it.mediaItems.orEmpty() }) {
            when (item.type) {
                MediaItem.TYPE_VIDEO ->
                    if (filter == SearchFilter.ALL || filter == SearchFilter.VIDEOS) {
                        videos.add(item.toYpMediaItem())
                    }

                MediaItem.TYPE_MUSIC ->
                    if (filter == SearchFilter.ALL || filter == SearchFilter.MUSIC) {
                        videos.add(item.toYpMediaItem())
                    }

                MediaItem.TYPE_CHANNEL ->
                    if (filter == SearchFilter.ALL || filter == SearchFilter.CHANNELS) {
                        channels.add(
                            YpChannelItem(
                                url = item.channelId?.let { "https://www.youtube.com/channel/$it" } ?: "",
                                name = item.title ?: "",
                                avatarUrl = item.cardImageUrl,
                                subscriberCount = -1,
                                description = null
                            )
                        )
                    }

                MediaItem.TYPE_PLAYLIST ->
                    if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
                        playlists.add(
                            YpPlaylistItem(
                                url = item.playlistId
                                    ?.let { "https://www.youtube.com/playlist?list=$it" } ?: "",
                                name = item.title ?: "",
                                thumbnailUrl = item.cardImageUrl,
                                authorName = item.author,
                                itemCount = -1
                            )
                        )
                    }
            }
        }
        return YpSearchPage(
            videos,
            channels,
            playlists,
            nextPageKey = lastOrNull { it.nextPageKey != null }?.nextPageKey
        )
    }

    private fun MediaItem.toYpMediaItem() = YpMediaItem(
        url = videoId?.let { "https://www.youtube.com/watch?v=$it" }
            ?: playlistId?.let { "https://www.youtube.com/playlist?list=$it" }
            ?: channelId?.let { "https://www.youtube.com/channel/$it" }
            ?: "",
        title = title ?: "",
        thumbnailUrl = cardImageUrl,
        authorName = author,
        authorUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
        durationSeconds = durationMs / 1000,
        viewCount = -1,
        uploadDate = productionDate,
        isLive = isLive
    )

    private fun MediaFormat.toYpFormat(muxed: Boolean, adaptive: Boolean): YpMediaFormat {
        val mime = mimeType ?: ""
        val isAudio = mime.startsWith("audio/")
        return YpMediaFormat(
            url = url ?: sourceUrl ?: "",
            mimeType = mime,
            qualityLabel = qualityLabel ?: quality ?: "",
            height = height,
            fps = fps?.toIntOrNull() ?: 0,
            bitrate = bitrate?.toIntOrNull() ?: 0,
            muxed = muxed,
            isAudioOnly = adaptive && isAudio,
            isVideoOnly = adaptive && !isAudio,
            isAdaptive = adaptive
        )
    }
    // endregion

    // region url parsing (engine-local: engines must not depend on each other)
    private fun extractVideoId(url: String): String? {
        extractQueryParam(url, "v")?.let { return it }
        val short = Regex("""(?:youtu\.be/|/shorts/|/live/|/embed/)([\w-]{6,})""")
        return short.find(url)?.groupValues?.get(1)
    }

    private fun extractChannelId(url: String): String? {
        Regex("""/channel/(UC[\w-]{10,})""").find(url)?.let { return it.groupValues[1] }
        Regex("""youtube\.com/(@[\w.-]+)""").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractQueryParam(url: String, key: String): String? = Regex("""[?&]$key=([^&#]+)""").find(url)?.groupValues?.get(1)
    // endregion

    /**
     * Minimal [MediaItem] carrying only what
     * [com.liskovsoft.mediaserviceinterfaces.ContentService.getGroup] reads to
     * open a playlist by id.
     */
    private class PlaylistMediaItem(private val playlistId: String) : MediaItem {
        override fun getPlaylistId(): String = playlistId
        override fun getVideoId(): String? = null
        override fun getReloadPageKey(): String? = null
        override fun getChannelId(): String? = null
        override fun getType(): Int = MediaItem.TYPE_PLAYLIST
        override fun getTitle(): String? = null

        override fun getId(): Int = 0
        override fun getAuthor(): String? = null
        override fun getAudioChannelConfig(): String? = null
        override fun getBackgroundImageUrl(): String? = null
        override fun getBadgeText(): String? = null
        override fun getCardImageUrl(): String? = null
        override fun getClickTrackingParams(): String? = null
        override fun getContentType(): String? = null
        override fun getFeedbackToken(): String? = null
        override fun getFeedbackToken2(): String? = null
        override fun getFeedbackEndpoint(): FeedbackEndpoint? = null
        override fun getParams(): String? = null
        override fun getProductionDate(): String? = null
        override fun getPurchasePrice(): String? = null
        override fun getRentalPrice(): String? = null
        override fun getSearchQuery(): String? = null
        override fun getVideoPreviewUrl(): String? = null
        override fun getSecondTitle(): CharSequence? = null
        override fun getHeight(): Int = 0
        override fun getWidth(): Int = 0
        override fun getPercentWatched(): Int = 0
        override fun getPlaylistIndex(): Int = 0
        override fun getRatingStyle(): Int = 0
        override fun getRatingScore(): Double = 0.0
        override fun getStartTimeSeconds(): Int = 0
        override fun getDurationMs(): Long = 0
        override fun getPublishedDate(): Long = 0
        override fun isMovie(): Boolean = false
        override fun isLive(): Boolean = false
        override fun isUpcoming(): Boolean = false
        override fun isShorts(): Boolean = false
        override fun hasNewContent(): Boolean = false
        override fun hasUploads(): Boolean = false
        override fun equals(other: Any?): Boolean = other is PlaylistMediaItem && other.playlistId == playlistId
        override fun hashCode(): Int = playlistId.hashCode()
    }

    companion object {
        const val ID = "newtube"
    }
}
