/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.playlist

import android.app.Application
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.playlist.PlaylistEntry
import io.github.yewplay.feature.playlist.PlaylistUiState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
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
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.ImageStrategy

/** A non-fatal error of a playlist screen, shown as a snackbar. */
data class PlaylistErrorEvent(val errorInfo: ErrorInfo)

/**
 * YewPlay: state of the Compose screen of a service's playlist. Loading, pagination and the
 * bookmark are NewPipe's, as in the previous PlaylistFragment.
 */
class YpPlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val remotePlaylistManager = RemotePlaylistManager(NewPipeDatabase.getInstance(application))
    private val historyRecordManager = HistoryRecordManager(application)
    private val disposables = CompositeDisposable()
    private var loadDisposable: Disposable? = null
    private var bookmarkDisposable: Disposable? = null

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PlaylistErrorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaylistErrorEvent> = _events

    var serviceId: Int = 0
        private set
    var url: String = ""
        private set

    /** The loaded playlist, for share and the uploader. */
    var info: PlaylistInfo? = null
        private set

    /** The error shown in place of the content, for its buttons. */
    var lastError: ErrorInfo? = null
        private set

    private var items: List<StreamInfoItem> = emptyList()
    private var nextPage: Page? = null
    private var bookmark: PlaylistRemoteEntity? = null
    private var initialized = false

    /** Starts the screen once; later calls (e.g. after a rotation) do nothing. */
    fun init(serviceId: Int, url: String, name: String, frontPage: Boolean) {
        if (initialized) {
            return
        }
        initialized = true
        this.serviceId = serviceId
        this.url = url
        _state.update { it.copy(title = name, showBack = !frontPage, bookmarked = false) }
        load(forceLoad = false)
    }

    fun retry() = load(forceLoad = true)

    private fun load(forceLoad: Boolean) {
        lastError = null
        _state.update { it.copy(isLoading = true, error = null) }
        loadDisposable?.dispose()
        loadDisposable = ExtractorHelper.getPlaylistInfo(serviceId, url, forceLoad)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleResult) { error ->
                val errorInfo = ErrorInfo(error, UserAction.REQUESTED_PLAYLIST, url, serviceId, url)
                lastError = errorInfo
                _state.update { it.copy(isLoading = false, error = YpItems.errorUi(app, errorInfo)) }
            }
    }

    private fun handleResult(result: PlaylistInfo) {
        info = result
        items = result.relatedItems
        nextPage = result.nextPage
        if (result.errors.isNotEmpty()) {
            _events.tryEmit(
                PlaylistErrorEvent(
                    ErrorInfo(result.errors, UserAction.REQUESTED_PLAYLIST, result.url, result)
                )
            )
        }
        val uploader = result.uploaderName
        _state.update {
            it.copy(
                title = result.name,
                coverUrls = cover(result),
                subtitle = if (uploader.isNullOrEmpty()) {
                    app.getString(R.string.playlist_no_uploader)
                } else {
                    uploader
                },
                subtitleAvatarUrl = ImageStrategy.choosePreferredImage(result.uploaderAvatars),
                subtitleClickable = !result.uploaderUrl.isNullOrEmpty(),
                description = description(result.description),
                isLoading = false,
                isEmpty = items.isEmpty(),
                hasMore = Page.isValid(nextPage),
                entries = entries(items)
            )
        }
        updateSummary(result)
        loadProgress()
        monitorBookmark(result)
    }

    private fun cover(result: PlaylistInfo): List<String?> {
        val fromItems = items.take(4).map { ImageStrategy.choosePreferredImage(it.thumbnails) }
        return fromItems.ifEmpty { listOf(ImageStrategy.choosePreferredImage(result.thumbnails)) }
    }

    private fun description(description: Description?): String? {
        val content = description?.content()?.takeIf { it.isNotBlank() } ?: return null
        return if (description.type() == Description.Type.HTML) {
            HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            content.trim()
        }
    }

    private fun entries(list: List<StreamInfoItem>): List<PlaylistEntry> = list.mapIndexed { index, item ->
        PlaylistEntry("$index:${item.url}", YpItems.toUi(app, item) as YpStreamItem)
    }

    private fun updateSummary(result: PlaylistInfo) {
        val duration = items.sumOf { it.duration.coerceAtLeast(0) }
        val summary = Localization.concatenateStrings(
            Localization.localizeStreamCount(app, result.streamCount),
            Localization.getDurationString(duration, !Page.isValid(nextPage), true)
        )
        _state.update { it.copy(summary = summary) }
    }

    fun loadMore() {
        val page = nextPage
        val result = info ?: return
        val current = _state.value
        if (!Page.isValid(page) || current.isLoading || current.isLoadingMore) {
            return
        }
        _state.update { it.copy(isLoadingMore = true) }
        loadDisposable?.dispose()
        loadDisposable = ExtractorHelper.getMorePlaylistItems(serviceId, url, page)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { more -> handleMore(result, more) },
                { error ->
                    // keep what was loaded; stop asking for more pages
                    _state.update { it.copy(isLoadingMore = false, hasMore = false) }
                    _events.tryEmit(
                        PlaylistErrorEvent(
                            ErrorInfo(error, UserAction.REQUESTED_PLAYLIST, url, serviceId, url)
                        )
                    )
                }
            )
    }

    private fun handleMore(result: PlaylistInfo, more: ListExtractor.InfoItemsPage<StreamInfoItem>) {
        if (more.errors.isNotEmpty()) {
            _events.tryEmit(
                PlaylistErrorEvent(
                    ErrorInfo(more.errors, UserAction.REQUESTED_PLAYLIST, url, serviceId, url)
                )
            )
        }
        items = items + more.items
        nextPage = more.nextPage
        _state.update {
            it.copy(
                isLoadingMore = false,
                hasMore = Page.isValid(nextPage),
                entries = entries(items)
            )
        }
        updateSummary(result)
        loadProgress()
    }

    private fun loadProgress() {
        disposables.add(
            YpItems.loadProgress(app, historyRecordManager, items)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { progress ->
                        if (progress.isNotEmpty()) {
                            _state.update { state ->
                                state.copy(
                                    entries = state.entries.map { entry ->
                                        val fraction = progress[entry.item.url]
                                        if (fraction == null) {
                                            entry
                                        } else {
                                            entry.copy(item = entry.item.copy(progress = fraction))
                                        }
                                    }
                                )
                            }
                        }
                    },
                    { }
                )
        )
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Bookmark                                                                                  */
    /* ---------------------------------------------------------------------------------------- */

    private fun monitorBookmark(result: PlaylistInfo) {
        bookmarkDisposable?.dispose()
        bookmarkDisposable = remotePlaylistManager.getPlaylist(result)
            .flatMap({ lists -> updateIfChanged(lists, result) }, { lists, _ -> lists })
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { lists ->
                    bookmark = lists.firstOrNull()
                    _state.update { it.copy(bookmarked = bookmark != null) }
                },
                {
                    _events.tryEmit(
                        PlaylistErrorEvent(
                            ErrorInfo(it, UserAction.REQUESTED_BOOKMARK, "Get playlist bookmarks")
                        )
                    )
                }
            )
    }

    /** Keeps a saved copy of the playlist up to date, as before. */
    private fun updateIfChanged(
        lists: List<PlaylistRemoteEntity>,
        result: PlaylistInfo
    ): Flowable<Int> {
        val saved = lists.firstOrNull()
        if (saved == null || saved.isIdenticalTo(result)) {
            return Flowable.just(-1)
        }
        return remotePlaylistManager.onUpdate(saved.uid, result).toFlowable()
    }

    fun toggleBookmark() {
        val result = info ?: return
        val saved = bookmark
        val action = if (saved == null) {
            remotePlaylistManager.onBookmark(result).map { }
        } else {
            remotePlaylistManager.deletePlaylist(saved.uid).map { }
        }
        disposables.add(
            action.observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { },
                    {
                        _events.tryEmit(
                            PlaylistErrorEvent(
                                ErrorInfo(it, UserAction.REQUESTED_BOOKMARK, "Changing playlist bookmark")
                            )
                        )
                    }
                )
        )
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Playback                                                                                  */
    /* ---------------------------------------------------------------------------------------- */

    /**
     * The playlist as a queue that keeps loading the next pages.
     *
     * @param index the video to start from
     * @return the queue, or null before the playlist is loaded
     */
    fun playQueue(index: Int = 0): PlayQueue? {
        val result = info ?: return null
        if (items.isEmpty()) {
            return null
        }
        return PlaylistPlayQueue(result.serviceId, result.url, nextPage, items, index.coerceIn(0, items.size - 1))
    }

    /** The extractor item of a video of the screen. */
    fun streamItemAt(index: Int): StreamInfoItem? = items.getOrNull(index)

    override fun onCleared() {
        loadDisposable?.dispose()
        bookmarkDisposable?.dispose()
        disposables.dispose()
    }
}
