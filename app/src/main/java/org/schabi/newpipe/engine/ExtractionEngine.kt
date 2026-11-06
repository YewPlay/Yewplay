/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine

import org.schabi.newpipe.engine.model.YpChannelInfo
import org.schabi.newpipe.engine.model.YpPlaylistInfo
import org.schabi.newpipe.engine.model.YpSearchPage
import org.schabi.newpipe.engine.model.YpVideoInfo

/** Capabilities an engine may declare. UI only shows what the active engine supports. */
enum class EngineCapability {
    SEARCH_VIDEOS,
    SEARCH_CHANNELS,
    SEARCH_PLAYLISTS,
    SEARCH_MUSIC,
    SEARCH_SUGGESTIONS,
    STREAM_EXTRACTION,
    ADAPTIVE_STREAMS,
    SUBTITLES,
    CHANNEL_INFO,
    PLAYLIST_INFO,
    COMMENTS,
    PAGINATION,
    TRENDING
}

/** Runtime health of an engine, surfaced in the engine settings screen. */
enum class EngineState {
    AVAILABLE,
    UNAVAILABLE,
    ERROR
}

/** Stable identity + static metadata of an engine implementation. */
data class EngineDescriptor(
    /** Stable machine id persisted in preferences (e.g. "newpipe", "newtube", "flow"). */
    val id: String,
    /** Proper noun, not translated. */
    val displayName: String,
    /** Short human description of what backs this engine. */
    val summary: String,
    val version: String,
    val capabilities: Set<EngineCapability>
)

/** Failure raised by an engine adapter; [recoverable] feeds the fallback policy. */
class EngineException(
    message: String,
    cause: Throwable? = null,
    val engineId: String,
    val recoverable: Boolean = true
) : Exception(message, cause)

enum class SearchFilter { ALL, VIDEOS, CHANNELS, PLAYLISTS, MUSIC }

/**
 * YewPlay MultiEngine — the single contract every extraction engine implements.
 *
 * Implementations live under `engine/impl/` and are registered in [EngineManager].
 * Callers never depend on a concrete engine; all operations are suspending and
 * engine-internal threading is encapsulated by the adapter.
 *
 * Methods only need to work when the engine declares the matching
 * [EngineCapability]; otherwise they may throw [EngineException] with
 * `recoverable = false`.
 */
interface ExtractionEngine {
    val descriptor: EngineDescriptor

    /** Cheap local availability probe (no network). */
    fun checkAvailability(): EngineState

    suspend fun search(query: String, filter: SearchFilter, pageKey: String?): YpSearchPage

    suspend fun suggestions(query: String): List<String>

    suspend fun getVideoInfo(url: String): YpVideoInfo

    suspend fun getChannelInfo(url: String, pageKey: String?): YpChannelInfo

    suspend fun getPlaylistInfo(url: String, pageKey: String?): YpPlaylistInfo

    /** Release engine resources. Called when the engine is deactivated. */
    fun shutdown() {}
}
