/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.bookmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.yewplay.feature.playlist.PlaylistsItem
import io.github.yewplay.feature.playlist.PlaylistsUiState
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
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.util.Localization

/**
 * YewPlay: state of the Compose list of saved playlists (local and bookmarked), with the order
 * and removals saved by NewPipe's playlist managers, as in the previous BookmarkFragment.
 */
class YpBookmarksViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val database = NewPipeDatabase.getInstance(application)
    val localManager = LocalPlaylistManager(database)
    private val remoteManager = RemotePlaylistManager(database)
    private val disposables = CompositeDisposable()
    private var subscription: Disposable? = null

    private val _state = MutableStateFlow(PlaylistsUiState())
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()
    private val _errors = MutableSharedFlow<ErrorInfo>(extraBufferCapacity = 8)

    /** Non-fatal errors, shown as snackbars. */
    val errors: SharedFlow<ErrorInfo> = _errors

    private var items: MutableList<PlaylistLocalItem> = mutableListOf()
    private val deletedLocal = mutableListOf<Long>()
    private val deletedRemote = mutableListOf<Long>()

    /** Edits not saved yet; database updates are ignored meanwhile, as before. */
    private var modified = false
    private var initialized = false

    /** Starts the screen once; later calls (e.g. after a rotation) do nothing. */
    fun init(frontPage: Boolean) {
        if (initialized) {
            return
        }
        initialized = true
        _state.update { it.copy(title = app.getString(R.string.yp_library_saved_playlists), showBack = !frontPage) }
        subscription = MergedPlaylistManager.getMergedOrderedPlaylists(localManager, remoteManager)
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { list ->
                    if (!modified) {
                        items = list.toMutableList()
                        publish()
                    }
                },
                { emitError(it, "Loading playlists") }
            )
    }

    private fun publish() {
        _state.update {
            it.copy(
                isLoading = false,
                items = items.map(::toUi),
                editing = it.editing && items.isNotEmpty()
            )
        }
    }

    private fun toUi(item: PlaylistLocalItem): PlaylistsItem = when (item) {
        is PlaylistMetadataEntry -> PlaylistsItem(
            key = "local:${item.uid}",
            name = item.orderingName.orEmpty(),
            thumbnailUrl = item.thumbnailUrl,
            detail = Localization.concatenateStrings(
                Localization.localizeStreamCount(app, item.streamCount),
                app.getString(R.string.yp_local_playlist)
            )
        )

        is PlaylistRemoteEntity -> PlaylistsItem(
            key = "remote:${item.uid}",
            name = item.orderingName.orEmpty(),
            thumbnailUrl = item.thumbnailUrl,
            detail = Localization.concatenateStrings(
                item.streamCount?.let { Localization.localizeStreamCount(app, it) }.orEmpty(),
                item.uploader.orEmpty()
            )
        )

        else -> PlaylistsItem("other:${item.uid}", item.orderingName.orEmpty(), item.thumbnailUrl, "")
    }

    fun itemAt(index: Int): PlaylistLocalItem? = items.getOrNull(index)

    /** Position of a playlist in the list, or -1 (by identity: uids of local and saved ones overlap). */
    fun indexOf(item: PlaylistLocalItem): Int = items.indexOfFirst { it === item }

    fun toggleEditing() {
        if (_state.value.editing) {
            saveImmediate()
        }
        _state.update { it.copy(editing = !it.editing && items.isNotEmpty()) }
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) {
            return
        }
        items.add(to, items.removeAt(from))
        modified = true
        publish()
    }

    fun remove(index: Int) {
        val item = items.getOrNull(index) ?: return
        items.removeAt(index)
        when (item) {
            is PlaylistMetadataEntry -> deletedLocal.add(item.uid)
            is PlaylistRemoteEntity -> deletedRemote.add(item.uid)
        }
        modified = true
        publish()
        saveImmediate()
    }

    /** Saves the order (display index) and the removals, as before. */
    fun saveImmediate() {
        if (!modified) {
            return
        }
        val localUpdates = mutableListOf<PlaylistMetadataEntry>()
        val remoteUpdates = mutableListOf<PlaylistRemoteEntity>()
        items.forEachIndexed { index, item ->
            if (item is PlaylistMetadataEntry && item.displayIndex != index.toLong()) {
                item.displayIndex = index.toLong()
                localUpdates.add(item)
            } else if (item is PlaylistRemoteEntity && item.displayIndex != index.toLong()) {
                item.displayIndex = index.toLong()
                remoteUpdates.add(item)
            }
        }
        val localDeletes = deletedLocal.toList()
        val remoteDeletes = deletedRemote.toList()
        deletedLocal.clear()
        deletedRemote.clear()
        disposables.add(
            localManager.updatePlaylists(localUpdates, localDeletes)
                .mergeWith(remoteManager.updatePlaylists(remoteUpdates, remoteDeletes))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ modified = false }, { emitError(it, "Saving playlist") })
        )
    }

    fun rename(item: PlaylistMetadataEntry, name: String) {
        disposables.add(
            localManager.renamePlaylist(item.uid, name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ }, { emitError(it, "Changing playlist name") })
        )
    }

    /** Back to the automatic thumbnail, when one was chosen by hand. */
    fun unsetThumbnail(item: PlaylistMetadataEntry) {
        disposables.add(
            Completable.fromAction {
                val streamId = localManager.getAutomaticPlaylistThumbnailStreamId(item.uid)
                localManager.changePlaylistThumbnail(item.uid, streamId, false).blockingGet()
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ }, { emitError(it, "Changing playlist thumbnail") })
        )
    }

    private fun emitError(error: Throwable, request: String) {
        _errors.tryEmit(ErrorInfo(error, UserAction.REQUESTED_BOOKMARK, request))
    }

    override fun onCleared() {
        saveImmediate()
        subscription?.dispose()
    }
}
