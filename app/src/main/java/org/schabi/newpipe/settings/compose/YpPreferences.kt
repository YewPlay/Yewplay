/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.compose

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpCheckRow
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpPreferenceCategory
import io.github.yewplay.core.designsystem.YpPreferenceRow
import io.github.yewplay.core.designsystem.YpRadioRow
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetTitle
import io.github.yewplay.core.designsystem.YpSwitchPreferenceRow
import io.github.yewplay.core.designsystem.YpTextField
import io.github.yewplay.core.designsystem.YpTheme
import kotlinx.coroutines.delay
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper

/**
 * YewPlay: one entry of a settings screen in Compose. The values are kept in the same shared
 * preferences, under the same keys, as the old XML preference screens.
 */
sealed interface YpPref {
    /** The preference key, also used to highlight a search result; null for plain entries. */
    val key: String?

    data class Category(val title: String) : YpPref {
        override val key: String? = null
    }

    data class Switch(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val default: Boolean,
        val dependency: String? = null,
        val enabled: Boolean = true,
        /** Called before saving; false keeps the old value. */
        val onChange: ((Boolean) -> Boolean)? = null
    ) : YpPref

    data class Choice(
        override val key: String,
        val title: String,
        val entries: List<String>,
        val values: List<String>,
        val default: String,
        /** Null to show the chosen entry as the description. */
        val summary: String? = null,
        val dependency: String? = null,
        val enabled: Boolean = true,
        val onChange: ((String) -> Boolean)? = null
    ) : YpPref

    data class MultiChoice(
        override val key: String,
        val title: String,
        val entries: List<String>,
        val values: List<String>,
        val default: Set<String>,
        val summary: String? = null
    ) : YpPref

    data class Text(
        override val key: String,
        val title: String,
        val default: String,
        val summary: String? = null,
        val keyboardType: KeyboardType = KeyboardType.Text,
        val onChange: ((String) -> Boolean)? = null
    ) : YpPref

    data class Action(
        override val key: String?,
        val title: String,
        val summary: String? = null,
        val icon: Int = 0,
        val enabled: Boolean = true,
        val onClick: () -> Unit
    ) : YpPref

    data class Info(val text: String) : YpPref {
        override val key: String? = null
    }

    /** Free content, for the few settings that are not one of the kinds above. */
    data class Custom(override val key: String?, val content: @Composable () -> Unit) : YpPref
}

/** Helpers to build the entries of a screen from the app's resources. */
class YpPrefBuilder(private val context: Context, val prefs: SharedPreferences) {
    val items = mutableListOf<YpPref>()

    fun s(@StringRes id: Int): String = context.getString(id)

    fun array(@ArrayRes id: Int): List<String> = context.resources.getStringArray(id).toList()

    fun category(@StringRes title: Int) {
        items += YpPref.Category(s(title))
    }

    fun switch(
        @StringRes key: Int,
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        default: Boolean,
        @StringRes dependency: Int = 0,
        enabled: Boolean = true,
        onChange: ((Boolean) -> Boolean)? = null
    ) {
        items += YpPref.Switch(
            key = s(key),
            title = s(title),
            summary = if (summary != 0) s(summary) else null,
            default = default,
            dependency = if (dependency != 0) s(dependency) else null,
            enabled = enabled,
            onChange = onChange
        )
    }

    fun choice(
        @StringRes key: Int,
        @StringRes title: Int,
        @ArrayRes entries: Int,
        @ArrayRes values: Int,
        @StringRes default: Int,
        @StringRes summary: Int = 0,
        @StringRes dependency: Int = 0,
        enabled: Boolean = true,
        onChange: ((String) -> Boolean)? = null
    ) {
        items += YpPref.Choice(
            key = s(key),
            title = s(title),
            entries = array(entries),
            values = array(values),
            default = s(default),
            summary = if (summary != 0) s(summary) else null,
            dependency = if (dependency != 0) s(dependency) else null,
            enabled = enabled,
            onChange = onChange
        )
    }

