package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A daily source whose answers a test scripts per day and attempt (0 for the first fetch of a day).
 * Every fetch is logged into [events] as `fetch <day>`, in order with whatever else the test logs there.
 * The default answer is an Ok puzzle whose payload is the number, which [FakeTechniqueWalker] walks.
 */
class ScriptedDailySource(
    override val gameId: GameId,
    override val archiveSince: LocalDate,
    private val events: MutableList<String>,
    private val reply: (day: LocalDate, attempt: Int) -> FetchResult<DailyFetch> = { day, _ -> puzzleOf(gameId, day, archiveSince) },
) : DailySource {

    private val attempts = HashMap<LocalDate, Int>()

    /** Every day fetched, in order, repeats included. */
    val fetched: MutableList<LocalDate> = mutableListOf()

    override fun puzzleNumber(day: LocalDate): Int = number(archiveSince, day)

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> = error("the index pass fetches past days only")

    override suspend fun fetchPast(date: LocalDate): FetchResult<DailyFetch> {
        fetched += date
        events += "fetch $date"
        val attempt = attempts.merge(date, 1, Int::plus)!! - 1
        return reply(date, attempt)
    }

    companion object {
        private fun number(since: LocalDate, day: LocalDate): Int = ChronoUnit.DAYS.between(since, day).toInt() + 1

        /** An Ok fetch of [day]'s puzzle; the payload byte is its number, or 0 when [walkable] is false. */
        fun puzzleOf(game: GameId, day: LocalDate, since: LocalDate, walkable: Boolean = true): FetchResult<DailyFetch> {
            val number = number(since, day)
            val payload = byteArrayOf(if (walkable) number.toByte() else 0)
            val puzzle = Puzzle(game, VariantId("v"), 1, payload)
            val ref = object : PuzzleRef {
                override val gameId: GameId = game
                override val key: PuzzleKey = PuzzleKey.Daily(day, number)
            }
            return FetchResult.Ok(DailyFetch(puzzle, ref))
        }
    }
}
