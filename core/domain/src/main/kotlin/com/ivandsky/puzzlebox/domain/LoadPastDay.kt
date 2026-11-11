package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * Opens a past day by date (ADR-0012): the cached day, else one fetch when the game's
 * source has an archive — so this may hit the network, and a fetched day opens offline
 * afterwards. The game's own plugin ([com.ivandsky.puzzlebox.game.GamePlugin.playAgainSession])
 * decodes and wraps it with the day's puzzleNumber identity (ADR-0004), so the envelope
 * names the game without any nav-shape change (ADR-0005).
 */
class LoadPastDay(
    private val daily: DailyRepository,
    private val registry: GameRegistry,
) {

    /** A null [puzzleNumber] takes the number the day was captured or fetched with. */
    suspend operator fun invoke(game: GameId, day: LocalDate, puzzleNumber: Int?): PastDayOutcome {
        // A game no registration authored is never opened.
        val plugin = registry[game] ?: return PastDayOutcome.Missing
        val ready = when (val read = daily.day(game, day)) {
            is DailyDay.Ready -> read
            DailyDay.NotCaptured -> return PastDayOutcome.Missing
            DailyDay.OutOfRange -> return PastDayOutcome.OutOfRange
            is DailyDay.Failed -> return PastDayOutcome.Failed(read.error)
        }
        val key = PuzzleKey.Daily(day, puzzleNumber ?: ready.puzzleNumber)
        return when (val session = plugin.playAgainSession(ready.puzzle, key)) {
            is DecodeResult.Ok -> PastDayOutcome.Ready(session.value)
            is DecodeResult.Err -> PastDayOutcome.Unreadable(session.error)
        }
    }
}
