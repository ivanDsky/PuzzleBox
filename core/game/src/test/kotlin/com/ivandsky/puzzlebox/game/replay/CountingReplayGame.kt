package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake game for replay tests: a row of marks, solved at [goal] marks. It counts the adapter calls.
 * [Session.trail] depends on the order of the marks and [Session.markedAtMs] on the stamp, so a
 * fold that reorders moves or passes another time cannot produce an equal session.
 */
class CountingReplayGame(private val goal: Int) : GameReplay<CountingReplayGame.Session> {

    data class Session(val marks: Int = 0, val trail: Long = 0, val markedAtMs: Long = -1) : GameSession

    private val steps = AtomicInteger()
    private val factsOfs = AtomicInteger()

    val stepCalls: Int get() = steps.get()
    val factsOfCalls: Int get() = factsOfs.get()

    override fun step(session: Session, move: ReplayMove, atMs: Long): Session? {
        steps.incrementAndGet()
        return when (move.kind) {
            MARK -> Session(session.marks + 1, session.trail * 31 + move.args[0], atMs)
            IDLE -> session
            else -> null
        }
    }

    override fun factsOf(initial: Session): (Session) -> ReplayFacts {
        factsOfs.incrementAndGet()
        return { session -> ReplayFacts(hints = 0, correct = session.marks, total = goal, solved = session.marks == goal) }
    }

    companion object {
        private const val MARK = 0
        private const val IDLE = 1
        private const val UNKNOWN = 2

        fun mark(value: Int) = ReplayMove(MARK, intArrayOf(value))

        /** A move the game understands that changes nothing. */
        val idle = ReplayMove(IDLE, IntArray(0))

        /** A move the game does not understand. */
        val unknown = ReplayMove(UNKNOWN, IntArray(0))
    }
}
