/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpTextField
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialogViewModel.DialogEvent
import org.schabi.newpipe.local.yewplay.YpDialogs

/**
 * YewPlay: create or edit a group of channels, as a Compose dialog with four steps: the name
 * and icon, the icon picker, the channel picker (with search and "only ungrouped") and the
 * confirmation to delete the group.
 */
class FeedGroupDialog : DialogFragment() {
    private enum class Screen { INITIAL, ICON, CHANNELS, DELETE }

    private lateinit var viewModel: FeedGroupDialogViewModel
    private var groupId: Long = NO_GROUP_SELECTED

    private var screen by mutableStateOf(Screen.INITIAL)
    private var name by mutableStateOf("")
    private var nameError by mutableStateOf(false)
    private var selectedIcon by mutableStateOf<FeedGroupIcon?>(null)
    private var selected by mutableStateOf<Set<Long>>(emptySet())
    private var selectionChanged = false
    private var query by mutableStateOf("")
    private var onlyUngrouped by mutableStateOf(false)
    private var processing by mutableStateOf(false)
    private var groupLoaded = false
    private var subscriptions by mutableStateOf<List<SubscriptionEntity>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(KEY_GROUP_ID, NO_GROUP_SELECTED) ?: NO_GROUP_SELECTED
        savedInstanceState?.let { state ->
            screen = Screen.entries[state.getInt(KEY_SCREEN, 0)]
            name = state.getString(KEY_NAME, "")
            selectedIcon = state.getInt(KEY_ICON, -1).takeIf { it >= 0 }?.let { FeedGroupIcon.entries[it] }
            selected = state.getLongArray(KEY_SELECTED)?.toSet() ?: emptySet()
            selectionChanged = state.getBoolean(KEY_SELECTION_CHANGED)
            query = state.getString(KEY_QUERY, "")
            onlyUngrouped = state.getBoolean(KEY_ONLY_UNGROUPED)
            groupLoaded = true
        }
        viewModel = ViewModelProvider(
            this,
            FeedGroupDialogViewModel.getFactory(requireContext(), groupId, query, onlyUngrouped)
        )[FeedGroupDialogViewModel::class.java]
        viewModel.groupLiveData.observe(this, ::onGroupLoaded)
        viewModel.subscriptionsLiveData.observe(this) { (channels, inGroup) ->
            subscriptions = channels
            if (!selectionChanged) {
                selected = selected + inGroup
            }
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
        outState.putInt(KEY_SCREEN, screen.ordinal)
        outState.putString(KEY_NAME, name)
        outState.putInt(KEY_ICON, selectedIcon?.ordinal ?: -1)
        outState.putLongArray(KEY_SELECTED, selected.toLongArray())
        outState.putBoolean(KEY_SELECTION_CHANGED, selectionChanged)
        outState.putString(KEY_QUERY, query)
        outState.putBoolean(KEY_ONLY_UNGROUPED, onlyUngrouped)
    }

    private fun onGroupLoaded(group: FeedGroupEntity?) {
        if (groupLoaded || group == null) {
            return
        }
        groupLoaded = true
        name = group.name
        selectedIcon = group.icon
        groupSortOrder = group.sortOrder
    }

    private var groupSortOrder: Long = -1

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = YpDialogs.card(requireContext(), showKeyboard = groupId == NO_GROUP_SELECTED) { dismiss ->
        BackHandler(enabled = screen != Screen.INITIAL) { screen = Screen.INITIAL }
        when (screen) {
            Screen.INITIAL -> InitialScreen(subscriptionsCount = selected.size, onDismiss = dismiss)
            Screen.ICON -> IconScreen()
            Screen.CHANNELS -> ChannelsScreen(subscriptions)
            Screen.DELETE -> DeleteScreen()
        }
    }

