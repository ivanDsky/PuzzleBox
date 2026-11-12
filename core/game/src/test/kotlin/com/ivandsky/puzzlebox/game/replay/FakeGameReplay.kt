package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/** A counter that is solved when [count] reaches [target]. [movedAtMs] is the stamp of the last move that changed the count. */
internal data class FakeSession(
    val target: Int,
    val count: Int = 0,
    val hints: Int = 0,
    val assist: Boolean = false,
    val movedAtMs: Long = 0,
) : GameSession

/** A counter game: add, hint and assist are the only moves it understands. */
internal class FakeGameReplay : GameReplay<FakeSession> {

    override fun step(session: FakeSession, move: ReplayMove, atMs: Long): FakeSession? {
        val arg = move.args.singleOrNull()
        return when {
            move.kind == ADD && arg == 0 -> session
            move.kind == ADD && arg != null -> session.copy(count = session.count + arg, movedAtMs = atMs)
            move.kind == HINT && move.args.isEmpty() ->
                session.copy(count = session.count + 1, hints = session.hints + 1, movedAtMs = atMs)
            move.kind == ASSIST && (arg == 0 || arg == 1) ->
                if (session.assist == (arg == 1)) session else session.copy(assist = arg == 1)
            else -> null
        }
    }

    override fun factsOf(initial: FakeSession): (FakeSession) -> ReplayFacts {
        val total = initial.target
        return { session ->
            ReplayFacts(
                hints = session.hints,
                correct = session.count.coerceIn(0, total),
                total = total,
                solved = session.count == total,
            )
        }
    }

    companion object {
        private const val ADD = 0
        private const val HINT = 1
        private const val ASSIST = 2
        private const val UNKNOWN = 3

        /** Adding zero changes nothing. */
        fun add(by: Int) = ReplayMove(ADD, intArrayOf(by))

        fun hint() = ReplayMove(HINT, IntArray(0))

        /** The game's one setting; setting it to what it already is changes nothing. */
        fun assist(on: Boolean) = ReplayMove(ASSIST, intArrayOf(if (on) 1 else 0))

        /** A move the game does not understand. */
        fun unknown() = ReplayMove(UNKNOWN, IntArray(0))
    }
}
