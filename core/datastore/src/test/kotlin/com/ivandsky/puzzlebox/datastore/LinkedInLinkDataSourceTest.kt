package com.ivandsky.puzzlebox.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInLinkDataSourceTest {

    private val none = LinkedInLinkPrefs(profileId = null, consentAtMs = null, pauseReason = null, postingConsentAtMs = null)

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

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null, null), read(file))
    }

    @Test
    fun `pause survives a restart with profile and consent intact`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
        }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, "RateLimited", null), read(file))
    }

    @Test
    fun `connect over a pause clears it and stores the new profile and consent`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
            it.connect("profile-2", 2_000L)
        }

        assertEquals(LinkedInLinkPrefs("profile-2", 2_000L, null, null), read(file))
    }

    @Test
    fun `resume clears the pause and keeps profile and consent`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
            it.resume()
        }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null, null), read(file))
    }

    @Test
    fun `clear wipes every key, also after a restart`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.pause("RateLimited")
            it.setPostingConsent(3_000L)
            it.clear()
            assertEquals(none, it.link.first())
        }

        assertEquals(none, read(file))
    }

    @Test
    fun `posting consent is off by default and survives a restart once given`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            assertEquals(null, it.link.first().postingConsentAtMs)
            it.setPostingConsent(2_000L)
        }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null, 2_000L), read(file))
    }

    @Test
    fun `posting consent set to null turns it off and keeps the link`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.setPostingConsent(2_000L)
            it.setPostingConsent(null)
        }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null, null), read(file))
    }

    @Test
    fun `posting consent is not stored while no profile is linked`() = runTest {
        val file = tempFile()

        process(file) { it.setPostingConsent(2_000L) }

        assertEquals(none, read(file))
    }

    @Test
    fun `pause and resume leave posting consent alone`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.setPostingConsent(2_000L)
            it.pause("Challenge")
            it.resume()
        }

        assertEquals(2_000L, read(file).postingConsentAtMs)
    }

    @Test
    fun `a sending marker survives a restart beside the link`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            assertEquals(true, it.markSending("profile-1", "zip/2026-11-05"))
        }

        assertEquals(
            LinkedInLinkPrefs("profile-1", 1_000L, null, null, sendingRefs = setOf("zip/2026-11-05")),
            read(file),
        )
    }

    @Test
    fun `a marker is refused and stores nothing unless its profile is the linked one`() = runTest {
        val unlinked = tempFile()
        val other = tempFile()

        process(unlinked) { assertEquals(false, it.markSending("profile-1", "zip/2026-11-05")) }
        process(other) {
            it.connect("profile-2", 1_000L)
            assertEquals(false, it.markSending("profile-1", "zip/2026-11-05"))
        }

        assertEquals(none, read(unlinked))
        assertEquals(LinkedInLinkPrefs("profile-2", 1_000L, null, null), read(other))
    }

    @Test
    fun `a send settled as posted swaps the marker for a posted ref and leaves other markers`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.markSending("profile-1", "zip/2026-11-05")
            it.markSending("profile-1", "queens/2026-11-05")
            it.settleSend("profile-1", "zip/2026-11-05", posted = true)
        }

        assertEquals(
            LinkedInLinkPrefs(
                "profile-1", 1_000L, null, null,
                postedRefs = setOf("zip/2026-11-05"),
                sendingRefs = setOf("queens/2026-11-05"),
            ),
            read(file),
        )
    }

    @Test
    fun `a send settled as not posted only drops its marker`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.markSending("profile-1", "zip/2026-11-05")
            it.settleSend("profile-1", "zip/2026-11-05", posted = false)
        }

        assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null, null), read(file))
    }

    @Test
    fun `a send settled as already held swaps the marker for an already ref, which another profile and a clear drop`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.markSending("profile-1", "zip/2026-11-05")
            it.settleSend("profile-1", "zip/2026-11-05", posted = false, already = true)
            assertEquals(LinkedInLinkPrefs("profile-1", 1_000L, null, null, alreadyRefs = setOf("zip/2026-11-05")), it.link.first())
            it.connect("profile-2", 2_000L)
        }

        assertEquals(LinkedInLinkPrefs("profile-2", 2_000L, null, null), read(file))
    }

    @Test
    fun `a send settled for a profile that is not the linked one changes nothing`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-2", 1_000L)
            it.markSending("profile-2", "zip/2026-11-05")
            it.settleSend("profile-1", "zip/2026-11-05", posted = true)
        }

        assertEquals(
            LinkedInLinkPrefs("profile-2", 1_000L, null, null, sendingRefs = setOf("zip/2026-11-05")),
            read(file),
        )
    }

    @Test
    fun `connecting another profile drops the post log and the same profile keeps it`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.markSending("profile-1", "zip/2026-11-05")
            it.settleSend("profile-1", "queens/2026-11-05", posted = true)
            it.connect("profile-1", 2_000L)
            assertEquals(setOf("queens/2026-11-05"), it.link.first().postedRefs)
            assertEquals(setOf("zip/2026-11-05"), it.link.first().sendingRefs)
            it.connect("profile-2", 3_000L)
        }

        assertEquals(LinkedInLinkPrefs("profile-2", 3_000L, null, null), read(file))
    }

    @Test
    fun `clear wipes the post log`() = runTest {
        val file = tempFile()

        process(file) {
            it.connect("profile-1", 1_000L)
            it.markSending("profile-1", "zip/2026-11-05")
            it.settleSend("profile-1", "queens/2026-11-05", posted = true)
            it.clear()
        }

        assertEquals(none, read(file))
    }
}
