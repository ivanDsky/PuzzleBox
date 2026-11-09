package com.ivandsky.puzzlebox.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.GameSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineGameSettingsRepositoryTest {

    private val on = GameSetting(GameId("g"), "aid", default = true)
    private val off = GameSetting(GameId("g"), "other", default = false)

    private fun newRepository(): OfflineGameSettingsRepository {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "prefs-${UUID.randomUUID()}.preferences_pb")
        return OfflineGameSettingsRepository(
            PuzzleBoxPreferencesDataSource(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                ) { file },
            ),
        )
    }

    @Test
    fun `an unset setting emits its default`() = runTest {
        val repository = newRepository()

        assertTrue(repository.flag(on).first())
        assertFalse(repository.flag(off).first())
    }

    @Test
    fun `a write to another setting does not re-emit`() = runTest {
        val repository = newRepository()
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.flag(on).toList(seen) }

        repository.setFlag(off, true)
        repository.setFlag(on, false)
        job.cancel()

        assertEquals(listOf(true, false), seen)
    }
}
