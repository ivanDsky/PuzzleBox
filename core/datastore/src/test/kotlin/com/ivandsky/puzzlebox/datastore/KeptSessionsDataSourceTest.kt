package com.ivandsky.puzzlebox.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** The `kept_<gameId>_{blob,day,elapsed}` key layout: complete entries read back, anything else is skipped. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeptSessionsDataSourceTest {

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
    ) { File(RuntimeEnvironment.getApplication().cacheDir, "kept-${UUID.randomUUID()}.preferences_pb") }

    private val dataSource = KeptSessionsDataSource(dataStore)

    @Test
    fun `an entry round-trips and game ids with underscores survive the key layout`() = runTest {
        val game = GameId("mini_sudoku")
        dataSource.put(KeptSessionsDataSource.Entry(game, LocalDate.of(2026, 11, 4), 1_234, byteArrayOf(1, 2)))

        val entry = dataSource.entries.first().single()
        assertEquals(game, entry.game)
        assertEquals(LocalDate.of(2026, 11, 4), entry.day)
        assertEquals(1_234L, entry.elapsedMs)
        assertArrayEquals(byteArrayOf(1, 2), entry.envelope)
    }

    @Test
    fun `partial, unparseable and foreign keys are skipped, never guessed`() = runTest {
        dataStore.edit { preferences ->
            // Blob without day or elapsed.
            preferences[byteArrayPreferencesKey("kept_zip_blob")] = byteArrayOf(1)
            // Unparseable day.
            preferences[byteArrayPreferencesKey("kept_tango_blob")] = byteArrayOf(2)
            preferences[stringPreferencesKey("kept_tango_day")] = "yesterday"
            preferences[longPreferencesKey("kept_tango_elapsed")] = 5
            // Day and elapsed without a blob, and a key this file never authored.
            preferences[stringPreferencesKey("kept_queens_day")] = "2026-11-04"
            preferences[longPreferencesKey("kept_queens_elapsed")] = 5
            preferences[stringPreferencesKey("theme_mode")] = "DARK"
        }

        assertEquals(emptyList<KeptSessionsDataSource.Entry>(), dataSource.entries.first())
    }

    @Test
    fun `remove clears every key of that game`() = runTest {
        val game = GameId("zip")
        dataSource.put(KeptSessionsDataSource.Entry(game, LocalDate.of(2026, 11, 4), 7, byteArrayOf(3)))

        dataSource.remove(game)

        assertEquals(emptyMap<String, Any>(), dataStore.data.first().asMap().mapKeys { it.key.name })
    }
}
