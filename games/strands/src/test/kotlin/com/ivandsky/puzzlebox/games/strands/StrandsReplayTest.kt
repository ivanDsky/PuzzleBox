package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.strands.engine.StrandsHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Strands replay adapter: both move kinds, the persisted kind numbers and the board facts. */
class StrandsReplayTest {

    private val at = 41_000L

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
    private val initial = StrandsSession(ref = null, state = StrandsState(puzzle), elapsedMs = 7L)
    private val pikePath = listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2), Coord(1, 3))

    private fun wire(kind: Int, vararg args: Int) = ReplayMove(kind, args)

    private fun StrandsSession.step(move: StrandsReplayMove): StrandsSession? =
        StrandsReplay.step(this, move.toReplayMove(puzzle.cols), at)

    private fun StrandsSession.play(vararg moves: StrandsReplayMove): StrandsSession =
        moves.fold(this) { session, move -> checkNotNull(session.step(move)) { "$move was rejected" } }

    private fun assertRejected(session: StrandsSession, vararg moves: ReplayMove) {
        for (move in moves) assertNull("$move", StrandsReplay.step(session, move, at))
    }

    private fun factsOf(session: StrandsSession): ReplayFacts = StrandsReplay.factsOf(initial)(session)

    // ------------------------------------------------------------------ submit

    @Test
    fun `submit of a theme word finds it`() {
        val next = initial.step(StrandsReplayMove.Submit(pies.cells))

        assertEquals(initial.withSubmit(pies.cells), next)
        assertEquals(listOf("PIES"), next?.state?.found)
    }

    @Test
    fun `submit of a non-theme word is recorded as a change`() {
        val next = initial.step(StrandsReplayMove.Submit(pikePath))

        assertNotSame(initial, next)
        assertEquals(setOf("PIKE"), next?.state?.nonTheme)
    }

    @Test
    fun `submit that changes nothing gives back the same instance`() {
        val found = initial.play(StrandsReplayMove.Submit(pies.cells))

        assertSame(found, found.step(StrandsReplayMove.Submit(pies.cells)))
        assertSame(initial, initial.step(StrandsReplayMove.Submit(pies.cells.take(2))))
        assertSame(initial, initial.step(StrandsReplayMove.Submit(listOf(Coord(0, 0), Coord(1, 3), Coord(0, 1), Coord(0, 2)))))
    }

    @Test
    fun `submit with hostile args is rejected`() {
        assertRejected(
            initial,
            wire(0),
            wire(0, -1, 0, 1),
            wire(0, 0, 1, 2, 8),
            wire(0, *IntArray(65)),
            wire(0, 0, 1, Int.MAX_VALUE),
            wire(0, 0, 1, Int.MIN_VALUE),
        )
    }

    // ------------------------------------------------------------------ hint

    @Test
    fun `hint puts the named word on the board and counts it`() {
        val outlined = initial.step(StrandsReplayMove.Hint(0, HintStage.Outlined))

        assertEquals(initial.withHint(StrandsHint.Reveal(ActiveHint("PIES", HintStage.Outlined))), outlined)
        assertEquals(1, outlined?.state?.hintsUsed)

        val ordered = outlined?.step(StrandsReplayMove.Hint(0, HintStage.OrderRevealed))
        assertEquals(ActiveHint("PIES", HintStage.OrderRevealed), ordered?.state?.activeHint)
        assertEquals(2, ordered?.state?.hintsUsed)
    }

    @Test
    fun `hint index one past the theme words names the spangram`() {
        val next = initial.step(StrandsReplayMove.Hint(1, HintStage.Outlined))

        assertEquals(ActiveHint("BAKE", HintStage.Outlined), next?.state?.activeHint)
    }

    private val outlinePies = StrandsReplayMove.Hint(0, HintStage.Outlined)
    private val orderPies = StrandsReplayMove.Hint(0, HintStage.OrderRevealed)
    private val outlineBake = StrandsReplayMove.Hint(1, HintStage.Outlined)
    private val orderBake = StrandsReplayMove.Hint(1, HintStage.OrderRevealed)

    @Test
    fun `order reveal with no prior outline is rejected`() {
        assertNull(initial.step(orderPies))
    }

    @Test
    fun `a repeated outline is rejected`() {
        assertNull(initial.play(outlinePies).step(outlinePies))
    }

    @Test
    fun `a hint on a found word is rejected`() {
        val found = initial.play(StrandsReplayMove.Submit(pies.cells))

        assertNull(found.step(outlinePies))
        assertNull(found.step(orderPies))
    }

    @Test
    fun `order reveal on a word that is not the active hint is rejected`() {
        assertNull(initial.play(outlinePies).step(orderBake))
    }

    @Test
    fun `a hint after the order is revealed is rejected`() {
        val ordered = initial.play(outlinePies, orderPies)

        assertNull(ordered.step(orderPies))
        assertNull(ordered.step(outlineBake))
    }

    @Test
    fun `outline then order matches live withHint sessions`() {
        val live = initial
            .withHint(StrandsHint.Reveal(ActiveHint("PIES", HintStage.Outlined)))
            .withHint(StrandsHint.Reveal(ActiveHint("PIES", HintStage.OrderRevealed)))

        assertEquals(live, initial.play(outlinePies, orderPies))
    }

    @Test
    fun `hint with hostile args is rejected`() {
        assertRejected(
            initial,
            wire(1),
            wire(1, 0),
            wire(1, 0, 0, 0),
            wire(1, -1, 0),
            wire(1, 2, 0),
            wire(1, 0, 2),
            wire(1, 0, -1),
        )
    }

    @Test
    fun `an unknown kind is rejected`() {
        assertRejected(initial, wire(2), wire(-1), wire(Int.MAX_VALUE))
    }

    // ------------------------------------------------------------------ wire format

    @Test
    fun `every move round trips and the kind numbers are the persisted ones`() {
        val moves = listOf(
            StrandsReplayMove.Submit(pikePath) to ReplayMove(0, intArrayOf(0, 1, 6, 7)),
            StrandsReplayMove.Hint(0, HintStage.Outlined) to ReplayMove(1, intArrayOf(0, 0)),
            StrandsReplayMove.Hint(1, HintStage.OrderRevealed) to ReplayMove(1, intArrayOf(1, 1)),
        )
        for ((typed, wire) in moves) {
            assertEquals(wire, typed.toReplayMove(puzzle.cols))
            assertEquals(typed, StrandsReplayMove.of(wire, puzzle))
        }
    }

    @Test
    fun `a path of 64 cells is accepted and 65 is not`() {
        val wide = puzzle.copy(rows = 8, cols = 9, letters = "A".repeat(72))
        assertNotNull(StrandsReplayMove.of(ReplayMove(0, IntArray(64)), wide))
        assertNull(StrandsReplayMove.of(ReplayMove(0, IntArray(65)), wide))
    }

    // ------------------------------------------------------------------ facts

    @Test
    fun `facts on an empty board`() {
        assertEquals(ReplayFacts(hints = 0, correct = 0, total = 2, solved = false), factsOf(initial))
    }

    @Test
    fun `facts count found words and ignore non-theme words`() {
        val partly = initial.play(StrandsReplayMove.Submit(pies.cells))
        assertEquals(ReplayFacts(0, 1, 2, false), factsOf(partly))

        val wrong = initial.play(StrandsReplayMove.Submit(pikePath))
        assertEquals(ReplayFacts(0, 0, 2, false), factsOf(wrong))
    }

    @Test
    fun `facts count hints and report a solved board`() {
        val solved = initial.play(
            StrandsReplayMove.Hint(0, HintStage.Outlined),
            StrandsReplayMove.Submit(pies.cells),
            StrandsReplayMove.Submit(bake.cells),
        )

        assertEquals(ReplayFacts(hints = 1, correct = 2, total = 2, solved = true), factsOf(solved))
    }

    @Test
    fun `a scripted solve ends on a board facts report solved`() {
        val facts = StrandsReplay.factsOf(initial)
        var session = initial
        for (move in listOf(
            StrandsReplayMove.Submit(pikePath),
            StrandsReplayMove.Submit(bake.cells),
            StrandsReplayMove.Submit(pies.cells),
        )) {
            assertTrue(!facts(session).solved)
            session = checkNotNull(session.step(move))
        }

        assertTrue(facts(session).solved)
    }

    @Test
    fun `factsOf twice gives independent functions with equal results`() {
        val first = StrandsReplay.factsOf(initial)
        val second = StrandsReplay.factsOf(initial)
        val board = initial.play(StrandsReplayMove.Submit(pies.cells))

        assertEquals(first(board), second(board))
        assertEquals(first(initial), second(initial))
    }

    @Test
    fun `the plugin exposes the adapter`() {
        assertSame(StrandsReplay, strandsPlugin.replay)
    }
}
