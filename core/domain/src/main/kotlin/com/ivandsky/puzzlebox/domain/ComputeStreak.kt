package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The one streak rule, over a streak's solved puzzle days. Callers pass only solved days;
 * opened-but-unsolved days never count. Duplicates collapse. Archived days (A23) are
 * history, not habit: playing one never extends or bridges the run, so callers drop them
 * before counting.
 *
 * Strict streak (A8): the run of consecutive solved days anchored at today's puzzle day
 * ([GetPuzzleDay]) — or yesterday when today has no solve yet, so the run stays alive
 * until the day is actually missed. With [ReleaseFlag.StreakFreeze] on, held freezes
 * cover missed days instead ([replay]). The flag is read on every computation rather
 * than collected: use cases have no UI scope to collect in, so a debug toggle shows on
 * the next history emission.
 */
class ComputeStreak(
    private val puzzleDay: GetPuzzleDay,
    private val releaseFlags: ReleaseFlags = ShippedReleaseFlags,
) {

    fun ledger(solvedDays: Collection<LocalDate>): StreakLedger {
        if (!releaseFlags.isEnabled(ReleaseFlag.StreakFreeze)) {
            return StreakLedger(current = strictCurrent(solvedDays), best = strictBest(solvedDays))
        }
        return replay(solvedDays).ledger
    }

    /**
     * The freeze rule replayed forward once: over the solved days, then up to today. Nothing
     * about freezes is stored, so a day that arrives late simply replays differently. Gaps are
     * measured, never walked, so the cost follows the solve count, not the calendar span.
     * Flag-blind: [ledger] owns the gate.
     */
    internal fun replay(solvedDays: Collection<LocalDate>): Replay {
        var current = 0
        var best = 0
        var held = 0
        var step = 0
        var run = 0
        val frozenDays = mutableSetOf<LocalDate>()
        var lastSolve: StreakFreezes.Moment? = null

        // A gap longer than the freezes held leaves no frozen day behind: live, the player saw
        // them spent one by one and the streak break anyway.
        fun miss(after: LocalDate, before: LocalDate): List<LocalDate> {
            val gap = ChronoUnit.DAYS.between(after, before) - 1
            if (gap <= 0) return emptyList()
            if (gap > held) {
                current = 0
                held = 0
                step = 0
                run = 0
                return emptyList()
            }
            held -= gap.toInt()
            step = maxOf(0, step - gap.toInt())
            run = 0
            return (1..gap).map(after::plusDays).also { frozenDays += it }
        }

        for (day in solvedDays.toSortedSet()) {
            val previous = lastSolve?.day
            if (previous == null) held = STARTING_FREEZES
            val spent = previous?.let { miss(it, day) }.orEmpty()
            current++
            run++
            best = maxOf(best, current)
            val stepDone = run == ladderLength(step)
            val earned = stepDone && held < MAX_FREEZES
            if (stepDone) {
                run = 0
                step++
            }
            if (earned) held++
            lastSolve = StreakFreezes.Moment(day, spent, earned)
        }
        // Today bounds the gap from outside: open with no solve, it is never a miss.
        lastSolve?.let { miss(it.day, puzzleDay()) }

        val daysToNext = if (held == MAX_FREEZES) null else ladderLength(step) - run
        return Replay(StreakLedger(current, best, StreakFreezes(held, frozenDays, daysToNext, lastSolve)), step, run)
    }

    /** [step] and [run] stay inside the module: surfaces read only [StreakFreezes.daysToNext]. */
    internal data class Replay(val ledger: StreakLedger, val step: Int, val run: Int)

    private fun strictCurrent(solvedDays: Collection<LocalDate>): Int {
        val solved = solvedDays.toHashSet()
        val today = puzzleDay()
        var day = if (today in solved) today else today.minusDays(1)
        var streak = 0
        while (day in solved) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun strictBest(solvedDays: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in solvedDays.toSortedSet()) {
            run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }
}

private const val STARTING_FREEZES = 2
private const val MAX_FREEZES = 5

private fun ladderLength(step: Int): Int = when (step) {
    0 -> 7
    1 -> 14
    else -> 28
}
