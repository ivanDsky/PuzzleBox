package com.ivandsky.puzzlebox.data.insights

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The adapter over a real Preferences DataStore: what it reads is what a mark wrote, and it outlives the adapter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreRecapOpeningsTest {

    private val file = File(RuntimeEnvironment.getApplication().cacheDir, "openings-${UUID.randomUUID()}.preferences_pb")

    private fun openings(scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())) =
        DataStoreRecapOpenings(PuzzleBoxPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { file }))

    @Test
    fun `nothing is opened at first`() = runTest {
        assertEquals(emptySet<String>(), openings().observe().first())
    }

    @Test
    fun `a mark is emitted to a live reader and kept per period`() = runTest {
        val openings = openings()

        openings.observe().test {
            assertEquals(emptySet<String>(), awaitItem())
            openings.markOpened("2026-10")
            assertEquals(setOf("2026-10"), awaitItem())
            openings.markOpened("2026-10")
            openings.markOpened("2026-11")
            assertEquals(setOf("2026-10", "2026-11"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mark survives a new adapter over the same file`() = runTest {
        val firstProcess = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        openings(firstProcess).markOpened("2026-10")
        firstProcess.cancel()

        assertEquals(setOf("2026-10"), openings().observe().first())
    }
}
