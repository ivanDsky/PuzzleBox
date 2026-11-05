package com.ivandsky.puzzlebox.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.KeptSessionsDataSource
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * The kept-session store over a real `kept_sessions`-style Preferences DataStore file. DataStore
 * allows one live instance per file, so each "process" gets its own scope; cancelling it ends the
 * process and a fresh repository over the same file stands in for an app restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreKeptSessionRepositoryTest {

    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val day = LocalDate.of(2026, 11, 4)

    private fun tempFile(): File =
        File(RuntimeEnvironment.getApplication().cacheDir, "kept-${UUID.randomUUID()}.preferences_pb")

    /** Runs [block] against one process's repository over [file], then ends that process. */
    private suspend fun TestScope.inProcess(file: File, block: suspend (KeptSessionRepository) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DataStoreKeptSessionRepository(
            KeptSessionsDataSource(PreferenceDataStoreFactory.create(scope = scope) { file }),
        )
        try {
            block(repository)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `first launch keeps nothing`() = runTest {
        inProcess(tempFile()) { repository ->
            assertEquals(emptyMap<GameId, KeptSession>(), repository.kept.first())
        }
    }

    @Test
    fun `kept sessions round-trip across a restart, one per game`() = runTest {
        val file = tempFile()
        val zipSession = KeptSession(zip, day, elapsedMs = 42_000, envelope = byteArrayOf(1, 3, 'z'.code.toByte(), 7))
        val tangoSession = KeptSession(tango, day.minusDays(1), elapsedMs = 0, envelope = ByteArray(0))

        inProcess(file) { repository ->
            repository.keep(zipSession)
            repository.keep(tangoSession)
        }

        inProcess(file) { restarted ->
            assertEquals(mapOf(zip to zipSession, tango to tangoSession), restarted.kept.first())
        }
    }

    @Test
    fun `keeping again replaces only that game's entry`() = runTest {
        val file = tempFile()
        val tangoSession = KeptSession(tango, day, elapsedMs = 5_000, envelope = byteArrayOf(9))
        val replacement = KeptSession(zip, day.plusDays(1), elapsedMs = 90_500, envelope = byteArrayOf(4, 5))

        inProcess(file) { repository ->
            repository.keep(KeptSession(zip, day, elapsedMs = 1_000, envelope = byteArrayOf(1, 2, 3)))
            repository.keep(tangoSession)
            repository.keep(replacement)
        }

        inProcess(file) { restarted ->
            assertEquals(mapOf(zip to replacement, tango to tangoSession), restarted.kept.first())
        }
    }

    @Test
    fun `discard removes only that game and survives a restart`() = runTest {
        val file = tempFile()
        val tangoSession = KeptSession(tango, day, elapsedMs = 5_000, envelope = byteArrayOf(9))

        inProcess(file) { repository ->
            repository.keep(KeptSession(zip, day, elapsedMs = 1_000, envelope = byteArrayOf(1)))
            repository.keep(tangoSession)
            repository.discard(zip)
            repository.discard(GameId("never-kept"))
        }

        inProcess(file) { restarted ->
            assertEquals(mapOf(tango to tangoSession), restarted.kept.first())
        }
    }

    @Test
    fun `a kept session equals by content, envelope included`() {
        val a = KeptSession(zip, day, 10, byteArrayOf(1, 2))
        assertEquals(a, KeptSession(zip, day, 10, byteArrayOf(1, 2)))
        assertEquals(a.hashCode(), KeptSession(zip, day, 10, byteArrayOf(1, 2)).hashCode())
        assertNotEquals(a, KeptSession(zip, day, 10, byteArrayOf(1, 3)))
        assertNotEquals(a, KeptSession(zip, day, 11, byteArrayOf(1, 2)))
    }
}
