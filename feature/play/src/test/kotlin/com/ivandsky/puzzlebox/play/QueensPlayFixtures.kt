package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.testing.FakeDailyRepository
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.time.LocalDate

/** Deterministic 8×8 fixture (queens-10 wiring tests): one queen per row/column/region
 *  on the main diagonal; row = region, so each row-region holds exactly one queen. */
object QueensPlayFixtures {

    val solution: List<Cell> = listOf(0, 12, 23, 29, 34, 46, 49, 59).map(::Cell)

    fun puzzle(): QueensPuzzle = QueensPuzzle(
        size = 8,
        regions = List(64) { it / 8 },
        queensPerUnit = 1,
        solution = solution,
    )

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(): Puzzle = Puzzle(
        gameId = QueensDefinition.id,
        variant = queensVariantId(8),
        formatVersion = QueensCodec.VERSION,
        payload = QueensCodec.encode(puzzle()),
    )

    /** Today in Queens' cadence zone — the identity of the loaded board. */
    fun today(): LocalDate = QueensDefinition.cadence.today()

    /** Today's captured Queens day (#42). */
    fun entry(day: LocalDate = today()) = DailyEntry(QueensDefinition.id, day, 42, "queens/n8", 0L)

    /** A daily repository serving [envelope] fresh for Queens. */
    fun fresh(history: List<DailyEntry> = emptyList()) =
        FakeDailyRepository().script(QueensDefinition.id, DailyToday.Fresh(envelope()), history)

    /** A daily repository whose Queens fetch fails with no cache. */
    fun unavailable() = FakeDailyRepository().script(
        QueensDefinition.id,
        DailyToday.Unavailable(FetchError.Unavailable("offline")),
        listOf(entry()),
    )
}
