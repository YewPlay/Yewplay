/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.github.yewplay.core.designsystem.YpCheckRow
import io.github.yewplay.core.designsystem.YpPreferenceCategory
import io.github.yewplay.core.designsystem.YpRadioRow
import io.github.yewplay.core.designsystem.YpTheme
import org.schabi.newpipe.R
import org.schabi.newpipe.engine.EngineManager
import org.schabi.newpipe.engine.EngineState
import org.schabi.newpipe.engine.EngineSwitchResult
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.ThemeHelper

/**
 * Settings → Extraction engine: lists the registered engines with their state
 * and capabilities, and switches the active engine through [EngineManager]
 * (the only place allowed to coordinate engines).
 */
class EngineSettingsFragment : Fragment() {

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.yp_settings_engine_title))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) {
                    EngineSettingsContent(
                        onSwitchResult = { result ->
                            val message = when (result) {
                                is EngineSwitchResult.Success -> getString(
                                    R.string.yp_engine_now_active,
                                    result.engine.displayName
                                )

                                is EngineSwitchResult.Failure -> getString(
                                    R.string.yp_engine_switch_failed,
                                    result.engineId,
                                    result.reason
                                )
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun EngineSettingsContent(onSwitchResult: (EngineSwitchResult) -> Unit) {
        val manager = EngineManager.get()
        val state by manager.state.collectAsState()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                YpPreferenceCategory(stringResource(R.string.yp_engine_section_engines))
            }
            items(state.availableEngines, key = { it.id }) { engine ->
                val stateText = when (state.engineStates[engine.id]) {
                    EngineState.AVAILABLE -> stringResource(R.string.yp_engine_state_available)
                    EngineState.UNAVAILABLE -> stringResource(R.string.yp_engine_state_unavailable)
                    else -> stringResource(R.string.yp_engine_state_error)
                }
                Column {
                    YpRadioRow(
                        label = "${engine.displayName} · $stateText",
                        selected = state.activeEngineId == engine.id,
                        onClick = { onSwitchResult(manager.switchTo(engine.id)) }
                    )
                    Text(
                        text = "${engine.summary} · v${engine.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 24.dp)
                    )
                    Text(
                        text = stringResource(R.string.yp_engine_capabilities_label) +
                            " " + engine.capabilities.joinToString { it.name.lowercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 24.dp, bottom = 4.dp)
                    )
                }
            }
            item {
                var autoFallback by remember { mutableStateOf(manager.isAutoFallbackEnabled()) }
                YpPreferenceCategory(stringResource(R.string.yp_engine_section_options))
                YpCheckRow(
                    label = stringResource(R.string.yp_engine_auto_fallback),
                    checked = autoFallback,
                    onClick = {
                        autoFallback = !autoFallback
                        manager.setAutoFallbackEnabled(autoFallback)
                    }
                )
                Text(
                    text = stringResource(R.string.yp_engine_auto_fallback_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 56.dp, end = 24.dp)
                )
            }
        }
    }
}
