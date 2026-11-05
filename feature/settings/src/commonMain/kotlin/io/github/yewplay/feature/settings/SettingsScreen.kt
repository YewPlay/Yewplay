/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpScreenMargin

/** One entry of the settings: icon, title, a line describing it and what it opens. */
data class SettingsEntry(
    val icon: Painter,
    val title: String,
    val description: String?,
    val onClick: () -> Unit
)

/** A named group of entries ("Playback", "Personalization", "Your data"). */
data class SettingsGroup(val title: String?, val entries: List<SettingsEntry>)

/** Texts and icons of the bar, given by the app so that they follow its translations. */
data class SettingsBar(
    val title: String,
    val back: Painter,
    val backDescription: String,
    val search: Painter,
    val searchDescription: String,
    val chevron: Painter
)

/**
 * YewPlay: the settings home (proposal, page 17): back and search on top, a large title, and the
 * named groups, each entry with an icon, its title and one line describing it.
 */
@Composable
fun SettingsScreen(
    bar: SettingsBar,
    groups: List<SettingsGroup>,
    onBack: () -> Unit,
    onSearch: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(painter = bar.back, contentDescription = bar.backDescription)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(painter = bar.search, contentDescription = bar.searchDescription)
                }
            }
        }
        item {
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin),
                text = bar.title,
                style = MaterialTheme.typography.headlineLarge
            )
        }
        groups.forEach { group ->
            if (group.title != null) {
                item {
                    Text(
                        modifier = Modifier.padding(
                            start = YpScreenMargin,
                            end = YpScreenMargin,
                            top = 20.dp,
                            bottom = 4.dp
                        ),
                        text = group.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item { Spacer(Modifier.height(12.dp)) }
            }
            items(group.entries, key = { it.title }) { entry ->
                val last = entry == group.entries.last()
                EntryRow(entry, bar.chevron, divider = !last)
            }
        }
    }
}

@Composable
private fun EntryRow(entry: SettingsEntry, chevron: Painter, divider: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = entry.onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = YpScreenMargin, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.title, style = MaterialTheme.typography.bodyLarge)
                if (entry.description != null) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                modifier = Modifier.size(18.dp),
                painter = chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = YpScreenMargin + 40.dp, end = YpScreenMargin),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
