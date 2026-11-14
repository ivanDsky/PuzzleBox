package com.ivandsky.puzzlebox.backend.firebase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The start-up step: it clears once when a sign-out left a mark, never without one, and retries after a failed clear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingCacheClearTest {

    private val mark = FakeCacheClearMark()
    private val clearer = FakeFirestoreCacheClearer()
    private val startup = PendingCacheClear(mark, clearer)

    @Test
    fun `a start with a mark clears once and drops the mark`() {
        mark.pending = true

        runBlocking { startup.run() }
        runBlocking { startup.run() }

        assertEquals(1, clearer.clears)
        assertFalse(mark.pending)
    }

    @Test
    fun `a start without a mark does not clear`() {
        runBlocking { startup.run() }

        assertEquals(0, clearer.clears)
    }

    @Test
    fun `a failed clear keeps the mark for the next start`() {
        mark.pending = true
        clearer.failure = IllegalStateException("Persistence cannot be cleared after the Firestore instance has been started")

        runBlocking { startup.run() }
        assertTrue(mark.pending)

        clearer.failure = null
        runBlocking { startup.run() }
        assertEquals(2, clearer.clears)
        assertFalse(mark.pending)
    }

    @Test
    fun `the preferences mark survives a new instance until dropped`() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesCacheClearMark(context).drop()
        assertFalse(PreferencesCacheClearMark(context).isPending())

        PreferencesCacheClearMark(context).mark()
        assertTrue(PreferencesCacheClearMark(context).isPending())

        PreferencesCacheClearMark(context).drop()
        assertFalse(PreferencesCacheClearMark(context).isPending())
    }
}
