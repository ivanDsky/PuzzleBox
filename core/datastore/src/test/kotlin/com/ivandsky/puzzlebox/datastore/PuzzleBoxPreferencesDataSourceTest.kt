package com.ivandsky.puzzlebox.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.model.ThemeRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * Robolectric because the production artifact is Preferences DataStore; the file it
 * writes is a plain temp file, so a fresh data source over the same file is an exact
 * stand-in for a new process reading persisted state.
 */
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
    fun `a healthy theme read is not flagged as failed`() = runTest {
        val dataSource = newDataSource(tempFile())
        dataSource.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeRead(ThemeMode.DARK, failed = false), dataSource.themeRead.first())
    }

    @Test
    fun `an unreadable preferences file reads as the default theme flagged failed`() = runTest {
        val file = tempFile().apply { writeBytes(ByteArray(64) { 0x7F }) }

        assertEquals(ThemeRead(ThemeMode.SYSTEM, failed = true), newDataSource(file).themeRead.first())
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

    @Test
    fun `first launch shows replay errors`() = runTest {
        val dataSource = newDataSource(tempFile())

        assertTrue(dataSource.preferences.first().showReplayErrors)
    }

    @Test
    fun `replay errors off survives a new data source over the same file, and on again too`() = runTest {
        val file = tempFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstProcess.setShowReplayErrors(false)
        firstScope.cancel()

        val secondScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val secondProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = secondScope) { file },
        )
        assertFalse(secondProcess.preferences.first().showReplayErrors)
        secondProcess.setShowReplayErrors(true)
        secondScope.cancel()

        assertTrue(newDataSource(file).preferences.first().showReplayErrors)
    }

    @Test
    fun `the Ranks hub defaults to Friends and a Global pick survives a new data source over the same file`() = runTest {
        val file = tempFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )
        assertTrue(firstProcess.preferences.first().ranksFriendsDefault)

        firstProcess.setRanksFriendsDefault(false)
        firstScope.cancel()

        assertFalse(newDataSource(file).preferences.first().ranksFriendsDefault)
    }

    @Test
    fun `the Ranks hub defaults to All games and a LinkedIn games pick survives a new data source over the same file`() = runTest {
        val file = tempFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )
        assertFalse(firstProcess.preferences.first().ranksLinkedInDefault)

        firstProcess.setRanksLinkedInDefault(true)
        firstScope.cancel()

        assertTrue(newDataSource(file).preferences.first().ranksLinkedInDefault)
    }

    @Test
    fun `vibration is on until switched off and an off survives a new data source over the same file`() = runTest {
        val file = tempFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )
        assertTrue(firstProcess.preferences.first().vibration)

        firstProcess.setVibration(false)
        firstScope.cancel()

        assertFalse(newDataSource(file).preferences.first().vibration)
    }

    @Test
    fun `self-duel time left is on until switched off and an off survives a new data source over the same file`() = runTest {
        val file = tempFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )
        assertTrue(firstProcess.preferences.first().showSelfDuelTimeLeft)

        firstProcess.setShowSelfDuelTimeLeft(false)
        firstScope.cancel()

        assertFalse(newDataSource(file).preferences.first().showSelfDuelTimeLeft)
    }

    /** The file as the build that shipped the camelCase key left it: [legacy] under it, or nothing at all. */
    private suspend fun TestScope.legacyFile(legacy: Boolean?): File {
        val file = tempFile()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        PreferenceDataStoreFactory.create(scope = scope) { file }.edit { preferences ->
            legacy?.let { preferences[booleanPreferencesKey("showSelfDuelTimeLeft")] = it }
        }
        scope.cancel()
        return file
    }

    /** [block] over the file as a restarted process reads it, running the preferences migrations; its DataStore closes after. */
    private suspend fun TestScope.restarted(file: File, block: suspend (PuzzleBoxPreferencesDataSource) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        block(
            PuzzleBoxPreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = scope, migrations = puzzleBoxPreferencesMigrations) { file },
            ),
        )
        scope.cancel()
    }

    @Test
    fun `a self-duel time left switched off under the old key stays off`() = runTest {
        restarted(legacyFile(legacy = false)) { assertFalse(it.preferences.first().showSelfDuelTimeLeft) }
    }

    @Test
    fun `a self-duel time left switched on under the old key stays on and a later switch-off persists`() = runTest {
        val file = legacyFile(legacy = true)
        restarted(file) {
            assertTrue(it.preferences.first().showSelfDuelTimeLeft)
            it.setShowSelfDuelTimeLeft(false)
        }

        restarted(file) { assertFalse(it.preferences.first().showSelfDuelTimeLeft) }
    }

    @Test
    fun `with no old key the self-duel time left is on and the Settings write persists under the new key`() = runTest {
        val file = legacyFile(legacy = null)
        restarted(file) {
            assertTrue(it.preferences.first().showSelfDuelTimeLeft)
            it.setShowSelfDuelTimeLeft(false)
        }

        restarted(file) { assertFalse(it.preferences.first().showSelfDuelTimeLeft) }
    }

    @Test
    fun `an unset game flag emits its default`() = runTest {
        val dataSource = newDataSource(tempFile())

        assertTrue(dataSource.gameFlag(GameId("g"), "aid", true).first())
        assertFalse(dataSource.gameFlag(GameId("g"), "aid", false).first())
    }

    @Test
    fun `a game flag persists and is independent per game`() = runTest {
        val file = tempFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = PuzzleBoxPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstProcess.setGameFlag(GameId("a"), "aid", false)
        firstScope.cancel()

        val restarted = newDataSource(file)
        assertFalse(restarted.gameFlag(GameId("a"), "aid", true).first())
        assertTrue(restarted.gameFlag(GameId("b"), "aid", true).first())
    }
}
