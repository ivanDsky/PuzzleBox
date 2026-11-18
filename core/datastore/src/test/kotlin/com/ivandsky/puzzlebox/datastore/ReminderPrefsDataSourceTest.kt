package com.ivandsky.puzzlebox.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ivandsky.puzzlebox.model.ReminderSettings
import java.io.File
import java.time.LocalTime
import java.util.UUID
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

/** The reminder choice file: defaults, round trip, minute truncation, the range guard and lazy file creation (spec A1, A3, A12). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderPrefsDataSourceTest {

    private val default = ReminderSettings(enabled = false, time = LocalTime.of(19, 0))

    private fun tempFile(): File =
        File(RuntimeEnvironment.getApplication().cacheDir, "reminders-${UUID.randomUUID()}.preferences_pb")

    /** DataStore allows one live instance per file, so each [block] is its own "process" and ends before the next opens the file. */
    private suspend fun TestScope.process(file: File, block: suspend (ReminderPrefsDataSource) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        block(ReminderPrefsDataSource(PreferenceDataStoreFactory.create(scope = scope) { file }))
        scope.cancel()
    }

    private suspend fun TestScope.read(file: File): ReminderSettings {
        var settings: ReminderSettings? = null
        process(file) { settings = it.settings.first() }
        return checkNotNull(settings)
    }

    @Test
    fun `an empty store reads off at 7 PM`() = runTest {
        assertEquals(default, read(tempFile()))
    }

    @Test
    fun `the enabled flag and the time are read back by a fresh instance over the same file`() = runTest {
        val file = tempFile()

        process(file) {
            it.setEnabled(true)
            it.setTime(LocalTime.of(6, 45))
        }

        assertEquals(ReminderSettings(enabled = true, time = LocalTime.of(6, 45)), read(file))
    }

    @Test
    fun `turning it off keeps the chosen time`() = runTest {
        val file = tempFile()

        process(file) {
            it.setTime(LocalTime.of(8, 30))
            it.setEnabled(true)
            it.setEnabled(false)
        }

        assertEquals(ReminderSettings(enabled = false, time = LocalTime.of(8, 30)), read(file))
    }

    @Test
    fun `a time with seconds is truncated to the minute on write`() = runTest {
        val file = tempFile()

        process(file) { it.setTime(LocalTime.of(7, 5, 59, 999_000_000)) }

        assertEquals(LocalTime.of(7, 5), read(file).time)
    }

    @Test
    fun `the first and last minute of the day round trip`() = runTest {
        val file = tempFile()

        process(file) { it.setTime(LocalTime.MIDNIGHT) }
        assertEquals(LocalTime.MIDNIGHT, read(file).time)

        process(file) { it.setTime(LocalTime.of(23, 59)) }
        assertEquals(LocalTime.of(23, 59), read(file).time)
    }

    @Test
    fun `a stored minute outside 0 to 1439 reads as 7 PM`() = runTest {
        val key = intPreferencesKey("time_minutes")
        for (stored in listOf(-1, 1440, 100_000)) {
            val file = tempFile()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            PreferenceDataStoreFactory.create(scope = scope) { file }.edit { it[key] = stored }
            scope.cancel()

            assertEquals("stored $stored", LocalTime.of(19, 0), read(file).time)
        }
    }

    @Test
    fun `no file exists after construction and reading, only after the first write`() = runTest {
        val file = tempFile()

        process(file) {
            assertEquals(default, it.settings.first())
            assertFalse(file.exists())
        }
        assertFalse(file.exists())

        process(file) { it.setEnabled(true) }

        assertTrue(file.exists())
    }
}
