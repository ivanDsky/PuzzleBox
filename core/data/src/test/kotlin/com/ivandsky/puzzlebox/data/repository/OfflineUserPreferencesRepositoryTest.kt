package com.ivandsky.puzzlebox.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
 * The repository over a real Preferences DataStore data source (persistence itself is
 * core:datastore's PuzzleBoxPreferencesDataSourceTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineUserPreferencesRepositoryTest {

    private val zip = GameId("zip")
    private val queens = GameId("queens")

    private fun tempFile(): File =
        File(
            RuntimeEnvironment.getApplication().cacheDir,
            "prefs-${UUID.randomUUID()}.preferences_pb",
        )

    private fun newRepository(
        file: File = tempFile(),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
    ): OfflineUserPreferencesRepository =
        OfflineUserPreferencesRepository(
            PuzzleBoxPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { file }),
        )

    private fun TestScope.processScope(): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())

    @Test
    fun `writes round-trip every mode`() = runTest {
        val repository = newRepository()

        for (mode in ThemeMode.entries) {
            repository.setThemeMode(mode)
            assertEquals(mode, repository.themeMode.first())
        }
    }

    @Test
    fun `tutorial is not seen on first launch`() = runTest {
        val repository = newRepository()

        assertFalse(repository.tutorialSeen(zip).first())
        assertFalse(repository.tutorialSeen(queens).first())
    }

    @Test
    fun `marking one game seen leaves the others unseen`() = runTest {
        val repository = newRepository()

        repository.markTutorialSeen(zip)

        assertTrue(repository.tutorialSeen(zip).first())
        assertFalse(repository.tutorialSeen(queens).first())
    }

    @Test
    fun `marking seen twice keeps every seen game`() = runTest {
        val repository = newRepository()

        repository.markTutorialSeen(zip)
        repository.markTutorialSeen(queens)
        repository.markTutorialSeen(zip)

        assertTrue(repository.tutorialSeen(zip).first())
        assertTrue(repository.tutorialSeen(queens).first())
    }

    @Test
    fun `seen tutorial survives a fresh repository over the same store`() = runTest {
        val file = tempFile()
        // DataStore allows only one live instance per file; cancelling the first scope
        // ends the "process" and the restarted one re-reads the file.
        val firstScope = processScope()
        newRepository(file, firstScope).markTutorialSeen(zip)
        firstScope.cancel()

        val restarted = newRepository(file, processScope())

        assertTrue(restarted.tutorialSeen(zip).first())
        assertFalse(restarted.tutorialSeen(queens).first())
    }

    @Test
    fun `the witness auto-shows on first launch`() = runTest {
        assertTrue(newRepository().autoShowWitness.first())
    }

    @Test
    fun `auto-show round-trips and survives a fresh repository over the same store`() = runTest {
        val file = tempFile()
        val firstScope = processScope()
        val first = newRepository(file, firstScope)

        first.setAutoShowWitness(false)
        assertFalse(first.autoShowWitness.first())
        firstScope.cancel()

        val restarted = newRepository(file, processScope())
        assertFalse(restarted.autoShowWitness.first())
        restarted.setAutoShowWitness(true)
        assertTrue(restarted.autoShowWitness.first())
    }
}
