/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.engine.model.YpChannelInfo
import org.schabi.newpipe.engine.model.YpPlaylistInfo
import org.schabi.newpipe.engine.model.YpSearchPage
import org.schabi.newpipe.engine.model.YpVideoInfo

class EngineManagerTest {

    private class InMemoryPrefs : EnginePrefs {
        val strings = mutableMapOf<String, String?>()
        val booleans = mutableMapOf<String, Boolean>()

        override fun getString(key: String, default: String?): String? = strings.getOrDefault(key, default)

        override fun putString(key: String, value: String) {
            strings[key] = value
        }

        override fun getBoolean(key: String, default: Boolean): Boolean = booleans.getOrDefault(key, default)

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }
    }

    private class FakeEngine(
        id: String,
        capabilities: Set<EngineCapability> = setOf(EngineCapability.SEARCH_VIDEOS),
        private val availability: EngineState = EngineState.AVAILABLE,
        private val failWith: EngineException? = null
    ) : ExtractionEngine {
        override val descriptor =
            EngineDescriptor(id, id.uppercase(), "fake", "1.0", capabilities)
        var shutdownCalled = false
            private set

        override fun checkAvailability(): EngineState = availability

        override suspend fun search(
            query: String,
            filter: SearchFilter,
            pageKey: String?
        ): YpSearchPage {
            failWith?.let { throw it }
            return YpSearchPage(emptyList(), emptyList(), emptyList(), nextPageKey = null)
        }

        override suspend fun suggestions(query: String): List<String> = emptyList()

        override suspend fun getVideoInfo(url: String): YpVideoInfo = throw unsupported()

        override suspend fun getChannelInfo(url: String, pageKey: String?): YpChannelInfo = throw unsupported()

        override suspend fun getPlaylistInfo(url: String, pageKey: String?): YpPlaylistInfo = throw unsupported()

        override fun shutdown() {
            shutdownCalled = true
        }

        private fun unsupported() = EngineException("unsupported", engineId = descriptor.id, recoverable = false)
    }

    private lateinit var prefs: InMemoryPrefs
    private lateinit var manager: EngineManager

    @Before
    fun setUp() {
        EngineManager.resetForTests()
        prefs = InMemoryPrefs()
        manager = EngineManager.init(prefs)
    }

    @Test
    fun `active falls back to newpipe when selection is unknown`() {
        val newpipe = FakeEngine(EngineManager.NEWPIPE_ID)
        manager.register(newpipe)
        prefs.putString(EngineManager.KEY_ENGINE, "ghost")

        assertEquals(newpipe, manager.active())
    }

    @Test
    fun `switchTo persists selection and shuts down previous engine`() {
        val newpipe = FakeEngine(EngineManager.NEWPIPE_ID)
        val other = FakeEngine("other")
        manager.register(newpipe)
        manager.register(other)

        val result = manager.switchTo("other")

        assertTrue(result is EngineSwitchResult.Success)
        assertEquals("other", prefs.getString(EngineManager.KEY_ENGINE, null))
        assertTrue(newpipe.shutdownCalled)
        assertEquals(other, manager.active())
    }

    @Test
    fun `switchTo rejects unregistered or unavailable engines without side effects`() {
        val newpipe = FakeEngine(EngineManager.NEWPIPE_ID)
        val broken = FakeEngine("broken", availability = EngineState.UNAVAILABLE)
        manager.register(newpipe)
        manager.register(broken)

        val unregistered = manager.switchTo("ghost")
        val unavailable = manager.switchTo("broken")

        assertTrue(unregistered is EngineSwitchResult.Failure)
        assertTrue(unavailable is EngineSwitchResult.Failure)
        assertEquals(EngineManager.NEWPIPE_ID, prefs.getString(EngineManager.KEY_ENGINE, EngineManager.NEWPIPE_ID))
        assertFalse(newpipe.shutdownCalled)
        assertEquals(newpipe, manager.active())
    }

    @Test
    fun `execute serves from active engine without fallback`() = runBlocking {
        val newpipe = FakeEngine(EngineManager.NEWPIPE_ID)
        manager.register(newpipe)

        val result = manager.execute(EngineCapability.SEARCH_VIDEOS) { engine ->
            engine.search("q", SearchFilter.ALL, null)
        }

        assertFalse(result.fallbackUsed)
        assertEquals(EngineManager.NEWPIPE_ID, result.servedBy.id)
    }

    @Test(expected = EngineException::class)
    fun `execute rethrows when fallback is disabled`(): Unit = runBlocking {
        val failing = FakeEngine(
            EngineManager.NEWPIPE_ID,
            failWith = EngineException("boom", engineId = EngineManager.NEWPIPE_ID, recoverable = true)
        )
        manager.register(failing)

        runBlocking {
            manager.execute(EngineCapability.SEARCH_VIDEOS) { engine ->
                engine.search("q", SearchFilter.ALL, null)
            }
        }
        Unit
    }

    @Test
    fun `execute uses one bounded fallback when opted in and never changes preference`() = runBlocking {
        val failing = FakeEngine(
            EngineManager.NEWPIPE_ID,
            failWith = EngineException("boom", engineId = EngineManager.NEWPIPE_ID, recoverable = true)
        )
        val backup = FakeEngine("backup")
        manager.register(failing)
        manager.register(backup)
        manager.setAutoFallbackEnabled(true)

        val result = manager.execute(EngineCapability.SEARCH_VIDEOS) { engine ->
            engine.search("q", SearchFilter.ALL, null)
        }

        assertTrue(result.fallbackUsed)
        assertEquals("backup", result.servedBy.id)
        assertFalse(prefs.strings.containsKey(EngineManager.KEY_ENGINE))
    }

    @Test(expected = EngineException::class)
    fun `execute fails when no fallback engine has the required capability`(): Unit = runBlocking {
        val failing = FakeEngine(
            EngineManager.NEWPIPE_ID,
            failWith = EngineException("boom", engineId = EngineManager.NEWPIPE_ID, recoverable = true)
        )
        val backupWithoutCapability = FakeEngine("backup", capabilities = emptySet())
        manager.register(failing)
        manager.register(backupWithoutCapability)
        manager.setAutoFallbackEnabled(true)

        runBlocking {
            manager.execute(EngineCapability.SEARCH_VIDEOS) { engine ->
                engine.search("q", SearchFilter.ALL, null)
            }
        }
        Unit
    }
}
