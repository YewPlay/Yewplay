package com.liskovsoft.youtubeapi.search.v2.gen

import com.liskovsoft.youtubeapi.browse.v2.gen.Content
import com.liskovsoft.youtubeapi.browse.v2.gen.ContinuationItemRenderer
import com.liskovsoft.youtubeapi.common.models.gen.ItemWrapper

internal data class SearchResult(
    val contents: Content?
)

/**
 * NEWTUBE(web-search): a WEB search continuation page arrives as appended continuation
 * items instead of continuationContents.
 */
internal data class SearchResultContinuation(
    val onResponseReceivedActions: List<Action?>?
) {
    data class Action(
        val appendContinuationItemsAction: AppendContinuationItems?
    ) {
        data class AppendContinuationItems(
            val continuationItems: List<Section?>?
        ) {
            data class Section(
                val itemSectionRenderer: ItemSection?,
                val continuationItemRenderer: ContinuationItemRenderer?
            ) {
                data class ItemSection(
                    val contents: List<ItemWrapper?>?
                )
            }
        }
    }
}
