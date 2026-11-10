package com.ivandsky.puzzlebox.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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
 * Robolectric because the production artifact is Preferences DataStore; the file it
 * writes is a plain temp file, so a fresh data source over the same file is an exact
 * stand-in for a new process reading persisted state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInLinkDataSourceTest {

    private val none = LinkedInLinkPrefs(profileId = null, consentAtMs = null, pauseReason = null)

    private fun tempFile(): File =
        File(
            RuntimeEnvironment.getApplication().cacheDir,
            "prefs-${UUID.randomUUID()}.preferences_pb",
        )

    /**
     * DataStore allows one live instance per file — one per process. Running [block] in its
     * own scope and cancelling it afterwards ends that "process" so the next one can open the file.
     */
    private suspend fun TestScope.process(file: File, block: suspend (LinkedInLinkDataSource) -> Unit) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        block(LinkedInLinkDataSource(PreferenceDataStoreFactory.create(scope = scope) { file }))
        scope.cancel()
    }

    private suspend fun TestScope.read(file: File): LinkedInLinkPrefs {
        var prefs: LinkedInLinkPrefs? = null
        process(file) { prefs = it.link.first() }
        return checkNotNull(prefs)
    }

    @Test
    fun `fresh file gives all-null prefs`() = runTest {
        assertEquals(none, read(tempFile()))
    }

    @Test
    fun `connect persists profile and consent without a pause`() = runTest {
        val file = tempFile()

        process(file) { it.connect("profile-1", 1_000L) }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null), read(file))
    }

    @Test
    fun `pause survives a restart with profile and consent intact`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
        }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, "RateLimited"), read(file))
    }

    @Test
    fun `connect over a pause clears it and stores the new profile and consent`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
            it.connect("profile-2", 2_000L)
        }

        assertEquals(LinkedInLinkPrefs("profile-2", 2_000L, null), read(file))
    }

    @Test
    fun `resume clears the pause and keeps profile and consent`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
            it.resume()
        }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null), read(file))
    }

    @Test
    fun `clear wipes all three, also after a restart`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
            it.clear()
            assertEquals(none, it.link.first())
        }

        assertEquals(none, read(file))
    }
}
