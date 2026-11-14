package com.ivandsky.puzzlebox.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The one streak rule, over a streak's solved puzzle days. Callers pass only solved days;
 * opened-but-unsolved days never count. Duplicates collapse. Archived days (A23) are
 * history, not habit: playing one never extends or bridges the run, so callers drop them
 * before counting.
 *
 * The streak is the run of solved days anchored at today's puzzle day ([GetPuzzleDay]), or at
 * yesterday while today has no solve yet, so it stays alive until the day is actually missed.
 * Held freezes cover missed days ([replay]).
 */
class ComputeStreak(private val puzzleDay: GetPuzzleDay) {

    operator fun invoke(solvedDays: Collection<LocalDate>): StreakLedger = replay(solvedDays).ledger

    /**
     * The freeze rule replayed forward once: over the solved days, then up to today. Nothing
     * about freezes is stored, so a day that arrives late simply replays differently. Gaps are
     * measured, never walked, so the cost follows the solve count, not the calendar span.
     * Days after today (clock skew, another zone) feed no freeze number; [Replay.ledger]'s best
     * still counts them.
     */
    internal fun replay(solvedDays: Collection<LocalDate>): Replay {
        val today = puzzleDay()
        val upToToday = solvedDays.filter { it <= today }
        val replay = walk(upToToday, today)
        if (upToToday.size == solvedDays.size) return replay
        return replay.copy(ledger = replay.ledger.copy(best = walk(solvedDays, today).ledger.best))
    }

    private fun walk(solvedDays: Collection<LocalDate>, today: LocalDate): Replay {
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
        lastSolve?.let { miss(it.day, today) }

        val daysToNext = if (held == MAX_FREEZES) null else ladderLength(step) - run
        return Replay(StreakLedger(current, best, StreakFreezes(held, frozenDays, daysToNext, lastSolve)), step, run)
    }

    /** [step] and [run] stay inside the module: surfaces read only [StreakFreezes.daysToNext]. */
    internal data class Replay(val ledger: StreakLedger, val step: Int, val run: Int)
}

private const val STARTING_FREEZES = 2
private const val MAX_FREEZES = 5

private fun ladderLength(step: Int): Int = when (step) {
    0 -> 7
    1 -> 14
    else -> 28
}
