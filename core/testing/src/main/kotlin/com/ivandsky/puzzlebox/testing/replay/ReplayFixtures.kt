package com.ivandsky.puzzlebox.testing.replay

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay

/**
 * Replays of a fake game of any length, built through the real [MoveLog] and codec so a format
 * break shows up in every test that opens one.
 */
object ReplayFixtures {
    val GAME: GameId = GameId("fixture")
    const val SHORT_MOVES: Int = 12
    const val LONG_MOVES: Int = 4_096

    val replay: GameReplay<FixtureSession> = FixtureReplay

    fun initial(total: Int): FixtureSession = FixtureSession(placed = 0, total = total, hints = 0)

    /**
     * [moves] place moves, one every [stepMs]. A hint follows every [hintEvery]-th place at the same
     * stamp (0 = none), so ticks stay one per place. A Solved solve places every piece; a GaveUp one
     * leaves one unplaced.
     */
    fun sealed(
        moves: Int,
        stepMs: Long = 1_000,
        hintEvery: Int = 0,
        outcome: ReplayOutcome = ReplayOutcome.Solved,
    ): SolveReplay {
        require(outcome != ReplayOutcome.Open) { "a fixture is sealed, so it is Solved or GaveUp" }
        val log = MoveLog.fresh(GAME)
        var session = initial(totalFor(moves, outcome))
        for (index in 1..moves) {
            val atMs = index * stepMs
            session = log.play(replay, session, PLACE, atMs)
            if (hintEvery > 0 && index % hintEvery == 0) session = log.play(replay, session, HINT, atMs)
        }
        return checkNotNull(log.seal(outcome, endMs = moves * stepMs)) { "fixture of $moves moves does not seal" }
    }

    fun timeline(
        moves: Int,
        stepMs: Long = 1_000,
        hintEvery: Int = 0,
        outcome: ReplayOutcome = ReplayOutcome.Solved,
    ): Timeline {
        val bytes = sealed(moves, stepMs, hintEvery, outcome)
        return checkNotNull(Timeline.open(GAME, replay, initial(totalFor(moves, outcome)), bytes)) {
            "fixture of $moves moves does not open"
        }
    }

    private fun totalFor(moves: Int, outcome: ReplayOutcome): Int =
        if (outcome == ReplayOutcome.GaveUp) moves + 1 else moves

    private const val PLACE_KIND = 0
    private const val HINT_KIND = 1
    private val PLACE = ReplayMove(PLACE_KIND, IntArray(0))
    private val HINT = ReplayMove(HINT_KIND, IntArray(0))

    private object FixtureReplay : GameReplay<FixtureSession> {
        override fun step(session: FixtureSession, move: ReplayMove, atMs: Long): FixtureSession? =
            when (move.kind) {
                PLACE_KIND -> session.copy(placed = session.placed + 1)
                HINT_KIND -> session.copy(hints = session.hints + 1)
                else -> null
            }

        override fun factsOf(initial: FixtureSession): (FixtureSession) -> ReplayFacts {
            val total = initial.total
            return { session ->
                ReplayFacts(
                    hints = session.hints,
                    correct = session.placed,
                    total = total,
                    solved = session.placed == total,
                )
            }
        }
    }
}
