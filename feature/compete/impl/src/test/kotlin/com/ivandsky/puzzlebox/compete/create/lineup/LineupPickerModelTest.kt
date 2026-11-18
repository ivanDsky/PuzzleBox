package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.SelfDuelView
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.custom
import com.ivandsky.puzzlebox.compete.create.friend
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Add / Change puzzle page is told, from the New duel state alone. */
class LineupPickerModelTest {
    private val friendWithRows = friend().let { it.copy(lineup = LineupEditor(LineupDraft(listOf(AnyGame(ZIP), CROWN_JEWELS.entry)))) }

    @Test
    fun `no page while the lineup editor has no target`() {
        assertNull(friendWithRows.lineupPicker())
    }

    @Test
    fun `adding the third puzzle is Add puzzle 3 with its row count and the games`() {
        val picker = friendWithRows.copy(lineup = friendWithRows.lineup.openAdd()).lineupPicker()!!

        assertEquals(3, picker.position)
        assertTrue(!picker.changing)
        assertEquals(GAMES, picker.games)
    }

    @Test
    fun `changing the first puzzle is Change puzzle 1`() {
        val picker = friendWithRows.copy(lineup = friendWithRows.lineup.openChange(0)).lineupPicker()!!

        assertEquals(1, picker.position)
        assertTrue(picker.changing)
    }

    @Test
    fun `a friend duel's page has no solved counts`() {
        val picker = friendWithRows.copy(lineup = friendWithRows.lineup.openAdd()).lineupPicker()!!

        assertTrue(!picker.pastYou)
        assertNull(picker.solved)
    }

    @Test
    fun `Past you's page carries what the player solved, per game`() {
        val past = custom()
        val picker = past.copy(lineup = past.lineup.openAdd()).lineupPicker()!!

        assertTrue(picker.pastYou)
        assertEquals(past.self.counts!!.byGame, picker.solved)
        assertEquals(17, picker.solved!![ZIP])
    }

    @Test
    fun `Past you's page has no counts until they are read, and so no game is off`() {
        val past = custom().let { it.copy(self = SelfDuelView(it.self.form, null, GAMES)) }
        val picker = past.copy(lineup = past.lineup.openAdd()).lineupPicker()!!

        assertTrue(picker.pastYou)
        assertNull(picker.solved)
    }

    @Test
    fun `the exact puzzles already in the lineup are what the page marks added`() {
        val lineup = LineupEditor(LineupDraft(listOf(AnyGame(QUEENS), CROWN_JEWELS.entry, OLD_CROWN.entry))).openAdd()
        val picker = friend().copy(lineup = lineup).lineupPicker()!!

        assertEquals(setOf(CROWN_JEWELS.entry.ref, OLD_CROWN.entry.ref), picker.added)
    }
}
