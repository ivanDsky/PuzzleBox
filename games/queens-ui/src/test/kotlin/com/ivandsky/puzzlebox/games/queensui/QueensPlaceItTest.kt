package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.PLACE_IT
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightAction
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensScoreModel
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queensui.hint.QueensPlaceIt
import com.ivandsky.puzzlebox.games.queensui.hint.QueensStepGame
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Place it" of a Queens hint callout (PlaceIt): a hint that names a last queen, a cross-out or a no-simple-step square
 * is applied through the board's own input, as the player's moves. Runs a real [QueensBoardState] on the 8×8 fixture
 * (unique solution, so the stepper judges against it) with a real move log.
 *
 * Fixture steps, found by pressing Hint: on an empty board the first step crosses 56 and 57 out; with queens on 0, 12
 * and 23 the step is the last queen of region 3 at 29; with queens on all but 59 it is the winning queen at 59.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class QueensPlaceItTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** One board as a release builds it: [queens] already stand, the log records every move, the session is saved on every change. */
    private class Play(
        placeIt: Boolean = true,
        autoCross: Boolean = true,
        queens: List<Int> = emptyList(),
        puzzle: QueensPuzzle = QueensPlayFixtures.puzzle(),
    ) {
        val log = MoveLog.fresh(QueensDefinition.id)
        val wins = ArrayList<CompletionRecord>()
        val saved = ArrayList<QueensSession>()
        val board = QueensBoardState(
            initialSession = queens.fold(QueensSession(QueensPlayFixtures.ref(), puzzle, autoCross = autoCross)) { session, idx ->
                session.withQueenPlaced(Cell(idx), Instant.EPOCH)
            },
            score = QueensScoreModel(),
            onWinRecord = { wins += it },
            recorder = QueensMoveLog(log, now = { Instant.EPOCH }),
            onSessionChanged = { saved += it },
            mode = QueensPlayFixtures.hintMode,
            scope = CoroutineScope(Dispatchers.Unconfined),
            placeIt = placeIt,
        )
        val session: QueensSession get() = board.session

        /** The moves the log holds, decoded. */
        fun moves(): List<QueensReplayMove> =
            checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "no readable log" }.moves
                .map { checkNotNull(QueensReplayMove.of(it.move, session.puzzle.cellCount)) }

        /** Presses Hint and runs the callout's action, the way a tap on the pill does. */
        fun hintAndPlace() {
            board.requestHint()
            checkNotNull(action()) { "the callout has no action" }.onClick()
        }

        fun action() = board.spotlight?.callout?.action
    }

    private fun crossed(play: Play): Set<Int> =
        play.session.marks.filterValues { it.kind == MarkKind.MANUAL }.keys.map { it.idx }.toSet()

    // Last queen (A4)

    @Test
    fun `a last-queen step places exactly that queen, and the log, Undo and the hint count follow`() {
        val play = Play(queens = THREE_QUEENS)
        play.board.requestHint()
        val spotlight = checkNotNull(play.board.spotlight)
        assertEquals(setOf(LAST_QUEEN), spotlight.roles.filterValues { it == SpotlightRole.TARGET }.keys)
        assertEquals(PLACE_IT, spotlight.callout?.action?.label)

        spotlight.callout!!.action!!.onClick()

        assertEquals(THREE_QUEENS + LAST_QUEEN, play.session.queens.map { it.idx })
        assertEquals(1, play.session.hintsUsed)
        assertEquals(listOf(QueensReplayMove.Hint, QueensReplayMove.Place(Cell(LAST_QUEEN))), play.moves())
        assertNull(play.board.spotlight)
        assertEquals(LAST_QUEEN, play.board.lastPlacedQueen)

        play.board.undo()

        assertEquals(THREE_QUEENS, play.session.queens.map { it.idx })
        assertEquals(1, play.session.hintsUsed)
    }

    @Test
    fun `a placed queen auto-fills its marks when Auto-cross is on and none when it is off`() {
        val on = Play(autoCross = true, queens = THREE_QUEENS)
        val off = Play(autoCross = false, queens = THREE_QUEENS)

        on.hintAndPlace()
        off.hintAndPlace()

        // What the session itself fills for a queen on 29, with the three queens already standing.
        val expected = QueensSession(QueensPlayFixtures.ref(), QueensPlayFixtures.puzzle())
        val filled = (THREE_QUEENS + LAST_QUEEN).fold(expected) { session, idx -> session.withQueenPlaced(Cell(idx), Instant.EPOCH) }
        assertTrue(on.session.marks.values.any { it.kind == MarkKind.AUTO })
        assertEquals(filled.marks, on.session.marks)
        assertTrue(off.session.marks.values.none { it.kind == MarkKind.AUTO })
    }

    @Test
    fun `the last queen of the board wins it once, like a tap`() {
        val play = Play(queens = QueensPlayFixtures.solution.map { it.idx }.dropLast(1))

        play.hintAndPlace()
        assertNull(play.board.spotlight) // gone with the win, like any input

        assertTrue(play.session.isComplete)
        assertEquals(1, play.wins.size)
        assertTrue(play.board.winEmitted)
    }

    @Test
    fun `a second run of the same action before the board recomposes places nothing more`() {
        val play = Play(queens = THREE_QUEENS)
        play.board.requestHint()
        val action = checkNotNull(play.action())

        action.onClick()
        val placed = play.session
        val movesBefore = play.moves()
        action.onClick()

        assertSame(placed, play.session)
        assertEquals(movesBefore, play.moves())
        assertEquals(1, play.session.queens.count { it.idx == LAST_QUEEN })
    }

    // Cross-out (A4, A5)

    @Test
    fun `a cross-out step crosses exactly the named squares, one move each, and Undo takes them back one at a time`() {
        val play = Play()
        play.board.requestHint()
        val named = checkNotNull(play.board.spotlight).roles.filterValues { it == SpotlightRole.EXCLUDED }.keys
        assertEquals(CROSS_OUT, named)

        play.board.spotlight!!.callout!!.action!!.onClick()

        assertEquals(CROSS_OUT, crossed(play))
        assertTrue(play.session.queens.isEmpty())
        assertEquals(1, play.session.hintsUsed)
        assertEquals(
            listOf(QueensReplayMove.Hint) + CROSS_OUT.sorted().map { QueensReplayMove.Cross(Cell(it)) },
            play.moves(),
        )
        assertNull(play.board.spotlight)

        play.board.undo()
        assertEquals(setOf(CROSS_OUT.min()), crossed(play))
        play.board.undo()
        assertTrue(crossed(play).isEmpty())
        assertEquals(1, play.session.hintsUsed)
    }

    @Test
    fun `a cross on an automatic mark makes it a manual cross, on a manual cross or a queen changes nothing`() {
        val play = Play(queens = listOf(0))
        val auto = play.session.marks.filterValues { it.kind == MarkKind.AUTO }.keys.first().idx
        val manual = 63
        play.board.onTap(manual)
        val before = play.session

        play.board.crossHintCells(listOf(auto))
        assertEquals(MarkKind.MANUAL, play.session.markAt(Cell(auto))?.kind)

        val after = play.session
        play.board.crossHintCells(listOf(manual))
        assertSame(after, play.session)

        play.board.crossHintCells(listOf(0))
        assertSame(after, play.session)
        assertEquals(listOf(Cell(0)), play.session.queens)
        assertEquals(before.queens, play.session.queens)
    }

    @Test
    fun `the biggest cross-out on the largest board finishes at once with one record and save per cross`() {
        val rows = QueensPuzzle(size = 11, regions = List(121) { it / 11 }, queensPerUnit = 1)
        val play = Play(puzzle = rows)
        val all = (0 until 121).toList()

        val started = System.nanoTime()
        play.board.crossHintCells(all)
        val tookMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(all.toSet(), crossed(play))
        assertEquals(121, play.moves().size)
        assertEquals(121, play.saved.size)
        assertTrue("took $tookMs ms", tookMs < 5_000)
        assertNotNull(QueensSessionCodec.serialize(play.session))
    }

    // No-simple-step square (A4)

    @Test
    fun `a no-simple-step square is crossed, never queened, through the board's own input`() {
        val play = Play()
        val reveal = QueensStep.Reveal(Cell(15))
        val game = QueensStepGame(
            session = { play.session },
            placeIt = QueensPlaceIt(play.board::placeHintQueen, play.board::crossHintCells),
        )

        val action = checkNotNull(game.spotlight(reveal, playId = 1).callout?.action)
        assertEquals(PLACE_IT, action.label)
        action.onClick()

        assertEquals(setOf(15), crossed(play))
        assertTrue(play.session.queens.isEmpty())
        assertEquals(listOf(QueensReplayMove.Cross(Cell(15))), play.moves())
    }

    // No action (A3, flag off)

    @Test
    fun `a mistake step shows no action`() {
        val play = Play()
        play.board.onTap(1)
        play.board.onTap(1) // a queen off the solution
        play.board.requestHint()

        val spotlight = checkNotNull(play.board.spotlight)
        assertTrue(SpotlightRole.CULPRIT in spotlight.roles.values)
        assertNull(spotlight.callout?.action)
    }

    @Test
    fun `with the flag off the callout has no action on any kind of step`() {
        for (queens in listOf(emptyList(), THREE_QUEENS)) {
            val play = Play(placeIt = false, queens = queens)

            play.board.requestHint()

            assertNotNull(play.board.spotlight?.callout)
            assertNull(play.board.spotlight?.callout?.action)
        }
    }

    @Test
    fun `the flag off leaves the spotlight exactly the mapper's`() {
        val play = Play(placeIt = false, queens = THREE_QUEENS)
        val game = QueensStepGame(session = { play.session })
        val step = game.capture()()

        play.board.requestHint()

        assertEquals(game.spotlight(step, playId = 1).copy(playId = 1), play.board.spotlight!!.copy(playId = 1))
    }

    // The flag and the route to the board (ADR-0022 §3, A8)

    private fun flags(vararg on: ReleaseFlag) = object : ReleaseFlags {
        override val enabled: StateFlow<Set<ReleaseFlag>> = MutableStateFlow(on.toSet())
    }

    @Test
    fun `the module reads PlaceIt alone, and a missing flag service is off`() {
        assertTrue(queensPlaceIt(flags(ReleaseFlag.PlaceIt)))
        assertFalse(queensPlaceIt(flags(ReleaseFlag.HintPolish)))
        assertFalse(queensPlaceIt(flags()))
        assertFalse(queensPlaceIt(null))
    }

    @Test
    fun `the play UI hands the flag to its boards but not to a guided lesson`() {
        val ui = QueensPlayUi(InMemoryQueensSettings(), { QueensPlayFixtures.hintMode }, placeIt = { true })
        fun actionOn(controller: GamePlayController): SpotlightAction? {
            val queens = controller as QueensPlayController
            assertEquals(Adopt.Adopted, queens.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))
            queens.requestHint()
            return queens.board?.spotlight?.callout?.action
        }
        val env = { PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}) }

        assertNotNull(actionOn(ui.createController(env())))
        assertNull(actionOn(ui.createLessonController(env())))
    }

    // Any other input removes the pill first (A10)

    @Test
    fun `a tap on the board clears the Spotlight and its action, and a hint press again is free`() {
        val play = Play()
        play.board.requestHint()

        play.board.onTap(FREE_CELL)

        assertNull(play.board.spotlight)
        play.board.undo()
        play.board.requestHint()
        assertNotNull(play.action())
        assertEquals(1, play.session.hintsUsed)
    }

    // Rendered board (A10)

    private fun show(play: Play) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                QueensPlayBoard(play.board, Modifier.fillMaxWidth().testTag(BOARD_TAG))
            }
        }
        composeRule.waitForIdle()
    }

    private fun showHint(play: Play) {
        show(play)
        play.board.requestHint()
        composeRule.waitForIdle()
    }

    @Test
    fun `a click on the pill places once and nothing else, and the callout goes`() {
        val play = Play(queens = THREE_QUEENS)
        showHint(play)

        composeRule.onNodeWithText(PLACE_IT).performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(THREE_QUEENS + LAST_QUEEN, play.session.queens.map { it.idx })
        assertEquals(listOf(QueensReplayMove.Hint, QueensReplayMove.Place(Cell(LAST_QUEEN))), play.moves())
        assertNull(play.board.spotlight)
        assertTrue(composeRule.onAllNodesWithText(PLACE_IT).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a drag that starts on the pill paints nothing`() {
        val play = Play()
        showHint(play)
        val before = play.session

        composeRule.onNodeWithText(PLACE_IT).performTouchInput { swipe(center, center + Offset(-300f, 0f)) }
        composeRule.waitForIdle()

        assertTrue(play.session.marks.isEmpty())
        assertTrue(play.session.queens.isEmpty())
        assertEquals(before.history, play.session.history)
        assertEquals(listOf(QueensReplayMove.Hint), play.moves())
    }

    @Test
    fun `a tap on the board outside the pill still reaches the board and clears the callout without placing`() {
        val play = Play()
        showHint(play)
        val pill = composeRule.onNodeWithText(PLACE_IT).fetchSemanticsNode().boundsInRoot
        val board = composeRule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot
        val n = play.session.puzzle.size
        // The corner cell farthest from the pill, so neither the pill nor its bubble is over it.
        val corner = listOf(0, n - 1, n * (n - 1), n * n - 1).maxBy { idx ->
            val center = Offset(
                board.left + board.width * ((idx % n) + 0.5f) / n,
                board.top + board.height * ((idx / n) + 0.5f) / n,
            )
            (center - pill.center).getDistance()
        }

        composeRule.onNodeWithTag(BOARD_TAG).performTouchInput {
            click(Offset(width * ((corner % n) + 0.5f) / n, height * ((corner / n) + 0.5f) / n))
        }
        composeRule.waitForIdle()

        assertEquals(setOf(corner), crossed(play))
        assertNull(play.board.spotlight)
    }

    @Test
    fun `a tap on the bubble's text beside the pill reaches the board cell under it, and the pill's action does not run`() {
        val play = Play()
        showHint(play)
        val text = checkNotNull(play.board.spotlight?.callout).text
        val bubble = composeRule.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot
        val board = composeRule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot
        val n = play.session.puzzle.size
        val at = Offset(bubble.left + 4f, bubble.center.y)
        val cell = ((at.y - board.top) / (board.height / n)).toInt() * n + ((at.x - board.left) / (board.width / n)).toInt()

        composeRule.onNodeWithTag(BOARD_TAG).performTouchInput { click(at - board.topLeft) }
        composeRule.waitForIdle()

        assertEquals(setOf(cell), crossed(play))
        assertNull(play.board.spotlight)
    }

    private companion object {
        /** Queens on row 0, 1 and 2 of the fixture's solution; the next step is the last queen of region 3. */
        val THREE_QUEENS = listOf(0, 12, 23)
        const val LAST_QUEEN = 29

        /** The first step on an empty board: row 7's evidence crosses these two out. */
        val CROSS_OUT = setOf(56, 57)

        /** Row 7, column 7: far from every step of these fixtures. */
        const val FREE_CELL = 63
        const val BOARD_TAG = "queens.test.board"
    }
}
