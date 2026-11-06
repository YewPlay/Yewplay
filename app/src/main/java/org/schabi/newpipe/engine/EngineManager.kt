/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal persistence abstraction so [EngineManager] stays unit-testable
 * without Android. The app provides a SharedPreferences-backed implementation.
 */
interface EnginePrefs {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** Result of an engine switch attempt, surfaced to the UI. */
sealed interface EngineSwitchResult {
    data class Success(val engine: EngineDescriptor) : EngineSwitchResult
    data class Failure(val engineId: String, val reason: String) : EngineSwitchResult
}

/**
 * YewPlay MultiEngine — central coordinator and the ONLY place that decides
 * which engine serves an operation.
 *
 * Responsibilities: register engines, persist/restore the user's selection,
 * expose capabilities, and execute operations with an optional, bounded,
 * non-destructive fallback (never changes the stored preference, never loops).
 */
class EngineManager private constructor(
    private val prefs: EnginePrefs
) {
    private val engines = LinkedHashMap<String, ExtractionEngine>()
    private val _state = MutableStateFlow(EngineManagerState())
    val state: StateFlow<EngineManagerState> = _state.asStateFlow()

    /** Snapshot exposed to the UI (settings screen, engine pickers). */
    data class EngineManagerState(
        val availableEngines: List<EngineDescriptor> = emptyList(),
        val activeEngineId: String = NEWPIPE_ID,
        val engineStates: Map<String, EngineState> = emptyMap()
    )

    @Synchronized
    fun register(engine: ExtractionEngine) {
        engines[engine.descriptor.id] = engine
        refreshState()
    }

    /** The active engine; falls back to NewPipe when the selection is unknown. */
    @Synchronized
    fun active(): ExtractionEngine = engines[prefs.getString(KEY_ENGINE, NEWPIPE_ID)] ?: engines.getValue(NEWPIPE_ID)

    fun descriptors(): List<EngineDescriptor> = synchronized(engines) {
        engines.values.map { it.descriptor }
    }

    fun descriptorOf(id: String): EngineDescriptor? = synchronized(engines) { engines[id]?.descriptor }

    fun stateOf(id: String): EngineState = synchronized(engines) {
        engines[id]?.checkAvailability() ?: EngineState.UNAVAILABLE
    }

    fun isAutoFallbackEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_FALLBACK, false)

    fun setAutoFallbackEnabled(enabled: Boolean) = prefs.putBoolean(KEY_AUTO_FALLBACK, enabled)

    /**
     * Switch the active engine with validation: the target must be registered and
     * report itself available. The previous engine is shut down AFTER the new one
     * validates, so a failed switch never leaves the app without an engine.
     */
    @Synchronized
    fun switchTo(id: String): EngineSwitchResult {
        val target = engines[id] ?: return EngineSwitchResult.Failure(id, "not registered")
        val state = target.checkAvailability()
        if (state == EngineState.UNAVAILABLE) {
            return EngineSwitchResult.Failure(id, "engine unavailable")
        }
        val previous = prefs.getString(KEY_ENGINE, NEWPIPE_ID)
        if (previous != id) {
            engines[previous]?.shutdown()
        }
        prefs.putString(KEY_ENGINE, id)
        refreshState()
        Log.i(TAG, "engine switched: $previous -> $id")
        return EngineSwitchResult.Success(target.descriptor)
    }

    /**
     * Execute [operation] on the active engine. When it fails with a recoverable
     * [EngineException] and the user opted into automatic fallback, try at most
     * one alternative engine that declares [required]. The stored preference is
     * never changed; the caller receives the result annotated with the engine
     * that actually served it so the UI can inform the user.
     */
    suspend fun <T> execute(
        required: EngineCapability,
        operation: suspend (ExtractionEngine) -> T
    ): FallbackResult<T> {
        val primary = active()
        try {
            return FallbackResult(operation(primary), primary.descriptor, fallbackUsed = false)
        } catch (e: EngineException) {
            Log.w(TAG, "engine ${primary.descriptor.id} failed: ${e.message}")
            if (!e.recoverable || !isAutoFallbackEnabled()) throw e
        } catch (e: Exception) {
            Log.w(TAG, "engine ${primary.descriptor.id} failed unexpectedly", e)
            if (!isAutoFallbackEnabled()) {
                throw EngineException(e.message ?: "operation failed", e, primary.descriptor.id)
            }
        }

        val alternative = synchronized(engines) {
            engines.values.firstOrNull {
                it.descriptor.id != primary.descriptor.id &&
                    required in it.descriptor.capabilities &&
                    it.checkAvailability() == EngineState.AVAILABLE
            }
        } ?: throw EngineException(
            "no fallback engine with $required available",
            engineId = primary.descriptor.id,
            recoverable = false
        )

        Log.i(TAG, "falling back to ${alternative.descriptor.id} for $required")
        return FallbackResult(operation(alternative), alternative.descriptor, fallbackUsed = true)
    }

    data class FallbackResult<T>(
        val value: T,
        val servedBy: EngineDescriptor,
        val fallbackUsed: Boolean
    )

    private fun refreshState() {
        val map = synchronized(engines) {
            engines.mapValues { (_, e) ->
                runCatching { e.checkAvailability() }.getOrDefault(EngineState.ERROR)
            }
        }
        _state.value = EngineManagerState(
            availableEngines = synchronized(engines) { engines.values.map { it.descriptor } },
            activeEngineId = prefs.getString(KEY_ENGINE, NEWPIPE_ID) ?: NEWPIPE_ID,
            engineStates = map
        )
    }

    companion object {
        private const val TAG = "EngineManager"
        const val KEY_ENGINE = "yp_extraction_engine"
        const val KEY_AUTO_FALLBACK = "yp_engine_auto_fallback"
        const val NEWPIPE_ID = "newpipe"

        @Volatile
        private var instance: EngineManager? = null

        fun init(prefs: EnginePrefs): EngineManager = instance ?: synchronized(this) {
            instance ?: EngineManager(prefs).also { instance = it }
        }

        fun get(): EngineManager = checkNotNull(instance) { "EngineManager.init() not called" }

        /** Test hook: reset the singleton between tests. */
        internal fun resetForTests() {
            synchronized(this) { instance = null }
        }
    }
}
