/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.playlist.PlaylistEntry
import io.github.yewplay.feature.playlist.PlaylistUiState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil

/** One-off things the local playlist screen asks of the fragment. */
sealed interface LocalPlaylistEvent {
    /** A non-fatal error, shown as a snackbar. */
    data class ShowError(val errorInfo: ErrorInfo) : LocalPlaylistEvent

    /** The chosen video is now the playlist's thumbnail. */
    data object ThumbnailChanged : LocalPlaylistEvent

    /** The playlist as text, ready to share. */
    data class Share(val content: String) : LocalPlaylistEvent
}

/**
 * YewPlay: state of the Compose screen of a local playlist. Order, removal, rename and
 * thumbnail are saved with NewPipe's LocalPlaylistManager, as in the previous
 * LocalPlaylistFragment.
 */
class YpLocalPlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val playlistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(application))
    private val disposables = CompositeDisposable()
    private var streamsDisposable: Disposable? = null

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<LocalPlaylistEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LocalPlaylistEvent> = _events

    var playlistId: Long = 0
        private set
    val name: String get() = _state.value.title

    private var entries: MutableList<PlaylistStreamEntry> = mutableListOf()

    /** Edits not saved yet; database updates are ignored meanwhile, as before. */
    private var modified = false
    private var initialized = false

    /** Starts the screen once; later calls (e.g. after a rotation) do nothing. */
    fun init(playlistId: Long, name: String, frontPage: Boolean) {
        if (initialized) {
            return
        }
        initialized = true
        this.playlistId = playlistId
        _state.update {
            it.copy(
                title = name,
                subtitle = app.getString(R.string.yp_local_playlist),
                canEdit = true,
                showBack = !frontPage
            )
        }
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        streamsDisposable?.dispose()
        streamsDisposable = playlistManager.getPlaylistStreams(playlistId)
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { streams ->
                    if (!modified) {
                        entries = streams.toMutableList()
                        publish()
                    }
                },
                { error ->
                    val info = ErrorInfo(error, UserAction.REQUESTED_BOOKMARK, "Loading local playlist")
                    _state.update { it.copy(isLoading = false, error = YpItems.errorUi(app, info)) }
                }
            )
    }

    private fun publish() {
        val duration = entries.sumOf { it.streamEntity.duration.coerceAtLeast(0) }
        _state.update {
            it.copy(
                isLoading = false,
                isEmpty = entries.isEmpty(),
                coverUrls = entries.take(4).map { entry -> entry.streamEntity.thumbnailUrl },
                summary = Localization.concatenateStrings(
                    Localization.localizeStreamCount(app, entries.size.toLong()),
                    Localization.getDurationString(duration, true, true)
                ),
                entries = entries.map(::toEntry),
                editing = it.editing && entries.isNotEmpty()
            )
        }
    }

    private fun toEntry(entry: PlaylistStreamEntry): PlaylistEntry {
        val stream = entry.streamEntity
        val live = StreamTypeUtil.isLiveStream(stream.streamType)
        val progress = if (stream.duration > 0) {
            (entry.progressMillis / 1000f / stream.duration).coerceIn(0f, 1f)
        } else {
            0f
        }
        return PlaylistEntry(
            key = "${entry.streamId}:${entry.joinIndex}",
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
                details = "",
                progress = progress
            )
        )
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Editing                                                                                   */
    /* ---------------------------------------------------------------------------------------- */

    fun toggleEditing() {
        if (_state.value.editing) {
            saveImmediate()
        }
        _state.update { it.copy(editing = !it.editing && entries.isNotEmpty()) }
    }

    fun move(from: Int, to: Int) {
        if (from !in entries.indices || to !in entries.indices || from == to) {
            return
        }
        entries.add(to, entries.removeAt(from))
        modified = true
        publish()
    }

    fun remove(index: Int) {
        val removed = entries.getOrNull(index) ?: return
        entries.removeAt(index)
        modified = true
        publish()
        // the removed video was the thumbnail: use the first one, unless it was chosen by hand
        val firstStreamId = entries.firstOrNull()?.streamEntity?.uid ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID
        disposables.add(
            Completable.fromAction {
                if (playlistManager.getPlaylistThumbnailStreamId(playlistId) == removed.streamId &&
                    !playlistManager.getIsPlaylistThumbnailPermanent(playlistId)
                ) {
                    playlistManager.changePlaylistThumbnail(playlistId, firstStreamId, false)
                        .blockingGet()
                }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ }, { emitError(it, "Changing playlist thumbnail") })
        )
        saveImmediate()
    }

    /** Saves the order and removals, if anything changed. */
    fun saveImmediate() {
        if (!modified) {
            return
        }
        val streamIds = entries.map { it.streamId }
        disposables.add(
            playlistManager.updateJoin(playlistId, streamIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { modified = false },
                    { emitError(it, "Saving playlist") }
                )
        )
    }

    fun rename(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return
        }
        _state.update { it.copy(title = trimmed) }
        disposables.add(
            playlistManager.renamePlaylist(playlistId, trimmed)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ }, { emitError(it, "Renaming playlist") })
        )
    }

    fun setThumbnail(index: Int) {
        val entry = entries.getOrNull(index) ?: return
        disposables.add(
            playlistManager.changePlaylistThumbnail(playlistId, entry.streamEntity.uid, true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _events.tryEmit(LocalPlaylistEvent.ThumbnailChanged) },
                    { emitError(it, "Changing playlist thumbnail") }
                )
        )
    }

    fun share(mode: PlayListShareMode) {
        disposables.add(
            playlistManager.getPlaylistStreams(playlistId)
                .firstOrError()
                .map { export(mode, it, app) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { text ->
                        val content = if (mode == PlayListShareMode.WITH_TITLES) {
                            app.getString(R.string.share_playlist_content_details, name, text)
                        } else {
                            text
                        }
                        _events.tryEmit(LocalPlaylistEvent.Share(content))
                    },
                    { emitError(it, "Sharing playlist") }
                )
        )
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Playback                                                                                  */
    /* ---------------------------------------------------------------------------------------- */

    /**
     * The playlist as a queue.
     *
     * @param index the video to start from
     * @return the queue, or null when the playlist is empty
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

    private fun emitError(error: Throwable, request: String) {
        _events.tryEmit(
            LocalPlaylistEvent.ShowError(ErrorInfo(error, UserAction.REQUESTED_BOOKMARK, request))
        )
    }

    override fun onCleared() {
        if (modified) {
            // not tied to the screen: the order must be saved even when it closes
            playlistManager.updateJoin(playlistId, entries.map { it.streamId }).subscribe({ }, { })
        }
        streamsDisposable?.dispose()
        disposables.dispose()
    }
}
