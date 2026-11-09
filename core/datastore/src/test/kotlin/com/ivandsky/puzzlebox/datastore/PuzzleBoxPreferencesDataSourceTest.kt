package com.ivandsky.puzzlebox.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Robolectric because the production artifact is Preferences DataStore; the file it
 * writes is a plain temp file, so a fresh data source over the same file is an exact
 * stand-in for a new process reading persisted state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PuzzleBoxPreferencesDataSourceTest {

    private fun newDataSource(file: File): PuzzleBoxPreferencesDataSource =
        PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
            ) { file },
        )

    private fun tempFile(): File =
        File(
            RuntimeEnvironment.getApplication().cacheDir,
            "prefs-${UUID.randomUUID()}.preferences_pb",
        )

    @Test
    fun `first launch defaults to system theme`() = runTest {
        val dataSource = newDataSource(tempFile())

        assertEquals(ThemeMode.SYSTEM, dataSource.preferences.first().themeMode)
    }

    @Test
    fun `theme choice survives a new data source over the same file`() = runTest {
        val file = tempFile()
        // DataStore allows only one live instance per file — one per process. Cancelling
        // the first scope ends the "process"; the restarted process re-reads the file.
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstProcess.setThemeMode(ThemeMode.DARK)
        firstScope.cancel()

        val restartedProcess = newDataSource(file)
        assertEquals(ThemeMode.DARK, restartedProcess.preferences.first().themeMode)
    }

    @Test
    fun `first launch auto-shows the witness`() = runTest {
        val dataSource = newDataSource(tempFile())

        assertTrue(dataSource.preferences.first().autoShowWitness)
    }

    @Test
    fun `auto-show off survives a new data source over the same file`() = runTest {
        val file = tempFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstProcess.setAutoShowWitness(false)
        firstScope.cancel()

        val restartedProcess = newDataSource(file)
        assertFalse(restartedProcess.preferences.first().autoShowWitness)
    }
}
