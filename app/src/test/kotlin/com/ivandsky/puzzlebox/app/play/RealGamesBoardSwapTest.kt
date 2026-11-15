package com.ivandsky.puzzlebox.app.play

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.repository.DailyRefresh
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudokuui.SudokuPlayController
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A changed daily replaces an untouched board and its store (A5, A16); the player's first input, of any kind,
 * keeps both (A18). Over every game's real controller, which is where each input kind is decided.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class RealGamesBoardSwapTest {

    private val day: LocalDate = TangoDefinition.cadence.today()
    private var graph: RealGamesGraph? = null

    @Before
    fun clearKoin() {
        stopKoin()
    }

    @After
    fun tearDown() {
        graph?.stop()
    }

    /** One game: its stored and differing envelopes, the puzzle its live controller shows, and its inputs. */
    private class Spec(
        val game: GameId,
        val stored: Puzzle,
        val revised: Puzzle,
        /** The puzzle the live controller plays, compared by value against the fixture's. */
        val shown: (ActivePlay) -> Any?,
        val storedPuzzle: Any,
        val revisedPuzzle: Any,
        /** A real move: logged, and it changes the session. */
        val move: (ActivePlay) -> Unit,
        /** An input that changes and logs nothing: a refused tap, an empty undo, a half-drawn trace. */
        val unlogged: (ActivePlay) -> Unit,
    )

    private val zip = Spec(
        ZipDefinition.id, ZipPlayFixture.envelope(), ZipPlayFixture.envelope(zipRevised()),
        { (it.controller as ZipPlayController).session?.puzzle }, ZipPlayFixture.puzzle(), zipRevised(),
        move = { (it.controller as ZipPlayController).onDragEvent(DragEvent.Extend(GridCell(0, 0))) },
        unlogged = { (it.controller as ZipPlayController).undo() },
    )

    private val tango = Spec(
        TangoDefinition.id, TangoPlayFixture.envelope(), TangoPlayFixture.envelope(tangoRevised()),
        { (it.controller as TangoPlayController).session?.puzzle }, TangoPlayFixture.puzzle, tangoRevised(),
        move = { (it.controller as TangoPlayController).onPlacement(TangoPlacement(TangoPlayFixture.placements.first().first, TangoPlayFixture.placements.first().second)) },
        unlogged = { (it.controller as TangoPlayController).onTap(0) }, // a given: the tap is refused
    )

    private val queens = Spec(
        QueensDefinition.id, QueensPlayFixture.envelope(), QueensPlayFixture.envelope(queensRevised()),
        { (it.controller as QueensPlayController).board?.session?.puzzle }, QueensPlayFixture.puzzle(), queensRevised(),
        move = { (it.controller as QueensPlayController).board!!.onTap(0) },
        unlogged = { (it.controller as QueensPlayController).board!!.undo() },
    )

    private val sudoku = Spec(
        SudokuGames.MINI.id, MiniSudokuPlayFixture.envelope(), MiniSudokuPlayFixture.envelope(sudokuRevised()),
        { (it.controller as SudokuPlayController).board?.session?.puzzle }, MiniSudokuPlayFixture.puzzle, sudokuRevised(),
        move = {
            val board = (it.controller as SudokuPlayController).board!!
            val idx = MiniSudokuPlayFixture.puzzle.givens.indexOf(0)
            board.select(idx)
            board.input(MiniSudokuPlayFixture.puzzle.solution[idx])
        },
        unlogged = { (it.controller as SudokuPlayController).board!!.undo() },
    )

    private val strands = Spec(
        StrandsDefinition.id, StrandsPlayFixture.envelope(), StrandsPlayFixture.envelope(strandsRevised()),
        { (it.controller as StrandsPlayController).session?.state?.puzzle }, StrandsPlayFixture.puzzle, strandsRevised(),
        move = { (it.controller as StrandsPlayController).onTrace(TraceStep.Submit(List(4) { col -> Coord(0, col) })) },
        unlogged = { (it.controller as StrandsPlayController).tap(Coord(0, 0)) },
    )

    private fun open(spec: Spec, script: FakeDailyRepository.() -> Unit): Pair<FakeDailyRepository, ActivePlay> {
        val daily = FakeDailyRepository().apply {
            setCached(spec.game, day, spec.stored)
            setToday(spec.game, spec.revised)
            script()
        }
        val play = RealGamesGraph(daily, day).also { graph = it }.apply { start() }.open(spec.game)
        awaitUntil("${spec.game.value} ready") { play.board.value.status == PlayBoardStatus.Ready && spec.shown(play) != null }
        return daily to play
    }

    private fun storedNow(daily: FakeDailyRepository, spec: Spec): Puzzle? = runBlocking { daily.cachedPuzzle(spec.game, day) }

    /** The refresh is answered and the player either has or has not acted first: [swapped] says what must follow. */
    private fun assertRefresh(spec: Spec, before: (ActivePlay) -> Unit, swapped: Boolean) {
        lateinit var gate: CompletableDeferred<Unit>
        val (daily, play) = open(spec) { gate = holdRefresh(spec.game) }
        before(play)
        gate.complete(Unit)
        awaitUntil("${spec.game.value} refresh answered") { daily.todayAnswers(spec.game) == 1 }
        if (swapped) {
            awaitUntil("${spec.game.value} swapped") { spec.shown(play) == spec.revisedPuzzle }
            assertSame(spec.revised, storedNow(daily, spec))
        } else {
            Thread.sleep(SETTLE_MS) // nothing happens to wait on: the answer is in, the swap would follow at once
            assertEquals(spec.storedPuzzle, spec.shown(play))
            assertSame(spec.stored, storedNow(daily, spec))
        }
    }

    private fun assertFailedKeepsBoard(spec: Spec) {
        val (daily, play) = open(spec) { setRefresh(spec.game, DailyRefresh.Failed(FetchError.Unavailable("offline"))) }
        awaitUntil("${spec.game.value} refresh answered") { daily.todayAnswers(spec.game) == 1 }
        Thread.sleep(SETTLE_MS)
        assertEquals(PlayBoardStatus.Ready, play.board.value.status)
        assertEquals(spec.storedPuzzle, spec.shown(play))
    }

    private fun foldClock(play: ActivePlay) {
        (play.controller as PlayClock).onBackground()
    }

    @Test fun `zip swaps an untouched board to the changed daily`() = assertRefresh(zip, {}, swapped = true)
    @Test fun `zip keeps a board after a move`() = assertRefresh(zip, zip.move, swapped = false)
    @Test fun `zip keeps a board after an input that logs nothing`() = assertRefresh(zip, zip.unlogged, swapped = false)
    @Test fun `zip swaps after the adopt's session and a clock fold`() = assertRefresh(zip, ::foldClock, swapped = true)
    @Test fun `zip shows no banner when the refresh fails`() = assertFailedKeepsBoard(zip)

    @Test fun `tango swaps an untouched board to the changed daily`() = assertRefresh(tango, {}, swapped = true)
    @Test fun `tango keeps a board after a move`() = assertRefresh(tango, tango.move, swapped = false)
    @Test fun `tango keeps a board after a refused tap`() = assertRefresh(tango, tango.unlogged, swapped = false)
    @Test fun `tango swaps after the adopt's session and a clock fold`() = assertRefresh(tango, ::foldClock, swapped = true)
    @Test fun `tango shows no banner when the refresh fails`() = assertFailedKeepsBoard(tango)

    @Test fun `queens swaps an untouched board to the changed daily`() = assertRefresh(queens, {}, swapped = true)
    @Test fun `queens keeps a board after a move`() = assertRefresh(queens, queens.move, swapped = false)
    @Test fun `queens keeps a board after an undo with nothing to undo`() = assertRefresh(queens, queens.unlogged, swapped = false)
    @Test fun `queens swaps after the adopt's session and a clock fold`() = assertRefresh(queens, ::foldClock, swapped = true)
    @Test fun `queens shows no banner when the refresh fails`() = assertFailedKeepsBoard(queens)

    @Test fun `mini sudoku swaps an untouched board to the changed daily`() = assertRefresh(sudoku, {}, swapped = true)
    @Test fun `mini sudoku keeps a board after a move`() = assertRefresh(sudoku, sudoku.move, swapped = false)
    @Test fun `mini sudoku keeps a board after an undo with nothing to undo`() = assertRefresh(sudoku, sudoku.unlogged, swapped = false)
    @Test fun `mini sudoku swaps after the adopt's session and a clock fold`() = assertRefresh(sudoku, ::foldClock, swapped = true)
    @Test fun `mini sudoku shows no banner when the refresh fails`() = assertFailedKeepsBoard(sudoku)

    @Test fun `strands swaps an untouched board to the changed daily`() = assertRefresh(strands, {}, swapped = true)
    @Test fun `strands keeps a board after a found word`() = assertRefresh(strands, strands.move, swapped = false)
    @Test fun `strands keeps a board after a half-drawn trace`() = assertRefresh(strands, strands.unlogged, swapped = false)
    @Test fun `strands swaps after the adopt's session and a clock fold`() = assertRefresh(strands, ::foldClock, swapped = true)
    @Test fun `strands shows no banner when the refresh fails`() = assertFailedKeepsBoard(strands)

    private companion object {
        const val SETTLE_MS = 300L

        fun zipRevised() = ZipPlayFixture.puzzle().let { it.copy(board = it.board.copy(waypoints = listOf(Cell(0), Cell(24)))) }

        fun tangoRevised() = TangoPlayFixture.puzzle.let { it.copy(givens = it.givens + (2 to TangoPlayFixture.solution[2])) }

        fun queensRevised() = QueensPlayFixture.puzzle(listOf(2, 4, 11, 13))

        fun sudokuRevised() = MiniSudokuPlayFixture.puzzle.let { p ->
            val idx = p.givens.indexOf(0)
            p.copy(givens = p.givens.toMutableList().also { it[idx] = p.solution[idx] })
        }

        fun strandsRevised() = StrandsPlayFixture.puzzle.copy(clue = "Other sale")
    }
}
