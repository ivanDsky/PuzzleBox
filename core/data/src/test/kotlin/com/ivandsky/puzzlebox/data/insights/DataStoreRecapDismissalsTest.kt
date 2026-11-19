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

/** The adapter over a real Preferences DataStore: what it reads is what a dismissal wrote, and it outlives the adapter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreRecapDismissalsTest {

    private val file = File(RuntimeEnvironment.getApplication().cacheDir, "recaps-${UUID.randomUUID()}.preferences_pb")

    private fun dismissals(scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())) =
        DataStoreRecapDismissals(PuzzleBoxPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { file }))

    @Test
    fun `nothing is dismissed at first`() = runTest {
        assertEquals(emptySet<String>(), dismissals().observe().first())
    }

    @Test
    fun `a dismissal is emitted to a live reader and kept per period`() = runTest {
        val dismissals = dismissals()

        dismissals.observe().test {
            assertEquals(emptySet<String>(), awaitItem())
            dismissals.dismiss("2026-10")
            assertEquals(setOf("2026-10"), awaitItem())
            dismissals.dismiss("2026-10")
            dismissals.dismiss("2026-11")
            assertEquals(setOf("2026-10", "2026-11"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a dismissal survives a new adapter over the same file`() = runTest {
        val firstProcess = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        dismissals(firstProcess).dismiss("2026-10")
        firstProcess.cancel()

        assertEquals(setOf("2026-10"), dismissals().observe().first())
    }
}
