package com.liskovsoft.youtubeapi.search.v2.gen

import com.liskovsoft.youtubeapi.common.models.gen.ItemWrapper
import com.liskovsoft.youtubeapi.next.v2.gen.getContinuationToken
import com.liskovsoft.youtubeapi.next.v2.gen.getItemWrappers
import com.liskovsoft.youtubeapi.next.v2.gen.getTitle

internal fun SearchResult.getNextPageKey() = getContents()?.firstOrNull()?.shelfRenderer?.getContinuationToken()
    ?: getContents()?.firstNotNullOfOrNull { it?.itemSectionRenderer?.continuations?.getContinuationToken() }
internal fun SearchResult.getItemWrappers(): List<ItemWrapper?>? = getContents()?.firstOrNull()?.shelfRenderer?.getItemWrappers()
    ?: getContents()?.firstNotNullOfOrNull { it?.itemSectionRenderer?.contents }
internal fun SearchResult.getSections(): List<SearchSection?>? = getContents()?.mapNotNull { section ->
    val shelf = section?.shelfRenderer
    val itemSection = section?.itemSectionRenderer
    when {
        shelf != null -> SearchSection(shelf.getTitle(), shelf.getContinuationToken(), shelf.getItemWrappers())

        // NEWTUBE(web-search): WEB search results are item sections (no title), not shelves
        itemSection != null -> SearchSection(
            null,
            itemSection.continuations?.getContinuationToken(),
            itemSection.contents
        )

        else -> null
    }
}

// NEWTUBE(web-search): the TV client answers with a top-level sectionListRenderer, the WEB
// client nests it under twoColumnSearchResultsRenderer. Accept both.
private fun SearchResult.getContents() = contents?.sectionListRenderer?.contents
    ?: contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer?.contents

/** One continuation page of a WEB search arrives as appended continuation items. */
internal fun SearchResultContinuation.toSearchSection(): SearchSection? {
    val sections = onResponseReceivedActions?.firstNotNullOfOrNull {
        it?.appendContinuationItemsAction?.continuationItems
    } ?: return null
    val items = sections.flatMap { it?.itemSectionRenderer?.contents.orEmpty() }
    val token = sections.firstNotNullOfOrNull {
        it?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
    }
    return if (items.isEmpty() && token == null) null else SearchSection(null, token, items)
}
