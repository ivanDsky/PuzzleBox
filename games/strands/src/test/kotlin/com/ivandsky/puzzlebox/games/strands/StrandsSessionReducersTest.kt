package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.games.strands.engine.StrandsHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class StrandsSessionReducersTest {

    private val pies = StrandsWord("PIES", (0..3).map { Coord(0, it) })
    private val bake = StrandsWord("BAKE", (0..3).map { Coord(1, it) })

    /** 2 × 4: theme PIES on the top row, spangram BAKE on the bottom; PIKE is a valid non-theme word. */
    private val puzzle = StrandsPuzzle(
        rows = 2,
        cols = 4,
        letters = "PIESBAKE",
        clue = "Bake sale",
        spangram = bake,
        themeWords = listOf(pies),
        validWords = setOf("PIES", "BAKE", "PIKE"),
        constructors = "Test Setter",
    )
    private val session = StrandsSession(ref = null, state = StrandsState(puzzle), elapsedMs = 7L)

    private val pikePath = listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2), Coord(1, 3))

    @Test
    fun `a theme word joins found and keeps the rest of the session`() {
        val next = session.withSubmit(pies.cells)

        assertEquals(session.copy(state = session.state.copy(found = listOf("PIES"))), next)
    }

    @Test
    fun `the spangram joins found`() {
        assertEquals(listOf("BAKE"), session.withSubmit(bake.cells).state.found)
    }

    @Test
    fun `a non-theme word joins nonTheme and not found`() {
        val next = session.withSubmit(pikePath)

        assertEquals(setOf("PIKE"), next.state.nonTheme)
        assertEquals(emptyList<String>(), next.state.found)
    }

    @Test
    fun `a repeat gives back the same instance`() {
        val once = session.withSubmit(pies.cells)

        assertSame(once, once.withSubmit(pies.cells))
    }

    @Test
    fun `an invalid, too short or empty path gives back the same instance`() {
        assertSame(session, session.withSubmit(listOf(Coord(0, 0), Coord(1, 3), Coord(0, 1), Coord(0, 2))))
        assertSame(session, session.withSubmit(pies.cells.take(2)))
        assertSame(session, session.withSubmit(emptyList()))
    }

    @Test
    fun `a hint goes on the board and counts once`() {
        val next = session.withHint(StrandsHint.Reveal(ActiveHint("PIES", HintStage.Outlined)))

        assertNotSame(session, next)
        assertEquals(ActiveHint("PIES", HintStage.Outlined), next.state.activeHint)
        assertEquals(1, next.state.hintsUsed)
        assertEquals(2, next.withHint(StrandsHint.Reveal(ActiveHint("PIES", HintStage.OrderRevealed))).state.hintsUsed)
    }
}