    /** A choice of durations: the values are seconds, shown as "1 hour", "5 minutes"... */
    fun durationChoice(
        @StringRes key: Int,
        @StringRes title: Int,
        @ArrayRes entries: Int,
        @ArrayRes values: Int,
        @StringRes default: Int,
        @StringRes summary: Int = 0,
        @StringRes dependency: Int = 0,
        enabled: Boolean = true
    ) {
        choice(key, title, entries, values, default, summary, dependency, enabled)
        val item = items.removeAt(items.lastIndex) as YpPref.Choice
        val names = item.values.mapIndexed { index, value ->
            val seconds = value.toIntOrNull() ?: 0
            if (seconds <= 0) item.entries[index] else Localization.localizeDuration(context, seconds)
        }
        items += item.copy(entries = names)
    }

    fun multiChoice(
        @StringRes key: Int,
        @StringRes title: Int,
        @ArrayRes entries: Int,
        @ArrayRes values: Int,
        @StringRes summary: Int = 0
    ) {
        items += YpPref.MultiChoice(
            key = s(key),
            title = s(title),
            entries = array(entries),
            values = array(values),
            // as in the XML screens: everything is chosen by default
            default = array(values).toSet(),
            summary = if (summary != 0) s(summary) else null
        )
    }

    fun action(
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        @StringRes key: Int = 0,
        summaryText: String? = null,
        @StringRes dependency: Int = 0,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        items += YpPref.Action(
            key = if (key != 0) s(key) else null,
            title = s(title),
            summary = summaryText ?: if (summary != 0) s(summary) else null,
            enabled = enabled && (dependency == 0 || prefs.getBoolean(s(dependency), false)),
            onClick = onClick
        )
    }

    fun info(@StringRes text: Int) {
        items += YpPref.Info(s(text))
    }
}

/**
 * YewPlay: base of the settings screens in Compose, hosted by the settings activity (which keeps
 * its toolbar with the title). Subclasses list their entries in [buildItems]; it is called again
 * whenever a preference changes, so entries can depend on other values.
 */
