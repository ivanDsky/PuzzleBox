package com.ivandsky.puzzlebox.data.cloud

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The store over a real Preferences DataStore file. DataStore allows one live instance per file, so a "process"
 * is a scope that is cancelled afterwards; the next store over the same file stands in for a new process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreSyncStateStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "cloud_sync_state.preferences_pb") }

    private suspend fun TestScope.process(block: suspend (SyncStateStore) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        block(DataStoreSyncStateStore(SyncStateDataSource(dataStore)))
        scope.cancel()
    }

    @Test
    fun `values round trip across a new store on the same file`() = runTest {
        process { store ->
            store.setCursor("u1", "completions", 42L)
            store.setLastUid("u1")
            store.markRestoreShown("u1")
            assertEquals(3, store.countWrites("u1", "2026-11-01", 3))
        }

        process { store ->
            assertEquals(42L, store.cursor("u1", "completions"))
            assertEquals("u1", store.lastUid.value)
            assertTrue(store.restoreShown("u1"))
            assertEquals(5, store.countWrites("u1", "2026-11-01", 2))
        }
    }

    @Test
    fun `a fresh file has no cursor no last uid no restore flag`() = runTest {
        process { store ->
            assertNull(store.cursor("u1", "completions"))
            assertNull(store.lastUid.value)
            assertFalse(store.restoreShown("u1"))
        }
    }

    @Test
    fun `last uid flow follows setLastUid`() = runTest {
        process { store ->
            store.setLastUid("u1")
            assertEquals("u1", store.lastUid.value)
            store.setLastUid("u2")
            assertEquals("u2", store.lastUid.value)
        }
    }

    @Test
    fun `two uids never see each other's state`() = runTest {
        process { store ->
            store.setCursor("u1", "completions", 1L)
            store.setCursor("u2", "completions", 2L)
            store.markRestoreShown("u1")
            store.countWrites("u1", "2026-11-01", 4)

            assertEquals(1L, store.cursor("u1", "completions"))
            assertEquals(2L, store.cursor("u2", "completions"))
            assertNull(store.cursor("u3", "completions"))
            assertFalse(store.restoreShown("u2"))
            assertEquals(7, store.countWrites("u2", "2026-11-01", 7))
        }
    }

    @Test
    fun `a uid that prefixes another uid never reaches its keys`() = runTest {
        process { store ->
            store.setCursor("a", "k", 1L)
            store.setCursor("a:b", "k", 2L)
            store.setCursor("a/b", "k", 3L)
            store.markRestoreShown("a:b")

            store.forget("a")

            assertNull(store.cursor("a", "k"))
            assertEquals(2L, store.cursor("a:b", "k"))
            assertEquals(3L, store.cursor("a/b", "k"))
            assertTrue(store.restoreShown("a:b"))
        }
    }

    @Test
    fun `kinds of one uid are independent`() = runTest {
        process { store ->
            store.setCursor("u1", "completions", 1L)
            store.setCursor("u1", "settings", 2L)
            assertEquals(1L, store.cursor("u1", "completions"))
            assertEquals(2L, store.cursor("u1", "settings"))
        }
    }

    @Test
    fun `the write counter accumulates within a day and resets on a new day`() = runTest {
        process { store ->
            assertEquals(2, store.countWrites("u1", "2026-11-01", 2))
            assertEquals(5, store.countWrites("u1", "2026-11-01", 3))
            assertEquals(1, store.countWrites("u1", "2026-11-02", 1))
            assertEquals(4, store.countWrites("u1", "2026-11-02", 3))
        }
    }

    @Test
    fun `forget drops cursors and the restore flag but keeps the last uid`() = runTest {
        process { store ->
            store.setLastUid("u1")
            store.setCursor("u1", "completions", 1L)
            store.setCursor("u1", "settings", 2L)
            store.markRestoreShown("u1")
            store.setCursor("u2", "completions", 9L)

            store.forget("u1")

            assertNull(store.cursor("u1", "completions"))
            assertNull(store.cursor("u1", "settings"))
            assertFalse(store.restoreShown("u1"))
            assertEquals("u1", store.lastUid.value)
            assertEquals(9L, store.cursor("u2", "completions"))
        }

        process { store ->
            assertEquals("u1", store.lastUid.value)
            assertNull(store.cursor("u1", "completions"))
        }
    }
}
