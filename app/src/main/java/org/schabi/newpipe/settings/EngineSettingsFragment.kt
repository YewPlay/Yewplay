/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.github.yewplay.core.designsystem.YpCheckRow
import io.github.yewplay.core.designsystem.YpPreferenceCategory
import io.github.yewplay.core.designsystem.YpRadioRow
import io.github.yewplay.core.designsystem.YpTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.engine.EngineManager
import org.schabi.newpipe.engine.EngineState
import org.schabi.newpipe.engine.EngineSwitchResult
import org.schabi.newpipe.engine.ExtractionEngine
import org.schabi.newpipe.engine.SearchFilter
import org.schabi.newpipe.engine.bridge.EngineExtractorBridge
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.local.yewplay.YpItems
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
        val scope = rememberCoroutineScope()
        // engineId -> diagnostic output; a null-yet-present key means "running"
        val probeResults = remember { mutableStateMapOf<String, String>() }

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
                    val probe = probeResults[engine.id]
                    val probing = probe == RUNNING
                    Text(
                        text = if (probing) {
                            stringResource(R.string.yp_engine_testing)
                        } else {
                            stringResource(R.string.yp_engine_test)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (probing) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .padding(start = 56.dp, end = 24.dp, bottom = 4.dp)
                            .clickable(enabled = !probing) {
                                val target = manager.engineOf(engine.id) ?: return@clickable
                                probeResults[engine.id] = RUNNING
                                scope.launch {
                                    probeResults[engine.id] = probeEngine(target)
                                }
                            }
                    )
                    if (probe != null && probe != RUNNING) {
                        Text(
                            text = probe,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 56.dp, end = 24.dp, bottom = 8.dp)
                        )
                    }
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
            item {
                val context = LocalContext.current
                var bridgeProbe by remember { mutableStateOf<String?>(null) }
                val probing = bridgeProbe == RUNNING
                val probeText = bridgeProbe
                YpPreferenceCategory(stringResource(R.string.yp_engine_section_diagnostics))
                Text(
                    text = if (probing) {
                        stringResource(R.string.yp_engine_testing)
                    } else {
                        stringResource(R.string.yp_engine_test_bridge)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (probing) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .padding(start = 56.dp, end = 24.dp, bottom = 4.dp)
                        .clickable(enabled = !probing) {
                            bridgeProbe = RUNNING
                            scope.launch { bridgeProbe = probeBridgePath(context) }
                        }
                )
                if (probeText != null && probeText != RUNNING) {
                    Text(
                        text = probeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 24.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }

    /**
     * End-to-end probe of a single engine (no fallback): suggestions, search and a video
     * info fetch against a stable test video. The first failing step aborts the probe and
     * reports the exact exception, so engine failures can be diagnosed from a screenshot.
     */
    private suspend fun probeEngine(engine: ExtractionEngine): String {
        val out = StringBuilder()
        try {
            val suggestions = engine.suggestions("music")
            out.append("suggestions: OK (").append(suggestions.size).append(")\n")
        } catch (e: Exception) {
            return out.append("suggestions: FAIL — ").append(describe(e)).toString()
        }
        try {
            val page = engine.search("yewplay", SearchFilter.ALL, null)
            out.append("search: OK (").append(page.items.size).append(" videos)\n")
        } catch (e: Exception) {
            return out.append("search: FAIL — ").append(describe(e)).toString()
        }
        try {
            // Big Buck Bunny on the official YouTube Movies channel: stable, no age gate.
            val info = engine.getVideoInfo("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
            out.append("videoInfo: OK (").append(info.formats.size).append(" formats)")
        } catch (e: Exception) {
            return out.append("videoInfo: FAIL — ").append(describe(e)).toString()
        }
        return out.toString()
    }

    /**
     * Full app-path probe: every engine is exercised through [EngineExtractorBridge] — the exact
     * code path the search screen and the player use — including [EngineManager.execute], the
     * conversion into extractor models and the Compose item mapping. The per-engine probe above
     * cannot see failures in those layers, because it calls the engine directly.
     *
     * The active engine is switched temporarily for each probe (engine shutdowns are no-ops)
     * and the user's selection is always restored afterwards.
     */
    private suspend fun probeBridgePath(context: Context): String = withContext(Dispatchers.IO) {
        val manager = EngineManager.get()
        val serviceId = ServiceList.YouTube.serviceId
        val original = manager.active().descriptor.id
        val out = StringBuilder()
        try {
            for (engine in manager.descriptors()) {
                out.append("── ").append(engine.displayName).append(" ──\n")
                when (val sw = manager.switchTo(engine.id)) {
                    is EngineSwitchResult.Failure -> {
                        out.append("switch: FAIL — ").append(sw.reason).append('\n')
                        continue
                    }

                    is EngineSwitchResult.Success -> Unit
                }
                probeBridgeSteps(context, serviceId, out)
                out.append('\n')
            }
        } finally {
            manager.switchTo(original)
        }
        out.append("active engine restored: ").append(original)
        out.toString()
    }

    private fun probeBridgeSteps(context: Context, serviceId: Int, out: StringBuilder) {
        try {
            val suggestions = EngineExtractorBridge.suggestionsFor(serviceId, "music").blockingGet()
            out.append("suggestions: OK (").append(suggestions.size).append(")\n")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            out.append("suggestions: FAIL — ").append(describe(e)).append('\n')
            return
        }
        val info = try {
            EngineExtractorBridge.searchFor(serviceId, "yewplay", emptyList(), "").blockingGet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            out.append("search: FAIL — ").append(describe(e)).append('\n')
            return
        }
        out.append("search: OK (").append(info.relatedItems.size).append(" items, errors: ")
            .append(info.errors.size)
            .append(if (Page.isValid(info.nextPage)) ", next page" else ", no next page")
            .append(")\n")
        var mapped = 0
        var mappingFailure: String? = null
        for ((index, item) in info.relatedItems.withIndex()) {
            try {
                YpItems.toUi(context, item)
                mapped++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mappingFailure = "item #$index (${item.javaClass.simpleName}, url=${item.url}): " +
                    describe(e)
                break
            }
        }
        if (mappingFailure != null) {
            out.append("ui mapping: FAIL — ").append(mappingFailure).append('\n')
            return
        }
        out.append("ui mapping: OK (").append(mapped).append('/')
            .append(info.relatedItems.size).append(")\n")
        try {
            // Big Buck Bunny on the official YouTube Movies channel: stable, no age gate.
            val si = EngineExtractorBridge
                .getStreamInfo(serviceId, "https://www.youtube.com/watch?v=aqz-KE-bpKQ", true)
                .blockingGet()
            out.append("video: OK (video: ").append(si.videoStreams.size)
                .append(", video-only: ").append(si.videoOnlyStreams.size)
                .append(", audio: ").append(si.audioStreams.size)
                .append(", HLS: ").append(if (si.hlsUrl.isNullOrBlank()) "no" else "yes")
                .append(", DASH: ").append(if (si.dashMpdUrl.isNullOrBlank()) "no" else "yes")
                .append(")\n")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            out.append("video: FAIL — ").append(describe(e)).append('\n')
        }
    }

    private fun describe(e: Throwable): String = buildString {
        append(e.javaClass.simpleName).append(": ").append(e.message ?: "no message")
        val cause = e.cause
        if (cause != null) {
            append(" ← ").append(cause.javaClass.simpleName).append(": ").append(cause.message ?: "")
        }
    }

    private companion object {
        const val RUNNING = "…"
    }
}
