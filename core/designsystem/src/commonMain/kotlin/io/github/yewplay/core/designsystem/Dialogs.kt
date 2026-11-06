/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val DialogShape = RoundedCornerShape(24.dp)

/**
 * YewPlay: a bottom sheet: rounded top corners, a drag handle and room for the navigation bar.
 * The host (a sheet dialog) gives it a transparent window, so this draws the whole surface.
 */
@Composable
fun YpSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        content()
    }
}

/** The top of a sheet about one item: optional leading image, title and a line below. */
@Composable
fun YpSheetHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YpScreenMargin, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** A sheet title on its own ("Add to playlist", ...). */
@Composable
fun YpSheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(
            start = YpScreenMargin,
            end = YpScreenMargin,
            top = 6.dp,
            bottom = 10.dp
        ),
        text = text,
        style = MaterialTheme.typography.titleLarge
    )
}

/**
 * One row of a sheet: an icon (or any [leading] content), the label with an optional
 * [supporting] line below it, and optional [trailing] content; the whole row is tappable.
 */
@Composable
fun YpSheetItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = YpScreenMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        } else if (icon != null) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = icon,
                contentDescription = null,
                tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * YewPlay: a dialog in the middle of the screen: title, optional message, free content and the
 * buttons at the bottom right (the confirming one in ivory/ink, the other one muted).
 */
@Composable
fun YpDialogCard(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DialogShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(top = 22.dp, bottom = 18.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        if (message != null) {
            Text(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp),
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (content != null) {
            Spacer(Modifier.height(12.dp))
            content()
        }
        if (confirmLabel != null || dismissLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                if (dismissLabel != null) {
                    YpPillButton(label = dismissLabel, primary = false, onClick = onDismiss)
                }
                if (confirmLabel != null) {
                    YpPillButton(
                        label = confirmLabel,
                        primary = true,
                        enabled = confirmEnabled,
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

/**
 * YewPlay: a text field for dialogs: rounded, filled with the container color, the placeholder
 * in muted text. With [autoFocus] it takes the focus (and the keyboard) when shown.
 */
@Composable
fun YpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    autoFocus: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (() -> Unit)? = null,
    horizontalPadding: Dp = 24.dp
) {
    val focusRequester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    // built on BasicTextField (foundation) and not on the material TextField, which the app's
    // library versions cannot run (it crashes on show)
    BasicTextField(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .focusRequester(focusRequester),
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(text = placeholder, style = textStyle, color = colors.onSurfaceVariant)
                }
                innerTextField()
            }
        }
    )
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
