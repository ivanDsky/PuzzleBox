package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import kotlinx.coroutines.flow.first

/**
 * Finds the puzzle of one duel leg. A daily leg's puzzle is taken from the cache, else fetched like the play path does;
 * a friend's leg from the row stored when its code opened, else opened now, so a leg this device never opened still has
 * its puzzle. The replay of a leg ([LoadLegReplay]) and the board the duel page previews both read it from here.
 * Reading keeps nothing: a friend's puzzle is saved when its duel is accepted or the puzzle is played, not by being looked
 * at, and the player's own puzzle (a [published] one) is read as theirs, never as a friend's, and is not called taken down
 * when they withdrew it.
 */
class LoadLegPuzzle(
    private val daily: DailyRepository,
    private val received: ReceivedPuzzleRepository,
    private val loadShared: LoadSharedPuzzle,
    private val published: PublishedPuzzleRepository,
) {
    suspend operator fun invoke(leg: LegRef): LegPuzzleRead = when (leg) {
        is LegRef.Daily -> dailyPuzzle(leg)
        is LegRef.Custom -> customPuzzle(leg)
    }

    private suspend fun dailyPuzzle(leg: LegRef.Daily): LegPuzzleRead {
        val cached = daily.cachedPuzzle(leg.gameId, leg.day)
        if (cached != null) return LegPuzzleRead.Found(cached)
        return when (val day = daily.day(leg.gameId, leg.day)) {
            is DailyDay.Ready -> LegPuzzleRead.Found(day.puzzle)
            is DailyDay.Failed -> if (day.error is FetchError.NotFound) LegPuzzleRead.Absent else LegPuzzleRead.Unavailable
            DailyDay.NotCaptured, DailyDay.OutOfRange -> LegPuzzleRead.Absent
        }
    }

    private suspend fun customPuzzle(leg: LegRef.Custom): LegPuzzleRead {
        val own = published.observe(leg.code).first() != null
        val row = received.get(leg.code) ?: when (val opened = loadShared.peek(leg.code, own)) {
            is OpenOutcome.Opened -> opened.puzzle
            OpenOutcome.Offline -> return LegPuzzleRead.Unavailable
            OpenOutcome.NoLongerAvailable -> return LegPuzzleRead.TakenDown
            else -> null
        }
        return row?.takeIf { it.gameId == leg.gameId }
            ?.let { LegPuzzleRead.Found(Puzzle(it.gameId, it.variant, it.sharedFormatVersion, it.puzzleBytes)) }
            ?: LegPuzzleRead.Absent
    }
}
