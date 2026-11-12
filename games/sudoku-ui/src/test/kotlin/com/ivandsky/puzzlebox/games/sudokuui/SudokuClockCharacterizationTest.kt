package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the Sudoku clock at start, tick, background, foreground and win, against an injected clock. */
class SudokuClockCharacterizationTest {

    private var clockMs = 1_000L
    private val saved = ArrayList<ByteArray>()
    private val won = ArrayList<CompletionRecord>()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun envelope() = Puzzle(SudokuGames.MINI.id, peanut450.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(peanut450))

    private fun controller() = SudokuPlayController(
        definition = SudokuGames.MINI,
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }),
        highlights = InMemorySudokuHighlightSettings(),
        now = { Instant.ofEpochMilli(clockMs) },
    )

    private fun savedSession(): SudokuSession = (SudokuSessionCodec.deserialize(saved.last()) as DecodeResult.Ok).value

    /** What the timer bar reads after the loop's next tick. */
    private fun SudokuPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    @Test
    fun `start tick background foreground and win pin the displayed and recorded time`() {
        val c = controller()
        assertEquals(Adopt.Adopted, c.adoptToday(envelope(), ref))

        assertEquals(0L, c.shown())

        clockMs += 2_000
        assertEquals(2_000L, c.shown())

        clockMs += 1_000
        c.onBackground()
        assertEquals(3_000L, savedSession().elapsedMs)
        clockMs += 60_000
        assertEquals(3_000L, c.shown())

        c.onForeground()
        clockMs += 500
        assertEquals(3_500L, c.shown())

        clockMs += 1_500
        val open = givens.indices.filter { givens[it] == 0 }
        for (idx in open) {
            c.board!!.select(idx)
            c.board!!.input(solution[idx])
        }
        assertEquals(1, won.size)
        assertEquals(5_000L, won.single().time)
        assertEquals(5_000L, savedSession().elapsedMs)
        assertEquals(5_000L, c.shown())
    }
}
