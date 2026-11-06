/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.util.text.LongPressLinkMovementMethod
import org.schabi.newpipe.util.text.TextLinkifier

/**
 * YewPlay: a description or a comment of NewPipe (HTML, Markdown or plain text) in Compose,
 * with the links of NewPipe: timestamps play the video from there, hashtags search, links open
 * in the app when it can. A long press on a link copies it.
 *
 * @param maxLines lines shown (with "…"); [Int.MAX_VALUE] for the whole text
 * @param selectable to select and copy parts of the text
 */
@Composable
fun YpRichText(
    description: Description,
    serviceId: Int,
    streamUrl: String?,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    selectable: Boolean = false
) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val disposables = remember { CompositeDisposable() }
    DisposableEffect(Unit) { onDispose { disposables.dispose() } }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
                typeface = ResourcesCompat.getFont(context, R.font.yewplay_sans)
                setLineSpacing(0f, LINE_SPACING)
            }
        },
        update = { view ->
            view.setTextColor(color)
            view.setLinkTextColor(linkColor)
            view.maxLines = maxLines
            view.ellipsize = if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END
            val key = description to selectable
            if (view.tag != key) {
                view.tag = key
                // selection and links do not go together: links come back when it ends
                view.setTextIsSelectable(selectable)
                val service = runCatching { NewPipe.getService(serviceId) }.getOrNull()
                TextLinkifier.fromDescription(
                    view,
                    description,
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                    service,
                    streamUrl,
                    disposables
                ) {
                    if (!selectable) {
                        it.movementMethod = LongPressLinkMovementMethod.getInstance()
                    }
                }
            }
        }
    )
}

private const val TEXT_SIZE_SP = 14f
private const val LINE_SPACING = 1.15f
