package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.domain.isStreakDay
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.moments.SolveFacts
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.GameId

/**
 * Replays the full game history, oldest day first, and builds for every counted solve the [SolveFacts] the Moment
 * detector would have read the moment it was solved: the game's best before it, the streak before it, how much of the
 * day's set was done. It builds only what the detector reads and judges nothing itself, so a recap's Moments are the
 * ones Result celebrated (A6); friends are absent, so top three and passed a friend never come out.
 *
 * The replay needs the whole history because a personal best, a first sub-X and a streak are all relative to what came
 * before, and a period's first solve has the earlier months behind it. The caller cuts the period out of the result.
 *
 * Per solve, as Result builds them for today's:
 * - the best is the game's best counted solve before it by the game's rank order, judged by [RecordVerdict], the rule
 *   Result's badge uses; the first solve of a game is no personal best.
 * - the streak is the day's place in its streak run, frozen days included ([StreakRuns]); only the day's first streak
 *   solve moves it, so a streak Moment is earned once a day.
 * - the set is every listed game; the solves of a day are taken in the order they were completed (an import has the
 *   instant it was imported, so game order breaks the tie), and the last of them finishes the set.
 * - a solve of a day that is no streak day (an archive puzzle opened later) is still a counted solve, so it is no play
 *   again: it earns what Result gives a first solve of an archive puzzle. Result reads its streak as the current streak
 *   without and with that day ([ComputeStreak]), so an archive day moves the streak only when it joins runs, and the
 *   streak Moment comes only then.
 */
internal class ReplaySolveFacts(
    private val computeStreak: ComputeStreak,
    private val registry: GameRegistry,
) {

    operator fun invoke(history: List<GameHistoryDay>): List<ReplayedSolve> {
        val counted = history.filter { it.isSolvedDaily() }
        val streakDays = counted.filter { it.isStreakDay() }.map { it.day }
        val ledger = computeStreak(streakDays)
        val streaks = StreakRuns.of(streakDays, ledger.freezes)
        val streakDaySet = streakDays.toSet()
        val games = registry.all.map { it.definition.id }
        val best = mutableMapOf<GameId, BestScore>()
        val bestHintFree = mutableMapOf<GameId, Long>()
        val replayed = mutableListOf<ReplayedSolve>()

        for ((day, solves) in counted.groupBy { it.day }.toSortedMap()) {
            val inOrder = solves.sortedWith(compareBy<GameHistoryDay>({ it.record?.firstCompletedAt }, { games.indexOf(it.gameId) }))
            val runStreak = streaks.lengthAt(day)
            var done = 0
            var timeMs = 0L
            var hints = 0
            var streakMoved = false
            for (solve in inOrder) {
                val record = checkNotNull(solve.record)
                val game = solve.gameId
                val order = registry[game]?.score?.rankOrder ?: RankOrder.TimeThenHints
                done++
                timeMs += record.time
                hints += record.hints
                val movesStreak = solve.isStreakDay() && !streakMoved
                streakMoved = streakMoved || movesStreak
                // An archive day is read as Result reads it: the current streak without the day and with it.
                val streakBefore: Int
                val streak: Int
                if (solve.isStreakDay()) {
                    streak = runStreak
                    streakBefore = if (movesStreak) runStreak - 1 else runStreak
                } else {
                    streakBefore = ledger.current
                    streak = if (day in streakDaySet) ledger.current else computeStreak(streakDaySet + day).current
                }
                replayed += ReplayedSolve(
                    game = game,
                    day = day,
                    facts = SolveFacts(
                        timeMs = record.time,
                        hints = record.hints,
                        playAgain = false,
                        practice = false,
                        newBest = RecordVerdict.of(best[game], record.time, record.hints, playAgain = false, order = order) == RecordVerdict.NewBest,
                        bestHintFreeMs = bestHintFree[game],
                        doneToday = done,
                        totalToday = games.size,
                        dayTotal = Moment.FullSetDone.DayTotal(timeMs, hints),
                        streakBefore = streakBefore,
                        streak = streak,
                    ),
                )
                val score = BestScore(record.time, record.hints)
                best[game] = best[game]?.let { minOf(it, score, order.comparator) } ?: score
                if (record.hints == 0) bestHintFree[game] = minOf(bestHintFree[game] ?: Long.MAX_VALUE, record.time)
            }
        }
        return replayed
    }
}
