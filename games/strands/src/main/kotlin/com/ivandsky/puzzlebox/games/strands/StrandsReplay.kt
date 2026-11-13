package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.MoveMark
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.strands.engine.StrandsHint

/**
 * Strands solve replay: a recorded move runs through the session reducer live play calls for it,
 * so a replayed board cannot drift from the played one. Strands reads no clock and has no undo.
 */
object StrandsReplay : GameReplay<StrandsSession> {

    override fun step(session: StrandsSession, move: ReplayMove, atMs: Long): StrandsSession? {
        val puzzle = session.state.puzzle
        return when (val typed = StrandsReplayMove.of(move, puzzle)) {
            null -> null
            is StrandsReplayMove.Submit -> session.withSubmit(typed.path)
            is StrandsReplayMove.Hint -> hinted(session, typed.wordIn(puzzle), typed.stage)
        }
    }

    /** Only a hint live play could show: the outline of an unfound word, then that word's order. */
    private fun hinted(session: StrandsSession, word: StrandsWord, stage: HintStage): StrandsSession? {
        val state = session.state
        val active = state.activeHint
        val legal = when (stage) {
            HintStage.Outlined -> active == null && word.text !in state.found
            HintStage.OrderRevealed -> active?.word == word.text && active.stage == HintStage.Outlined
        }
        if (!legal) return null
        return session.withHint(StrandsHint.Reveal(ActiveHint(word.text, stage)))
    }

    /**
     * A hint is a Hint mark naming its word's cells by `row * cols + col`, the packing the recorded
     * paths use. A submit is Normal: live play counts no wrong guess (a non-word is never recorded).
     */
    override fun markOf(before: StrandsSession, move: ReplayMove, after: StrandsSession, previous: ReplayMove?): MoveMark {
        val puzzle = after.state.puzzle
        val hint = StrandsReplayMove.of(move, puzzle) as? StrandsReplayMove.Hint ?: return MoveMark.Plain
        val cells = hint.wordIn(puzzle).cells.mapTo(LinkedHashSet()) { it.row * puzzle.cols + it.col }
        return MoveMark(MoveKind.Hint, cells)
    }

    override fun factsOf(initial: StrandsSession): (StrandsSession) -> ReplayFacts {
        val total = initial.state.puzzle.totalWords
        return { session ->
            ReplayFacts(
                hints = session.state.hintsUsed,
                correct = session.state.found.size,
                total = total,
                solved = session.state.isComplete,
            )
        }
    }
}
