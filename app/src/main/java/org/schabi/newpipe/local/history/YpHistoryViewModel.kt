/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.playlist.PlaylistEntry
import io.github.yewplay.feature.playlist.PlaylistUiState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil

/** One-off things the history screen asks of the fragment. */
sealed interface HistoryEvent {
    data class ShowError(val errorInfo: ErrorInfo) : HistoryEvent

    data object Deleted : HistoryEvent
}

/**
 * YewPlay: state of the Compose history: what was watched, last played first or most played
 * first, read from NewPipe's HistoryRecordManager as before.
 */
class YpHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    val recordManager = HistoryRecordManager(application)
    val disposables = CompositeDisposable()
    private var subscription: Disposable? = null

    private val _state = MutableStateFlow(PlaylistUiState(numbered = false))
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HistoryEvent> = _events

    /** Most played first, instead of last played first. */
    var mostPlayed = false
        private set

    private var all: List<StreamStatisticsEntry> = emptyList()
    private var entries: List<StreamStatisticsEntry> = emptyList()
    private var initialized = false

    /** Starts the screen once; later calls (e.g. after a rotation) do nothing. */
    fun init(frontPage: Boolean) {
        if (initialized) {
            return
        }
        initialized = true
        _state.update {
            it.copy(
                title = app.getString(R.string.title_activity_history),
                showBack = !frontPage,
                numbered = false
            )
        }
        subscription = recordManager.getStreamStatistics()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { list ->
                    all = list
                    publish()
                },
                { error ->
                    val info = ErrorInfo(error, UserAction.SOMETHING_ELSE, "History Statistics")
                    _state.update { it.copy(isLoading = false, error = YpItems.errorUi(app, info)) }
                }
            )
    }

    fun toggleSort() {
        mostPlayed = !mostPlayed
        publish()
    }

    private fun publish() {
        entries = if (mostPlayed) {
            all.sortedByDescending { it.watchCount }
        } else {
            all.sortedByDescending { it.latestAccessDate }
        }
        _state.update {
            it.copy(
                isLoading = false,
                isEmpty = entries.isEmpty(),
                subtitle = app.getString(if (mostPlayed) R.string.title_most_played else R.string.title_last_played),
                summary = Localization.localizeStreamCount(app, entries.size.toLong()),
                coverUrls = entries.take(4).map { entry -> entry.streamEntity.thumbnailUrl },
                entries = entries.map(::toEntry)
            )
        }
    }

    private fun toEntry(entry: StreamStatisticsEntry): PlaylistEntry {
        val stream = entry.streamEntity
        val live = StreamTypeUtil.isLiveStream(stream.streamType)
        val count = entry.watchCount.toInt()
        val watched = app.resources.getQuantityString(R.plurals.yp_history_watch_count, count, count)
        return PlaylistEntry(
            key = entry.streamId.toString(),
            item = YpStreamItem(
                serviceId = stream.serviceId,
                url = stream.url,
                title = stream.title,
                uploaderName = stream.uploader,
                thumbnailUrl = stream.thumbnailUrl,
                durationText = when {
                    stream.duration > 0 -> Localization.getDurationString(stream.duration)
                    live -> app.getString(R.string.duration_live)
                    else -> null
                },
                isLive = live,
                details = Localization.concatenateStrings(
                    watched,
                    Localization.relativeTime(entry.latestAccessDate)
                ),
                progress = if (stream.duration > 0) {
                    (entry.progressMillis / 1000f / stream.duration).coerceIn(0f, 1f)
                } else {
                    0f
                }
            )
        )
    }

    /** Removes a video from the history and its saved position, as before. */
    fun delete(index: Int) {
        val entry = entries.getOrNull(index) ?: return
        disposables.add(
            recordManager.deleteStreamHistoryAndState(entry.streamId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _events.tryEmit(HistoryEvent.Deleted) },
                    {
                        _events.tryEmit(
                            HistoryEvent.ShowError(
                                ErrorInfo(it, UserAction.DELETE_FROM_HISTORY, "Deleting item")
                            )
                        )
                    }
                )
        )
    }

    /**
     * The history as a queue, in the order shown.
     *
     * @param index the video to start from
     * @return the queue, or null when the history is empty
     */
    fun playQueue(index: Int = 0): PlayQueue? {
        val items = entries.mapNotNull { runCatching { it.toStreamInfoItem() }.getOrNull() }
        if (items.isEmpty()) {
            return null
        }
        return SinglePlayQueue(items, index.coerceIn(0, items.size - 1))
    }

    /** The video at a position, as an extractor item (for the item menu). */
    fun streamItemAt(index: Int): StreamInfoItem? = entries.getOrNull(index)?.let {
        runCatching { it.toStreamInfoItem() }.getOrNull()
    }

    override fun onCleared() {
        subscription?.dispose()
        disposables.dispose()
    }
}
