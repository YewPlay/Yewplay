/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.helper

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.math.MathUtils
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetTitle
import io.github.yewplay.core.designsystem.YpSlider
import io.github.yewplay.core.designsystem.YpSwitch
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.util.SliderStrategy

/**
 * YewPlay: speed and pitch of the player as a Compose bottom sheet. Changes apply right away;
 * "Cancel" goes back to the values the sheet opened with, "Reset" to the defaults.
 */
class PlaybackParameterDialog : DialogFragment() {
    private var callback: Callback? = null

    private var initialTempo = DEFAULT_TEMPO
    private var initialPitch = DEFAULT_PITCH
    private var initialSkipSilence = DEFAULT_SKIP_SILENCE

    private var tempo by mutableDoubleStateOf(DEFAULT_TEMPO)
    private var pitch by mutableDoubleStateOf(DEFAULT_PITCH)
    private var skipSilence by mutableStateOf(DEFAULT_SKIP_SILENCE)
    private var unhooked by mutableStateOf(true)
    private var semitones by mutableStateOf(false)
    private var step by mutableDoubleStateOf(DEFAULT_STEP)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Callback) {
            callback = context
        } else if (callback == null) {
            dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            initialTempo = it.getDouble(KEY_INITIAL_TEMPO, initialTempo)
            initialPitch = it.getDouble(KEY_INITIAL_PITCH, initialPitch)
            initialSkipSilence = it.getBoolean(KEY_INITIAL_SKIP, initialSkipSilence)
            tempo = it.getDouble(KEY_TEMPO, tempo)
            pitch = it.getDouble(KEY_PITCH, pitch)
            skipSilence = it.getBoolean(KEY_SKIP, skipSilence)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        unhooked = prefs.getBoolean(getString(R.string.playback_unhook_key), true)
        semitones = prefs.getBoolean(getString(R.string.playback_adjust_by_semitones_key), false)
        step = prefs.getFloat(getString(R.string.adjustment_step_key), DEFAULT_STEP.toFloat()).toDouble()
        if (!unhooked) {
            ensureHooked()
        }
        if (semitones) {
            val valid = validPitch(pitch)
            if (valid != pitch) {
                pitch = valid
                updateCallback()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble(KEY_INITIAL_TEMPO, initialTempo)
        outState.putDouble(KEY_INITIAL_PITCH, initialPitch)
        outState.putBoolean(KEY_INITIAL_SKIP, initialSkipSilence)
        outState.putDouble(KEY_TEMPO, tempo)
        outState.putDouble(KEY_PITCH, pitch)
        outState.putBoolean(KEY_SKIP, skipSilence)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = YpDialogs.sheet(requireContext()) { dismiss ->
        YpSheet {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                YpSheetTitle(stringResource(R.string.playback_speed_control))
                ParameterSection(
                    title = stringResource(R.string.playback_tempo),
                    valueText = PlayerHelper.formatSpeed(tempo),
                    position = QUADRATIC.progressOf(tempo).toFloat(),
                    max = QUADRATIC.progressOf(MAX).toFloat(),
                    minText = PlayerHelper.formatSpeed(MIN),
                    maxText = PlayerHelper.formatSpeed(MAX),
                    stepText = PlayerHelper.formatPitch(step),
                    onPosition = { onTempoChanged(QUADRATIC.valueOf(it.toInt())) },
                    onStep = { direction -> onTempoChanged(tempo + step * direction) }
                )
                PitchModeTabs(semitones = semitones, onSelect = ::setPitchMode)
                if (semitones) {
                    ParameterSection(
                        title = stringResource(R.string.playback_pitch),
                        valueText = PlayerSemitoneHelper.formatPitchSemitones(pitch),
                        position = SEMITONES.progressOf(pitch).toFloat(),
                        max = (2 * PlayerSemitoneHelper.SEMITONE_COUNT).toFloat(),
                        minText = PlayerSemitoneHelper.formatPitchSemitones(-PlayerSemitoneHelper.SEMITONE_COUNT),
                        maxText = PlayerSemitoneHelper.formatPitchSemitones(PlayerSemitoneHelper.SEMITONE_COUNT),
                        stepText = "1",
                        onPosition = { onPitchChanged(SEMITONES.valueOf(it.toInt())) },
                        onStep = { direction ->
                            onPitchChanged(
                                PlayerSemitoneHelper.semitonesToPercent(
                                    PlayerSemitoneHelper.percentToSemitones(pitch) + direction
                                )
                            )
                        }
                    )
                } else {
                    ParameterSection(
                        title = stringResource(R.string.playback_pitch),
                        valueText = PlayerHelper.formatPitch(pitch),
                        position = QUADRATIC.progressOf(pitch).toFloat(),
                        max = QUADRATIC.progressOf(MAX).toFloat(),
                        minText = PlayerHelper.formatPitch(MIN),
                        maxText = PlayerHelper.formatPitch(MAX),
                        stepText = PlayerHelper.formatPitch(step),
                        onPosition = { onPitchChanged(QUADRATIC.valueOf(it.toInt())) },
                        onStep = { direction -> onPitchChanged(pitch + step * direction) }
                    )
                }
                StepChips(
                    title = stringResource(R.string.playback_step),
                    selected = step,
                    onSelect = ::changeStep
                )
                SwitchRow(stringResource(R.string.unhook_checkbox), unhooked, ::changeUnhooked)
                SwitchRow(stringResource(R.string.skip_silence_checkbox), skipSilence) {
                    skipSilence = it
                    updateCallback()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = YpScreenMargin, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    YpPillButton(
                        label = stringResource(R.string.playback_reset),
                        primary = false,
                        onClick = {
                            setAll(DEFAULT_TEMPO, DEFAULT_PITCH, DEFAULT_SKIP_SILENCE)
                        }
                    )
                    Box(Modifier.weight(1f))
                    YpPillButton(
                        label = stringResource(R.string.cancel),
                        primary = false,
                        onClick = {
                            setAll(initialTempo, initialPitch, initialSkipSilence)
                            dismiss()
                        }
                    )
                    YpPillButton(label = stringResource(R.string.ok), primary = true, onClick = dismiss)
                }
            }
        }
    }

    private fun setAll(newTempo: Double, newPitch: Double, newSkipSilence: Boolean) {
        tempo = MathUtils.clamp(newTempo, MIN, MAX)
        pitch = validPitch(newPitch)
        skipSilence = newSkipSilence
        updateCallback()
    }

    private fun onTempoChanged(value: Double) {
        if (unhooked) {
            tempo = MathUtils.clamp(value, MIN, MAX)
        } else {
            setBoth(value)
        }
        updateCallback()
    }

    private fun onPitchChanged(value: Double) {
        if (unhooked) {
            pitch = validPitch(value)
        } else {
            setBoth(value)
        }
        updateCallback()
    }

    private fun setBoth(value: Double) {
        tempo = MathUtils.clamp(value, MIN, MAX)
        pitch = validPitch(value)
    }

    private fun changeUnhooked(value: Boolean) {
        unhooked = value
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putBoolean(getString(R.string.playback_unhook_key), value)
        }
        if (!value) {
            ensureHooked()
        }
    }

    /** Speed and pitch move together while hooked: bring them to the same value. */
    private fun ensureHooked() {
        if (tempo != pitch) {
            setBoth(minOf(tempo, pitch))
            updateCallback()
        }
    }

    private fun setPitchMode(bySemitones: Boolean) {
        semitones = bySemitones
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putBoolean(getString(R.string.playback_adjust_by_semitones_key), bySemitones)
        }
        if (bySemitones) {
            val valid = validPitch(pitch)
            if (valid != pitch) {
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "Bringing pitch to the nearest semitone: $pitch -> $valid")
                }
                onPitchChanged(valid)
            }
        } else if (!unhooked) {
            ensureHooked()
        }
    }

    private fun changeStep(value: Double) {
        step = value
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putFloat(getString(R.string.adjustment_step_key), value.toFloat())
        }
    }

    private fun validPitch(value: Double): Double {
        val clamped = MathUtils.clamp(value, MIN, MAX)
        return if (semitones) {
            PlayerSemitoneHelper.semitonesToPercent(PlayerSemitoneHelper.percentToSemitones(clamped))
        } else {
            clamped
        }
    }

    private fun updateCallback() {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "Updating callback: tempo = $tempo, pitch = $pitch, skipSilence = $skipSilence")
        }
        callback?.onPlaybackParameterChanged(tempo.toFloat(), pitch.toFloat(), skipSilence)
    }

    fun interface Callback {
        fun onPlaybackParameterChanged(playbackTempo: Float, playbackPitch: Float, playbackSkipSilence: Boolean)
    }

    companion object {
        private const val TAG = "PlaybackParameterDialog"
        private const val MIN = 0.10
        private const val MAX = 3.00
        private const val DEFAULT_TEMPO = 1.00
        private const val DEFAULT_PITCH = 1.00
        private const val DEFAULT_STEP = 0.25
        private const val DEFAULT_SKIP_SILENCE = false
        private val STEPS = listOf(0.01, 0.05, 0.10, 0.25, 1.00)

        private const val KEY_INITIAL_TEMPO = "initial_tempo"
        private const val KEY_INITIAL_PITCH = "initial_pitch"
        private const val KEY_INITIAL_SKIP = "initial_skip_silence"
        private const val KEY_TEMPO = "tempo"
        private const val KEY_PITCH = "pitch"
        private const val KEY_SKIP = "skip_silence"

        private val QUADRATIC: SliderStrategy = SliderStrategy.Quadratic(MIN, MAX, 1.00, 10_000)
        private val SEMITONES = object : SliderStrategy {
            override fun progressOf(value: Double): Int = PlayerSemitoneHelper.percentToSemitones(value) + PlayerSemitoneHelper.SEMITONE_COUNT

            override fun valueOf(progress: Int): Double = PlayerSemitoneHelper.semitonesToPercent(progress - PlayerSemitoneHelper.SEMITONE_COUNT)
        }

        @JvmStatic
        fun newInstance(
            playbackTempo: Double,
            playbackPitch: Double,
            playbackSkipSilence: Boolean,
            callback: Callback?
        ): PlaybackParameterDialog = PlaybackParameterDialog().apply {
            this.callback = callback
            initialTempo = playbackTempo
            initialPitch = playbackPitch
            initialSkipSilence = playbackSkipSilence
            tempo = playbackTempo
            pitch = playbackPitch
            skipSilence = playbackSkipSilence
        }

        @Composable
        private fun ParameterSection(
            title: String,
            valueText: String,
            position: Float,
            max: Float,
            minText: String,
            maxText: String,
            stepText: String,
            onPosition: (Float) -> Unit,
            onStep: (direction: Int) -> Unit
        ) {
            val colors = MaterialTheme.colorScheme
            Column(modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                    Text(text = valueText, style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("−$stepText") { onStep(-1) }
                    YpSlider(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        value = position.coerceIn(0f, max),
                        valueRange = 0f..max,
                        onValueChange = onPosition
                    )
                    StepButton("+$stepText") { onStep(1) }
                }
                Row {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = minText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(text = maxText, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }

        @Composable
        private fun StepButton(label: String, onClick: () -> Unit) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
            }
        }

        @Composable
        private fun PitchModeTabs(semitones: Boolean, onSelect: (Boolean) -> Unit) {
            Row(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip(stringResource(R.string.percent), !semitones) { onSelect(false) }
                Chip(stringResource(R.string.semitone), semitones) { onSelect(true) }
            }
        }

        @Composable
        private fun StepChips(title: String, selected: Double, onSelect: (Double) -> Unit) {
            Column(modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    STEPS.forEach { value ->
                        Chip(PlayerHelper.formatPitch(value), value == selected) { onSelect(value) }
                    }
                }
            }
        }

        @Composable
        private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
            val colors = MaterialTheme.colorScheme
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHighest)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.onPrimary else colors.onSurface
                )
            }
        }

        @Composable
        private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            val colors = MaterialTheme.colorScheme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChange(!checked) }
                    .padding(horizontal = YpScreenMargin, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(modifier = Modifier.weight(1f), text = label, style = MaterialTheme.typography.bodyLarge)
                YpSwitch(checked = checked, onCheckedChange = onChange)
            }
        }
    }
}
