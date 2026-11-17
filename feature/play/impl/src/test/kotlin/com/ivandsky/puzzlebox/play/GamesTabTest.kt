package com.ivandsky.puzzlebox.play

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which tab a saved or requested tab shows as: Friends and Custom are the same place under two
 * pill sets, so each stands in for the other when its own pill is not there.
 */
class GamesTabTest {

    @Test
    fun `with a Custom slot a Friends tab shows as Custom`() {
        assertEquals(GamesTab.Custom, GamesTab.Friends.shownWith(custom = true))
    }

    @Test
    fun `without a Custom slot a saved Custom tab shows as Friends`() {
        assertEquals(GamesTab.Friends, GamesTab.Custom.shownWith(custom = false))
    }

    @Test
    fun `with a Community tab Games keeps only Dailies and Duels`() {
        assertEquals(listOf(GamesTab.Dailies, GamesTab.Duels), gamesTabs(custom = false, community = true))
        assertEquals(listOf(GamesTab.Dailies, GamesTab.Duels), gamesTabs(custom = true, community = true))
    }

    @Test
    fun `with a Community tab a saved Friends or Custom tab shows as Dailies`() {
        assertEquals(GamesTab.Dailies, GamesTab.Friends.shownWith(custom = false, community = true))
        assertEquals(GamesTab.Dailies, GamesTab.Custom.shownWith(custom = false, community = true))
        assertEquals(GamesTab.Duels, GamesTab.Duels.shownWith(custom = false, community = true))
    }
}
