package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** Strands' daily identity (ADR-0004): the print date and its date-derived number (A10). */
data class StrandsPuzzleRef(
    override val date: LocalDate,
    override val puzzleNumber: Int?,
) : PuzzleRef {
    override val gameId: GameId get() = StrandsDefinition.id
}
