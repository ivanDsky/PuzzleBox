package com.ivandsky.puzzlebox.data.visibility

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import com.ivandsky.puzzlebox.model.ShareCode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hidden puzzles over a real DataStore file; a second store over the same file stands in for a new process. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreHiddenPuzzlesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "cloud_sync_state.preferences_pb") }

    private suspend fun TestScope.process(block: suspend (SyncStateDataSource, HiddenPuzzles) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val source = SyncStateDataSource(PreferenceDataStoreFactory.create(scope = scope) { file })
        block(source, DataStoreHiddenPuzzles(source))
        scope.cancel()
    }

    @Test
    fun `a hidden puzzle survives a new process and keeps the earlier ones`() = runTest {
        process { _, hidden ->
            hidden.add(FIRST)
            hidden.add(SECOND)
        }

        process { _, hidden -> assertEquals(setOf(FIRST, SECOND), hidden.codes()) }
    }

    @Test
    fun `a state file from before the hide set reads as empty`() = runTest {
        process { source, _ -> source.setLastUid("u1") }

        process { _, hidden -> assertEquals(emptySet<ShareCode>(), hidden.codes()) }
    }

    @Test
    fun `hiding the same puzzle twice keeps one`() = runTest {
        process { _, hidden ->
            hidden.add(FIRST)
            hidden.add(FIRST)

            assertEquals(setOf(FIRST), hidden.codes())
        }
    }

    @Test
    fun `the hide set is not tied to a signed in player`() = runTest {
        process { source, hidden ->
            hidden.add(FIRST)
            source.forget("u1")

            assertEquals(setOf(FIRST), hidden.codes())
        }
    }

    private companion object {
        val FIRST = checkNotNull(ShareCode.parse("K7Q2MX"))
        val SECOND = checkNotNull(ShareCode.parse("ABC234"))
    }
}
