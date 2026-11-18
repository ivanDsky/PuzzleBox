package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.PLACE_IT
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuStepGame
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins "Place it" on a Mini Sudoku hint (PlaceIt, #1423): the callout's action puts the explained digit in the explained
 * cell as the player's own Place, through the board's one placement. It is no hint reveal, ignores Notes mode, leaves the
 * selection, counts nothing, and a given, a repeat, an elimination and a mistake do nothing or show no action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuPlaceItTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    /** A first press shows a single here. */
    private val peanut450 = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
        grid("463251 521436 142563 356142 234615 615324"),
    )

    /** Lab board rnd:154: no single, so a first press shows an elimination. */
    private val noSingle = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..62.4 42.6.. 6.142. 24...6 .6254. ..4.62"),
        grid("516234 423651 631425 245316 162543 354162"),
    )

    private val log = MoveLog.fresh(SudokuGames.MINI.id)

    private fun state(puzzle: SudokuPuzzle = peanut450, session: SudokuSession = SudokuSession(ref = null, puzzle = puzzle), placeIt: Boolean = true) =
        SudokuBoardState(
            initialSession = session,
            now = { Instant.EPOCH },
            log = log,
            hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
            scope = CoroutineScope(Dispatchers.Unconfined),
            placeIt = placeIt,
        )

    private fun SudokuBoardState.target(): Int =
        spotlight!!.roles.entries.single { it.value == SpotlightRole.TARGET }.key

    private fun SudokuBoardState.pill() = spotlight!!.callout!!.action!!

    private fun recorded(): List<StampedMove> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "no readable log" }.moves

    private fun moves(vararg expected: SudokuReplayMove) = expected.map { it.toReplayMove() }

    private fun SudokuBoardState.digitAt(idx: Int) = session.board.digitAt(idx)

    @Test
    fun `place it puts the explained digit on the explained cell as a Place, undoable, and counts nothing`() {
        val s = state()
        val before = s.session.board
        s.requestHint()
        val idx = s.target()
        val action = s.pill()
        assertEquals(PLACE_IT, action.label)
        assertEquals(1, s.session.hintsUsed)

        action.onClick()

        assertEquals(peanut450.solution[idx], s.digitAt(idx))
        assertEquals((0 until 36).filter { s.digitAt(it) != before.digitAt(it) }, listOf(idx))
        assertEquals(1, s.session.hintsUsed)
        assertNull(s.spotlight)
        assertEquals(moves(SudokuReplayMove.Hint, SudokuReplayMove.Place(idx, peanut450.solution[idx])), recorded().map { it.move })

        s.undo()

        assertEquals(before, s.session.board)
    }

    @Test
    fun `a reveal step's callout places the revealed digit the same way`() {
        val s = state()
        val idx = (0 until 36).first { s.session.board.cells[it] == SudokuCell.Empty }
        val digit = peanut450.solution[idx]
        val game = SudokuStepGame(session = { s.session }, placeIt = s::placeExplained)

        game.spotlight(SudokuStep.Reveal(idx, digit), playId = 1).callout!!.action!!.onClick()

        assertEquals(digit, s.digitAt(idx))
        assertEquals(moves(SudokuReplayMove.Place(idx, digit)), recorded().map { it.move })
    }

    @Test
    fun `flag off, a single's callout has no action`() {
        val s = state(placeIt = false)

        s.requestHint()

        assertNotNull(s.spotlight!!.callout)
        assertNull(s.spotlight!!.callout!!.action)
    }

    @Test
    fun `an elimination's callout has no action even with the flag on`() {
        val s = state(noSingle)

        s.requestHint()

        assertTrue(SpotlightRole.EXCLUDED in s.spotlight!!.roles.values)
        assertNull(s.spotlight!!.callout!!.action)
    }

    @Test
    fun `a mistake's callout has no action even with the flag on`() {
        val s = state().apply {
            select(0)
            input(WRONG_DIGIT)
        }

        s.requestHint()

        assertEquals(setOf(0), s.spotlight!!.roles.filterValues { it == SpotlightRole.CULPRIT }.keys)
        assertNull(s.spotlight!!.callout!!.action)
    }

    @Test
    fun `notes mode is ignored and the selection stays where it was`() {
        val s = state()
        s.toggleNotesMode()
        s.select(SELECTED)
        s.requestHint()
        val idx = s.target()

        s.pill().onClick()

        assertEquals(peanut450.solution[idx], s.digitAt(idx))
        assertTrue(s.session.notesMode)
        assertEquals(SELECTED, s.selected)
        assertEquals(moves(SudokuReplayMove.Hint, SudokuReplayMove.Place(idx, peanut450.solution[idx])), recorded().map { it.move })
    }

    @Test
    fun `a cell holding notes takes the digit and undo brings the notes back exactly`() {
        val s = state()
        s.requestHint()
        val idx = s.target()
        s.toggleNotesMode()
        s.select(idx)
        s.input(1)
        s.input(2)
        s.toggleNotesMode()
        val noted = s.session.board.cells[idx]
        assertTrue(noted is SudokuCell.Notes)
        s.requestHint()

        s.pill().onClick()
        assertEquals(peanut450.solution[idx], s.digitAt(idx))
        s.undo()

        assertEquals(noted, s.session.board.cells[idx])
    }

    @Test
    fun `a second run of the same action places nothing more`() {
        val s = state()
        s.requestHint()
        val action = s.pill()
        action.onClick()
        val placed = s.session
        val logged = recorded()

        action.onClick()

        assertEquals(placed, s.session)
        assertEquals(logged, recorded())
    }

    @Test
    fun `a given cell is refused as a tap on it is`() {
        val s = state()
        val given = (0 until 36).first { s.session.board.cells[it] is SudokuCell.Given }
        val game = SudokuStepGame(session = { s.session }, placeIt = s::placeExplained)
        val before = s.session

        game.spotlight(SudokuStep.Reveal(given, peanut450.solution[given]), playId = 1).callout!!.action!!.onClick()

        assertEquals(before, s.session)
        assertNotNull(s.rejection)
        assertTrue(log.snapshot() == null || recorded().isEmpty())
    }

    @Test
    fun `placing the last digit solves the board`() {
        var almost = SudokuSession(ref = null, puzzle = peanut450)
        peanut450.solution.forEachIndexed { cell, digit ->
            if (cell == LAST) return@forEachIndexed
            SudokuMoves.place(almost.board, cell, digit)?.let { almost = almost.withCommand(it, Instant.EPOCH) }
        }
        assertFalse(almost.isComplete)
        val s = state(session = almost)
        s.select(LAST)
        s.requestHint()
        assertEquals(LAST, s.target())

        s.pill().onClick()

        assertTrue(s.session.isComplete)
        assertNull(s.selected) // a win clears the selection, as for a tap
    }

    // Rendered board

    private fun show(state: SudokuBoardState) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SudokuGrid(state.model, state::select, Modifier.size(360.dp), spotlight = state.spotlight)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `tapping the pill places once and neither selects a cell nor takes a second tap`() {
        val s = state()
        show(s)
        s.requestHint()
        composeRule.waitForIdle()
        val idx = s.target()

        composeRule.onNodeWithText(PLACE_IT).performClick()
        composeRule.waitForIdle()

        assertEquals(peanut450.solution[idx], s.digitAt(idx))
        assertNull(s.selected)
        assertEquals(moves(SudokuReplayMove.Hint, SudokuReplayMove.Place(idx, peanut450.solution[idx])), recorded().map { it.move })
        composeRule.onNodeWithText(PLACE_IT).assertDoesNotExist()
    }

    @Test
    fun `a tap on a board cell outside the pill still reaches the board under the overlay`() {
        val s = state()
        show(s)
        s.requestHint()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(PLACE_IT).assertExists()
        val other = (0 until 36).first { it !in s.spotlight!!.roles && s.session.board.cells[it] !is SudokuCell.Given }

        composeRule.onNodeWithTag("sudoku.cell.$other").performClick()
        composeRule.waitForIdle()

        assertEquals(other, s.selected)
        assertNull(s.spotlight)
        assertEquals(moves(SudokuReplayMove.Hint), recorded().map { it.move })
    }

    // Wiring

    private val koinModules = listOf(miniSudokuUiModule, sudokuUiModule)

    @After
    @OptIn(KoinInternalApi::class)
    fun dropCachedSingles() = koinModules.forEach { module -> module.mappings.values.forEach { it.dropAll() } }

    private fun placeItFrom(vararg on: ReleaseFlag): Boolean {
        dropCachedSingles()
        val koin = koinApplication {
            modules(
                miniSudokuUiModule,
                module {
                    single<DispatcherProvider> {
                        object : DispatcherProvider {
                            override val main = Dispatchers.Default
                            override val io = Dispatchers.IO
                            override val default = Dispatchers.Unconfined
                        }
                    }
                    single<SudokuHighlightSettings> { InMemorySudokuHighlightSettings() }
                    single<ReleaseFlags> { object : ReleaseFlags { override val enabled = MutableStateFlow(on.toSet()) } }
                },
            )
        }.koin
        val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})
        return try {
            (koin.get<GamePlayUi>(qualifier(SudokuGames.MINI.id.value)).createController(env) as SudokuPlayController).placeIt
        } finally {
            koin.close()
        }
    }

    @Test
    fun `Mini reads PlaceIt from the flags, off without it`() {
        assertFalse(placeItFrom())
        assertFalse(placeItFrom(ReleaseFlag.HintPolish))
        assertTrue(placeItFrom(ReleaseFlag.PlaceIt))
    }

    private companion object {
        const val WRONG_DIGIT = 1
        const val SELECTED = 20
        const val LAST = 35
    }
}
