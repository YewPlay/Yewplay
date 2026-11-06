/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.dialog

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Queue
import java.util.function.Consumer
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.util.SavedState as NewPipeSavedState
import org.schabi.newpipe.util.StateSaver

/**
 * Base of the dialogs that put streams in a local playlist: keeps the streams across
 * configuration changes. The look of each dialog is drawn in Compose by its subclass.
 */
abstract class PlaylistDialog : DialogFragment(), StateSaver.WriteRead {
    var onDismissListener: DialogInterface.OnDismissListener? = null

    var streamEntities: List<StreamEntity>? = null

    private var savedState: NewPipeSavedState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedState = StateSaver.tryToRestore(savedInstanceState, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        StateSaver.onDestroy(savedState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    override fun generateSuffix(): String = "." + (streamEntities?.size ?: 0) + ".list"

    override fun writeTo(objectsToSave: Queue<Any>) {
        objectsToSave.add(streamEntities)
    }

    @Suppress("UNCHECKED_CAST")
    override fun readFrom(savedObjects: Queue<Any>) {
        streamEntities = savedObjects.poll() as List<StreamEntity>?
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activity?.let {
            savedState = StateSaver.tryToSave(it.isChangingConfigurations, savedState, outState, this)
        }
    }

    companion object {
        /**
         * Creates the dialog to append the streams to a playlist when there is one, or the
         * dialog to create a playlist otherwise, and hands it to [onExec].
         */
        @JvmStatic
        fun createCorrespondingDialog(
            context: Context?,
            streamEntities: List<StreamEntity>,
            onExec: Consumer<PlaylistDialog>
        ): Disposable = LocalPlaylistManager(NewPipeDatabase.getInstance(context!!))
            .hasPlaylists()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { hasPlaylists ->
                onExec.accept(
                    if (hasPlaylists) {
                        PlaylistAppendDialog.newInstance(streamEntities)
                    } else {
                        PlaylistCreationDialog.newInstance(streamEntities)
                    }
                )
            }

        /** The same for every stream of the player's queue; nothing when the queue is empty. */
        @JvmStatic
        fun showForPlayQueue(player: Player, fragmentManager: FragmentManager): Disposable {
            val streamEntities = player.playQueue?.streams.orEmpty().map { StreamEntity(it) }
            if (streamEntities.isEmpty()) {
                return Disposable.empty()
            }
            return createCorrespondingDialog(player.context, streamEntities) { dialog ->
                dialog.show(fragmentManager, "PlaylistDialog")
            }
        }
    }
}