abstract class YpPreferenceFragment : Fragment() {
    protected lateinit var prefs: SharedPreferences
    private var version by mutableIntStateOf(0)
    private var highlightKey by mutableStateOf<String?>(null)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }

    @get:StringRes
    protected abstract val titleRes: Int

    protected abstract fun YpPrefBuilder.buildItems()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(titleRes))
        // values changed elsewhere (another screen, an import) while this one was hidden
        version++
    }

    /** Scrolls to the entry with [key] and marks it for a moment (a result of the search). */
    fun highlight(key: String) {
        highlightKey = key
    }

    /** Asks the screen to be built again (for values kept outside the shared preferences). */
    protected fun refresh() {
        version++
    }

    /** Opens another settings screen of the activity. */
    protected fun openScreen(fragmentClass: Class<out Fragment>) {
        (activity as? SettingsActivity)?.openSettingsFragment(fragmentClass.name)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) {
                    val tick = version
                    val items = remember(tick) { YpPrefBuilder(context, prefs).apply { buildItems() }.items.toList() }
                    PreferenceList(items, tick)
                }
            }
        }
    }

    @Composable
    private fun PreferenceList(items: List<YpPref>, tick: Int) {
        val listState = rememberLazyListState()
        val target = highlightKey
        var flashing by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(target, items) {
            val index = items.indexOfFirst { it.key != null && it.key == target }
            if (target != null && index >= 0) {
                listState.animateScrollToItem(index)
                flashing = target
                delay(HIGHLIGHT_MILLIS)
                flashing = null
                highlightKey = null
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(items) { item -> Entry(item, highlighted = item.key != null && item.key == flashing, tick = tick) }
        }
    }

    @Composable
    private fun Entry(item: YpPref, highlighted: Boolean, @Suppress("UNUSED_PARAMETER") tick: Int) {
        when (item) {
            is YpPref.Category -> YpPreferenceCategory(item.title)

            is YpPref.Custom -> item.content()

            is YpPref.Info -> Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp),
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is YpPref.Switch -> YpSwitchPreferenceRow(
                title = item.title,
                summary = item.summary,
                checked = prefs.getBoolean(item.key, item.default),
                enabled = item.enabled && isEnabled(item.dependency),
                highlighted = highlighted,
                onCheckedChange = { value ->
                    if (item.onChange?.invoke(value) != false) {
                        prefs.edit { putBoolean(item.key, value) }
                    }
                }
            )

            is YpPref.Choice -> {
                val value = prefs.getString(item.key, item.default) ?: item.default
                val chosen = item.entries.getOrNull(item.values.indexOf(value))
                YpPreferenceRow(
                    title = item.title,
                    // "%s" in the description is replaced by the chosen entry
                    summary = item.summary?.let { if (it.contains("%s") && chosen != null) it.replace("%s", chosen) else it } ?: chosen,
                    enabled = item.enabled && isEnabled(item.dependency),
                    highlighted = highlighted,
                    onClick = { showChoice(item, value) }
                )
            }

            is YpPref.MultiChoice -> YpPreferenceRow(
                title = item.title,
                summary = item.summary,
                highlighted = highlighted,
                onClick = { showMultiChoice(item) }
            )

            is YpPref.Text -> YpPreferenceRow(
                title = item.title,
                summary = item.summary ?: prefs.getString(item.key, item.default),
                highlighted = highlighted,
                onClick = { showText(item) }
            )

            is YpPref.Action -> YpPreferenceRow(
                title = item.title,
                summary = item.summary,
                icon = if (item.icon != 0) painterResource(item.icon) else null,
                enabled = item.enabled,
                highlighted = highlighted,
                onClick = item.onClick,
                trailing = null
            )
        }
    }

    private fun isEnabled(dependency: String?): Boolean = dependency == null || prefs.getBoolean(dependency, false)

    private fun showChoice(item: YpPref.Choice, current: String) {
        YpDialogs.sheet(requireContext()) { dismiss ->
            YpSheet {
                YpSheetTitle(item.title)
                Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    item.entries.forEachIndexed { index, entry ->
                        val value = item.values.getOrNull(index) ?: return@forEachIndexed
                        YpRadioRow(
                            label = entry,
                            selected = value == current,
                            onClick = {
                                dismiss()
                                if (value != current && item.onChange?.invoke(value) != false) {
                                    prefs.edit { putString(item.key, value) }
                                }
                            }
                        )
                    }
                }
            }
        }.show()
    }

    private fun showMultiChoice(item: YpPref.MultiChoice) {
        YpDialogs.card(requireContext()) { dismiss ->
            var selected by rememberSaveable {
                mutableStateOf(prefs.getStringSet(item.key, item.default)?.toList().orEmpty())
            }
            YpDialogCard(
                title = item.title,
                confirmLabel = stringResource(R.string.ok),
                onConfirm = {
                    prefs.edit { putStringSet(item.key, selected.toSet()) }
                    dismiss()
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            ) {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    item.entries.forEachIndexed { index, entry ->
                        val value = item.values.getOrNull(index) ?: return@forEachIndexed
                        YpCheckRow(
                            label = entry,
                            checked = value in selected,
                            onClick = { selected = if (value in selected) selected - value else selected + value }
                        )
                    }
                }
            }
        }.show()
    }

    private fun showText(item: YpPref.Text) {
        YpDialogs.card(requireContext(), showKeyboard = true) { dismiss ->
            var text by rememberSaveable { mutableStateOf(prefs.getString(item.key, item.default).orEmpty()) }
            YpDialogCard(
                title = item.title,
                confirmLabel = stringResource(R.string.ok),
                onConfirm = {
                    if (item.onChange?.invoke(text) != false) {
                        prefs.edit { putString(item.key, text) }
                    }
                    dismiss()
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            ) {
                YpTextField(value = text, onValueChange = { text = it }, autoFocus = true, keyboardType = item.keyboardType)
            }
        }.show()
    }

    private companion object {
        const val HIGHLIGHT_MILLIS = 1500L
    }
}
