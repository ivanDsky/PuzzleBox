package com.ivandsky.puzzlebox.play

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Games pills: Dailies, Duels and Custom, and which pill a saved or requested tab shows as. */
class GamesTabTest {

    @Test
    fun `Games shows Dailies, Duels and Custom`() {
        assertEquals(listOf(GamesTab.Dailies, GamesTab.Duels, GamesTab.Custom), gamesTabs())
    }

    @Test
    fun `a saved Custom tab shows as Custom`() {
        assertEquals(GamesTab.Custom, GamesTab.Custom.shownWith())
    }

    @Test
    fun `with a Community tab Games keeps only Dailies and Duels`() {
        assertEquals(listOf(GamesTab.Dailies, GamesTab.Duels), gamesTabs(community = true))
    }

    @Test
    fun `with a Community tab a saved Custom tab shows as Dailies`() {
        assertEquals(GamesTab.Dailies, GamesTab.Custom.shownWith(community = true))
        assertEquals(GamesTab.Duels, GamesTab.Duels.shownWith(community = true))
    }
}
