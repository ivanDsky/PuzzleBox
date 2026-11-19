package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.domain.isStreakDay
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.moments.SolveFacts
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * Replays the full game history, in the order the solves were made, and builds for every counted solve the [SolveFacts]
 * the Moment detector read the moment it was solved: the game's best before it, the streak before it, how much of the
 * day's set was done. It builds only what the detector reads and judges nothing itself, so a recap's Moments are the
 * ones Result celebrated (A6); friends are absent, so top three and passed a friend never come out.
 *
 * The replay needs the whole history because a personal best, a first sub-X and a streak are all relative to what came
 * before, and a period's first solve has the earlier months behind it. The caller cuts the period out of the result.
 *
 * Nothing here reads the clock's today: Result judged each solve on the day it was made, so a replay does too, and a
 * past month's Moments do not change as days pass.
 *
 * A solve is made on its own puzzle day, except an archive solve (a past puzzle opened later), which is made on the
 * puzzle day of its completion time ([GetPuzzleDay.on]). An archive solve without a completion time is taken as made
 * on its own puzzle day. Per solve, as Result builds them:
 * - the best is the game's best counted solve made before it by the game's rank order, judged by [RecordVerdict], the
 *   rule Result's badge uses; the first solve of a game is no personal best.
 * - the streak of a streak day is the day's place in its streak run, frozen days included ([StreakRuns]); only the
 *   day's first streak solve moves it, so a streak Moment is earned once a day.
 * - the streak of an archive solve is read as Result reads it: the current streak on the day it was made, without the
 *   archive day and with it, over the streak days known by then. It moves only when the day joins runs.
 * - the set is every listed game; the games solved on the day it was made, before it, plus this one are what is done,
 *   as Result reads today's set. The solves of a day are taken in the order they were completed (an import has the
 *   instant it was imported, so game order breaks the tie), and the last of them finishes the set.
 */
internal class ReplaySolveFacts(
    private val computeStreak: ComputeStreak,
    private val puzzleDay: GetPuzzleDay,
    private val registry: GameRegistry,
) {

    private class Solve(val row: GameHistoryDay, val madeOn: LocalDate) {
        val record get() = checkNotNull(row.record)
    }

    operator fun invoke(history: List<GameHistoryDay>): List<ReplayedSolve> {
        val counted = history.filter { it.isSolvedDaily() }
        val streakDays = counted.filter { it.isStreakDay() }.map { it.day }
        val ledger = computeStreak(streakDays)
        val streaks = StreakRuns.of(streakDays, ledger.freezes)
        val games = registry.all.map { it.definition.id }
        val ordered = counted.map { Solve(it, madeOn(it)) }
            .sortedWith(compareBy<Solve>({ it.madeOn }, { it.row.record?.firstCompletedAt }, { games.indexOf(it.row.gameId) }))
        val position = ordered.withIndex().associate { (index, solve) -> solve to index }
        val byMadeOn = ordered.groupBy { it.madeOn }

        val best = mutableMapOf<GameId, BestScore>()
        val bestHintFree = mutableMapOf<GameId, Long>()
        val streakMoved = mutableSetOf<LocalDate>()
        val replayed = mutableListOf<ReplayedSolve>()

        for (solve in ordered) {
            val record = solve.record
            val game = solve.row.gameId
            val day = solve.row.day
            val order = registry[game]?.score?.rankOrder ?: RankOrder.TimeThenHints
            // The other games' solves of the day this one was made on, made before it: what Result's today set holds.
            val others = byMadeOn[solve.madeOn].orEmpty()
                .filter { it.row.gameId != game && position.getValue(it) < position.getValue(solve) }

            val streakBefore: Int
            val streak: Int
            if (solve.row.isStreakDay()) {
                val movesStreak = streakMoved.add(day)
                streak = streaks.lengthAt(day)
                streakBefore = if (movesStreak) streak - 1 else streak
            } else {
                val known = streakDays.filterTo(mutableSetOf()) { it <= solve.madeOn }
                streakBefore = computeStreak(known, solve.madeOn).current
                streak = if (day in known) streakBefore else computeStreak(known + day, solve.madeOn).current
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
                    doneToday = others.size + 1,
                    totalToday = games.size,
                    dayTotal = Moment.FullSetDone.DayTotal(
                        timeMs = record.time + others.sumOf { it.record.time },
                        hints = record.hints + others.sumOf { it.record.hints },
                    ),
                    streakBefore = streakBefore,
                    streak = streak,
                ),
            )
            val score = BestScore(record.time, record.hints)
            best[game] = best[game]?.let { minOf(it, score, order.comparator) } ?: score
            if (record.hints == 0) bestHintFree[game] = minOf(bestHintFree[game] ?: Long.MAX_VALUE, record.time)
        }
        return replayed
    }

    /** The puzzle day a solve was made on: its own, or for an archive solve with a completion time, the day that time fell on. */
    private fun madeOn(row: GameHistoryDay): LocalDate {
        val completedAt = row.record?.firstCompletedAt
        if (row.isStreakDay() || completedAt == null) return row.day
        return maxOf(row.day, puzzleDay.on(completedAt))
    }
}
