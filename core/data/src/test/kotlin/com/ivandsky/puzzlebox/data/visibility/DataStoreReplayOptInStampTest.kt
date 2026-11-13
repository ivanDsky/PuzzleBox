package com.ivandsky.puzzlebox.data.visibility

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stamp over a real DataStore file; a second store over the same file stands in for a new process. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreReplayOptInStampTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "cloud_sync_state.preferences_pb") }

    private suspend fun TestScope.process(block: suspend (ReplayOptInStamp) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        block(DataStoreReplayOptInStamp(SyncStateDataSource(dataStore)))
        scope.cancel()
    }

    @Test
    fun `a stamp survives a new process and a second mark keeps the first`() = runTest {
        process { stamp ->
            assertNull(stamp.since("u1"))
            stamp.markIfAbsent("u1", 100L)
            stamp.markIfAbsent("u1", 200L)
        }

        process { stamp -> assertEquals(100L, stamp.since("u1")) }
    }

    @Test
    fun `clearing lets the next opt-in be stamped afresh`() = runTest {
        process { stamp ->
            stamp.markIfAbsent("u1", 100L)
            stamp.clear("u1")
            assertNull(stamp.since("u1"))
            stamp.markIfAbsent("u1", 300L)
            assertEquals(300L, stamp.since("u1"))
        }
    }

    @Test
    fun `a stamp belongs to its uid alone`() = runTest {
        process { stamp ->
            stamp.markIfAbsent("u1", 100L)
            stamp.markIfAbsent("u1/x", 150L)
            stamp.clear("u1/x")

            assertEquals(100L, stamp.since("u1"))
            assertNull(stamp.since("u1/x"))
            assertNull(stamp.since("u2"))
        }
    }
}