    @Composable
    private fun InitialScreen(subscriptionsCount: Int, onDismiss: () -> Unit) {
        val editing = groupId != NO_GROUP_SELECTED
        YpDialogCard(
            title = stringResource(if (editing) R.string.yp_feed_group_edit else R.string.yp_feed_group_new),
            confirmLabel = stringResource(if (editing) R.string.ok else R.string.create),
            confirmEnabled = !processing,
            onConfirm = ::save,
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = onDismiss
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { screen = Screen.ICON },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(26.dp),
                        painter = painterResource((selectedIcon ?: FeedGroupIcon.ALL).getDrawableRes()),
                        contentDescription = stringResource(R.string.yp_feed_group_icon)
                    )
                }
                Spacer(Modifier.width(12.dp))
                YpTextField(
                    modifier = Modifier.weight(1f),
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) {
                            nameError = false
                        }
                    },
                    placeholder = stringResource(R.string.name),
                    autoFocus = !editing,
                    horizontalPadding = 0.dp
                )
            }
            if (nameError) {
                Text(
                    modifier = Modifier.padding(start = 88.dp, top = 4.dp),
                    text = stringResource(R.string.feed_group_dialog_empty_name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { screen = Screen.CHANNELS }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feed_group_dialog_select_subscriptions),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.feed_group_dialog_selection_count,
                            subscriptionsCount,
                            subscriptionsCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(R.drawable.yp_ic_caret_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (editing) {
                Box(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                    YpPillButton(
                        label = stringResource(R.string.delete),
                        primary = false,
                        enabled = !processing,
                        onClick = { screen = Screen.DELETE }
                    )
                }
            }
        }
    }

    @Composable
    private fun IconScreen() {
        YpDialogCard(
            title = stringResource(R.string.yp_feed_group_icon),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { screen = Screen.INITIAL }
        ) {
            LazyVerticalGrid(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .padding(horizontal = 16.dp),
                columns = GridCells.Adaptive(48.dp)
            ) {
                items(FeedGroupIcon.entries) { icon ->
                    val isSelected = icon == (selectedIcon ?: FeedGroupIcon.ALL)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .clickable {
                                selectedIcon = icon
                                screen = Screen.INITIAL
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = Modifier.size(22.dp),
                            painter = painterResource(icon.getDrawableRes()),
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ChannelsScreen(subscriptions: List<SubscriptionEntity>?) {
        YpDialogCard(
            title = stringResource(R.string.tab_subscriptions),
            message = pluralStringResource(
                R.plurals.feed_group_dialog_selection_count,
                selected.size,
                selected.size
            ),
            confirmLabel = stringResource(R.string.ok),
            onConfirm = { screen = Screen.INITIAL }
        ) {
            YpTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.filterSubscriptionsBy(it)
                },
                placeholder = stringResource(R.string.search)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onlyUngrouped = !onlyUngrouped
                        viewModel.toggleShowOnlyUngrouped(onlyUngrouped)
                    }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.feed_group_show_only_ungrouped_subscriptions),
                    style = MaterialTheme.typography.bodyMedium
                )
                CheckMark(onlyUngrouped)
            }
            if (subscriptions != null && subscriptions.isEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    text = stringResource(R.string.import_subscriptions_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                items(subscriptions.orEmpty(), key = { it.uid }) { channel ->
                    val isSelected = channel.uid in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectionChanged = true
                                selected = if (isSelected) selected - channel.uid else selected + channel.uid
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        YpAvatar(url = channel.avatarUrl, size = 36)
                        Text(
                            modifier = Modifier.weight(1f),
                            text = channel.name.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        CheckMark(isSelected)
                    }
                }
            }
        }
    }

    @Composable
    private fun DeleteScreen() {
        YpDialogCard(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.feed_group_dialog_delete_message),
            confirmLabel = stringResource(R.string.delete),
            confirmEnabled = !processing,
            onConfirm = { viewModel.deleteGroup() },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { screen = Screen.INITIAL }
        )
    }

    @Composable
    private fun CheckMark(checked: Boolean) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    modifier = Modifier.size(14.dp),
                    painter = painterResource(R.drawable.ic_done),
                    contentDescription = null,
                    tint = colors.onPrimary
                )
            }
        }
    }

    private fun save() {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            nameError = true
            return
        }
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.feed_group_dialog_empty_selection, Toast.LENGTH_SHORT).show()
            return
        }
        val icon = selectedIcon ?: FeedGroupIcon.ALL
        if (groupId == NO_GROUP_SELECTED) {
            viewModel.createGroup(trimmed, icon, selected)
        } else {
            viewModel.updateGroup(trimmed, icon, selected, groupSortOrder)
        }
    }

    companion object {
        private const val KEY_GROUP_ID = "KEY_GROUP_ID"
        private const val NO_GROUP_SELECTED = -1L
        private const val KEY_SCREEN = "screen"
        private const val KEY_NAME = "name"
        private const val KEY_ICON = "icon"
        private const val KEY_SELECTED = "selected"
        private const val KEY_SELECTION_CHANGED = "selection_changed"
        private const val KEY_QUERY = "query"
        private const val KEY_ONLY_UNGROUPED = "only_ungrouped"

        fun newInstance(groupId: Long = NO_GROUP_SELECTED): FeedGroupDialog = FeedGroupDialog().apply {
            arguments = bundleOf(KEY_GROUP_ID to groupId)
        }
    }
}
