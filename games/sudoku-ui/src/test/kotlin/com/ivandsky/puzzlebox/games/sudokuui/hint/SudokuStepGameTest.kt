package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the Sudoku game adds to [StepHints]: `known` (A7), `lastMove`, the mapper wiring, the erase hook, and that a
 * press places nothing (A1). Driven through a real [StepHints] on [Dispatchers.Unconfined], so a press delivers at once.
 */
class SudokuStepGameTest {

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    /** The Mini fixture of SudokuBoardStateTest. */
    private val peanut450 = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
        grid("463251 521436 142563 356142 234615 615324"),
    )

    /** Lab board rnd:154 (SudokuStepperEliminationTest): no single, a pointing then a claiming, neither unlocking one. */
    private val noSingle = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..62.4 42.6.. 6.142. 24...6 .6254. ..4.62"),
        grid("516234 423651 631425 245316 162543 354162"),
    )

    /** Lab board rnd:107 (SudokuStepperEliminationTest): pointings in four boxes, none unlocking a single. */
    private val fourPointings = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("1.5..6 3.6.5. .53.4. .....5 .325.. 5....3"),
        grid("125436 346152 253641 461325 632514 514263"),
    )

    /** One play: the live session, the game under test, and the real press flow around it. */
    private inner class Play(puzzle: SudokuPuzzle) {
        var session = SudokuSession(ref = null, puzzle = puzzle)
        val erased = mutableListOf<SudokuStep.Mistake>()
        var used = 0
        val game = SudokuStepGame(session = { session }, onEraseCulprit = { erased += it })
        val hints = StepHints(HintMode.Stepwise(Dispatchers.Unconfined), CoroutineScope(Dispatchers.Unconfined), game, { used }, { used = it })

        /** What the next press would find, read off the game's own pure block. */
        fun next(): SudokuStep = game.capture()()

        /** Presses, checks the Spotlight on screen is exactly the step's, and returns that step. */
        fun press(): SudokuStep {
            val step = next()
            hints.press()
            val shown = hints.spotlight
            assertEquals(SudokuSpotlightMapper.map(step, session.puzzle, session.board, shown?.playId ?: 0), shown)
            return step
        }

        /** A player's edit, then the input notice the controller gives (34). */
        fun play(command: SudokuCommand?) {
            session = session.withCommand(checkNotNull(command), Instant.EPOCH)
            hints.inputMade()
        }

        fun place(cell: Int, digit: Int) = play(SudokuMoves.place(session.board, cell, digit))
    }

    private fun keyOf(step: SudokuStep) = SudokuSpotlightMapper.keyOf(step)

    // known (A7)

    @Test
    fun `a shown elimination is known after the next input, so the next press moves on`() {
        val play = Play(noSingle)
        val first = play.press()
        assertTrue("$first", first is SudokuStep.Elimination)

        play.hints.inputMade()

        val second = play.press()
        assertNotEquals(keyOf(first), keyOf(second))
        assertEquals(SudokuStepper.next(noSingle, play.session.board, null, (first as SudokuStep.Elimination).struck), second)
    }

    @Test
    fun `press and input cycles solve the board without showing a key twice`() {
        val play = Play(fourPointings)
        val keys = mutableListOf<SudokuSpotlightMapper.Key>()
        var eliminations = 0
        while (true) {
            val step = play.press()
            val key = keyOf(step) ?: break
            assertTrue("$key shown twice in $keys", key !in keys)
            keys += key
            when (step) {
                is SudokuStep.Elimination -> { eliminations++; play.hints.inputMade() }
                is SudokuStep.Single -> play.place(step.place.cell, step.place.digit)
                is SudokuStep.Reveal -> play.place(step.cell, step.digit)
                else -> error("unexpected $step")
            }
        }
        assertTrue("no elimination on the way", eliminations >= 2)
        assertTrue(play.session.isComplete)
        assertEquals(keys.size, play.used)
    }

    @Test
    fun `re-pressing with no input between adds nothing and keeps the key`() {
        val play = Play(noSingle)
        val first = play.press()
        val again = play.press()

        assertEquals(first, again)
        assertEquals(2, play.hints.spotlight?.playId)
        assertEquals(1, play.used)
        assertEquals(first, play.next())
    }

    @Test
    fun `an input with nothing shown adds nothing`() {
        val play = Play(noSingle)
        val offered = play.next()

        play.hints.inputMade()

        assertEquals(offered, play.press())
    }

    @Test
    fun `an input after a shown mistake adds nothing`() {
        val play = Play(noSingle)
        val offered = play.next()
        play.place(EMPTY_CELL, WRONG_DIGIT)
        assertTrue(play.press() is SudokuStep.Mistake)

        play.session = play.session.withUndo()
        play.hints.inputMade()

        assertEquals(offered, play.press())
    }

    @Test
    fun `reset forgets known, so the same elimination is offered and counted again`() {
        val play = Play(noSingle)
        val first = play.press()
        play.hints.inputMade()
        assertNotEquals(first, play.next())

        play.hints.reset()

        assertEquals(first, play.press())
        assertEquals(2, play.used)
    }

    @Test
    fun `reset forgets the shown elimination too, so the next input adds nothing`() {
        val play = Play(noSingle)
        val first = play.press()

        play.hints.reset()
        play.hints.inputMade()

        assertEquals(first, play.next())
    }

    @Test
    fun `a fresh game starts with nothing known`() {
        assertEquals(SudokuStepper.next(noSingle, SudokuBoard.of(noSingle), null, emptyMap()), Play(noSingle).next())
    }

    @Test
    fun `the compute block works on a copy of known taken at capture`() {
        val play = Play(noSingle)
        val first = play.press()
        val block = play.game.capture()

        play.hints.inputMade()

        assertEquals(first, block())
        assertNotEquals(first, play.next())
    }

    // A press places nothing (A1)

    @Test
    fun `a press leaves the session untouched, a reveal included`() {
        val play = Play(noSingle)
        assertTrue(play.press() is SudokuStep.Elimination)
        play.hints.inputMade()
        val before = play.session

        val reveal = play.press()

        assertTrue("$reveal", reveal is SudokuStep.Reveal)
        assertEquals(before, play.session)
        play.press()
        assertEquals(before, play.session)
    }

    // Wrong digit

    @Test
    fun `a wrong digit is shown once as the culprit, then the second press hands it to the erase hook`() {
        val play = Play(peanut450)
        play.place(0, 1)
        val wrong = play.session

        val step = play.press()

        assertEquals(SudokuStep.Mistake(0, 1, 12), step)
        assertEquals(listOf(0), play.hints.spotlight?.roles?.filterValues { it == SpotlightRole.CULPRIT }?.keys?.toList())
        assertTrue(play.erased.isEmpty())

        play.hints.press()

        assertEquals(listOf(step), play.erased)
        assertNull(play.hints.spotlight)
        assertEquals(wrong, play.session)
    }

    // lastMove

    @Test
    fun `of two equal singles the one nearer the newest placement is shown`() {
        val play = Play(peanut450)
        play.place(PLACED_CELL, peanut450.solution[PLACED_CELL])
        val board = play.session.board
        val near = SudokuStepper.next(peanut450, board, PLACED_CELL, emptyMap())
        assertNotEquals(SudokuStepper.next(peanut450, board, null, emptyMap()), near)

        assertEquals(near, play.press())
    }

    @Test
    fun `a note toggle and an erase are last moves too`() {
        val play = Play(peanut450)
        play.play(SudokuMoves.toggleNote(play.session.board, NOTED_CELL, 1))
        assertEquals(SudokuStepper.next(peanut450, play.session.board, NOTED_CELL, emptyMap()), play.next())

        play.place(NOTED_CELL, 2)
        play.play(SudokuMoves.erase(play.session.board, NOTED_CELL))
        assertEquals(SudokuStepper.next(peanut450, play.session.board, NOTED_CELL, emptyMap()), play.next())
        assertNotEquals(SudokuStepper.next(peanut450, play.session.board, null, emptyMap()), play.next())
    }

    @Test
    fun `after a clear there is no last move and the step is deterministic`() {
        val play = Play(peanut450)
        play.place(PLACED_CELL, peanut450.solution[PLACED_CELL])
        play.play(SudokuMoves.clear(play.session.board))

        val step = play.press()

        assertEquals(SudokuStepper.next(peanut450, play.session.board, null, emptyMap()), step)
        assertEquals(step, Play(peanut450).next())
    }

    // Solved and the mapper

    @Test
    fun `a solved step has no key, so a completed session shows nothing and counts nothing`() {
        assertNull(Play(peanut450).game.keyOf(SudokuStep.Solved))
        val play = Play(peanut450)
        peanut450.solution.forEachIndexed { cell, digit -> if (play.session.board.digitAt(cell) == 0) play.place(cell, digit) }
        assertTrue(play.session.isComplete)

        assertEquals(SudokuStep.Solved, play.press())

        assertNull(play.hints.spotlight)
        assertEquals(0, play.used)
    }

    @Test
    fun `the spotlight draws the wrong entry read from the live board`() {
        val play = Play(peanut450)
        play.place(0, 1)
        val step = play.next()

        assertEquals(SudokuSpotlightGlyphs(mapOf(0 to "1")), play.game.spotlight(step, 7)?.glyphs)
    }

    private companion object {
        /** An empty cell of [noSingle] and a digit that is not its solution. */
        const val EMPTY_CELL = 0
        const val WRONG_DIGIT = 1

        /** Empty cells of peanut450 where a last move changes which of two equal singles wins. */
        const val PLACED_CELL = 30
        const val NOTED_CELL = 25
    }
}
