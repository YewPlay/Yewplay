/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.preferencesearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTheme
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpThemePrefs

/**
 * YewPlay: the results of the search in the settings, in Compose: each setting with where it
 * is (the breadcrumbs), its title and its description. Tapping one opens it.
 */
class PreferenceSearchFragment : Fragment() {
    private var prefSearcher: PreferenceSearcher? = null
    private var results by mutableStateOf<List<PreferenceSearchItem>?>(null)

    fun setSearcher(searcher: PreferenceSearcher) {
        prefSearcher = searcher
    }

    fun updateSearchResults(keyword: String) {
        val searcher = prefSearcher ?: return
        results = searcher.searchFor(keyword)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) {
                    val list = results
                    if (list != null && list.isEmpty()) {
                        Text(
                            modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 24.dp),
                            text = stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(list.orEmpty(), key = { it.key }) { item ->
                                ResultRow(item)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ResultRow(item: PreferenceSearchItem) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onItemClicked(item) }
                .padding(horizontal = YpScreenMargin, vertical = 12.dp)
        ) {
            if (item.breadcrumbs.isNotEmpty()) {
                Text(
                    text = item.breadcrumbs,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            if (item.summary.isNotEmpty()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private fun onItemClicked(item: PreferenceSearchItem) {
        val listener = activity as? PreferenceSearchResultListener
            ?: throw ClassCastException("$activity must implement SearchPreferenceResultListener")
        listener.onSearchResultClicked(item)
    }

    companion object {
        @JvmField
        val NAME: String = PreferenceSearchFragment::class.java.simpleName
    }
}
