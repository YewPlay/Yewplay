/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.lazy.LazyListState

/**
 * YewPlay: swaps the dragged item with the one under its center, if any. Shared by the lists
 * whose items the user reorders by dragging a handle (playlist editing, play queue).
 *
 * @param listState the state of the lazy list being dragged over
 * @param prefix the prefix of the keys of the draggable items in the list
 * @param keys the keys of the items, in their current order, without the prefix
 * @param key the key of the dragged item, without the prefix
 * @param offset the accumulated drag offset of the item
 * @param onMove called with the from/to indexes when the item passes over another one
 * @return the drag offset, corrected for the new position of the item
 */
fun ypMoveDragged(
    listState: LazyListState,
    prefix: String,
    keys: List<String>,
    key: String,
    offset: Float,
    onMove: (Int, Int) -> Unit
): Float {
    val visible = listState.layoutInfo.visibleItemsInfo
    val dragged = visible.firstOrNull { it.key == prefix + key } ?: return offset
    val center = dragged.offset + offset + dragged.size / 2f
    val target = visible.firstOrNull {
        val itemKey = it.key as? String ?: return@firstOrNull false
        itemKey.startsWith(prefix) && itemKey != prefix + key &&
            center > it.offset && center < it.offset + it.size
    } ?: return offset
    val from = keys.indexOf(key)
    val to = keys.indexOf((target.key as String).removePrefix(prefix))
    if (from < 0 || to < 0) {
        return offset
    }
    onMove(from, to)
    // after the move the item is laid out where the target was
    return offset - (target.offset - dragged.offset)
}
