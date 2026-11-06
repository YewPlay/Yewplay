/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import io.github.yewplay.core.designsystem.YpDialogCard
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialogViewModel.DialogEvent
import org.schabi.newpipe.local.yewplay.YpDialogs

/**
 * YewPlay: the order of the channel groups, as a Compose dialog: each group with buttons to
 * move it up or down, and "OK" to save.
 */
class FeedGroupReorderDialog : DialogFragment() {
    private lateinit var viewModel: FeedGroupReorderDialogViewModel

    private var groups by mutableStateOf<List<FeedGroupEntity>>(emptyList())
    private var orderedIds: List<Long> = emptyList()
    private var processing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderedIds = savedInstanceState?.getLongArray(KEY_ORDER)?.toList() ?: emptyList()
        viewModel = ViewModelProvider(this)[FeedGroupReorderDialogViewModel::class.java]
        viewModel.groupsLiveData.observe(this) { list ->
            if (orderedIds.isEmpty()) {
                orderedIds = list.map { it.uid }
            }
            groups = list.sortedBy { orderedIds.indexOf(it.uid) }
        }
        viewModel.dialogEventLiveData.observe(this) { event ->
            when (event) {
                DialogEvent.ProcessingEvent -> {
                    processing = true
                    isCancelable = false
                }

                DialogEvent.SuccessEvent -> dismiss()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray(KEY_ORDER, orderedIds.toLongArray())
    }

    private fun move(from: Int, to: Int) {
        if (to !in groups.indices) {
            return
        }
        groups = groups.toMutableList().apply { add(to, removeAt(from)) }
        orderedIds = groups.map { it.uid }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = YpDialogs.card(requireContext()) { dismiss ->
        YpDialogCard(
            title = stringResource(R.string.feed_groups_header_title),
            confirmLabel = stringResource(R.string.ok),
            confirmEnabled = !processing,
            onConfirm = { viewModel.updateOrder(orderedIds) },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = dismiss
        ) {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(groups, key = { _, group -> group.uid }) { index, group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(22.dp),
                            painter = painterResource(group.icon.getDrawableRes()),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                            text = group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(enabled = index > 0 && !processing, onClick = { move(index, index - 1) }) {
                            Icon(painter = painterResource(R.drawable.ic_arrow_drop_up), contentDescription = null)
                        }
                        IconButton(
                            enabled = index < groups.lastIndex && !processing,
                            onClick = { move(index, index + 1) }
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_ORDER = "order"
    }
}
