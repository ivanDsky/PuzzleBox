package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers

/** Deterministic 8×8 fixture (queens-10 wiring tests): [solution] is the only layout of [puzzle], so the stepper judges
 *  entries against it (ticket 43: a stored solution counts only when the board has no other). */
object QueensPlayFixtures {

    /** Columns 0 4 7 5 2 6 1 3 by row. */
    val solution: List<Cell> = listOf(0, 12, 23, 29, 34, 46, 49, 59).map(::Cell)

    fun puzzle(): QueensPuzzle = QueensPuzzle(
        size = 8,
        regions = listOf(
            0, 4, 1, 1, 1, 1, 2, 2,
            4, 4, 1, 2, 1, 1, 2, 2,
            4, 4, 2, 2, 2, 2, 2, 2,
            4, 4, 4, 4, 4, 3, 4, 4,
            5, 5, 4, 5, 4, 4, 4, 5,
            6, 5, 5, 5, 5, 5, 5, 5,
            6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 7, 7, 7, 7, 7, 7,
        ),
        queensPerUnit = 1,
        solution = solution,
    )

    /** Regions are rows and [solution] is stored, but thousands of layouts solve it. */
    fun rowsPuzzle(): QueensPuzzle = puzzle().copy(regions = List(64) { it / 8 })

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(puzzle: QueensPuzzle = puzzle()): Puzzle = Puzzle(
        gameId = QueensDefinition.id,
        variant = queensVariantId(8),
        formatVersion = QueensCodec.VERSION,
        payload = QueensCodec.encode(puzzle),
    )

    /** Today in Queens' cadence zone — the identity of the loaded board. */
    fun today(): LocalDate = QueensDefinition.cadence.today()

    /** Today's resolved record identity (#42). */
    fun ref(): QueensPuzzleRef = QueensPuzzleRef(PuzzleKey.Daily(today(), 42))

    /** The play registration as a release wires it: Stepwise hints, computed on the calling thread. */
    fun playUi(settings: QueensSettings = InMemoryQueensSettings()): QueensPlayUi =
        QueensPlayUi(settings, HintMode.Stepwise(Dispatchers.Unconfined))
}
