/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetItem
import io.github.yewplay.core.designsystem.YpSheetTitle
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.yewplay.YpDialogs

/**
 * YewPlay: "Add to playlist" as a Compose bottom sheet: "New playlist" on top, then the user's
 * playlists; the ones that already have the stream are dimmed and marked with a check.
 */
class PlaylistAppendDialog : PlaylistDialog() {
    private val disposables = CompositeDisposable()
    private val playlists = mutableStateOf<List<PlaylistDuplicatesEntry>?>(null)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val manager = LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()))
        streamEntities?.firstOrNull()?.let { first ->
            disposables.add(
                manager.getPlaylistDuplicates(first.url)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { playlists.value = it }
            )
        }
        return YpDialogs.sheet(requireContext()) { _ ->
            YpSheet {
                AppendContent(
                    playlists = playlists.value,
                    onNewPlaylist = ::openCreatePlaylistDialog,
                    onSelect = { onPlaylistSelected(manager, it) }
                )
            }
        }
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    /** Swaps this sheet for the dialog that creates a playlist (with the same streams). */
    fun openCreatePlaylistDialog() {
        val streams = streamEntities ?: return
        if (!isAdded) {
            return
        }
        val creation = PlaylistCreationDialog.newInstance(streams)
        // the dismiss listener goes with the dialog that is left on screen
        creation.onDismissListener = onDismissListener
        onDismissListener = null
        creation.show(parentFragmentManager, TAG)
        requireDialog().dismiss()
    }

    private fun onPlaylistSelected(manager: LocalPlaylistManager, playlist: PlaylistDuplicatesEntry) {
        val streams = streamEntities ?: return
        val text = if (playlist.timesStreamIsContained > 0) {
            getString(
                R.string.playlist_add_stream_success_duplicate,
                playlist.timesStreamIsContained
            )
        } else {
            getString(R.string.playlist_add_stream_success)
        }
        val toast = Toast.makeText(requireContext().applicationContext, text, Toast.LENGTH_SHORT)
        // not tied to this dialog: it closes right away, the database work must still finish
        manager.appendToPlaylist(playlist.uid, streams)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                toast.show()
                if (playlist.thumbnailStreamId == PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                    manager.changePlaylistThumbnail(playlist.uid, streams[0].uid, false)
                        .subscribe()
                }
            }
        requireDialog().dismiss()
    }

    companion object {
        private val TAG = PlaylistAppendDialog::class.java.canonicalName

        @JvmStatic
        fun newInstance(streamEntities: List<StreamEntity>): PlaylistAppendDialog = PlaylistAppendDialog().apply { this.streamEntities = streamEntities }
    }
}

@Composable
private fun AppendContent(
    playlists: List<PlaylistDuplicatesEntry>?,
    onNewPlaylist: () -> Unit,
    onSelect: (PlaylistDuplicatesEntry) -> Unit
) {
    YpSheetTitle(stringResource(R.string.add_to_playlist))
    YpSheetItem(
        label = stringResource(R.string.create_playlist),
        icon = painterResource(R.drawable.ic_add),
        onClick = onNewPlaylist
    )
    if (playlists.orEmpty().any { it.timesStreamIsContained > 0 }) {
        Text(
            modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 6.dp),
            text = stringResource(R.string.duplicate_in_playlist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
        items(playlists.orEmpty(), key = { it.uid }) { playlist ->
            val contained = playlist.timesStreamIsContained > 0
            YpSheetItem(
                modifier = Modifier.alpha(if (contained) 0.55f else 1f),
                label = playlist.orderingName.orEmpty(),
                onClick = { onSelect(playlist) },
                leading = { PlaylistCover(playlist.thumbnailUrl) },
                supporting = pluralStringResource(
                    R.plurals.yp_video_count,
                    playlist.streamCount.toInt(),
                    playlist.streamCount.toInt()
                ),
                trailing = if (contained) {
                    {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(R.drawable.ic_done),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun PlaylistCover(url: String?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
    }
}
