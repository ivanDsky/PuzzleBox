package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.QueensDailyEntry
import com.ivandsky.puzzlebox.data.repository.QueensDailyRepository
import com.ivandsky.puzzlebox.data.repository.QueensToday
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

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

    /** Envelope as QueensDailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(): Puzzle = Puzzle(
        gameId = QueensDefinition.id,
        variant = queensVariantId(8),
        formatVersion = QueensCodec.VERSION,
        payload = QueensCodec.encode(puzzle()),
    )
}

class FakeQueensDailyRepository(
    private val todayResult: QueensToday,
    private val history: List<QueensDailyEntry> = emptyList(),
) : QueensDailyRepository {

    override suspend fun today(): QueensToday = todayResult

    override suspend fun cached(day: LocalDate): Puzzle? = null

    override fun observeHistory(): Flow<List<QueensDailyEntry>> =
        kotlinx.coroutines.flow.flowOf(history)

    companion object {
        /** Today in Queens' cadence zone — the identity of the loaded board. */
        fun today(): LocalDate = LocalDate.now((QueensDefinition.cadence as Cadence.Daily).zone)

        fun unavailable() = FakeQueensDailyRepository(
            todayResult = QueensToday.Unavailable(FetchError.Unavailable("offline")),
            history = listOf(QueensDailyEntry(today(), 42, "queens/n8", 0L)),
        )
    }
}
