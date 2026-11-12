package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * The one rank-order contract table (ADR-0013; spec A25, A26): a game's [Case.previous]
 * first solve, then a [Case.play] — a play-again of that puzzle, or a first solve of another.
 * Each row states the play's verdict and the score the store's best returns once both are
 * written, so the Room query, [FakeCompletionRepository] and the Result verdict are checked
 * against the same rows and cannot drift apart. The verdict rounds to whole seconds; the
 * stored best does not, so a same-second tie still keeps the faster row.
 */
object RankOrderCases {

    /** The verdict a [Case] expects, free of core:game-ui's RecordVerdict type. */
    enum class Outcome { NewBest, TiedBest, NotBest, PlayAgain }

    data class Case(
        val name: String,
        val order: RankOrder,
        val previous: BestScore,
        val play: BestScore,
        val outcome: Outcome,
        val bestIsPlay: Boolean,
    ) {
        val playAgain: Boolean get() = outcome == Outcome.PlayAgain
        val best: BestScore get() = if (bestIsPlay) play else previous
    }

    private val time = RankOrder.TimeThenHints
    private val hints = RankOrder.HintsThenTime

    val all: List<Case> = listOf(
        Case("time-first: faster wins whatever the hints", time, BestScore(95_000, 0), BestScore(94_000, 3), Outcome.NewBest, bestIsPlay = true),
        Case("time-first: same time ties, fewer hints stays best", time, BestScore(95_000, 0), BestScore(95_000, 2), Outcome.TiedBest, bestIsPlay = false),
        Case("time-first: ms faster in the same second ties", time, BestScore(3_862, 0), BestScore(3_826, 0), Outcome.TiedBest, bestIsPlay = true),
        Case("time-first: slower is not a best", time, BestScore(95_000, 2), BestScore(120_000, 0), Outcome.NotBest, bestIsPlay = false),
        Case("time-first: a faster replay never ranks", time, BestScore(95_000, 0), BestScore(3_000, 0), Outcome.PlayAgain, bestIsPlay = false),
        Case("hints-first: fewer hints wins though slower", hints, BestScore(60_000, 2), BestScore(90_000, 0), Outcome.NewBest, bestIsPlay = true),
        Case("hints-first: more hints loses though faster", hints, BestScore(90_000, 0), BestScore(60_000, 1), Outcome.NotBest, bestIsPlay = false),
        Case("hints-first: equal hints, faster wins", hints, BestScore(95_000, 1), BestScore(94_000, 1), Outcome.NewBest, bestIsPlay = true),
        Case("hints-first: equal hints, same second ties", hints, BestScore(3_862, 1), BestScore(3_826, 1), Outcome.TiedBest, bestIsPlay = true),
        Case("hints-first: equal hints, slower is not a best", hints, BestScore(95_000, 1), BestScore(120_000, 1), Outcome.NotBest, bestIsPlay = false),
        Case("hints-first: a fewer-hints replay never ranks", hints, BestScore(95_000, 2), BestScore(10_000, 0), Outcome.PlayAgain, bestIsPlay = false),
    )
}
