package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class SudokuPlayControllerTest {

    private var clockMs = 1_000L
    private val saved = ArrayList<ByteArray>()
    private val won = ArrayList<CompletionRecord>()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun envelope(puzzle: SudokuPuzzle = peanut450, gameId: com.ivandsky.puzzlebox.model.GameId = SudokuGames.MINI.id) =
        Puzzle(gameId, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))

    private fun controller() = SudokuPlayController(
        definition = SudokuGames.MINI,
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }),
        highlights = InMemorySudokuHighlightSettings(),
        now = { Instant.ofEpochMilli(clockMs) },
    )

    private fun adopted(): SudokuPlayController = controller().also { assertEquals(Adopt.Adopted, it.adoptToday(envelope(), ref)) }

    private fun savedSession(): SudokuSession = (SudokuSessionCodec.deserialize(saved.last()) as DecodeResult.Ok).value

    @Test
    fun `adopting 450 publishes the fresh session`() {
        val c = adopted()
        assertEquals(peanut450, c.board!!.session.puzzle)
        assertEquals(ref, savedSession().ref)
        assertFalse(c.isComplete)
    }

    @Test
    fun `placing a digit publishes a session that decodes back with that entry`() {
        val c = adopted()
        c.board!!.select(0)
        c.board!!.input(4)
        assertEquals(SudokuCell.Entry(4), savedSession().board.cells[0])
        assertTrue(c.canUndo)
    }

    @Test
    fun `placing on a given changes nothing`() {
        val c = adopted()
        c.board!!.select(2)
        val before = saved.size
        c.board!!.input(1)
        assertEquals(before, saved.size)
    }

    @Test
    fun `a foreign envelope is rejected`() {
        val c = controller()
        assertTrue(c.adoptToday(envelope(gameId = SudokuGames.CLASSIC.id), ref) is Adopt.Rejected)
        assertNull(c.board)
    }

    @Test
    fun `solving emits one win record with the banked time`() {
        val c = adopted()
        val open = givens.indices.filter { givens[it] == 0 }
        c.board!!.select(open.first())
        c.board!!.input(solution[open.first()])
        clockMs += 5_000
        for (idx in open.drop(1)) {
            c.board!!.select(idx)
            c.board!!.input(solution[idx])
        }
        assertTrue(c.isComplete)
        assertEquals(1, won.size)
        assertEquals(ref, won.single().puzzleRef)
        assertEquals(5_000L, won.single().time)
    }

    @Test
    fun `restore brings back the entry without re-emitting`() {
        adopted().board!!.apply { select(0); input(4) }
        val c = controller()
        assertEquals(Restored(ref), c.adoptRestored(saved.last()))
        assertEquals(SudokuCell.Entry(4), c.board!!.session.board.cells[0])
        assertTrue(won.isEmpty())
    }

    @Test
    fun `undo reverts the placement`() {
        val c = adopted()
        c.board!!.select(0)
        c.board!!.input(4)
        c.undo()
        assertEquals(SudokuCell.Empty, savedSession().board.cells[0])
    }

    /** What the timer bar reads after the loop's next tick. */
    private fun SudokuPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    @Test
    fun `a fresh puzzle's timer runs with no move made`() {
        val c = adopted()
        assertNotNull(c.clockWindow)

        clockMs += 3_000

        assertEquals(3_000L, c.shown())
    }

    @Test
    fun `selecting and the notes toggle leave the running clock alone`() {
        val c = adopted()
        c.board!!.select(0)
        c.board!!.toggleNotesMode()
        clockMs += 3_000
        assertEquals(3_000L, c.shown())
        c.board!!.toggleNotesMode()
        c.board!!.input(4)
        clockMs += 2_000
        assertEquals(5_000L, c.shown()) // a move neither restarts nor shifts the window
    }

    @Test
    fun `a restored in-progress session continues from its saved time and advances`() {
        val first = adopted()
        first.board!!.select(0)
        first.board!!.input(4)
        clockMs += 1_500
        first.onBackground()
        val bytes = saved.last()

        clockMs += 60_000 // process-death gap: never counted
        val c = controller()
        assertNotNull(c.adoptRestored(bytes))
        assertEquals(1_500L, c.shown())
        clockMs += 2_000
        assertEquals(3_500L, c.shown())
    }

    @Test
    fun `a restored untouched session's timer runs too`() {
        val first = adopted()
        first.onBackground()
        val c = controller()
        assertNotNull(c.adoptRestored(saved.last()))
        clockMs += 1_000
        assertEquals(1_000L, c.shown())
    }

    @Test
    fun `background folds the running window and foreground reopens it without a move`() {
        val c = adopted()
        clockMs += 1_500
        c.onBackground()
        assertNull(c.clockWindow)
        assertEquals(1_500L, savedSession().elapsedMs)
        clockMs += 60_000
        assertEquals(1_500L, c.shown())
        c.onForeground()
        assertNotNull(c.clockWindow)
        clockMs += 500
        assertEquals(2_000L, c.shown())
    }

    @Test
    fun `replay restarts from zero and advances at once`() {
        val c = adopted()
        val open = givens.indices.filter { givens[it] == 0 }
        clockMs += 4_000
        for (idx in open) {
            c.board!!.select(idx)
            c.board!!.input(solution[idx])
        }
        assertTrue(c.isComplete)
        assertNull(c.clockWindow)

        c.replay()
        assertEquals(0L, c.shown())
        clockMs += 700
        assertEquals(700L, c.shown())
    }

    @Test
    fun `a counted hint is counted and the clock keeps running`() {
        val c = adopted()
        assertTrue(c.hintEnabled)
        c.requestHint()
        assertEquals(1, c.hintsUsed)
        assertNotNull(c.hintCaption)
        assertNotNull(c.clockWindow)
    }

    @Test
    fun `solving by hint reveals emits the win once and disables hints`() {
        val c = adopted()
        repeat(givens.count { it == 0 } * 2) { c.requestHint() }
        assertTrue(c.isComplete)
        c.requestHint()
        c.undo()
        assertEquals(1, won.size)
        assertFalse(c.hintEnabled)
        assertNull(c.clockWindow)
    }

    @Test
    fun `a given refuses input through the feedback seam`() {
        val c = adopted()
        c.board!!.select(2)
        c.board!!.input(1)
        assertEquals("That number is part of the puzzle.", c.rejection)
    }

    @Test
    fun `restore keeps notes mode and starts with no selection`() {
        adopted().board!!.apply {
            select(0)
            toggleNotesMode()
            input(4)
        }
        val c = controller()
        c.adoptRestored(saved.last())
        assertTrue(c.board!!.session.notesMode)
        assertEquals(SudokuCell.Notes(1 shl 3), c.board!!.session.board.cells[0])
        assertNull(c.board!!.selected)
        assertNotNull(c.clockWindow)
    }

    // Stepwise hints (ticket 34)

    private val stepwiseSource = HintModeSource { HintMode.Stepwise(Dispatchers.Unconfined) }

    private val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it })

    /** Lab board rnd:154: no single, so the first press shows an elimination. */
    private val noSingle = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..62.4 42.6.. 6.142. 24...6 .6254. ..4.62"),
        grid("516234 423651 631425 245316 162543 354162"),
    )

    private fun stepwiseMini(puzzle: SudokuPuzzle = peanut450): SudokuPlayController =
        SudokuPlayController(SudokuGames.MINI, env, InMemorySudokuHighlightSettings(), now = { Instant.ofEpochMilli(clockMs) }, hintMode = stepwiseSource.current())
            .also { assertEquals(Adopt.Adopted, it.adoptToday(envelope(puzzle), ref)) }

    @Test
    fun `a stepwise mini press shows a spotlight, places nothing and publishes the count`() {
        val c = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings(), MiniSudokuBrand, stepwiseSource).createController(env) as SudokuPlayController
        assertEquals(Adopt.Adopted, c.adoptToday(envelope(), ref))
        val board = c.board!!.session.board

        c.requestHint()

        assertNotNull(c.board!!.spotlight)
        assertNull(c.board!!.hint)
        assertNull(c.hintCaption)
        assertEquals(board, c.board!!.session.board)
        assertEquals(1, c.hintsUsed)
        assertEquals(1, savedSession().hintsUsed)
    }

    @Test
    fun `a stepwise press leaves the running clock exactly as the legacy press does`() {
        val c = stepwiseMini()
        clockMs += 2_000
        val window = c.clockWindow
        val shown = c.shown()
        val banked = c.board!!.session.elapsedMs

        c.requestHint()

        assertNotNull(c.board!!.spotlight)
        assertEquals(window, c.clockWindow)
        assertEquals(shown, c.shown())
        assertEquals(banked, c.board!!.session.elapsedMs)
        assertEquals(1, c.hintsUsed)
    }

    @Test
    fun `a replay starts a board with no spotlight and nothing known`() {
        val c = stepwiseMini(noSingle)
        c.requestHint()
        val first = checkNotNull(c.board!!.spotlight)
        c.board!!.toggleNotesMode()

        c.replay()

        assertNull(c.board!!.spotlight)
        c.requestHint()
        assertEquals(first.roles, c.board!!.spotlight?.roles)
        assertEquals(1, c.hintsUsed)
    }

    @Test
    fun `a press still computing when the board is replayed never touches the new session`() {
        val scheduler = TestCoroutineScheduler()
        val held = HintMode.Stepwise(StandardTestDispatcher(scheduler))
        val c = SudokuPlayController(SudokuGames.MINI, env, InMemorySudokuHighlightSettings(), now = { Instant.ofEpochMilli(clockMs) }, hintMode = held)
        assertEquals(Adopt.Adopted, c.adoptToday(envelope(noSingle), ref))
        c.requestHint()

        c.replay()
        val savedByReplay = saved.size
        scheduler.advanceUntilIdle()

        assertEquals(savedByReplay, saved.size)
        assertEquals(0, savedSession().hintsUsed)
        assertEquals(0, c.hintsUsed)
        assertNull(c.board!!.spotlight)
    }

    @OptIn(KoinInternalApi::class)
    @Test
    fun `classic keeps the legacy hint under a stepwise source`() {
        val koin = koinApplication { modules(miniSudokuUiModule, sudokuUiModule, module { single { stepwiseSource }; single<SudokuHighlightSettings> { InMemorySudokuHighlightSettings() } }) }.koin
        try {
            val c = koin.get<GamePlayUi>(qualifier(SudokuGames.CLASSIC.id.value)).createController(env) as SudokuPlayController
            val classicRef = SudokuPuzzleRef(SudokuGames.CLASSIC.id, ref.key)
            assertEquals(Adopt.Adopted, c.adoptToday(envelope(SudokuSamples.classic, SudokuGames.CLASSIC.id), classicRef))

            c.requestHint()

            assertNull(c.board!!.spotlight)
            assertNotNull(c.board!!.hint)
            assertNotNull(c.hintCaption)
            assertEquals(1, c.hintsUsed)
        } finally {
            koin.close()
            // Koin caches each single on the top-level module's factory; drop it so no other test sees this play UI.
            listOf(miniSudokuUiModule, sudokuUiModule).forEach { module -> module.mappings.values.forEach { it.dropAll() } }
        }
    }
}
