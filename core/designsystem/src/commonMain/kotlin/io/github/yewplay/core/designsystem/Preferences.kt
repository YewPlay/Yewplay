/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/*
 * YewPlay: the rows of the settings screens (proposal, p. 17 and 21: "groups with a grey title
 * and items with a one-line description").
 */

/** The grey title of a group of settings. */
@Composable
fun YpPreferenceCategory(title: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = YpScreenMargin, end = YpScreenMargin, top = 22.dp, bottom = 6.dp),
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * One setting: optional icon, the title, the description (or the chosen value) below it, and
 * optional [trailing] content (a switch, a chevron...).
 */
@Composable
fun YpPreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.surfaceContainerHighest else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = YpScreenMargin, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) {
            Icon(modifier = Modifier.size(24.dp), painter = icon, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrEmpty()) {
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** A setting turned on or off by a switch; tapping the whole row toggles it. */
@Composable
fun YpSwitchPreferenceRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false
) {
    YpPreferenceRow(
        modifier = modifier,
        title = title,
        summary = summary,
        enabled = enabled,
        highlighted = highlighted,
        onClick = { onCheckedChange(!checked) },
        trailing = { YpSwitch(checked = checked, onCheckedChange = { if (enabled) onCheckedChange(it) }) }
    )
}

/** A choice in a list of options: a round mark, filled when selected (single choice). */
@Composable
fun YpRadioRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    YpChoiceRow(label, selected, CircleShape, onClick, modifier)
}

/** A choice in a list of options: a square mark, filled when checked (several choices). */
@Composable
fun YpCheckRow(label: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    YpChoiceRow(label, checked, RoundedCornerShape(6.dp), onClick, modifier)
}

@Composable
private fun YpChoiceRow(
    label: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = YpScreenMargin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(shape)
                .background(if (selected) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(shape)
                        .background(colors.onPrimary)
                )
            }
        }
        Text(modifier = Modifier.weight(1f), text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
