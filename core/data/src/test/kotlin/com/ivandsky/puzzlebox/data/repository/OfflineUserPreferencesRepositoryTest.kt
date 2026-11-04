package com.ivandsky.puzzlebox.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
 * The repository over a real Preferences DataStore data source (persistence itself is
 * core:datastore's PuzzleBoxPreferencesDataSourceTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineUserPreferencesRepositoryTest {

    private fun newRepository(): OfflineUserPreferencesRepository =
        OfflineUserPreferencesRepository(
            PuzzleBoxPreferencesDataSource(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                ) {
                    File(
                        RuntimeEnvironment.getApplication().cacheDir,
                        "prefs-${UUID.randomUUID()}.preferences_pb",
                    )
                },
            ),
        )

    @Test
    fun `writes round-trip every mode`() = runTest {
        val repository = newRepository()

        for (mode in ThemeMode.entries) {
            repository.setThemeMode(mode)
            assertEquals(mode, repository.themeMode.first())
        }
    }
}
