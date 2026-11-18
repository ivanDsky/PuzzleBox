package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesSheetCopyTest {
    @Test
    fun `each game's sheet carries its card's rules verbatim and one or two controls lines`() {
        listOf("zip", "tango", "queens", "strands", "minisudoku").forEach { id ->
            val copy = checkNotNull(rulesSheetCopy(GameId(id))) { "no sheet copy for $id" }
            assertEquals(id, gameRules(GameId(id)), copy.rules)
            assertTrue("$id controls: ${copy.controls}", copy.controls.size in 1..2)
        }
    }

    @Test
    fun `classic sudoku and an unknown game have no sheet`() {
        assertNull(rulesSheetCopy(GameId("sudoku")))
        assertNull(rulesSheetCopy(GameId("no-such-game")))
    }

    @Test
    fun `queens explains where auto-cross lives`() {
        val queens = checkNotNull(rulesSheetCopy(GameId("queens")))
        assertEquals("Auto-cross, in the ⋮ menu at the top, marks the squares each queen rules out.", queens.controls.last())
    }
}
