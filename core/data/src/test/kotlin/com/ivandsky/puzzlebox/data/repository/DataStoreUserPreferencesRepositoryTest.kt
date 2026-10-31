package com.ivandsky.puzzlebox.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.data.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Robolectric because the production artifact is Preferences DataStore; the file it
 * writes is a plain temp file, so a fresh repository over the same file is an exact
 * stand-in for a new process reading persisted state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreUserPreferencesRepositoryTest {

    private fun newRepository(file: File): DataStoreUserPreferencesRepository =
        DataStoreUserPreferencesRepository(
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
        val repository = newRepository(tempFile())

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun `theme choice survives a new repository over the same file`() = runTest {
        val file = tempFile()
        // DataStore allows only one live instance per file — one per process. Cancelling
        // the first scope ends the "process"; the restarted process re-reads the file.
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = DataStoreUserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstProcess.setThemeMode(ThemeMode.DARK)
        firstScope.cancel()

        val restartedProcess = newRepository(file)
        assertEquals(ThemeMode.DARK, restartedProcess.themeMode.first())
    }

    @Test
    fun `writes round-trip every mode`() = runTest {
        val repository = newRepository(tempFile())

        for (mode in ThemeMode.entries) {
            repository.setThemeMode(mode)
            assertEquals(mode, repository.themeMode.first())
        }
    }
}
