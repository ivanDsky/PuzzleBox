package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey

/**
 * Strands puzzle identity (ADR-0018, spec A11): an NYT daily (print date + its date-derived
 * number, A10) or a friend's custom puzzle.
 */
data class StrandsPuzzleRef(override val key: PuzzleKey) : PuzzleRef {
    override val gameId: GameId get() = StrandsDefinition.id
}
