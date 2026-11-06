/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpTextField
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.yewplay.YpDialogs

/** YewPlay: "New playlist": a name and "Create", as a Compose dialog. */
class PlaylistCreationDialog : PlaylistDialog() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = YpDialogs.card(requireContext(), showKeyboard = true) { dismiss ->
        val name = rememberSaveable { mutableStateOf("") }
        YpDialogCard(
            title = stringResource(R.string.create_playlist),
            confirmLabel = stringResource(R.string.create),
            confirmEnabled = name.value.isNotBlank(),
            onConfirm = {
                create(name.value.trim())
                dismiss()
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = dismiss
        ) {
            YpTextField(
                value = name.value,
                onValueChange = { name.value = it },
                placeholder = stringResource(R.string.name),
                autoFocus = true,
                onDone = {
                    if (name.value.isNotBlank()) {
                        create(name.value.trim())
                        dismiss()
                    }
                }
            )
        }
    }

    private fun create(name: String) {
        val streams = streamEntities ?: return
        val context = requireContext().applicationContext
        val toast = Toast.makeText(context, R.string.playlist_creation_success, Toast.LENGTH_SHORT)
        LocalPlaylistManager(NewPipeDatabase.getInstance(context))
            .createPlaylist(name, streams)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { toast.show() }
    }

    companion object {
        @JvmStatic
        fun newInstance(streamEntities: List<StreamEntity>): PlaylistCreationDialog = PlaylistCreationDialog().apply { this.streamEntities = streamEntities }
    }
}
