/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.core

import org.schabi.newpipe.engine.EngineCapability
import org.schabi.newpipe.engine.EngineManager
import org.schabi.newpipe.engine.model.YpVideoInfo

/**
 * Resolves a video page URL into playable [YpVideoInfo] through the engine
 * layer. All engine routing — active engine, opt-in bounded fallback — stays
 * inside [EngineManager]; this class is only the playback-facing entry point.
 */
class EngineStreamResolver(
    private val engineManager: EngineManager = EngineManager.get()
) {
    data class ResolvedVideo(
        val info: YpVideoInfo,
        /** Engine that actually served the resolution (fallback-aware). */
        val servedByEngineId: String,
        val fallbackUsed: Boolean
    )

    suspend fun resolve(url: String): ResolvedVideo {
        val result = engineManager.execute(EngineCapability.STREAM_EXTRACTION) { engine ->
            engine.getVideoInfo(url)
        }
        return ResolvedVideo(
            info = result.value,
            servedByEngineId = result.servedBy.id,
            fallbackUsed = result.fallbackUsed
        )
    }
}
