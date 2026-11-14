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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The skipped solves over a real DataStore file; a second store over the same file stands in for a new process. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreSkippedPostsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "cloud_sync_state.preferences_pb") }

    private suspend fun TestScope.process(block: suspend (SkippedPosts) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        block(DataStoreSkippedPosts(SyncStateDataSource(dataStore)))
        scope.cancel()
    }

    @Test
    fun `a skipped solve survives a new process and keeps the earlier ones`() = runTest {
        process { skipped ->
            skipped.add("u1", "zip/2026-10-31/592")
            skipped.add("u1", "zip/2026-10-30/591")
        }

        process { skipped ->
            assertTrue(skipped.contains("u1", "zip/2026-10-31/592"))
            assertTrue(skipped.contains("u1", "zip/2026-10-30/591"))
            assertFalse(skipped.contains("u1", "zip/2026-10-31/593"))
        }
    }

    @Test
    fun `a skipped solve belongs to its uid alone`() = runTest {
        process { skipped ->
            skipped.add("u1", "zip/2026-10-31/592")
            skipped.add("u1/x", "zip/2026-10-31/593")

            assertFalse(skipped.contains("u2", "zip/2026-10-31/592"))
            assertFalse(skipped.contains("u1", "zip/2026-10-31/593"))
        }
    }
}
