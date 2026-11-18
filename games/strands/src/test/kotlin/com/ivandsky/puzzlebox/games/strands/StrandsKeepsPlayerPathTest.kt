package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** A found word keeps the cells-in-order the player traced, not the puzzle's own path over the same cells. */
class StrandsKeepsPlayerPathTest {

    // 2 × 2 ABBA: the spangram reads A B B A along two orders over the same four cells.
    private val canonical = listOf(Coord(0, 0), Coord(0, 1), Coord(1, 0), Coord(1, 1))
    private val other = listOf(Coord(0, 0), Coord(1, 0), Coord(0, 1), Coord(1, 1))
    private val puzzle = StrandsPuzzle(
        rows = 2,
        cols = 2,
        letters = "ABBA",
        clue = "Pairs",
        spangram = StrandsWord("ABBA", canonical),
        themeWords = emptyList(),
        validWords = setOf("ABBA"),
        constructors = "Test Setter",
    )
    private val session = StrandsSession(ref = null, state = StrandsState(puzzle))

    private fun StrandsSession.spangramLink() =
        StrandsRendererModel.of(state).links.single { it.kind == LinkKind.Spangram }.cells

    @Test
    fun `a non-canonical trace over the same cells is the path drawn for the found word`() {
        assertEquals(other, session.withSubmit(other).spangramLink())
    }

    @Test
    fun `the canonical trace draws the canonical path`() {
        val next = session.withSubmit(canonical)

        assertEquals(canonical, next.spangramLink())
        assertEquals(session.state.copy(found = listOf("ABBA")), next.state)
    }

    @Test
    fun `the replay step keeps the submitted trace`() {
        val next = checkNotNull(StrandsReplay.step(session, StrandsReplayMove.Submit(other).toReplayMove(puzzle.cols), atMs = 0))

        assertEquals(other, next.spangramLink())
    }

    @Test
    fun `a trace over other cells draws the puzzle's own path`() {
        val elsewhere = StrandsPuzzle(2, 3, "ABBCCA", "Pairs", puzzle.spangram, emptyList(), setOf("ABBA"), "Test Setter")
        val path = listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2), Coord(1, 2))

        val next = StrandsSession(ref = null, state = StrandsState(elsewhere)).withSubmit(path)

        assertEquals(canonical, next.spangramLink())
    }

    @Test
    fun `kept paths are read back from the logged submits for found words only`() {
        val restored = session.copy(state = session.state.copy(found = listOf("ABBA")))

        assertEquals(other, restored.withKeptPaths(listOf(other)).spangramLink())
        assertEquals(canonical, restored.withKeptPaths(emptyList()).spangramLink())
    }
}
