package com.ivandsky.puzzlebox.profile.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Posting on carries the consent time; off carries none; neither touches the link state. */
class UpdateLinkedInPostingTest {

    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val update = UpdateLinkedInPosting(link, clock = { 7_000L })

    @Test
    fun `turning on records the consent time from the clock`() = runTest {
        update(on = true)

        assertEquals(listOf<Long?>(7_000L), link.postingChanges)
        assertTrue(link.posting.first())
    }

    @Test
    fun `turning off clears it with no consent time`() = runTest {
        update(on = true)

        update(on = false)

        assertEquals(listOf<Long?>(7_000L, null), link.postingChanges)
        assertFalse(link.posting.first())
    }

    @Test
    fun `a paused link keeps its choice and its pause`() = runTest {
        val paused = FakeLinkedInLink(LinkState.Paused("me", Kind.Challenge))

        UpdateLinkedInPosting(paused, clock = { 7_000L })(on = true)

        assertTrue(paused.posting.first())
        assertEquals(LinkState.Paused("me", Kind.Challenge), paused.state.first())
    }

    @Test
    fun `while not connected nothing is turned on`() = runTest {
        val none = FakeLinkedInLink(LinkState.NotConnected)

        UpdateLinkedInPosting(none, clock = { 7_000L })(on = true)

        assertFalse(none.posting.first())
    }
}
