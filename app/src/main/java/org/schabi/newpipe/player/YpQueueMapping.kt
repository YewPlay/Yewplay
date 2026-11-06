/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * Maps a [PlayQueue] to the [YpQueueEntry] list shown by every queue UI (the listen screen
 * and the video player panel), so the mapping exists in a single place.
 *
 * @param keyFor provides a stable key for each item, kept by the caller
 */
fun mapPlayQueueEntries(
    queue: PlayQueue,
    keyFor: (PlayQueueItem) -> String
): List<YpQueueEntry> {
    return queue.streams.map { item ->
        YpQueueEntry(
            key = keyFor(item),
            serviceId = item.serviceId,
            url = item.url,
            title = item.title,
            details = Localization.concatenateStrings(
                item.uploader,
                ServiceHelper.getNameOfServiceById(item.serviceId)
            ),
            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            durationText = if (item.duration > 0) {
                Localization.getDurationString(item.duration)
            } else {
                null
            },
            isLive = item.streamType == StreamType.LIVE_STREAM ||
                item.streamType == StreamType.AUDIO_LIVE_STREAM
        )
    }
}

/** "16:20 / 48:30": the queue progress over its total duration, like the legacy screens. */
fun playQueueTimeText(queue: PlayQueue, currentProgressMillis: Int): String? {
    val streams = queue.streams
    val currentIndex = queue.index
    if (streams.isEmpty() || currentIndex < 0 || currentIndex >= streams.size) {
        return null
    }
    val before = streams.subList(0, currentIndex).sumOf(PlayQueueItem::getDuration)
    val total = streams.sumOf(PlayQueueItem::getDuration)
    val queueProgress = before + currentProgressMillis / 1000L
    return Localization.getDurationString(queueProgress) + " / " +
        Localization.getDurationString(total)
}
