/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import io.github.yewplay.core.designsystem.YpDialogCard
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs

/**
 * YewPlay: "Loading…" while the details of a stream are fetched before downloading it, as a
 * Compose dialog; only "Cancel" closes it.
 */
class LoadingDialog(@param:StringRes private val title: Int) : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = YpDialogs.card(requireContext()) { _ ->
        YpDialogCard(
            title = stringResource(title),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = ::dismiss
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
