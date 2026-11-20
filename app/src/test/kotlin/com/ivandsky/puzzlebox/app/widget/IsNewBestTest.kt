package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsNewBestTest {

    private val zip = GameId("zip")
    private val today = LocalDate.of(2026, 11, 20)
    private val isNewBest = IsNewBest()

    private data class Ref(override val gameId: GameId, override val key: PuzzleKey) : PuzzleRef

    private fun record(game: GameId, key: PuzzleKey, time: Long, hints: Int = 0) =
        CompletionRecord(Ref(game, key), time, hints, RecordStats(1, "{}"))

    private fun daily(daysAgo: Long, time: Long, hints: Int = 0, game: GameId = zip) =
        record(game, PuzzleKey.Daily(today.minusDays(daysAgo), puzzleNumber = null), time, hints)

    private fun check(vararg records: CompletionRecord, order: RankOrder = RankOrder.TimeThenHints) =
        isNewBest(records.toList(), zip, today, order)

    @Test
    fun `beating the best of earlier days is a new best`() {
        assertTrue(check(daily(0, time = 40_000), daily(1, time = 50_000), daily(2, time = 60_000)))
    }

    @Test
    fun `tying the best of earlier days is a new best`() {
        assertTrue(check(daily(0, time = 50_000), daily(1, time = 50_000)))
    }

    @Test
    fun `slower than an earlier day is not a new best`() {
        assertFalse(check(daily(0, time = 55_000), daily(1, time = 50_000), daily(2, time = 90_000)))
    }

    @Test
    fun `a first ever solve has nothing to beat`() {
        assertFalse(check(daily(0, time = 40_000)))
    }

    @Test
    fun `no solve today is not a new best`() {
        assertFalse(check(daily(1, time = 50_000)))
    }

    @Test
    fun `the game's own order decides, so fewer hints beat a faster time under hints-first`() {
        val records = arrayOf(daily(0, time = 90_000, hints = 0), daily(1, time = 40_000, hints = 2))

        assertTrue(check(*records, order = RankOrder.HintsThenTime))
        assertFalse(check(*records, order = RankOrder.TimeThenHints))
    }

    @Test
    fun `another game's solves and custom puzzles are not counted`() {
        assertFalse(
            check(
                daily(0, time = 50_000),
                daily(1, time = 10_000, game = GameId("queens")),
                record(zip, PuzzleKey.Custom("ABC234"), time = 10_000),
            ),
        )
    }
}
