package com.ivandsky.puzzlebox.data.visibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibilityTierTest {

    @Test
    fun `postsToBoards is every tier but Hidden`() {
        assertEquals(listOf(false, true, true, true), VisibilityTier.entries.map { it.postsToBoards })
    }

    @Test
    fun `allowsChallenge is Duel and above`() {
        assertEquals(listOf(false, false, true, true), VisibilityTier.entries.map { it.allowsChallenge })
    }

    @Test
    fun `allowsReplays is Replays only`() {
        assertEquals(listOf(false, false, false, true), VisibilityTier.entries.map { it.allowsReplays })
    }

    @Test
    fun `storage names are hidden board duel replays and round-trip`() {
        assertEquals(listOf("hidden", "board", "duel", "replays"), VisibilityTier.entries.map { it.storageName })
        VisibilityTier.entries.forEach { assertEquals(it, VisibilityTier.fromStorageName(it.storageName)) }
        assertNull(VisibilityTier.fromStorageName("gold"))
        assertNull(VisibilityTier.fromStorageName(null))
    }

    @Test
    fun `effective is null while Unknown, Board when Unset, the pick when Chosen`() {
        assertNull(OwnTier.Unknown.effective)
        assertEquals(VisibilityTier.Board, OwnTier.Unset.effective)
        VisibilityTier.entries.forEach { assertEquals(it, OwnTier.Chosen(it).effective) }
    }
}
